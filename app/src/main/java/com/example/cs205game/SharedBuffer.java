package com.example.cs205game;

import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;

public class SharedBuffer {
    private static final String TAG = "SharedBuffer";
    private final Queue<Process> buffer;
    private final int capacity;

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
    public synchronized void put(Process process) throws InterruptedException {
        while (buffer.size() >= capacity) {
            Log.d(TAG, "Buffer full, waiting to put Process " + process.getId());
            wait(); // Waits on this object's monitor
        }
        Log.d(TAG, "Putting Process " + process.getId() + " into buffer.");
        process.resetBufferCooldown(); // Reset cooldown when adding to buffer
        buffer.offer(process);
        notifyAll(); // Notifies threads waiting on this object's monitor (e.g., in take())
    }

    /**
     * Takes a completed process from the buffer. Blocks if the buffer is empty or the head process is not ready.
     * Called by the consumers (Clients).
     * @return The process taken from the buffer.
     * @throws InterruptedException if the thread is interrupted while waiting.
     */
    public synchronized Process take() throws InterruptedException {
        while (buffer.isEmpty() || !buffer.peek().isReadyForConsumption()) {
            if (buffer.isEmpty()) {
                 Log.d(TAG, "Buffer empty, waiting to take...");
            } else {
                 Log.d(TAG, "Buffer not empty, but head Process " + buffer.peek().getId() + " not ready (cooldown: " + buffer.peek().getBufferCooldownRemaining() + "), waiting...");
            }
            wait(); // Waits on this object's monitor
        }
        Process takenProcess = buffer.poll();
        Log.d(TAG, "Taking Process " + takenProcess.getId() + " from buffer.");
        // No need to notify here, as taking doesn't make it non-empty necessarily for others waiting on non-empty.
        // notifyAll(); // This might be needed if put waits on non-full
        return takenProcess;
    }

    /** Returns a snapshot of the processes currently in the buffer. */
    public synchronized Process[] getProcessesInBuffer() {
        Process[] processes = new Process[buffer.size()];
        buffer.toArray(processes); // More efficient way to copy to array
        return processes;
    }

    /** Returns the current number of items in the buffer. */
    public synchronized int size() {
        return buffer.size();
    }

    /** Returns the total capacity of the buffer. */
    public int getCapacity() {
        return capacity;
    }

    /** Updates cooldowns and notifies waiting consumers if the head becomes ready. */
    public synchronized void update(double deltaTime) {
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
            notifyAll();
        }
    }

    /** Clears the buffer of all processes. */
    public synchronized void clear() {
        buffer.clear();
        Log.d(TAG, "SharedBuffer cleared.");
    }
} 