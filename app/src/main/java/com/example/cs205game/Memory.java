package com.example.cs205game;

import android.util.Log;

public class Memory {
    private static final String TAG = "Memory";
    private final int capacity;
    private int availableMemory;

    public Memory(int capacity) {
        this.capacity = capacity;
        this.availableMemory = capacity;
        Log.i(TAG, "Memory initialized with capacity: " + capacity);
    }

    public int getCapacity() {
        return capacity;
    }

    public int getAvailableMemory() {
        return availableMemory;
    }

    public int getUsedMemory() {
        return capacity - availableMemory;
    }

    /**
     * Checks if there is enough available memory for a given requirement.
     * @param memoryRequired The amount of memory needed.
     * @return true if enough memory is available, false otherwise.
     */
    public synchronized boolean hasEnoughMemory(int memoryRequired) {
        return availableMemory >= memoryRequired;
    }

    /**
     * Attempts to allocate a certain amount of memory.
     * Succeeds only if enough memory is available.
     * @param memoryToAllocate The amount of memory to allocate.
     * @return true if allocation was successful, false otherwise.
     */
    public synchronized boolean allocateMemory(int memoryToAllocate) {
        if (hasEnoughMemory(memoryToAllocate)) {
            availableMemory -= memoryToAllocate;
            Log.d(TAG, "Allocated " + memoryToAllocate + "GB. Available: " + availableMemory);
            return true;
        }
        Log.w(TAG, "Failed to allocate " + memoryToAllocate + "GB. Only " + availableMemory + " available.");
        return false;
    }

    /**
     * Frees up a previously allocated amount of memory.
     * @param memoryToFree The amount of memory to free.
     */
    public synchronized void freeMemory(int memoryToFree) {
        if (memoryToFree <= 0) return;

        availableMemory += memoryToFree;
        if (availableMemory > capacity) {
            Log.w(TAG, "Freed more memory than capacity? Freed: " + memoryToFree + ", Available: " + availableMemory + ", Capacity: " + capacity);
            availableMemory = capacity; // Cap at maximum capacity
        }
         Log.d(TAG, "Freed " + memoryToFree + "GB. Available: " + availableMemory);
    }
} 