package main.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConcurrentList<T> implements Iterable<T> {
    private final List<T> list;
    private final ReadWriteLock lock;

    public ConcurrentList() {
        list = new ArrayList<>();
        lock = new ReentrantReadWriteLock();
    }

    public void add(T element) {
        lock.writeLock().lock();
        try {
            list.add(element);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void remove(T element) {
        lock.writeLock().lock();
        try {
            list.remove(element);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean contains(T element) {
        lock.readLock().lock();
        try {
            return list.contains(element);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<T> getAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(list);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return list.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Iterator<T> iterator() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(list).iterator();
        } finally {
            lock.readLock().unlock();
        }
    }
}
