import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Date;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class URLShortner { 
	
	static final File WEB_ROOT = new File(".");
	static final String DEFAULT_FILE = "index.html";
	static final String FILE_NOT_FOUND = "404.html";
	static final String METHOD_NOT_SUPPORTED = "not_supported.html";
	static final String REDIRECT_RECORDED = "redirect_recorded.html";
	static final String REDIRECT = "redirect.html";
	static final String NOT_FOUND = "notfound.html";
	static URLShortnerDB database=null;
	static String hostname = "";
	static String masterName = "";
	static String replicateName = "";
	static final String path = "../orchestration/hosts";
	// port to listen connection
	static final int PORT = 1900;

	// private static final int THREAD_POOL_SIZE = 24;
    // private static final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
	private static final ExecutorService threadPool = Executors.newCachedThreadPool();

	// verbose mode
	static final boolean verbose = false;

	public static void main(String[] args) {
		initializeHostnames();

		database = new URLShortnerDB();
		try {
			ServerSocket serverConnect = new ServerSocket(PORT);
			System.out.println("Server started.\nListening for connections on port : " + PORT + " ...\n");
			
			// we listen until user halts server execution
			while (true) {
				if (verbose) { System.out.println("Connecton opened. (" + new Date() + ")"); }
				threadPool.submit(new URLShortnerThread(serverConnect.accept()));
			}
		} catch (IOException e) {
			System.err.println("Server Connection error : " + e.getMessage());
		}
	}

	public static void initializeHostnames() {
		try {
			// Get this server's address
			InetAddress inetAddress = InetAddress.getLocalHost();
			hostname = inetAddress.getHostName();

			ArrayList<String> lines = new ArrayList<>();
			int index = 0;
			try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
				String line;
				int i = 0;
				while ((line = reader.readLine()) != null) {
					lines.add(line);
					if (line.equals(hostname)) index = i;
					i++;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			// Set replica servers address
			replicateName = index + 1 < lines.size() ? lines.get(index + 1) : lines.getFirst();
			masterName = index - 1 >= 0 ? lines.get(index - 1) : lines.getLast();
			if (verbose) System.out.println("I am: "+ hostname+" master of: "+ replicateName+" copy of: "+ masterName);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}
}
