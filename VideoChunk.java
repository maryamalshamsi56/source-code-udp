package udp;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Represents a chunk of video data to be transmitted over UDP.
 */
public class VideoChunk implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int sequenceNumber;
    private byte[] data;
    private boolean criticalFrame;
    private long timestamp;
    private boolean needsAck;
    
    public VideoChunk(int sequenceNumber, byte[] data, boolean criticalFrame, long timestamp) {
        this.sequenceNumber = sequenceNumber;
        this.data = data;
        this.criticalFrame = criticalFrame;
        this.timestamp = timestamp;
        this.needsAck = criticalFrame; // Only critical frames need acknowledgment
    }
    
    public int getSequenceNumber() {
        return sequenceNumber;
    }
    
    public byte[] getData() {
        return data;
    }
    
    public boolean isCriticalFrame() {
        return criticalFrame;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public boolean needsAcknowledgment() {
        return needsAck;
    }
    
    public int getSize() {
        return data.length;
    }
    
    @Override
    public String toString() {
        return "VideoChunk{" +
                "sequenceNumber=" + sequenceNumber +
                ", dataSize=" + (data != null ? data.length : 0) +
                ", criticalFrame=" + criticalFrame +
                ", timestamp=" + timestamp +
                '}';
    }
} 