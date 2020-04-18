package de.metaphisto.buoy;

import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.core.variable.CoreVariableInstance;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.variable.serializer.TypedValueSerializer;
import org.camunda.bpm.engine.impl.variable.serializer.VariableSerializers;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class IdempotenceTest {

    @Mock
    private ExecutionEntity delegateExecution;

    @Mock
    private CoreVariableInstance coreVariableInstance;

    @Mock
    private ObjectValue processContainerObject;

    @Mock
    private ExecutionEntity leereDelegateExecution;

    @Mock
    private CommandContext commandContext;

    @Mock
    private ProcessEngineConfigurationImpl processEngineConfiguration;

    @Mock
    private VariableSerializers variableSerializers;

    @Mock
    private TypedValueSerializer typedValueSerializer;

    private IdempotenceWithLogfile idempotence;

    @BeforeClass
    public static void init() {
        if (!IdempotenceWithLogfile.isInitialized()) {
            IdempotenceWithLogfile.initialize("target/anker");
        }
    }

    @Before
    public void setUp() {
        idempotence = IdempotenceWithLogfile.getInstance();
        Set<String> processVariables = new HashSet<>();
        processVariables.add("processContainer");
        when(delegateExecution.getVariableNamesLocal()).thenReturn(processVariables);
        when(delegateExecution.getVariableInstance(eq("processContainer"))).thenReturn(coreVariableInstance);
        when(coreVariableInstance.getTypedValue(eq(false))).thenReturn(processContainerObject);
        when(processContainerObject.getValueSerialized()).thenReturn("asv");
        when(processContainerObject.getObjectTypeName()).thenReturn("de.metaphisto.ProcessContainer");
    }

    @Test
    public void testWrite() throws IOException {
        String correlationId = "IdempotenceTest.testWrite1";
        idempotence.rollover();
        idempotence.putBuoy(correlationId, delegateExecution);
        assertTrue(idempotence.entryExists(correlationId, delegateExecution));
    }

    @Test
    public void testRead() throws IOException {
        String correlationId = "IdempotenceTest.testRead2" + System.nanoTime();
        Context.setCommandContext(commandContext);
        Context.setProcessEngineConfiguration(processEngineConfiguration);
        idempotence.rollover();
        idempotence.putBuoy(correlationId, delegateExecution);
        assertTrue(idempotence.entryExists(correlationId, delegateExecution));
        idempotence.readBuoyStateIntoProcessVariables(correlationId, leereDelegateExecution);
        verify(leereDelegateExecution).setVariableLocal(any(), any(), any());
    }
}

