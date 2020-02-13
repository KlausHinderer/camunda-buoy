package de.metaphisto.buoy.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;

import static de.metaphisto.buoy.persistence.AbstractStoreHolder.WriteMode.FORCE_FLUSH_BUFFER_TO_CHANNEL;

/**
 *
 */
public abstract class AbstractPersistenceTechnology<T extends WritableByteChannel> {

    protected AbstractStoreHolder<T> storeHolder;

    protected String keyForWrite = null;

    public boolean startKey(String key, String toWrite, ByteBuffer byteBuffer, boolean locked) {
        return locked;
        //TODO implement. or unite with appendNext.
    }

    /**
     * Writes an entry< key,toWrite > to the ByteBuffer. If buffer is full, it will be flushed to the underlying channel. May leave the whole of part of the String in the ByteBuffer if FORCE_FLUSH is not set.
     *
     * @param toWrite    the desired String
     * @param key        the key for the entry
     * @param byteBuffer the target
     * @return true if the channel has been locked
     * @throws IOException when flushing to the channel went wrong
     */
    //TODO eliminate WRITE_MODE
    public boolean appendNext(String toWrite, String key, ByteBuffer byteBuffer, boolean locked, AbstractStoreHolder.WriteMode writeMode) throws IOException {
        byte[] bytes = toWrite.getBytes(Charset.defaultCharset());
        int i = 0;
        boolean checkBounds = bytes.length >= byteBuffer.remaining();
        if (checkBounds) {
            do {
                int bytesZuSchreiben = Math.min(byteBuffer.remaining(), bytes.length - i);
                byteBuffer.put(bytes, i, bytesZuSchreiben);
                if (!(byteBuffer.remaining() > 0)) {
                    if (!locked) {
                        storeHolder.lock();
                        locked = true;
                    }
                    locked = beforeFlush(byteBuffer, key, locked);
                    byteBuffer.flip();
                    do {
                        storeHolder.getChannel().write(byteBuffer);
                    } while (byteBuffer.remaining() > 0);
                    byteBuffer.clear();
                    locked = afterFlush(byteBuffer, key, locked);
                }
                i = i + bytesZuSchreiben;
            } while (i < bytes.length);
        } else {
            if (SocketStoreHolder.DEBUG_LOG) {
                System.out.println(toWrite);
            }
            byteBuffer.put(bytes);
        }

        if (FORCE_FLUSH_BUFFER_TO_CHANNEL == writeMode) {
            if (byteBuffer.position() > 0) {
                if (!locked) {
                    locked = true;
                    storeHolder.lock();
                }
                locked = beforeFlush(byteBuffer, key, locked);
                byteBuffer.flip();
                do {
                    storeHolder.getChannel().write(byteBuffer);
                } while (byteBuffer.remaining() > 0);
                byteBuffer.clear();
                locked = afterFlush(byteBuffer, key, locked);
            }
        }
        return locked;
    }

    /**
     * Called before initiating a write command.
     *
     * @param byteBuffer the empty byteBuffer
     * @param key        the key under which the anker entry will be written
     * @param locked     flag if the channel is already locked
     * @return true if the channel has been locked
     */
    public boolean beforeFirstWriteCommand(ByteBuffer byteBuffer, String key, boolean locked) throws IOException {
        return locked;
    }

    /**
     * Called after all variables have been written to the buffer. Implementation generally needs to flush the buffer.
     *
     * @param byteBuffer
     * @param key
     * @param locked
     * @return
     * @throws IOException
     */
    public boolean afterLastWriteCommand(ByteBuffer byteBuffer, String key, boolean locked) throws IOException {
        return locked;
    }

    public boolean beforeFlush(ByteBuffer byteBuffer, String key, boolean locked) throws IOException {
        return locked;
    }

    public boolean afterFlush(ByteBuffer byteBuffer, String key, boolean locked) throws IOException {
        return locked;
    }

    public void beforeReadCommand(ByteBuffer byteBuffer) {
    }

    public void afterReadCommand(ByteBuffer byteBuffer) {
    }

    public void unlock() {
        storeHolder.unlock();
    }

    public abstract boolean prepareForRead(String key, boolean locked, ByteBuffer byteBuffer) throws IOException;

    /**
     * Reads the next available bytes for key into the bytebuffer. Caller must first invoke once prepareForRead for the key.
     *
     * @param key        the key to be read
     * @param byteBuffer the target for the content. Will be cleared before reading.
     * @return the number of remaining bytes for the key or -1 if the end has been reached
     */
    public abstract int readNext(String key, ByteBuffer byteBuffer) throws IOException;
}
