import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class URLShortnerThread extends Thread {
	static final File WEB_ROOT = new File(".");
	static final String DEFAULT_FILE = "index.html";
	static final String FILE_NOT_FOUND = "404.html";
	static final String METHOD_NOT_SUPPORTED = "not_supported.html";
	static final String REDIRECT_RECORDED = "redirect_recorded.html";
	static final String REDIRECT = "redirect.html";
	static final String NOT_FOUND = "notfound.html";
	static final int remoteport = 1900;

	private Socket connect = null;

	// verbose mode
	static final boolean verbose = false;

	public URLShortnerThread(Socket socket) {
		super("URLShortnerThread");
		this.connect = socket;
	}

	public void run() {
		BufferedReader in = null; PrintWriter out = null; BufferedOutputStream dataOut = null;

		try {
			in = new BufferedReader(new InputStreamReader(connect.getInputStream()));
			out = new PrintWriter(connect.getOutputStream());
			dataOut = new BufferedOutputStream(connect.getOutputStream());

			String input = in.readLine();

			if (input.equals("AddNewServer")) {
				URLShortner.initializeHostnames();
				return;
			}

			if (verbose)System.out.println(URLShortner.hostname + " first line: " + input);
			Pattern pput = Pattern.compile("^PUT\\s+/\\?short=(\\S+)&long=(\\S+)&hash=(\\S+)\\s+(\\S+)$");
			Pattern pupdate = Pattern.compile("^UPDATE\\s+/\\?short=(\\S+)&long=(\\S+)&hash=(\\S+)\\s+(\\S+)$");
			Matcher mput = pput.matcher(input);
			Matcher mupdate = pupdate.matcher(input);

			if (mput.matches() || mupdate.matches()) {
				String shortResource = mput.matches() ? mput.group(1) : mupdate.group(1);
				String longResource = mput.matches() ? mput.group(2) : mupdate.group(2);
				int hashResource = mput.matches() ? Integer.parseInt(mput.group(3)) : Integer.parseInt(mupdate.group(3));
				// String httpVersion=mput.group(4);

				URLShortner.database.save(shortResource, longResource, hashResource);

				File file = new File(WEB_ROOT, REDIRECT_RECORDED);
				int fileLength = (int) file.length();
				String contentMimeType = "text/html";
				// read content to return to client
				byte[] fileData = readFileData(file, fileLength);

				out.println("HTTP/1.1 200 OK");
				out.println("Server: Java HTTP Server/Shortner : 1.0");
				out.println("Date: " + new Date());
				out.println("Content-type: " + contentMimeType);
				out.println("Content-length: " + fileLength);
				out.println();
				out.flush();

				dataOut.write(fileData, 0, fileLength);
				dataOut.flush();

				// Send request to replicate server
				if (mput.matches()) {
					Thread t = new Thread() {
						public void run() {
							try {
								if (verbose) System.out.println("My replicate server " + URLShortner.replicateName);
								Socket connect = new Socket(URLShortner.replicateName, remoteport);
								String reply;
								BufferedReader streamFromServer = new BufferedReader(new InputStreamReader(connect.getInputStream()));
								final OutputStream streamToServer = connect.getOutputStream();

								int spaceIndex = input.indexOf(" ");
								String result = "UPDATE" + input.substring(spaceIndex);
								if (verbose) System.out.println(result);
								streamToServer.write((result + "\n").getBytes(), 0, result.length() + 1);
								streamToServer.flush();
								while ((reply = streamFromServer.readLine()) != null) {
								}
								streamToServer.close();
								streamFromServer.close();
								connect.close();
							} catch (IOException e) {
								System.out.println(e);
							}
						}
					};
					t.start();
				}
			} else {
				Pattern pget = Pattern.compile("^(\\S+)\\s+/(\\S+)\\s+(\\S+)$");
				Matcher mget = pget.matcher(input);
				if (mget.matches()) {
					String method = mget.group(1);
					String shortResource = mget.group(2);
					String httpVersion = mget.group(3);

					String longResource = URLShortner.database.find(shortResource);
					if (longResource != null) {
						File file = new File(WEB_ROOT, REDIRECT);
						int fileLength = (int) file.length();
						String contentMimeType = "text/html";

						// read content to return to client
						byte[] fileData = readFileData(file, fileLength);

						// out.println("HTTP/1.1 301 Moved Permanently");
						out.println("HTTP/1.1 307 Temporary Redirect");
						out.println("Location: " + longResource);
						out.println("Server: Java HTTP Server/Shortner : 1.0");
						out.println("Date: " + new Date());
						out.println("Content-type: " + contentMimeType);
						out.println("Content-length: " + fileLength);
						out.println();
						out.flush();

						dataOut.write(fileData, 0, fileLength);
						dataOut.flush();
					} else {
						File file = new File(WEB_ROOT, FILE_NOT_FOUND);
						int fileLength = (int) file.length();
						String content = "text/html";
						byte[] fileData = readFileData(file, fileLength);

						out.println("HTTP/1.1 404 File Not Found");
						out.println("Server: Java HTTP Server/Shortner : 1.0");
						out.println("Date: " + new Date());
						out.println("Content-type: " + content);
						out.println("Content-length: " + fileLength);
						out.println();
						out.flush();

						dataOut.write(fileData, 0, fileLength);
						dataOut.flush();
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Server error: " + e);
		} finally {
			try {
				in.close();
				out.close();
				connect.close(); // we close socket connection
			} catch (Exception e) {
				System.err.println("Error closing stream : " + e.getMessage());
			}

			if (verbose) {
				System.out.println("Connection closed.\n");
			}
		}
	}

	private static byte[] readFileData(File file, int fileLength) throws IOException {
		FileInputStream fileIn = null;
		byte[] fileData = new byte[fileLength];

		try {
			fileIn = new FileInputStream(file);
			fileIn.read(fileData);
		} finally {
			if (fileIn != null)
				fileIn.close();
		}

		return fileData;
	}
}