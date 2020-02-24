package de.metaphisto.buoy.persistence;

/**
 *
 */
public class ReadAction {

    private boolean locked;

    private String key;

    private int bytesAvailableForKey = -1;

    public ReadAction(boolean locked, String key, int bytesAvailableForKey) {
        this.locked = locked;
        this.key = key;
        this.bytesAvailableForKey = bytesAvailableForKey;
    }

    public boolean isLocked() {
        return locked;
    }

    public String getKey() {
        return key;
    }

    public int getBytesAvailableForKey() {
        return bytesAvailableForKey;
    }

    public void setBytesAvailableForKey(int bytesAvailableForKey) {
        this.bytesAvailableForKey = bytesAvailableForKey;
    }
}
