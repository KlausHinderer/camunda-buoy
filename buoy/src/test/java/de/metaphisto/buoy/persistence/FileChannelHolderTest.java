package de.metaphisto.buoy.persistence;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileChannelHolderTest {

    public static final String FILENAME = "target/channelTest" + System.currentTimeMillis();
    private AbstractStoreHolder fileChannelHolder;

    public FileChannelHolderTest() throws IOException {
        FileChannelHolder temp = new FileChannelHolder(FILENAME);
        fileChannelHolder = temp.register();
    }

    @Test
    public void testWrite() throws IOException {
        File file = new File(FILENAME);
        assertEquals(0, file.length());
        boolean locked = false;

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4);
        locked = AbstractStoreHolder.schreibeString("12345678", byteBuffer, fileChannelHolder, locked, AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL);
        assertEquals(8, file.length());
        assertTrue(locked);
        assertEquals(4, byteBuffer.remaining());
        locked = AbstractStoreHolder.schreibeString("1234567", byteBuffer, fileChannelHolder, locked, AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL);
        assertEquals("The next full buffersize must have been written", 12, file.length());
        assertEquals("3 bytes must be in the buffer", 3, byteBuffer.position());

        locked = AbstractStoreHolder.schreibeString("1234", byteBuffer, fileChannelHolder, locked, AbstractStoreHolder.WriteMode.FORCE_FLUSH_BUFFER_TO_CHANNEL);
        assertEquals("buffer must be empty after forced flush", 0, byteBuffer.position());
        assertEquals("Only a forced flush can make the file grow by a size != buffersize", 19, file.length());
        fileChannelHolder.unlock();
        fileChannelHolder.unregister();
    }
}