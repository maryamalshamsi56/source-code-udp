package udp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects and calculates performance metrics for the video streaming system.
 */
public class PerformanceMetrics {
    
    // Throughput metrics
    private AtomicLong totalBytesReceived = new AtomicLong(0);
    private AtomicLong totalBytesSent = new AtomicLong(0);
    private AtomicLong startTimeMillis = new AtomicLong(0);
    
    // Latency metrics
    private List<Long> latencyMeasurements = new ArrayList<>();
    
    // Packet loss metrics
    private AtomicLong totalPacketsSent = new AtomicLong(0);
    private AtomicLong totalPacketsReceived = new AtomicLong(0);
    private AtomicLong criticalPacketsLost = new AtomicLong(0);
    private AtomicLong nonCriticalPacketsLost = new AtomicLong(0);
    
    // Singleton instance
    private static PerformanceMetrics instance;
    
    private PerformanceMetrics() {
        startTimeMillis.set(System.currentTimeMillis());
    }
    
    public static synchronized PerformanceMetrics getInstance() {
        if (instance == null) {
            instance = new PerformanceMetrics();
        }
        return instance;
    }
    
    /**
     * Record that bytes were sent.
     * 
     * @param bytes The number of bytes sent
     */
    public void recordBytesSent(long bytes) {
        totalBytesSent.addAndGet(bytes);
        totalPacketsSent.incrementAndGet();
    }
    
    /**
     * Record that bytes were received.
     * 
     * @param bytes The number of bytes received
     */
    public void recordBytesReceived(long bytes) {
        totalBytesReceived.addAndGet(bytes);
        totalPacketsReceived.incrementAndGet();
    }
    
    /**
     * Record a latency measurement.
     * 
     * @param latencyMs The latency in milliseconds
     */
    public synchronized void recordLatency(long latencyMs) {
        latencyMeasurements.add(latencyMs);
    }
    
    /**
     * Record a lost packet.
     * 
     * @param isCritical Whether the lost packet contained a critical frame
     */
    public void recordPacketLoss(boolean isCritical) {
        if (isCritical) {
            criticalPacketsLost.incrementAndGet();
        } else {
            nonCriticalPacketsLost.incrementAndGet();
        }
    }
    
    /**
     * Calculate the current throughput in bytes per second.
     * 
     * @return The throughput in bytes per second
     */
    public double calculateThroughput() {
        long elapsedTimeSeconds = (System.currentTimeMillis() - startTimeMillis.get()) / 1000;
        if (elapsedTimeSeconds == 0) {
            return 0;
        }
        return (double) totalBytesReceived.get() / elapsedTimeSeconds;
    }
    
    /**
     * Calculate the average latency.
     * 
     * @return The average latency in milliseconds
     */
    public synchronized double calculateAverageLatency() {
        if (latencyMeasurements.isEmpty()) {
            return 0;
        }
        
        long sum = 0;
        for (Long latency : latencyMeasurements) {
            sum += latency;
        }
        
        return (double) sum / latencyMeasurements.size();
    }
    
    /**
     * Calculate the packet loss rate.
     * 
     * @return The packet loss rate as a percentage
     */
    public double calculatePacketLossRate() {
        long sentPackets = totalPacketsSent.get();
        if (sentPackets == 0) {
            return 0;
        }
        
        long lostPackets = sentPackets - totalPacketsReceived.get();
        return ((double) lostPackets / sentPackets) * 100;
    }
    
    /**
     * Calculate critical frame loss rate.
     * 
     * @return The critical frame loss rate as a percentage
     */
    public double calculateCriticalFrameLossRate() {
        long totalCriticalLost = criticalPacketsLost.get();
        long totalPackets = totalPacketsSent.get();
        
        if (totalPackets == 0) {
            return 0;
        }
        
        return ((double) totalCriticalLost / totalPackets) * 100;
    }
    
    /**
     * Reset all metrics.
     */
    public synchronized void reset() {
        totalBytesReceived.set(0);
        totalBytesSent.set(0);
        startTimeMillis.set(System.currentTimeMillis());
        latencyMeasurements.clear();
        totalPacketsSent.set(0);
        totalPacketsReceived.set(0);
        criticalPacketsLost.set(0);
        nonCriticalPacketsLost.set(0);
    }
    
    /**
     * Get a summary of all metrics.
     * 
     * @return A string summarizing all performance metrics
     */
    public String getMetricsSummary() {
        return String.format(
                "=== Performance Metrics ===\n" +
                "Throughput: %.2f KB/s\n" +
                "Average Latency: %.0f ms\n" +
                "Packet Loss Rate: %.2f%%\n" +
                "Critical Frame Loss Rate: %.2f%%\n" +
                "Total Bytes Sent: %d\n" +
                "Total Bytes Received: %d\n" +
                "Total Packets Sent: %d\n" +
                "Total Packets Received: %d\n",
                calculateThroughput() / 1024,
                calculateAverageLatency(),
                calculatePacketLossRate(),
                calculateCriticalFrameLossRate(),
                totalBytesSent.get(),
                totalBytesReceived.get(),
                totalPacketsSent.get(),
                totalPacketsReceived.get()
        );
    }
} 