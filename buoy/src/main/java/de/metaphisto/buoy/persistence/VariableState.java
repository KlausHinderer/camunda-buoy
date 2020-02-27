package de.metaphisto.buoy.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static de.metaphisto.buoy.persistence.AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL;

/**
 * Class that reads or writes an entry for a variable. This is done with polymorphism.
 * Later implementations may replace polymorphism by a switch-statement for performance reasons.
 */
abstract class VariableState {

    protected static final byte UNDERSCORE = "_".getBytes()[0];
    protected static final byte OPENING_BRACKET = "[".getBytes()[0];
    protected static final byte COLON_TERMINATOR = ":".getBytes()[0];

    protected List<Byte> variableBuffer = new ArrayList<>();

    /**
     * @param byteBuffer the content
     * @return true if the section has been terminated
     */
    public abstract boolean consume(ByteBuffer byteBuffer);

    public abstract VariableState nextState();

    /**
     * @param variableName          the name
     * @param variableType          the type
     * @param variableValue         the value
     * @param key                   the current key of the buoy
     * @param byteBuffer            the byteBuffer to fill
     * @param persistenceTechnology the persistence to flush the buffer to
     * @param locked                the flag if the channel has already been locked
     * @return locked
     */
    public abstract boolean serialize(String variableName, String variableType, String variableValue, String key, ByteBuffer byteBuffer, AbstractPersistenceTechnology persistenceTechnology, boolean locked) throws IOException;

    public String getValue() {
        byte[] content = new byte[variableBuffer.size()];
        for (int i = 0; i < variableBuffer.size(); i++) {
            content[i] = variableBuffer.get(i);
        }
        return new String(content);
    }

}


class VariableNameState extends VariableState {

    private static final VariableNameState INSTANCE = new VariableNameState();

    private boolean prefixTerminated = false;

    private VariableNameState() {
    }

    public static VariableNameState getInstance() {
        INSTANCE.prefixTerminated = false;
        INSTANCE.variableBuffer.clear();
        return INSTANCE;
    }

    @Override
    public boolean consume(ByteBuffer byteBuffer) {
        while (byteBuffer.hasRemaining()) {
            byte b = byteBuffer.get();
            if (!prefixTerminated) {
                if (UNDERSCORE == b) {
                    prefixTerminated = true;
                }
            } else {
                if (OPENING_BRACKET == b) {
                    return true;
                } else {
                    variableBuffer.add(b);
                }
            }
        }
        return false;
    }

    @Override
    public VariableState nextState() {
        return ReadVariableType.getInstance();
    }

    @Override
    public boolean serialize(String variableName, String variableType, String variableValue, String key, ByteBuffer byteBuffer, AbstractPersistenceTechnology persistenceTechnology, boolean locked) throws IOException {
        return persistenceTechnology.appendNext("V_" + variableName + "[", key, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
    }
}

class ReadVariableType extends VariableState {

    private static final ReadVariableType INSTANCE = new ReadVariableType();

    private ReadVariableType() {
    }

    public static ReadVariableType getInstance() {
        INSTANCE.variableBuffer.clear();
        return INSTANCE;
    }

    @Override
    public boolean consume(ByteBuffer byteBuffer) {
        while (byteBuffer.hasRemaining()) {
            byte b = byteBuffer.get();
            if (COLON_TERMINATOR == b) {
                return true;
            } else {
                variableBuffer.add(b);
            }
        }
        return false;
    }

    @Override
    public VariableState nextState() {
        return ReadVariableValue.getInstance();
    }

    @Override
    public boolean serialize(String variableName, String variableType, String variableValue, String key, ByteBuffer byteBuffer, AbstractPersistenceTechnology persistenceTechnology, boolean locked) throws IOException {
        return persistenceTechnology.appendNext(variableType + ":", key, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
    }
}

class ReadVariableValue extends VariableState {

    private static final ReadVariableValue INSTANCE = new ReadVariableValue();

    private ReadVariableValue() {
    }

    public static ReadVariableValue getInstance() {
        INSTANCE.variableBuffer.clear();
        INSTANCE.prefixDone = false;
        INSTANCE.remainingLength = Integer.MAX_VALUE;
        return INSTANCE;
    }

    private boolean prefixDone = false;
    private int remainingLength = Integer.MAX_VALUE;

    @Override
    public boolean consume(ByteBuffer byteBuffer) {
        while (byteBuffer.hasRemaining() && remainingLength > 0) {
            byte b = byteBuffer.get();
            if (!prefixDone) {
                if (COLON_TERMINATOR == b) {
                    remainingLength = Integer.parseInt(getValue());
                    variableBuffer.clear();
                    prefixDone = true;
                } else {
                    variableBuffer.add(b);
                }
            } else {
                variableBuffer.add(b);
                remainingLength--;
            }
        }
        return remainingLength <= 0;
    }

    @Override
    public VariableState nextState() {
        return VariableNameState.getInstance();
    }

    @Override
    public boolean serialize(String variableName, String variableType, String variableValue, String key, ByteBuffer byteBuffer, AbstractPersistenceTechnology persistenceTechnology, boolean locked) throws IOException {
        locked = persistenceTechnology.appendNext(variableValue.length() + ":", key, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
        locked = persistenceTechnology.appendNext(variableValue, key, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
        locked = persistenceTechnology.appendNext("]", key, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
        return locked;
    }
}