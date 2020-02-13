package de.metaphisto.buoy.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 *
 */
public class LogFilePersistence extends AbstractPersistenceTechnology<FileChannel> {
    public LogFilePersistence(String filename) throws IOException {
        storeHolder = new FileChannelHolder(filename);
    }

    @Override
    public boolean beforeFirstWriteCommand(ByteBuffer byteBuffer, String key, boolean locked) throws IOException {
        locked = AbstractStoreHolder.schreibeString(key + "{", byteBuffer, storeHolder, locked, AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL);
        return locked;
    }

    @Override
    public boolean afterLastWriteCommand(ByteBuffer byteBuffer, String key, boolean locked) throws IOException {
        return AbstractStoreHolder.schreibeString("\n", byteBuffer, storeHolder, locked, AbstractStoreHolder.WriteMode.FORCE_FLUSH_BUFFER_TO_CHANNEL);
    }

    @Override
    public boolean prepareForRead(String key, boolean locked, ByteBuffer byteBuffer) {
        return false;
    }

    @Override
    public int readNext(String key, ByteBuffer byteBuffer) throws IOException {
        return 0;
    }

    public void setRolloverHint() {
        storeHolder.setRolloverHint();
    }


    public void register() {
        storeHolder.register();
    }

    public void unregister() {
        storeHolder.unregister();
    }

    public String getAnkerPackageName() {
        return storeHolder.getAnkerPackageName();
    }

}
