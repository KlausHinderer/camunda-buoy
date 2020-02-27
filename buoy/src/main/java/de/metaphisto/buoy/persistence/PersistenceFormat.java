package de.metaphisto.buoy.persistence;

import org.camunda.bpm.engine.delegate.DelegateExecution;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class PersistenceFormat {
    public boolean writeVariable(String variableName, String variableType, String variableValue, String key, ByteBuffer byteBuffer, AbstractPersistenceTechnology persistenceTechnology, boolean locked) throws IOException {
        variableState = VariableNameState.getInstance();
        //Serialize name
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

    //Points to the execution or to a parent
    private DelegateExecution currentExecution = null;

    private VariableState variableState;
    List<String> readValues = new ArrayList<>(3);

    public void readChunk(String key, ByteBuffer byteBuffer, DelegateExecution delegateExecution) {
        if (variableState == null) {
            variableState = VariableNameState.getInstance();
            readValues.clear();
        }
        do {
            boolean stateComplete = variableState.consume(byteBuffer);
            if (stateComplete) {
                //transition to next state
                readValues.add(variableState.getValue());
                variableState = variableState.nextState();
            }
        } while (byteBuffer.hasRemaining());
    }
}
