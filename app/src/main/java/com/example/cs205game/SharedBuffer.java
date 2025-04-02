package com.example.cs205game;

import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SharedBuffer {
    private static final String TAG = "SharedBuffer";
    private final Queue<Process> buffer;
    private final int capacity;
    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    public SharedBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new LinkedList<>();
        Log.i(TAG, "SharedBuffer initialized with capacity: " + capacity);
    }

    /**
     * Adds a completed process to the buffer. Blocks if the buffer is full.
     * Called by the producer (GameManager after CPU completion).
     * @param process The completed process to add.
     * @throws InterruptedException if the thread is interrupted while waiting.
     */
    public void put(Process process) throws InterruptedException {
        lock.lock();
        try {
            while (buffer.size() == capacity) {
                Log.d(TAG, "Buffer is full, waiting to put Process " + process.getId());
                notFull.await(); // Wait until buffer is not full
            }
            buffer.offer(process);
            process.setCurrentState(Process.ProcessState.IN_BUFFER);
            Log.i(TAG, "Process " + process.getId() + " added to buffer. Size: " + buffer.size());
            notEmpty.signal(); // Signal that the buffer is no longer empty
        } finally {
            lock.unlock();
        }
    }

    /**
     * Takes a completed process from the buffer. Blocks if the buffer is empty.
     * Called by the consumers (Clients).
     * @return The process taken from the buffer.
     * @throws InterruptedException if the thread is interrupted while waiting.
     */
    public Process take() throws InterruptedException {
        lock.lock();
        try {
            while (buffer.isEmpty()) {
                Log.d(TAG, "Buffer is empty, waiting to take process.");
                notEmpty.await(); // Wait until buffer is not empty
            }
            Process process = buffer.poll();
             Log.i(TAG, "Process " + process.getId() + " taken from buffer. Size: " + buffer.size());
            notFull.signal(); // Signal that the buffer is no longer full
            return process;
        } finally {
            lock.unlock();
        }
    }

    public int getCurrentSize() {
        lock.lock();
        try {
            return buffer.size();
        } finally {
            lock.unlock();
        }
    }

     public Queue<Process> getBufferQueue() { // Mostly for inspection/drawing
         // Return a copy or handle synchronization if iterating externally
         lock.lock();
         try {
             // Be cautious returning the raw queue if modification is possible elsewhere
             // For read-only UI purposes, might be okay with external sync
             // Or return Collections.unmodifiableQueue(new LinkedList<>(buffer));
             return buffer; // Returning direct reference - requires careful handling externally
         } finally {
             lock.unlock();
         }
     }
} 