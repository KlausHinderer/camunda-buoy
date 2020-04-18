package de.metaphisto.buoy.persistence;

import de.metaphisto.buoy.ExpiringCache;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 *
 */
public class LogFilePersistence extends AbstractPersistenceTechnology<FileChannel> {
    protected ExpiringCache expiringCache;

    public LogFilePersistence(String filename, ExpiringCache expiringCache) throws IOException {
        storeHolder = new FileChannelHolder(filename);
        this.expiringCache = expiringCache;
    }

    @Override
    public boolean beforeFirstWriteCommand(ByteBuffer byteBuffer, String key, boolean locked) throws IOException {
        locked = AbstractStoreHolder.schreibeString(key + "{", byteBuffer, storeHolder, locked, AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL);
        return locked;
    }

    @Override
    public boolean afterLastWriteCommand(ByteBuffer byteBuffer, String key, boolean locked) throws IOException {
        return AbstractStoreHolder.schreibeString("}\n", byteBuffer, storeHolder, locked, AbstractStoreHolder.WriteMode.FORCE_FLUSH_BUFFER_TO_CHANNEL);
    }

    @Override
    public ReadAction prepareForRead(String key, boolean locked, ByteBuffer byteBuffer) throws IOException {
        ReadAction readAction;

        //get the actual filename containing the key
        String filename = expiringCache.get(key);
        if (filename == null) {
            throw new RuntimeException("File not found for idempotenceKey: " + key);
        }
        if (this.getAnkerPackageName().equals(filename)) {
            throw new RuntimeException("File is in use to store persistence information, thus cannot be opened for read access");
        }
        String buoy = null;
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            while (br.ready()) {
                String line = br.readLine();
                if (line.startsWith(key)) {
                    buoy = line;
                    break;
                }
            }
        }

        //remove the key and starting '{'  and closing '}' from the line
        buoy = buoy.substring(key.length() + 1, buoy.length() - 1);

        readAction = new FileReadAction(locked, key, buoy.getBytes());
        //read the first chunk into the bytebuffer
        readNext(readAction, byteBuffer);

        return readAction;
    }

    @Override
    public int readNext(ReadAction readAction, ByteBuffer byteBuffer) throws IOException {
        FileReadAction fileReadAction = (FileReadAction) readAction;
        if (fileReadAction.getBytesAvailableForKey() <= 0) {
            return -1;
        }
        int toWrite = Math.min(byteBuffer.remaining(), fileReadAction.getBytesAvailableForKey());
        byteBuffer.put(fileReadAction.getLine(), fileReadAction.getPosition(), toWrite);
        byteBuffer.flip();

        fileReadAction.setPosition(fileReadAction.getPosition() + toWrite);
        fileReadAction.setBytesAvailableForKey(fileReadAction.getBytesAvailableForKey() - toWrite);
        return fileReadAction.getBytesAvailableForKey();
    }

    public void setRolloverHint() {
        storeHolder.setRolloverHint();
    }

    public String getAnkerPackageName() {
        return storeHolder.getAnkerPackageName();
    }

}
