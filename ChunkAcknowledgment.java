package udp;

import java.io.Serializable;

/**
 * Represents an acknowledgment for a received video chunk.
 */
public class ChunkAcknowledgment implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int sequenceNumber;
    private long receivedTimestamp;
    
    public ChunkAcknowledgment(int sequenceNumber, long receivedTimestamp) {
        this.sequenceNumber = sequenceNumber;
        this.receivedTimestamp = receivedTimestamp;
    }
    
    public int getSequenceNumber() {
        return sequenceNumber;
    }
    
    public long getReceivedTimestamp() {
        return receivedTimestamp;
    }
    
    @Override
    public String toString() {
        return "ChunkAcknowledgment{" +
                "sequenceNumber=" + sequenceNumber +
                ", receivedTimestamp=" + receivedTimestamp +
                '}';
    }
} 