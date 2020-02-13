package de.metaphisto.buoy.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * This class wraps a SocketChannel to Redis and provides the following operations:
 * 1. write a String of unknown length to Redis
 * 2. read a key from Redis
 * Since for write, the length of the key is not known before, the redis inline protocol is used to append chunks to the key.
 */
public class RedisPersistence extends AbstractPersistenceTechnology<SocketChannel> {

    private static final ByteBuffer COMMAND_TERMINATOR = ByteBuffer.allocateDirect(2);
    private static final byte[] CR_LF = "\r\n".getBytes();

    private int bytesAvailableForKey = -1;

    private String keyForRead = null;

    public RedisPersistence() {
        storeHolder = new SocketStoreHolder("");
        COMMAND_TERMINATOR.clear();
        COMMAND_TERMINATOR.put(CR_LF);
    }

    @Override
    public boolean afterFlush(ByteBuffer byteBuffer, String key, boolean locked) throws IOException {
        if (!locked) {
            storeHolder.lock();
            locked = true;
        }

        int length = readLength(byteBuffer);

        byteBuffer.clear();
        byteBuffer.limit(byteBuffer.limit() - CR_LF.length);

        byteBuffer.put(("APPEND " + key + " ").getBytes());

        return locked;
    }

    protected int readLength(ByteBuffer byteBuffer) throws IOException {
        do {
            storeHolder.getChannel().read(byteBuffer);
            byteBuffer.flip();
            StringBuilder responseLengthString = new StringBuilder();
            for (int i = 0; i < byteBuffer.limit(); i++) {
                byte b = byteBuffer.get();
                if (b == ":".getBytes()[0]) {
                } else if (b == "\n".getBytes()[0]) {
                    byteBuffer.clear();
                    return Integer.valueOf(responseLengthString.toString());
                } else if (b == "\r".getBytes()[0]) {
                } else {
                    responseLengthString.append((new char[]{(char) b}));
                }
            }
        } while (true);
    }

    @Override
    public boolean beforeFlush(ByteBuffer byteBuffer, String key, boolean locked) throws IOException {
        byteBuffer.limit(byteBuffer.limit() + CR_LF.length);
        byteBuffer.put(CR_LF);
        return locked;
    }

    @Override
    public boolean beforeFirstWriteCommand(ByteBuffer byteBuffer, String key, boolean locked) throws IOException {
        byteBuffer.put(("APPEND " + key + " ").getBytes());
        //For cr lf
        byteBuffer.limit(byteBuffer.limit() - CR_LF.length);
        return locked;
    }

    @Override
    public boolean afterLastWriteCommand(ByteBuffer byteBuffer, String key, boolean locked) throws IOException {
        byte[] commandBytes = ("APPEND " + key + " ").getBytes();
        if (byteBuffer.position() > commandBytes.length) {
            locked = AbstractStoreHolder.schreibeString("\r\n", byteBuffer, storeHolder, locked, AbstractStoreHolder.WriteMode.FORCE_FLUSH_BUFFER_TO_CHANNEL);
        }
        readLength(byteBuffer);
        byteBuffer.clear();
        return locked;
    }

    @Override
    public void beforeReadCommand(ByteBuffer byteBuffer) {
        super.beforeReadCommand(byteBuffer);
    }

    @Override
    public void afterReadCommand(ByteBuffer byteBuffer) {
        super.afterReadCommand(byteBuffer);
    }

    @Override
    public boolean prepareForRead(String key, boolean locked, ByteBuffer byteBuffer) throws IOException {
        if (!locked) {
            storeHolder.lock();
            locked = true;
        }
        keyForRead = key;
        bytesAvailableForKey = -1;

        AbstractStoreHolder.schreibeString("*2\r\n$3\r\nGET\r\n$" + key.length() + "\r\n" + key + "\r\n", byteBuffer, storeHolder, locked, AbstractStoreHolder.WriteMode.FORCE_FLUSH_BUFFER_TO_CHANNEL);

        String responseLengthString = null;
        do {
            storeHolder.getChannel().read(byteBuffer);
            byteBuffer.flip();
            while (byteBuffer.hasRemaining()) {
                byte b = byteBuffer.get();
                if (b == "$".getBytes()[0]) {
                    responseLengthString = "";
                } else if (b == "\n".getBytes()[0]) {
                    bytesAvailableForKey = Integer.valueOf(responseLengthString);
                    break;
                } else if (b == "\r".getBytes()[0]) {
                } else {
                    responseLengthString += new String(new byte[]{b});
                }
            }
            if (bytesAvailableForKey < 0) {
                byteBuffer.flip();
            }
        } while (bytesAvailableForKey < 0);

        bytesAvailableForKey -= byteBuffer.remaining();
        //add two bytes for \r\n
        bytesAvailableForKey += 2;
        return locked;
    }

    @Override
    public int readNext(String key, ByteBuffer byteBuffer) throws IOException {
        if (!key.equals(keyForRead)) {
            throw new RuntimeException("readNext invoked for key " + key + " but prepareForRead invoked for key " + keyForRead);
        }
        byteBuffer.clear();
        if (bytesAvailableForKey <= 0) {
            return -1;
        }
        int read = storeHolder.getChannel().read(byteBuffer);
        bytesAvailableForKey -= read;
        byteBuffer.flip();
        return bytesAvailableForKey;
    }
}
