import java.util.Date;
import java.util.concurrent.*;

public class Cache {
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static ConcurrentHashMap<String, Node> cache = new ConcurrentHashMap();
    static LinkedBlockingQueue<String> keys = new LinkedBlockingQueue<>();
    private final long expiration = 60000; // Cache expires in 60000 milliseconds = 1 minute
    private final int capacity = 100000; // Cache maximum size

    public Cache() {
        cleanCache();
    }

    // Start a thread that periodically removes expired cached items
    public void cleanCache() {
        Thread t = new Thread() {
            public void run() {
                while (true) {
                    long currentTime = new Date().getTime();
                    for (String key : cache.keySet()) {
                        if (currentTime > (cache.get(key).time + expiration)) {
                            cache.remove(key);
                            keys.remove(key);
                        }
                    }
                    try {
                        Thread.sleep(expiration / 2);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        t.start();
    }

    // Add request data to the cache
    public void put(String request, String response) {
        if (cache.size() >= capacity)
            evictRandom();
        Node node = new Node(request, response);
        cache.put(request, node);
        keys.add(request);
    }

    public void evictRandom() {
        String keyToRemove = keys.poll();
        cache.remove(keyToRemove);
    }

    // Get request data from the cache
    public String get(String request) {
        Node node = cache.get(request);
        if (node == null)
            return null;
        node.updateExpiration();
        return node.response;
    }

    // Remove a cached data
    public void remove(String request) {
        Node removed = cache.remove(request);
        if (removed != null)
            keys.remove(request);
    }

    // A class to store request data
    private class Node {
        String request, response;
        long time;

        public Node(String request, String response) {
            this.request = request;
            this.response = response;
            this.time = new Date().getTime();
        }

        public void updateExpiration() {
            this.time = new Date().getTime();
        }

        public String toString() {
            return "Request: " + this.request + " Time: " + this.time;
        }
    }
}