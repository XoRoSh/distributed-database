import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class URLShortner {
	// port to listen connection
	static final int PORT = 8080;
	public static ArrayList<Range> hashRange = new ArrayList<>();
	public static final int rangeSize = 10000;
	public static ArrayList<String> hosts = new ArrayList<>();
	static final String path = "../orchestration/hosts";
	static Cache cache = new Cache();

	// private static final int THREAD_POOL_SIZE = 16;
    // private static final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
	private static final ExecutorService threadPool = Executors.newCachedThreadPool();

	// verbose mode
	static final boolean verbose = false;

	private static String newHost;
	private static String prevHost;
	private static String nextHost;
	private static int index = -1;
	private static Range range;
	private static int size;

	public static void main(String[] args) {
		try {
			initializeHosts();
			initializeHashRange();

			ServerSocket clientConnect = new ServerSocket(PORT);
			System.out.println("Server started.\nListening for connections on port : " + PORT + " ...\n");

			// we listen until user halts server execution
			while (true) {
				if (verbose) {
					System.out.println("Connecton opened. (" + new Date() + ")");
				}
                threadPool.submit(new URLShortnerThread(clientConnect.accept()));
			}
		} catch (IOException e) {
			System.err.println("Server Connection error : " + e.getMessage());
		}
		finally {
            // Ensure thread pool is shutdown on server termination
            shutdownThreadPool();
        }
	}
	public static void shutdownThreadPool() {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
                if (!threadPool.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS))
                    System.err.println("Thread pool did not terminate.");
            }
        } catch (InterruptedException ie) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
	public static void initializeHosts() {
		try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
			String host;
			while ((host = reader.readLine()) != null) {
				if (host.charAt(0) == '#')
					continue;
				hosts.add(host);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void addNewServer(String host) {
		if (hosts.contains(host))
			return;

		// Initialize the hosts names and hash ranges
		initializeValues(host);
		copyData();

		if (verbose)
			System.out.println("Before: " + hosts + "\n" + hashRange);

		// Add new host to our list of hosts
		if (index < hosts.size())
			hosts.add(index, newHost);
		else
			hosts.add(host);

		reassignHashRange();
		deleteOldReplica();
		addToFile();
		if (verbose)
			System.out.println("After: " + hosts + "\n" + hashRange);

		try {
			// Update servers replication info
			Socket connect = new Socket(URLShortner.prevHost, URLShortnerThread.remoteport);
			String request = "AddNewServer";
			OutputStream streamToServer = connect.getOutputStream();

			// Send request to server 1
			streamToServer.write((request + "\n").getBytes(), 0, request.length() + 1);
			streamToServer.flush();
			connect.close();
			streamToServer.close();

			// Send to server 2
			connect = new Socket(URLShortner.nextHost, URLShortnerThread.remoteport);
			streamToServer = connect.getOutputStream();
			streamToServer.write((request + "\n").getBytes(), 0, request.length() + 1);
			streamToServer.flush();
			connect.close();
			streamToServer.close();
		} catch (Exception e) {
		}
	}

	public static void initializeValues(String host) {
		newHost = host;
		index = index + 2 > hosts.size() ? 1 : index + 2;
		prevHost = hosts.get(index - 1);
		if (index < hosts.size())
			nextHost = hosts.get(index);
		else
			nextHost = hosts.getFirst();
		range = hashRange.get(index - 1);

		if (verbose)
			System.out.println(String.format("New %s, Replica of %s, Master of %s, Covers %s", newHost, prevHost,
					nextHost, range));
	}

	public static void copyData() {
		try {
			File copyScript = new File("./copyData");
			ProcessBuilder processBuilder = new ProcessBuilder("bash", copyScript.getAbsolutePath(), newHost, prevHost,
					String.valueOf(range.start), String.valueOf(range.end));
			Process process = processBuilder.start();
			int exitCode = process.waitFor();
			if (verbose)
				System.out.println("Exit code: " + exitCode);
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	public static void reassignHashRange() {
		size = (range.end - range.start) / 2;
		Range oldServerRange = new Range(range.start, range.start + size);
		Range newServerRange = new Range(range.start + size + 1, range.end);
		hashRange.set(index - 1, oldServerRange);
		if (index < hosts.size())
			hashRange.add(index, newServerRange);
		else
			hashRange.add(newServerRange);
	}

	public static void deleteOldReplica() {
		try {
			File deleteScript = new File("./deleteData");
			ProcessBuilder processBuilder;
			Process process;
			int exitCode;

			processBuilder = new ProcessBuilder("bash", deleteScript.getAbsolutePath(), nextHost,
					String.valueOf(range.start), String.valueOf(range.start + size));
			process = processBuilder.start();
			exitCode = process.waitFor();
			if (verbose)
				System.out.println("Exit code: " + exitCode);

			processBuilder = new ProcessBuilder("bash", deleteScript.getAbsolutePath(), prevHost,
					String.valueOf(range.start + size + 1), String.valueOf(range.end));
			process = processBuilder.start();
			exitCode = process.waitFor();
			if (verbose)
				System.out.println("Exit code: " + exitCode);
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	public static void addToFile() {
		try {
			File addScript = new File("./addToFile");
			ProcessBuilder processBuilder = new ProcessBuilder("bash", addScript.getAbsolutePath(), newHost,
					String.valueOf(index + 1));
			Process process = processBuilder.start();
			int exitCode = process.waitFor();
			if (verbose)
				System.out.println("Exit code: " + exitCode);
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	public static void initializeHashRange() {
		int size = hosts.size();
		int start = 0;
		int partition = rangeSize / size;
		int end = start + partition - 1;
		for (int i = 0; i < size; i++) {
			Range range = new Range(start, end);
			hashRange.add(range);
			start = end + 1;
			end = start + partition - 1;
		}
	}

	public static class Range {
		private final int start;
		private final int end;

		public Range(int start, int end) {
			this.start = start;
			this.end = end;
		}

		public boolean contains(int number) {
			return number >= start && number <= end;
		}

		public String toString() {
			return "{" + this.start + " - " + this.end + "} ";
		}
	}
}
