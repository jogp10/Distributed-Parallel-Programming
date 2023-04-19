package main.utils;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentLinkedQueue<T> {
    private Node<T> head;
    private Node<T> tail;
    private Lock lock;
    private Condition condition;

    public ConcurrentLinkedQueue() {
        head = new Node<T>(null);
        tail = head;
        lock = new ReentrantLock();
        condition = lock.newCondition();
    }

    public void offer(T element) {
        Node<T> newNode = new Node<T>(element);
        lock.lock();
        try {
            tail.next = newNode;
            tail = newNode;
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    public T poll() throws InterruptedException {
        lock.lock();
        try {
            while (head.next == null) {
                condition.await();
            }
            T element = head.next.element;
            head = head.next;
            return element;
        } finally {
            lock.unlock();
        }
    }

    private static class Node<T> {
        private T element;
        private Node<T> next;

        public Node(T element) {
            this.element = element;
        }
    }
}
