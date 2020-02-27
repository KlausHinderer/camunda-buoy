package de.metaphisto.buoy.persistence;

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class PersistenceFormatTest {

    public static final String VARIABLE_NAME = "variable_Name";
    public static final String VARIABLE_TYPE = "String";
    public static final String VARIABLE_VALUE = "abc_123";

    @Test
    public void testWriteThenRead() throws IOException {
        PersistenceFormat persistenceFormat = new PersistenceFormat();
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(256);
        persistenceFormat.writeVariable(VARIABLE_NAME, VARIABLE_TYPE, VARIABLE_VALUE, "key", byteBuffer, new LogFilePersistence("as"), true);
        byteBuffer.flip();
        persistenceFormat.readChunk("key", byteBuffer, null);
        assertEquals(VARIABLE_NAME, persistenceFormat.readValues.get(0));
        assertEquals(VARIABLE_TYPE, persistenceFormat.readValues.get(1));
        assertEquals(VARIABLE_VALUE, persistenceFormat.readValues.get(2));
    }

}