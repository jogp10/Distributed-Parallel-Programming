package main.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentHashMap<K, V> {
    private final Map<K, V> map;
    private final ReentrantLock lock;

    public ConcurrentHashMap() {
        map = new HashMap<>();
        lock = new ReentrantLock();
    }

    public V get(K key) {
        lock.lock();
        try {
            return map.get(key);
        } finally {
            lock.unlock();
        }
    }

    public V getOrDefault(K key, V defaultValue) {
        lock.lock();
        try {
            return map.getOrDefault(key, defaultValue);
        } finally {
            lock.unlock();
        }
    }

    public void put(K key, V value) {
        lock.lock();
        try {
            map.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    public void remove(K key) {
        lock.lock();
        try {
            map.remove(key);
        } finally {
            lock.unlock();
        }
    }

    public boolean containsKey(K key) {
        lock.lock();
        try {
            return map.containsKey(key);
        } finally {
            lock.unlock();
        }
    }

    public boolean containsValue(V value) {
        lock.lock();
        try {
            return map.containsValue(value);
        } finally {
            lock.unlock();
        }
    }
}
