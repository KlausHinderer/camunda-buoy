package de.metaphisto.buoy.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

import static de.metaphisto.buoy.persistence.AbstractStoreHolder.WriteMode.FORCE_FLUSH_BUFFER_TO_CHANNEL;


/**
 * Holder class for a WritableByteChannel. Many Anker can be written to the Channel, each in many chunks. To keep the chunks in order, obtain the exclusive lock before writing the first chunk and release it after the last chunk.
 * When a read of an anker is attempted, the rolloverHint is set and a new instance of the StoreHolder is created with a new ankerPackageName. When the last registered accessor of this instance unregisters, rollover() is called and the instance will be free for garbage collection.
 */
public abstract class AbstractStoreHolder<T extends WritableByteChannel> {

    public static final boolean DEBUG_LOG = true;
    protected static final Logger LOG = LoggerFactory.getLogger(FileChannelHolder.class);
    protected String ankerPackageName;
    private AtomicBoolean locked = new AtomicBoolean(false);
    private int accessInProgress = 0;
    private boolean rolloverHint = false;


    public AbstractStoreHolder(String ankerPackageName) {
        this.ankerPackageName = ankerPackageName;
    }

    /**
     * Writes a String to the ByteBuffer. If buffer is full, it will be flushed to the underlying channel. May leave the whole of part of the String in the ByteBuffer if FORCE_FLUSH is not set.
     *
     * @param toWrite    the desired String
     * @param byteBuffer the target
     * @param channel    the underlying channel
     * @return true if the channel has been locked
     * @throws IOException when flushing to the channel went wrong
     */
    public static boolean schreibeString(String toWrite, ByteBuffer byteBuffer, AbstractStoreHolder channel, boolean locked, WriteMode writeMode) throws IOException {
        if (DEBUG_LOG) {
            ByteBuffer duplicate = byteBuffer.duplicate();
            int bytes = duplicate.position();
            duplicate.flip();
            byte[] temp = new byte[bytes];
            duplicate.get(temp);
            System.out.println(new String(temp) + toWrite);
        }
        byte[] bytes = toWrite.getBytes(Charset.defaultCharset());
        int i = 0;
        boolean checkBounds = bytes.length >= byteBuffer.remaining();
        if (checkBounds) {
            do {
                int bytesZuSchreiben = Math.min(byteBuffer.remaining(), bytes.length - i);
                byteBuffer.put(bytes, i, bytesZuSchreiben);
                if (!(byteBuffer.remaining() > 0)) {
                    byteBuffer.flip();
                    if (!locked) {
                        channel.lock();
                        locked = true;
                    }
                    do {
                        channel.getChannel().write(byteBuffer);
                    } while (byteBuffer.remaining() > 0);
                    byteBuffer.clear();
                }
                i = i + bytesZuSchreiben;
            } while (i < bytes.length);
        } else {
            byteBuffer.put(bytes);
        }

        if (FORCE_FLUSH_BUFFER_TO_CHANNEL == writeMode) {
            if (byteBuffer.position() > 0) {
                byteBuffer.flip();
                if (!locked) {
                    locked = true;
                    channel.lock();
                }
                do {
                    channel.getChannel().write(byteBuffer);
                } while (byteBuffer.remaining() > 0);
                byteBuffer.clear();
            }
        }
        return locked;
    }

    /**
     * Marks the underlying channel for exclusive write-access. This method is not reentrant, so calling it when already holding the lock will block forever.
     * This default implementation assumes a lock is held for typically << 1 ms and spins with Thread.yield(). For that case, performance of this implementation will be better than ReentrantLock.
     */
    public void lock() {
        boolean success;
        do {
            success = locked.compareAndSet(false, true);
            if (!success) {
                //TODO review performance against LockSupport.parkNanos
                Thread.yield();
            }
        } while (!success);
    }

    public void unlock() {
        if (!locked.compareAndSet(true, false)) {
            throw new RuntimeException("unlock() called without owning a lock");
        }
    }

    public AbstractStoreHolder register() {
        accessInProgress++;
        return this;
    }

    public void unregister() {
        int pendingAccess = accessInProgress--;
        if (rolloverHint && pendingAccess <= 0) {
            rollover();
        }
    }

    protected void rollover() {
    }

    public void setRolloverHint() {
        if (accessInProgress <= 0) {
            rollover();
        } else {
            rolloverHint = true;
        }
    }

    public String getAnkerPackageName() {
        return ankerPackageName;
    }

    abstract T getChannel();

    public enum WriteMode {
        FORCE_FLUSH_BUFFER_TO_CHANNEL,
        ONLY_FLUSH_IF_BUFFER_FULL
    }
}
