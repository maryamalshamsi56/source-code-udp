package udp;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Utility for comparing performance metrics at different packet loss rates.
 */
public class PerformanceComparator {
    
    private static final int[] PACKET_LOSS_RATES = {5, 15, 25}; // The packet loss rates to test
    private static final int SERVER_PORT = 4444;
    private static final int ACK_PORT = 4445;
    private static final int CLIENT_PORT = 4446;
    private static final int CHUNK_SIZE = 64 * 1024; // 64KB chunks
    private static final String CLIENT_ADDRESS = "127.0.0.1";
    
    // Store metrics for each packet loss rate
    private static final Map<Integer, MetricsResult> metricsResults = new HashMap<>();
    
    /**
     * Main method for running the performance comparison.
     * 
     * @param args Command line arguments (path to video file)
     */
    public static void main(String[] args) {
        String videoPath;
        
        if (args.length > 0) {
            videoPath = args[0];
        } else {
            // Ask for the video file path
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter the path to the video file: ");
            videoPath = scanner.nextLine();
        }
        
        File videoFile = new File(videoPath);
        if (!videoFile.exists() || !videoFile.canRead()) {
            System.err.println("Video file doesn't exist or cannot be read: " + videoPath);
            return;
        }
        
        System.out.println("Starting performance comparison with video: " + videoFile.getName());
        System.out.println("Will test with packet loss rates: 5%, 15%, and 25%");
        System.out.println("-------------------------------------------------");
        
        // Run tests for each packet loss rate
        for (int lossRate : PACKET_LOSS_RATES) {
            System.out.println("\nRunning test with " + lossRate + "% packet loss rate");
            runTest(videoFile, lossRate);
            
            // Wait a bit between tests
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.println("\nPerformance comparison completed!");
        
        // Display results in a formatted table
        displayComparativeTable();
    }
    
    /**
     * Run a test with the specified packet loss rate.
     * 
     * @param videoFile The video file to stream
     * @param packetLossRate The packet loss rate to simulate
     */
    private static void runTest(File videoFile, int packetLossRate) {
        VideoStreamingServer server = new VideoStreamingServer(
                SERVER_PORT, ACK_PORT, CHUNK_SIZE, packetLossRate);
        
        try {
            // Reset metrics for this test
            PerformanceMetrics.getInstance().reset();
            
            // Start the server
            server.start();
            
            // Self-contained test using loopback interface
            // The server's internal metrics collection will record performance data
            
            // Stream the video to the loopback address
            InetAddress clientAddress = InetAddress.getByName(CLIENT_ADDRESS);
            server.streamVideo(videoFile, clientAddress, CLIENT_PORT);
            
            // Store the metrics for this packet loss rate
            double throughput = PerformanceMetrics.getInstance().calculateThroughput() / 1024; // KB/s
            double latency = PerformanceMetrics.getInstance().calculateAverageLatency();
            double criticalFrameLoss = PerformanceMetrics.getInstance().calculateCriticalFrameLossRate();
            
            // Store the measured metrics for this packet loss rate
            metricsResults.put(packetLossRate, new MetricsResult(throughput, latency, criticalFrameLoss));
            
            // Print the results
            System.out.println("\nResults for " + packetLossRate + "% packet loss rate:");
            System.out.println(PerformanceMetrics.getInstance().getMetricsSummary());
            
        } catch (IOException e) {
            System.err.println("Error during test: " + e.getMessage());
        } finally {
            server.stop();
        }
    }
    
    /**
     * Display a comparative table of metrics across different packet loss rates.
     */
    private static void displayComparativeTable() {
        System.out.println("\nComparative analysis table based on actual measured test results across varying packet loss");
        System.out.println("rates. These metrics were collected using the PerformanceComparator class:");
        
        // Table header
        String header = String.format("| %-15s | %-25s | %-20s | %-20s |", 
                "Packet Loss Rate", "Average Throughput (KB/s)", "Average Latency (ms)", "Critical Frame Loss (%)");
        String separator = "-".repeat(header.length());
        
        System.out.println(separator);
        System.out.println(header);
        System.out.println(separator);
        
        // Table rows
        for (int rate : PACKET_LOSS_RATES) {
            MetricsResult result = metricsResults.get(rate);
            if (result != null) {
                System.out.printf("| %-15s | %-25.0f | %-20.0f | %-20.1f |\n", 
                        rate + "%", result.throughput, result.latency, result.criticalFrameLoss);
            }
        }
        
        System.out.println(separator);
    }
    
    /**
     * Inner class to store metrics results for a specific packet loss rate.
     */
    private static class MetricsResult {
        final double throughput;
        final double latency;
        final double criticalFrameLoss;
        
        MetricsResult(double throughput, double latency, double criticalFrameLoss) {
            this.throughput = throughput;
            this.latency = latency;
            this.criticalFrameLoss = criticalFrameLoss;
        }
    }
    
    /**
     * Compare metrics and print the analysis.
     * This would be used in a real test where we collect metrics from multiple runs.
     * 
     * @param lowLossMetrics Metrics from low packet loss test
     * @param medLossMetrics Metrics from medium packet loss test
     * @param highLossMetrics Metrics from high packet loss test
     */
    private static void analyzeMetrics(String lowLossMetrics, String medLossMetrics, String highLossMetrics) {
        System.out.println("\n=== PERFORMANCE ANALYSIS ===");
        System.out.println("\n5% Packet Loss Results:");
        System.out.println(lowLossMetrics);
        
        System.out.println("\n15% Packet Loss Results:");
        System.out.println(medLossMetrics);
        
        System.out.println("\n25% Packet Loss Results:");
        System.out.println(highLossMetrics);
        
        System.out.println("\nAnalysis:");
        System.out.println("As packet loss increases:");
        System.out.println("1. Throughput decreases due to more retransmissions");
        System.out.println("2. Viewer-perceived latency increases as more frames need recovery");
        System.out.println("3. The selective repeat ARQ mechanism helps maintain critical frame delivery");
        System.out.println("4. At high loss rates (25%), the stream quality deteriorates significantly");
    }
} 