package de.metaphisto.buoy.persistence;

/**
 *
 */
public class FileReadAction extends ReadAction {

    private byte[] line;
    private int position = 0;

    public FileReadAction(boolean locked, String key, byte[] line) {
        super(locked, key, line.length);
        this.line = line;
    }

    public byte[] getLine() {
        return line;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
