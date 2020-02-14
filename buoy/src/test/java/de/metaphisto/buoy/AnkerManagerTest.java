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
public class AnkerManagerTest {

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

    private AnkerManager ankerManager;

    @BeforeClass
    public static void init() {
        AnkerManager.initialize("target/anker");
    }

    @Before
    public void setUp() {
        ankerManager = AnkerManager.getInstance();
        Set<String> prozessVariablen = new HashSet<>();
        prozessVariablen.add("processContainer");
        when(delegateExecution.getParent()).thenReturn(delegateExecution);
        when(delegateExecution.getVariableNamesLocal()).thenReturn(prozessVariablen);
        when(delegateExecution.getVariableInstance(eq("processContainer"))).thenReturn(coreVariableInstance);
        when(coreVariableInstance.getTypedValue(eq(false))).thenReturn(processContainerObject);
        when(processContainerObject.getValueSerialized()).thenReturn("asv");
        when(processContainerObject.getObjectTypeName()).thenReturn("de.schufa.ProcessContainer");
    }

    @Test
    public void testSchreibe() throws IOException {
        ankerManager.schreibeAnker("1", delegateExecution);
        assertTrue(ankerManager.ankerExists("1", delegateExecution));
    }

    @Test
    public void testLese() throws IOException {
        Context.setCommandContext(commandContext);
        Context.setProcessEngineConfiguration(processEngineConfiguration);
        when(leereDelegateExecution.getParent()).thenReturn(leereDelegateExecution);
        ankerManager.rollover();
        ankerManager.schreibeAnker("2", delegateExecution);
        assertTrue(ankerManager.ankerExists("2", delegateExecution));
        ankerManager.leseAnkerInProzessVariablen("2", leereDelegateExecution);
        verify(leereDelegateExecution).setVariableLocal(any(), any(), any());
    }
}
