package udp;

import udp.PerformanceMetrics;
import udp.ChunkAcknowledgment;
import udp.VideoChunk;
import udp.SerializationUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Client for receiving video streams over UDP with selective repeat ARQ for critical frames.
 */
public class VideoStreamingClient {
    
    private final int clientPort;
    private final int serverAckPort;
    private final String outputPath;
    private DatagramSocket receiveSocket;
    private DatagramSocket sendSocket;
    private volatile boolean running;
    private ExecutorService executorService;
    private final PerformanceMetrics metrics;
    
    // Map to store received chunks for ordered processing
    private final ConcurrentMap<Integer, VideoChunk> receivedChunks = new ConcurrentHashMap<>();
    private final ConcurrentSkipListSet<Integer> processedSequenceNumbers = new ConcurrentSkipListSet<>();
    private int nextExpectedSequence = 0;
    
    // Maximum out-of-order buffer size
    private static final int MAX_BUFFER_SIZE = 100;
    
    /**
     * Constructor for VideoStreamingClient.
     * 
     * @param clientPort The port to listen on for incoming video chunks
     * @param serverAddress The server's IP address
     * @param serverAckPort The server's port for receiving acknowledgments
     * @param outputPath Path where received video will be saved
     */
    public VideoStreamingClient(int clientPort, String serverAddress, int serverAckPort, String outputPath) {
        this.clientPort = clientPort;
        this.serverAckPort = serverAckPort;
        this.outputPath = outputPath;
        this.metrics = PerformanceMetrics.getInstance();
    }
    
    /**
     * Start the client and begin receiving video.
     * 
     * @throws SocketException If a socket error occurs
     */
    public void start() throws SocketException {
        receiveSocket = new DatagramSocket(clientPort);
        sendSocket = new DatagramSocket();
        running = true;
        executorService = Executors.newFixedThreadPool(2);
        
        // Create output directory if it doesn't exist
        File outputDir = new File(outputPath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        System.out.println("Video streaming client started on port " + clientPort);
        System.out.println("Sending acknowledgments to port " + serverAckPort);
        
        // Start the chunk receiver thread
        executorService.submit(this::receiveVideoChunks);
        
        // Start the chunk processor thread
        executorService.submit(this::processChunks);
    }
    
    /**
     * Stop the client.
     */
    public void stop() {
        running = false;
        
        if (receiveSocket != null && !receiveSocket.isClosed()) {
            receiveSocket.close();
        }
        
        if (sendSocket != null && !sendSocket.isClosed()) {
            sendSocket.close();
        }
        
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.println(metrics.getMetricsSummary());
        System.out.println("Video streaming client stopped");
    }
    
    /**
     * Receive video chunks from the server.
     */
    private void receiveVideoChunks() {
        byte[] buffer = new byte[64 * 1024 + 1000]; // Buffer large enough for a chunk plus overhead
        
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiveSocket.receive(packet);
                
                metrics.recordBytesReceived(packet.getLength());
                
                // Deserialize the chunk
                Object obj = SerializationUtil.deserialize(Arrays.copyOf(buffer, packet.getLength()));
                if (obj instanceof VideoChunk) {
                    VideoChunk chunk = (VideoChunk) obj;
                    handleVideoChunk(chunk, packet.getAddress());
                }
            } catch (IOException | ClassNotFoundException e) {
                if (running) {
                    System.err.println("Error receiving video chunk: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Handle a received video chunk.
     * 
     * @param chunk The video chunk to handle
     * @param serverAddress The server's IP address
     * @throws IOException If an I/O error occurs
     */
    private void handleVideoChunk(VideoChunk chunk, InetAddress serverAddress) throws IOException {
        int seqNum = chunk.getSequenceNumber();
        System.out.println("Received chunk #" + seqNum + 
                " (" + chunk.getSize() + " bytes)" +
                (chunk.isCriticalFrame() ? " [CRITICAL]" : ""));
        
        // Add to received chunks map for ordered processing
        receivedChunks.put(seqNum, chunk);
        
        // Send acknowledgment for critical frames
        if (chunk.needsAcknowledgment()) {
            sendAcknowledgment(chunk.getSequenceNumber(), serverAddress);
        }
        
        // Trigger processing if this is the next expected sequence number
        if (seqNum == nextExpectedSequence) {
            synchronized (receivedChunks) {
                receivedChunks.notifyAll();
            }
        }
        
        // Clean up if buffer gets too large (this is a simple approach)
        if (receivedChunks.size() > MAX_BUFFER_SIZE) {
            cleanupOldChunks();
        }
    }
    
    /**
     * Clean up old chunks that are no longer needed.
     */
    private void cleanupOldChunks() {
        // Simple cleanup: remove chunks with sequence numbers much lower than current
        List<Integer> toRemove = new ArrayList<>();
        int threshold = nextExpectedSequence - MAX_BUFFER_SIZE/2;
        
        for (Map.Entry<Integer, VideoChunk> entry : receivedChunks.entrySet()) {
            if (entry.getKey() < threshold) {
                toRemove.add(entry.getKey());
            }
        }
        
        for (Integer seqNum : toRemove) {
            receivedChunks.remove(seqNum);
        }
        
        if (!toRemove.isEmpty()) {
            System.out.println("Cleaned up " + toRemove.size() + " old chunks");
        }
    }
    
    /**
     * Send an acknowledgment for a received chunk.
     * 
     * @param sequenceNumber The sequence number to acknowledge
     * @param serverAddress The server's IP address
     * @throws IOException If an I/O error occurs
     */
    private void sendAcknowledgment(int sequenceNumber, InetAddress serverAddress) throws IOException {
        ChunkAcknowledgment ack = new ChunkAcknowledgment(sequenceNumber, System.currentTimeMillis());
        byte[] data = SerializationUtil.serialize(ack);
        
        DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverAckPort);
        sendSocket.send(packet);
        
        System.out.println("Sent ACK for chunk #" + sequenceNumber);
    }
    
    /**
     * Process received chunks in sequence.
     */
    private void processChunks() {
        try {
            File outputFile = new File(outputPath, "received_video.mp4");
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                
                while (running) {
                    VideoChunk chunk = null;
                    
                    // Wait for chunks if none are available
                    if (!receivedChunks.containsKey(nextExpectedSequence)) {
                        synchronized (receivedChunks) {
                            receivedChunks.wait(100);
                        }
                        continue;
                    }
                    
                    // Process chunks in sequence as much as possible
                    while ((chunk = receivedChunks.get(nextExpectedSequence)) != null) {
                        // Write the chunk data to file
                        fos.write(chunk.getData());
                        fos.flush();
                        
                        // Mark as processed and remove from buffer
                        processedSequenceNumbers.add(nextExpectedSequence);
                        receivedChunks.remove(nextExpectedSequence);
                        
                        System.out.println("Processed chunk #" + nextExpectedSequence);
                        nextExpectedSequence++;
                        
                        // Calculate metrics for this chunk
                        long latency = System.currentTimeMillis() - chunk.getTimestamp();
                        metrics.recordLatency(latency);
                    }
                    
                    // Handle missing chunks - if we're waiting too long, skip if non-critical
                    if (!receivedChunks.containsKey(nextExpectedSequence)) {
                        // Check if we have any higher sequence numbers
                        boolean hasHigherSequence = false;
                        for (Integer seqNum : receivedChunks.keySet()) {
                            if (seqNum > nextExpectedSequence) {
                                hasHigherSequence = true;
                                break;
                            }
                        }
                        
                        // Skip missing chunk if we have higher ones and have waited long enough
                        // Low-latency optimization: skip non-critical frames to maintain playback speed
                        if (hasHigherSequence) {
                            System.out.println("Skipping missing chunk #" + nextExpectedSequence);
                            nextExpectedSequence++;
                        }
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            if (running) {
                System.err.println("Error processing video chunks: " + e.getMessage());
            }
        }
    }
    
    /**
     * Main method for running the client as a standalone application.
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        int clientPort = 4446;  // Client listens on 4446
        String serverAddress = "127.0.0.1";
        int serverAckPort = 4445;  // Server's ACK port is 4445
        String outputPath = "./received_videos";
        
        // Parse command line arguments if provided
        if (args.length >= 1) {
            try {
                clientPort = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid client port: " + args[0]);
                return;
            }
        }
        
        if (args.length >= 2) {
            serverAddress = args[1];
        }
        
        if (args.length >= 3) {
            try {
                serverAckPort = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid server acknowledgment port: " + args[2]);
                return;
            }
        }
        
        if (args.length >= 4) {
            outputPath = args[3];
        }
        
        VideoStreamingClient client = new VideoStreamingClient(clientPort, serverAddress, serverAckPort, outputPath);
        
        try {
            client.start();
            System.out.println("Video streaming client started on port " + clientPort);
            System.out.println("Connected to server at " + serverAddress);
            System.out.println("Server acknowledgment port: " + serverAckPort);
            System.out.println("Saving received video to: " + outputPath);
            
            // Wait for user to stop the client
            System.out.println("\nPress ENTER to stop the client");
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();
            
        } catch (SocketException e) {
            System.err.println("Error starting client: " + e.getMessage());
        } finally {
            client.stop();
        }
    }
} 