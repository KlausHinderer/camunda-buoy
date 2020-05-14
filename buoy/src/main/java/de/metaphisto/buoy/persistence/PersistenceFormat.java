package de.metaphisto.buoy.persistence;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.variable.impl.value.NullValueImpl;
import org.camunda.bpm.engine.variable.impl.value.ObjectValueImpl;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl;
import org.camunda.bpm.engine.variable.value.TypedValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Defines the format of the serialized data.
 */
public class PersistenceFormat {

    public static final String ENCODED = "Encoded";

    public boolean startScope(String key, ByteBuffer byteBuffer, AbstractPersistenceTechnology persistenceTechnology, boolean locked) throws IOException {
        locked = persistenceTechnology.appendNext("{",key, byteBuffer, locked, AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL);
        return locked;
    }

    public boolean writeVariable(String variableName, String variableType, String variableValue, String key, ByteBuffer byteBuffer, AbstractPersistenceTechnology persistenceTechnology, boolean locked) throws IOException {
        variableState = StartScopeOrVariableState.getInstance();
        //Serialize prefix
        locked = variableState.serialize(variableName, variableType, variableValue, key, byteBuffer, persistenceTechnology, locked);
        //Serialize name
        variableState = variableState.nextState();
        locked = variableState.serialize(variableName, variableType, variableValue, key, byteBuffer, persistenceTechnology, locked);
        //Serialize type
        variableState = variableState.nextState();
        locked = variableState.serialize(variableName, variableType, variableValue, key, byteBuffer, persistenceTechnology, locked);
        //Serialize Value
        variableState = variableState.nextState();
        locked = variableState.serialize(variableName, variableType, variableValue, key, byteBuffer, persistenceTechnology, locked);

        variableState = null;
        return locked;
    }

    private VariableState variableState;
    public List<String> readValues = new ArrayList<>(3);

    public void readChunk(String key, ByteBuffer byteBuffer, DelegateExecution delegateExecution) {
        //Points to the execution or to a parent
        DelegateExecution currentExecution = delegateExecution;

        if (variableState == null) {
            variableState = StartScopeOrVariableState.getInstance();
            readValues.clear();
        }
        int level = 0;
        do {
            boolean stateComplete = variableState.consume(byteBuffer);
            if (variableState instanceof StartScopeOrVariableState) {
                if (((StartScopeOrVariableState) variableState).isTerminator()) {
                    level--;
                    if(level<0) {
                        return;
                    }
                }
                if (((StartScopeOrVariableState) variableState).isStartScope()) {
                    level++;
                    currentExecution = ((ExecutionEntity)currentExecution).getParent();
                }
            }
            if (stateComplete) {
                //transition to next state
                if (variableState.hasOutput()) {
                    if(variableState instanceof ReadVariableValue) {
                        writeSavedStateToProcessVariables(readValues.get(0),readValues.get(1), variableState.getValue(), (ExecutionEntity) currentExecution);
                        readValues.clear();
                    }else {
                        readValues.add(variableState.getValue());
                    }
                }
                variableState = variableState.nextState();
            }
        } while (byteBuffer.hasRemaining());
    }

    private void writeSavedStateToProcessVariables(String name, String type, String value, ExecutionEntity executionEntity) {
        if(type.endsWith(ENCODED)){
            type = type.substring(0, type.indexOf(ENCODED));
            value = new String(Base64.getDecoder().decode(value.getBytes()));
        }
        TypedValue typedValue;
        switch (type) {
            case "NullValueImpl":
                typedValue = NullValueImpl.INSTANCE;
                break;
            case "string":
                typedValue = new PrimitiveTypeValueImpl.StringValueImpl(value);
                break;
            case "long":
                typedValue = new PrimitiveTypeValueImpl.LongValueImpl(Long.valueOf(value));
                break;
            case "integer":
                typedValue = new PrimitiveTypeValueImpl.IntegerValueImpl(Integer.valueOf(value));
                break;
            case "boolean":
                typedValue = new PrimitiveTypeValueImpl.BooleanValueImpl(Boolean.valueOf(value));
                break;
            case "double":
                typedValue = new PrimitiveTypeValueImpl.DoubleValueImpl(Double.valueOf(value));
                break;
            default:
                typedValue = new ObjectValueImpl(null, value, "application/x-java-serialized-object", type, false);
        }
        executionEntity.setVariableLocal(name, typedValue, executionEntity);
    }
}
