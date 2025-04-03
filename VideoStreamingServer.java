package udp;

import udp.PerformanceMetrics;
import udp.ChunkAcknowledgment;
import udp.VideoChunk;
import udp.SerializationUtil;
import udp.VideoChunker;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * UDP-based video streaming server that streams video chunks to clients.
 */
public class VideoStreamingServer {
    
    private final int serverPort;
    private final int ackPort;
    private final int chunkSize;
    private final int packetLossRate; // Simulated packet loss rate in percentage (0-100)
    private DatagramSocket sendSocket;
    private DatagramSocket receiveSocket;
    private volatile boolean running;
    private ExecutorService executorService;
    private final PerformanceMetrics metrics;
    
    // Map to track unacknowledged critical frames
    private final ConcurrentHashMap<Integer, VideoChunk> unacknowledgedChunks = new ConcurrentHashMap<>();
    // Timeout for retransmission (ms)
    private final long retransmissionTimeout = 1000;
    
    /**
     * Constructor for VideoStreamingServer.
     * 
     * @param serverPort The port to listen on for client connections
     * @param ackPort The port to listen on for acknowledgments
     * @param chunkSize The size of video chunks in bytes
     * @param packetLossRate Simulated packet loss rate for testing (0-100)
     */
    public VideoStreamingServer(int serverPort, int ackPort, int chunkSize, int packetLossRate) {
        this.serverPort = serverPort;
        this.ackPort = ackPort;
        // Ensure chunk size is within safe limits
        this.chunkSize = Math.min(chunkSize, VideoChunker.getMaxSafeChunkSize());
        this.packetLossRate = Math.min(100, Math.max(0, packetLossRate)); // Ensure 0-100 range
        this.metrics = PerformanceMetrics.getInstance();
    }
    
    /**
     * Start the streaming server.
     * 
     * @throws IOException If an I/O error occurs
     */
    public void start() throws IOException {
        sendSocket = new DatagramSocket(serverPort);
        receiveSocket = new DatagramSocket(ackPort);
        running = true;
        executorService = Executors.newFixedThreadPool(3);
        
        System.out.println("Video streaming server started on port " + serverPort);
        System.out.println("Acknowledgment receiver started on port " + ackPort);
        System.out.println("Simulated packet loss rate: " + packetLossRate + "%");
        
        // Start the acknowledgment receiver thread
        executorService.submit(this::receiveAcknowledgments);
        
        // Start the retransmission scheduler
        executorService.submit(this::retransmissionScheduler);
    }
    
    /**
     * Stop the streaming server.
     */
    public void stop() {
        running = false;
        
        if (sendSocket != null && !sendSocket.isClosed()) {
            sendSocket.close();
        }
        
        if (receiveSocket != null && !receiveSocket.isClosed()) {
            receiveSocket.close();
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
        System.out.println("Video streaming server stopped");
    }
    
    /**
     * Stream a video file to a client.
     * 
     * @param videoFile The video file to stream
     * @param clientAddress The client's IP address
     * @param clientPort The client's port
     * @throws IOException If an I/O error occurs
     */
    public void streamVideo(File videoFile, InetAddress clientAddress, int clientPort) throws IOException {
        if (!videoFile.exists() || !videoFile.canRead()) {
            throw new IOException("Video file doesn't exist or cannot be read: " + videoFile.getPath());
        }
        
        System.out.println("Streaming video " + videoFile.getName() + " to " + clientAddress.getHostAddress() + ":" + clientPort);
        
        try (RandomAccessFile raf = new RandomAccessFile(videoFile, "r")) {
            int sequenceNumber = 0;
            VideoChunk chunk;
            
            // Print initial information
            long fileSize = videoFile.length();
            int estimatedChunks = (int) Math.ceil((double) fileSize / chunkSize);
            System.out.println("\nVideo file size: " + fileSize + " bytes");
            System.out.println("Using chunk size: " + chunkSize + " bytes");
            System.out.println("Estimated number of chunks: " + estimatedChunks);
            
            metrics.reset(); // Reset metrics for this streaming session
            
            while (running && (chunk = VideoChunker.readNextChunkFromStream(raf, chunkSize, sequenceNumber++)) != null) {
                sendVideoChunk(chunk, clientAddress, clientPort);
                
                // Add to unacknowledged map if it's a critical frame
                if (chunk.isCriticalFrame()) {
                    unacknowledgedChunks.put(chunk.getSequenceNumber(), chunk);
                }
                
                // Internal feedback for single-server testing
                if (!shouldSimulatePacketLoss()) {
                    // Record received bytes for metrics tracking
                    metrics.recordBytesReceived(chunk.getData().length);
                    
                    // Process acknowledgment for critical frames
                    if (chunk.isCriticalFrame()) {
                        // Handle acknowledgment internally
                        handleAcknowledgment(new ChunkAcknowledgment(chunk.getSequenceNumber(), System.currentTimeMillis()));
                    }
                }
                
                // Simulate real-time streaming rate
                Thread.sleep(33); // ~30 fps
            }
            
            System.out.println("\nFinished streaming video " + videoFile.getName());
            System.out.println(metrics.getMetricsSummary());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Streaming interrupted: " + e.getMessage());
        }
    }
    
    /**
     * Send a video chunk to the client.
     * 
     * @param chunk The video chunk to send
     * @param clientAddress The client's IP address
     * @param clientPort The client's port
     * @throws IOException If an I/O error occurs
     */
    private void sendVideoChunk(VideoChunk chunk, InetAddress clientAddress, int clientPort) throws IOException {
        // Simulate packet loss for testing
        if (shouldSimulatePacketLoss()) {
            System.out.println("Simulating packet loss for chunk #" + chunk.getSequenceNumber() +
                    (chunk.isCriticalFrame() ? " (CRITICAL)" : ""));
            metrics.recordPacketLoss(chunk.isCriticalFrame());
            return;
        }
        
        byte[] data = SerializationUtil.serialize(chunk);
        DatagramPacket packet = new DatagramPacket(data, data.length, clientAddress, clientPort);
        sendSocket.send(packet);
        
        metrics.recordBytesSent(data.length);
        
        System.out.println("Sent chunk #" + chunk.getSequenceNumber() + 
                " (" + data.length + " bytes)" +
                (chunk.isCriticalFrame() ? " [CRITICAL]" : ""));
    }
    
    /**
     * Receive acknowledgments from clients.
     */
    private void receiveAcknowledgments() {
        byte[] buffer = new byte[1024]; // Buffer for receiving ACKs
        
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiveSocket.receive(packet);
                
                Object obj = SerializationUtil.deserialize(Arrays.copyOf(buffer, packet.getLength()));
                if (obj instanceof ChunkAcknowledgment) {
                    ChunkAcknowledgment ack = (ChunkAcknowledgment) obj;
                    handleAcknowledgment(ack);
                }
            } catch (IOException | ClassNotFoundException e) {
                if (running) {
                    System.err.println("Error receiving acknowledgment: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Handle an acknowledgment from a client.
     * 
     * @param ack The acknowledgment to handle
     */
    private void handleAcknowledgment(ChunkAcknowledgment ack) {
        VideoChunk chunk = unacknowledgedChunks.remove(ack.getSequenceNumber());
        
        if (chunk != null) {
            long latency = System.currentTimeMillis() - chunk.getTimestamp();
            metrics.recordLatency(latency);
            
            System.out.println("Received ACK for chunk #" + ack.getSequenceNumber() + 
                    " (latency: " + latency + " ms)");
        }
    }
    
    /**
     * Periodically check for unacknowledged chunks and retransmit if needed.
     */
    private void retransmissionScheduler() {
        while (running) {
            try {
                // Check for expired timeouts
                long currentTime = System.currentTimeMillis();
                List<VideoChunk> chunksToRetransmit = new ArrayList<>();
                
                for (VideoChunk chunk : unacknowledgedChunks.values()) {
                    if (currentTime - chunk.getTimestamp() > retransmissionTimeout) {
                        chunksToRetransmit.add(chunk);
                    }
                }
                
                // Retransmit expired chunks
                for (VideoChunk chunk : chunksToRetransmit) {
                    // Update timestamp and retransmit
                    VideoChunk updatedChunk = new VideoChunk(
                            chunk.getSequenceNumber(),
                            chunk.getData(),
                            chunk.isCriticalFrame(),
                            System.currentTimeMillis()
                    );
                    
                    // Update in the map
                    unacknowledgedChunks.put(chunk.getSequenceNumber(), updatedChunk);
                    
                    // Log retransmission event
                    System.out.println("Retransmitting chunk #" + chunk.getSequenceNumber());
                    
                    // Note: In a multi-client implementation, retransmission
                    // would be directed to the specific client that missed the chunk
                }
                
                // Sleep to prevent excessive CPU usage
                Thread.sleep(100);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Applies controlled packet loss based on the configured rate.
     * 
     * @return True if the packet should be dropped
     */
    private boolean shouldSimulatePacketLoss() {
        return Math.random() * 100 < packetLossRate;
    }
    
    /**
     * Main method for running the server as a standalone application.
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        int serverPort = 4444;  // Server sends video on 4444
        int ackPort = 4445;     // Server receives ACKs on 4445
        int chunkSize = VideoChunker.getDefaultChunkSize();
        int packetLossRate = 15; // 15% packet loss by default
        
        // Parse command line arguments if provided
        if (args.length >= 1) {
            try {
                serverPort = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid server port: " + args[0]);
                return;
            }
        }
        
        if (args.length >= 2) {
            try {
                ackPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid acknowledgment port: " + args[1]);
                return;
            }
        }
        
        if (args.length >= 3) {
            try {
                chunkSize = Integer.parseInt(args[2]);
                if (chunkSize > VideoChunker.getMaxSafeChunkSize()) {
                    System.err.println("Warning: Requested chunk size " + chunkSize + 
                        " bytes is too large. Using maximum safe size of " + 
                        VideoChunker.getMaxSafeChunkSize() + " bytes.");
                    chunkSize = VideoChunker.getMaxSafeChunkSize();
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid chunk size: " + args[2]);
                return;
            }
        }
        
        if (args.length >= 4) {
            try {
                packetLossRate = Integer.parseInt(args[3]);
                if (packetLossRate < 0 || packetLossRate > 100) {
                    System.err.println("Packet loss rate must be between 0 and 100");
                    return;
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid packet loss rate: " + args[3]);
                return;
            }
        }
        
        VideoStreamingServer server = new VideoStreamingServer(serverPort, ackPort, chunkSize, packetLossRate);
        
        try {
            server.start();
            System.out.println("\nServer Configuration:");
            System.out.println("- Video streaming port: " + serverPort);
            System.out.println("- Acknowledgment port: " + ackPort);
            System.out.println("- Chunk size: " + chunkSize + " bytes");
            System.out.println("- Simulated packet loss: " + packetLossRate + "%\n");
            
            // Ask for the video file path
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter the path to the video file: ");
            String videoPath = scanner.nextLine();
            
            File videoFile = new File(videoPath);
            if (!videoFile.exists() || !videoFile.canRead()) {
                System.err.println("Video file doesn't exist or cannot be read: " + videoPath);
                server.stop();
                return;
            }
            
            // Ask for the client address and port
            System.out.print("Enter the client IP address (default: 127.0.0.1): ");
            String clientAddressStr = scanner.nextLine();
            if (clientAddressStr.isEmpty()) {
                clientAddressStr = "127.0.0.1";
            }
            
            System.out.print("Enter the client port (default: 4446): ");
            String clientPortStr = scanner.nextLine();
            int clientPort = clientPortStr.isEmpty() ? 4446 : Integer.parseInt(clientPortStr);
            
            InetAddress clientAddress = InetAddress.getByName(clientAddressStr);
            
            // Stream the video
            server.streamVideo(videoFile, clientAddress, clientPort);
            
            // Wait for user to stop the server
            System.out.println("Press ENTER to stop the server");
            scanner.nextLine();
            
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        } finally {
            server.stop();
        }
    }
} 