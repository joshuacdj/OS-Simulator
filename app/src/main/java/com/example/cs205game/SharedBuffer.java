package com.example.cs205game;

import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.atomic.AtomicInteger;

public class SharedBuffer {
    private static final String TAG = "SharedBuffer";
    private final Queue<Process> buffer;
    private final int capacity;
    
    // More robust thread-safety with explicit locks and conditions
    private final ReentrantLock bufferLock = new ReentrantLock();
    private final Condition notFull = bufferLock.newCondition();
    private final Condition notEmpty = bufferLock.newCondition();
    
    // Track statistics for monitoring
    private final AtomicInteger putCount = new AtomicInteger(0);
    private final AtomicInteger takeCount = new AtomicInteger(0);
    private volatile boolean isShutdown = false;

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
        if (process == null) {
            throw new IllegalArgumentException("Cannot put null process into buffer");
        }
        
        bufferLock.lock();
        try {
            // Wait while buffer is full and not shutdown
            while (buffer.size() >= capacity && !isShutdown) {
                Log.d(TAG, "Buffer full, waiting to put Process " + process.getId());
                notFull.await(); // More specific condition than wait()
            }
            
            // Check if shutdown occurred while waiting
            if (isShutdown) {
                throw new InterruptedException("Buffer was shut down while waiting to put");
            }
            
            // Add to buffer
            Log.d(TAG, "Putting Process " + process.getId() + " into buffer.");
            process.resetBufferCooldown(); // Reset cooldown when adding to buffer
            buffer.offer(process);
            putCount.incrementAndGet();
            
            // Signal consumers that buffer is not empty
            notEmpty.signalAll(); // Wake up consumers
        } finally {
            bufferLock.unlock();
        }
    }

    /**
     * Takes a completed process from the buffer. Blocks if the buffer is empty or the head process is not ready.
     * Called by the consumers (Clients).
     * @return The process taken from the buffer.
     * @throws InterruptedException if the thread is interrupted while waiting.
     */
    public Process take() throws InterruptedException {
        Process takenProcess = null;
        
        bufferLock.lock();
        try {
            // Wait while buffer is empty or head process not ready, and not shutdown
            while ((buffer.isEmpty() || !isHeadProcessReady()) && !isShutdown) {
                if (buffer.isEmpty()) {
                    Log.d(TAG, "Buffer empty, waiting to take...");
                } else {
                    Log.d(TAG, "Buffer not empty, but head Process " + buffer.peek().getId() + 
                          " not ready (cooldown: " + buffer.peek().getBufferCooldownRemaining() + 
                          "), waiting...");
                }
                notEmpty.await(); // More specific condition than wait()
            }
            
            // Check if shutdown occurred while waiting
            if (isShutdown && buffer.isEmpty()) {
                return null; // Return null on shutdown with empty buffer
            }
            
            // If we get here, either buffer has an item or shutdown occurred
            if (!buffer.isEmpty() && isHeadProcessReady()) {
                takenProcess = buffer.poll();
                Log.d(TAG, "Taking Process " + takenProcess.getId() + " from buffer.");
                takeCount.incrementAndGet();
                
                // Signal producers that buffer is not full
                notFull.signalAll(); // Wake up producers
            }
            
            return takenProcess;
        } finally {
            bufferLock.unlock();
        }
    }

    /**
     * Non-blocking attempt to take a process from the buffer.
     * @return A process if one is ready, or null if buffer is empty or no process is ready.
     */
    public Process tryTake() {
        bufferLock.lock();
        try {
            if (buffer.isEmpty() || !isHeadProcessReady()) {
                return null;
            }
            
            Process takenProcess = buffer.poll();
            Log.d(TAG, "Non-blocking take of Process " + takenProcess.getId() + " from buffer.");
            takeCount.incrementAndGet();
            
            // Signal producers that buffer is not full
            notFull.signalAll();
            return takenProcess;
        } finally {
            bufferLock.unlock();
        }
    }

    /**
     * Checks if the head process is ready for consumption.
     * Must be called with lock held.
     */
    private boolean isHeadProcessReady() {
        return !buffer.isEmpty() && buffer.peek().isReadyForConsumption();
    }

    /** Returns a snapshot of the processes currently in the buffer. */
    public Process[] getProcessesInBuffer() {
        bufferLock.lock();
        try {
            Process[] processes = new Process[buffer.size()];
            buffer.toArray(processes); // More efficient way to copy to array
            return processes;
        } finally {
            bufferLock.unlock();
        }
    }

    /** Returns the current number of items in the buffer. */
    public int size() {
        bufferLock.lock();
        try {
            return buffer.size();
        } finally {
            bufferLock.unlock();
        }
    }

    /** Returns the total capacity of the buffer. */
    public int getCapacity() {
        return capacity; // Immutable value, no locking needed
    }

    /** Returns the total number of put operations performed. */
    public int getPutCount() {
        return putCount.get();
    }

    /** Returns the total number of take operations performed. */
    public int getTakeCount() {
        return takeCount.get();
    }

    /** Updates cooldowns and notifies waiting consumers if the head becomes ready. */
    public void update(double deltaTime) {
        bufferLock.lock();
        try {
            boolean headWasReady = !buffer.isEmpty() && buffer.peek().isReadyForConsumption();

            // Update cooldown for all processes in buffer
            for (Process p : buffer) {
                p.updateBufferCooldown(deltaTime);
            }

            // Check if the head became ready *after* updates
            boolean headIsNowReady = !buffer.isEmpty() && buffer.peek().isReadyForConsumption();

            // If the head wasn't ready before, but is ready now, notify waiting consumers
            if (!headWasReady && headIsNowReady) {
                Log.d(TAG, "Head Process " + buffer.peek().getId() + " became ready, notifying consumers.");
                notEmpty.signalAll();
            }
        } finally {
            bufferLock.unlock();
        }
    }

    /** 
     * Shuts down the buffer, waking up any waiting threads.
     * Used when the game is stopping or resetting.
     */
    public void shutdown() {
        bufferLock.lock();
        try {
            isShutdown = true;
            // Wake up all waiting threads
            notEmpty.signalAll();
            notFull.signalAll();
            Log.d(TAG, "SharedBuffer shutdown signaled, waking all waiting threads.");
        } finally {
            bufferLock.unlock();
        }
    }

    /** Clears the buffer of all processes. */
    public void clear() {
        bufferLock.lock();
        try {
            buffer.clear();
            isShutdown = false; // Reset shutdown flag
            Log.d(TAG, "SharedBuffer cleared.");
            // Signal producers since buffer is now empty
            notFull.signalAll();
        } finally {
            bufferLock.unlock();
        }
    }
} 