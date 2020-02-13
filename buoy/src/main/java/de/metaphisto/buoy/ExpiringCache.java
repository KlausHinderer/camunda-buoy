
package de.metaphisto.buoy;

import java.util.HashMap;
import java.util.Map;

public class ExpiringCache {
    private Map<String, String> map = new HashMap<>(200);

    //TODO: implement expiration logic or use some lib
    public ExpiringCache(int expirationTime) {
    }

    public synchronized String get(String key) {
        return map.get(key);
    }

    public synchronized void put(String key, String val) {
        map.put(key, val);
    }

    public synchronized void clear() {
        map.clear();
    }
}