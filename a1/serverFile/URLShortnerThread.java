import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.net.Socket;
import java.util.*;
import java.io.*;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.print.DocFlavor.URL;

public class URLShortnerThread extends Thread {
	private Socket client = null;
	static final int remoteport = 1900;
	// verbose mode
	static final boolean verbose = false;

	public URLShortnerThread(Socket socket) {
		super("URLShortnerThread");
		this.client = socket;
	}

	public int[] hashRequest(String shortToHash, int modSize) {
		int primaryRange = Math.abs(shortToHash.hashCode()) % modSize;
		if (verbose) {
			System.out.println(shortToHash.hashCode());
			System.out.println(primaryRange);
		}
		int primaryHostIndex = retrieveHostIndex(primaryRange);
		int secondaryHostIndex = (primaryHostIndex + 1) % URLShortner.hosts.size();

		if (verbose) {
			System.out.println("Primary Server Index: " + primaryHostIndex);
			System.out.println("Replica Server Index: " + secondaryHostIndex);
		}
		return new int[]{ primaryHostIndex, secondaryHostIndex, primaryRange};
	}

	public void run() {
		Socket server = null;
		try {
			BufferedReader streamFromClient = new BufferedReader(new InputStreamReader(client.getInputStream()));
			final OutputStream streamToClient = client.getOutputStream();

			String requestLine = streamFromClient.readLine();
			var parseURI = getShortFromUri(requestLine);
			var method = parseURI.first(); // 0 = GET Request, 1 = PUT Request
			var shortFromUri = parseURI.second();

			// Retrive from cache only if its a GET Request
			if (method == 0) {
				String cachedResponse = URLShortner.cache.get(shortFromUri);
				if (cachedResponse != null) {
					streamToClient.write(cachedResponse.getBytes(), 0, cachedResponse.length());
					streamToClient.flush();
					streamFromClient.close();
					streamToClient.close();
					client.close();
					return;
				}

			} else if (method == 1) {
				if (verbose)
					System.out.println("Deleting from cache ");
				URLShortner.cache.remove(shortFromUri);
				long currentTimestamp = System.currentTimeMillis();
				var log = shortFromUri + " " + String.valueOf(currentTimestamp);
				if (verbose) System.out.println(Paths.get("a1/logging/log.txt").toAbsolutePath());
				var path = Paths.get("../logging/log.txt").toAbsolutePath();

				// try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(String.valueOf(path), true))) {
				// 	bufferedWriter.write(log + "\n");
				// } catch (Exception e) {
				// 	System.out.println("Couldn't log PUT request");
				// }
			} else if (method == 2) {
				addNewServer(shortFromUri);
				return;
			} else {
				System.out.println("Invalid Request Method");
				return;
			}

			int nOfServers = URLShortner.hosts.size();
			if (verbose) {
				System.out.println("N of servers is: " + nOfServers);
			}

			assert shortFromUri != null;
			int[] serverIndices = hashRequest(shortFromUri, URLShortner.rangeSize);
			int primaryIndex = serverIndices[0];
			int secondaryIndex = serverIndices[1];
			int hashValue = serverIndices[2];

			String primaryHostname = URLShortner.hosts.get(primaryIndex);
			String secondaryHostname = URLShortner.hosts.get(secondaryIndex);

			if (verbose) {
				System.out.println("Primary Hostname: " + primaryHostname);
				System.out.println("Replica Hostname: " + secondaryHostname);
			}
			// Make a connection to the real server.
			// If we cannot connect to the server, send an error to the
			// client, disconnect, and continue waiting for connections.
			String hostname;
			String backup;

			// Put request should go to primary server unless it goes down
			if (method == 1) {
				// put request
				hostname = primaryHostname;
				backup = secondaryHostname;

				// Add the hash value to the PUT request to store in database
				int index = requestLine.indexOf(" ") + 1;
				index = requestLine.indexOf(" ", index);
				StringBuilder sb = new StringBuilder(requestLine);
        		sb.insert(index, "&hash=" + hashValue);
				requestLine = sb.toString();
			} else {
				// get request
				// randomise which server takes the get request for equal distribution
				int randomNumber = (int) (Math.random() * 2); // Generates 0 or 1
				if (randomNumber == 0) {
					hostname = primaryHostname;
					backup = secondaryHostname;
				} else {
					hostname = secondaryHostname;
					backup = primaryHostname;
				}
			}
			try {
				server = new Socket(hostname, remoteport);
				if (verbose) System.out.println(hostname);
			} catch (IOException e) {
				try {
					server = new Socket(backup, remoteport);
					if (verbose) System.out.println(backup);
				} catch (IOException er) {
					PrintWriter out = new PrintWriter(streamToClient);
					out.print("Proxy server cannot connect to host :"
							+ remoteport + ":\n" + er + "\n");
					out.flush();
					client.close();
				}
			}

			// Get server streams.
			BufferedReader streamFromServer = new BufferedReader(new InputStreamReader(server.getInputStream()));
			final OutputStream streamToServer = server.getOutputStream();

			// Send request to server
			streamToServer.write((requestLine + "\n").getBytes(), 0, requestLine.length() + 1);
			streamToServer.flush();

			// Read the server's responses
			// and pass them back to the client.
			try {
				String reply, cacheResponse = "";
				while ((reply = streamFromServer.readLine()) != null) {
					streamToClient.write((reply + "\n").getBytes(), 0, reply.length() + 1);
					cacheResponse += reply + "\n";

				}
				streamToClient.flush();
				// Cache Response only if it's a GET Request
				if (method == 0) {
					URLShortner.cache.put(shortFromUri, cacheResponse);
				}
			} catch (IOException e) {
			}
			streamToClient.close();

		} catch (IOException e) {
			System.err.println(e);
		} finally {
			try {
				if (server != null)
					server.close();
				if (client != null)
					client.close();
			} catch (IOException e) {
			}
		}
	}

	record Tuple<T1, T2>(T1 first, T2 second) {
	}

	private Tuple<Integer, String> getShortFromUri(String requestLine) {
		String[] requestParts = requestLine.split(" ");
		String method = requestParts[0];
		String uri = requestParts[1];
		if (method.equals("PUT")) {
			if (verbose)
				System.out.println("PUT request received");
			return new Tuple<>(1, parseShortResourceFromPUT(uri));
		} else if (method.equals("GET")) {
			if (verbose)
				System.out.println("GET request received");
			return new Tuple<>(0, parseShortResourceFromGET(uri));
		} else if (method.equals("AddNewServer"))
			return new Tuple<>(2, uri);
		else {
			if (verbose)
				System.out.println("Unsupported request");
			return new Tuple<>(-1, null);
		}
	}

	private String parseShortResourceFromGET(String uri) {
		String[] uriParts = uri.split("/"); // Split by "/"
		if (uriParts.length > 1) {
			return uriParts[1]; // This will be shortResource
		}
		return null;
	}

	private String parseShortResourceFromPUT(String uri) {

		int shortStart = uri.indexOf("short=") + 6;
		int shortEnd = uri.indexOf("&", shortStart);
		if (shortEnd != -1) {
			return uri.substring(shortStart, shortEnd);
		} else {
			System.out.println("WRONG PUT REQUEST FORMAT!");
			return null;
		}
	}

	private int retrieveHostIndex(int key) {
		for (int i = 0; i < URLShortner.hashRange.size(); i++) {
			if (URLShortner.hashRange.get(i).contains(key)) {
				return i;
			}
		}
		return -1;
	}

	public void addNewServer(String uri) {
		int serverStart = uri.indexOf("server=") + 7;
		if (verbose) System.out.println(uri.substring(serverStart)) ;
		URLShortner.addNewServer(uri.substring(serverStart));
	}
}