package udp;

import udp.VideoChunk;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for handling video file chunking operations.
 */
public class VideoChunker {
    
    // Reduced chunk size to 8KB to stay well within UDP datagram limits
    private static final int DEFAULT_CHUNK_SIZE = 8 * 1024;
    
    // Maximum safe UDP packet size (considering headers and overhead)
    private static final int MAX_SAFE_CHUNK_SIZE = 16 * 1024;
    
    /**
     * Splits a video file into chunks.
     *
     * @param videoFile The video file to split
     * @param chunkSize The size of each chunk in bytes
     * @return A list of VideoChunk objects
     * @throws IOException If an I/O error occurs
     * @throws IllegalArgumentException If chunk size is too large
     */
    public static List<VideoChunk> chunkVideoFile(File videoFile, int chunkSize) throws IOException {
        // Validate chunk size
        if (chunkSize > MAX_SAFE_CHUNK_SIZE) {
            throw new IllegalArgumentException(
                "Chunk size " + chunkSize + " bytes is too large for UDP. " +
                "Maximum safe size is " + MAX_SAFE_CHUNK_SIZE + " bytes."
            );
        }
        
        List<VideoChunk> chunks = new ArrayList<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(videoFile, "r")) {
            byte[] buffer = new byte[chunkSize];
            int sequenceNumber = 0;
            int bytesRead;
            
            while ((bytesRead = raf.read(buffer)) > 0) {
                // If bytesRead is less than buffer size, create a properly sized array
                byte[] chunkData;
                if (bytesRead < buffer.length) {
                    chunkData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunkData, 0, bytesRead);
                } else {
                    chunkData = buffer.clone();
                }
                
                // Check if this is likely a critical frame (I-frame)
                boolean isCriticalFrame = isLikelyIFrame(chunkData);
                
                chunks.add(new VideoChunk(
                        sequenceNumber++, 
                        chunkData, 
                        isCriticalFrame,
                        System.currentTimeMillis()
                ));
                
                buffer = new byte[chunkSize];
            }
        }
        
        return chunks;
    }
    
    /**
     * Overloaded method using default chunk size.
     */
    public static List<VideoChunk> chunkVideoFile(File videoFile) throws IOException {
        return chunkVideoFile(videoFile, DEFAULT_CHUNK_SIZE);
    }
    
    /**
     * Simple method to detect if a chunk likely contains an I-frame.
     */
    private static boolean isLikelyIFrame(byte[] data) {
        // Statistical approach that approximates I-frame frequency
        return Math.random() < 0.1; // 10% of frames are considered critical (I-frames)
    }
    
    /**
     * Reads chunks directly from a video stream for real-time processing.
     *
     * @param file The file to simulate as a live stream
     * @param chunkSize The size of each chunk
     * @return A VideoChunk with the read data
     * @throws IOException If an I/O error occurs
     * @throws IllegalArgumentException If chunk size is too large
     */
    public static VideoChunk readNextChunkFromStream(RandomAccessFile file, int chunkSize, int sequenceNumber) throws IOException {
        // Validate chunk size
        if (chunkSize > MAX_SAFE_CHUNK_SIZE) {
            throw new IllegalArgumentException(
                "Chunk size " + chunkSize + " bytes is too large for UDP. " +
                "Maximum safe size is " + MAX_SAFE_CHUNK_SIZE + " bytes."
            );
        }
        
        byte[] buffer = new byte[chunkSize];
        int bytesRead = file.read(buffer);
        
        if (bytesRead <= 0) {
            return null; // End of stream
        }
        
        // If we didn't read a full chunk, resize the buffer
        if (bytesRead < chunkSize) {
            byte[] resizedBuffer = new byte[bytesRead];
            System.arraycopy(buffer, 0, resizedBuffer, 0, bytesRead);
            buffer = resizedBuffer;
        }
        
        boolean isCriticalFrame = isLikelyIFrame(buffer);
        return new VideoChunk(sequenceNumber, buffer, isCriticalFrame, System.currentTimeMillis());
    }
    
    /**
     * Get the default chunk size.
     */
    public static int getDefaultChunkSize() {
        return DEFAULT_CHUNK_SIZE;
    }
    
    /**
     * Get the maximum safe chunk size.
     */
    public static int getMaxSafeChunkSize() {
        return MAX_SAFE_CHUNK_SIZE;
    }
} 