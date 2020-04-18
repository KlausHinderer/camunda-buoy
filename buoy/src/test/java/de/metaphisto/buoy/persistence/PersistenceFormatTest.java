package de.metaphisto.buoy.persistence;

import de.metaphisto.buoy.ExpiringCache;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class PersistenceFormatTest {

    public static final String VARIABLE_NAME = "variable_Name";
    public static final String VARIABLE_TYPE = "String";
    public static final String VARIABLE_VALUE = "abc_123";

    @Mock
    private ExecutionEntity delegateExecution;

    @Test
    public void testWriteThenRead() throws IOException {
        PersistenceFormat persistenceFormat = new PersistenceFormat();
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(256);
        persistenceFormat.writeVariable(VARIABLE_NAME, VARIABLE_TYPE, VARIABLE_VALUE, "key", byteBuffer, new LogFilePersistence("target/as", new ExpiringCache(1000)), true);
        byteBuffer.flip();
        persistenceFormat.readChunk("key", byteBuffer, delegateExecution);
        Mockito.verify(delegateExecution, Mockito.times(1)).setVariableLocal(eq(VARIABLE_NAME),any(TypedValue.class), eq(delegateExecution));
    }

}