package udp;

import java.io.*;

/**
 * Utility class for serializing and deserializing objects.
 */
public class SerializationUtil {
    
    /**
     * Serialize an object to a byte array.
     * 
     * @param obj Object to serialize
     * @return Byte array representation of the object
     * @throws IOException If serialization fails
     */
    public static byte[] serialize(Object obj) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            return baos.toByteArray();
        }
    }
    
    /**
     * Deserialize a byte array back to an object.
     * 
     * @param bytes Byte array to deserialize
     * @return Deserialized object
     * @throws IOException If deserialization fails
     * @throws ClassNotFoundException If class of serialized object cannot be found
     */
    public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return ois.readObject();
        }
    }
} 