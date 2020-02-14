package de.metaphisto.buoy.persistence;

import org.camunda.bpm.engine.delegate.DelegateExecution;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static de.metaphisto.buoy.persistence.AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL;

/**
 *
 */
public class PersistenceFormat {
    public boolean writeVariable(String variableName, String variableType, String variableValue, String key, ByteBuffer byteBuffer, AbstractPersistenceTechnology persistenceTechnology, boolean locked) throws IOException {
        locked = persistenceTechnology.appendNext("V_"+variableName + "[" + variableType + ":" + variableValue.length() + ":", key, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
        locked = persistenceTechnology.appendNext(variableValue, key, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
        locked = persistenceTechnology.appendNext("]", key, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
        return locked;
    }

    //Points to the execution or to a parent
    private DelegateExecution currentExecution = null;

    public void readChunk(String key, ByteBuffer byteBuffer, DelegateExecution delegateExecution) {

    }

    //TODO pack everything into an Action
    List<Byte> variableBuffer;
    String variableName;

    protected void startVariable(ByteBuffer byteBuffer){
        variableBuffer = new ArrayList<>(64);
        continueVariable(byteBuffer);
    }

    protected void continueVariable(ByteBuffer byteBuffer){
        byte b = 0x00;
        while (byteBuffer.hasRemaining() && (b = byteBuffer.get()) != "[".getBytes()[0]) {
            variableBuffer.add( b);
        }
        if(b =="[".getBytes()[0]) {
            byte[] read = new byte[variableBuffer.size()];
            for (int i = variableBuffer.size()-1; i >=0 ; i--) {
                read[i] = variableBuffer.get(i);
            }
            variableName = new String(read);
        }
    }
}
