package main.utils;

import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentLinkedList<T> implements Iterable<T> {
    private Node<T> head;
    private Node<T> tail;
    private final Lock lock;

    public ConcurrentLinkedList() {
        head = null;
        tail = null;
        lock = new ReentrantLock();
    }

    public void add(T element) {
        Node<T> newNode = new Node<>(element);

        lock.lock();
        try {
            if (head == null) {
                head = newNode;
                tail = newNode;
            } else {
                tail.next = newNode;
                tail = newNode;
            }
        } finally {
            lock.unlock();
        }
    }

    public void remove(T element) {
        lock.lock();
        try {
            Node<T> prev = null;
            Node<T> curr = head;

            while (curr != null) {
                if (curr.value.equals(element)) {
                    if (prev == null) {
                        head = curr.next;
                        if (head == null) {
                            tail = null;
                        }
                    } else {
                        prev.next = curr.next;
                        if (curr.next == null) {
                            tail = prev;
                        }
                    }
                    break;
                }
                prev = curr;
                curr = curr.next;
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean contains(T element) {
        lock.lock();
        try {
            Node<T> curr = head;
            while (curr != null) {
                if (curr.value.equals(element)) {
                    return true;
                }
                curr = curr.next;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Iterator<T> iterator() {
        return null;
    }

    private static class Node<T> {
        private T value;
        private Node<T> next;

        public Node(T value) {
            this.value = value;
            this.next = null;
        }
    }
}
