package com.syncduo.server.mq;

import java.util.LinkedHashMap;
import java.util.Map;

public class LimitedSizeSet<K> {
    private final int maxSize;
    private final LinkedHashMap<K, Boolean> map;

    public LimitedSizeSet(int maxSize) {
        this.maxSize = maxSize;
        this.map = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Boolean> eldest) {
                return size() > LimitedSizeSet.this.maxSize;
            }
        };
    }

    public void add(K key) {
        map.put(key, Boolean.TRUE);
    }

    public boolean contains(K key) {
        if (map.containsKey(key)) {
            this.refresh(key);
            return true;
        } else {
            return false;
        }
    }

    public void refresh(K key) {
        if (map.containsKey(key)) {
            // Access to refresh the order
            map.get(key);
        }
    }

    @Override
    public String toString() {
        return map.keySet().toString();
    }
}