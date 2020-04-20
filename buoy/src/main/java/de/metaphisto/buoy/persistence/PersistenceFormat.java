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
import java.util.List;

/**
 * Defines the format of the serialized data.
 */
public class PersistenceFormat {
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
        do {
            boolean stateComplete = variableState.consume(byteBuffer);
            if (variableState instanceof StartScopeOrVariableState) {
                if (((StartScopeOrVariableState) variableState).isTerminator()) {
                    return;
                }
                if (((StartScopeOrVariableState) variableState).isStartScope()) {
                    currentExecution = currentExecution.getSuperExecution();
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
            default:
                typedValue = new ObjectValueImpl(null, value, "application/x-java-serialized-object", type, false);
        }
        executionEntity.setVariableLocal(name, typedValue, executionEntity);
    }
}
