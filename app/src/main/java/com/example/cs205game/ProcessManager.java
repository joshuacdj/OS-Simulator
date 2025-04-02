package com.example.cs205game;

import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

public class ProcessManager {
    private static final String TAG = "ProcessManager";
    private static final int MAX_QUEUE_CAPACITY = 10;
    private static final double MIN_SPAWN_INTERVAL_S = 4.0;
    private static final double MAX_SPAWN_INTERVAL_S = 8.0;
    private static final double IO_PROCESS_PROBABILITY = 0.4; // 40%
    private static final double BASE_PATIENCE_S = 15.0;
    private static final double MIN_CPU_TIME_S = 4.0;
    private static final double MAX_CPU_TIME_S = 8.0;
    private static final double MIN_IO_TIME_S = 3.0; // Example IO time range
    private static final double MAX_IO_TIME_S = 6.0; // Example IO time range
    private static final int MIN_MEMORY_REQ = 1;
    private static final int MAX_MEMORY_REQ = 16;
    // Controls rarity of high memory reqs (lower value = rarer)
    private static final double HIGH_MEMORY_PROBABILITY_FACTOR = 0.2;

    private final Queue<Process> processQueue;
    private final Random random;
    private double timeSinceLastSpawn = 0.0;
    private double nextSpawnTime = 0.0; // Time until next spawn

    public ProcessManager() {
        this.processQueue = new LinkedList<>(); // Using LinkedList as a Queue
        this.random = new Random();
        scheduleNextSpawn();
    }

    /**
     * Updates the process manager, potentially spawning a new process and
     * updating patience for processes in the queue.
     *
     * @param deltaTime Time elapsed since the last update in seconds.
     * @param onPatienceExpired A callback to handle processes whose patience runs out.
     */
    public void update(double deltaTime, java.util.function.Consumer<Process> onPatienceExpired) {
        // 1. Update patience for existing processes in the queue
        updatePatienceCounters(deltaTime, onPatienceExpired);

        // 2. Check if it's time to spawn a new process
        timeSinceLastSpawn += deltaTime;
        if (timeSinceLastSpawn >= nextSpawnTime) {
            if (processQueue.size() < MAX_QUEUE_CAPACITY) {
                spawnProcess();
                timeSinceLastSpawn = 0.0; // Reset timer
                scheduleNextSpawn(); // Schedule the *next* one
            } else {
                 Log.d(TAG, "Queue full, delaying spawn.");
                 // Optional: reschedule spawn check for a short delay instead of waiting full interval?
                 // For now, just waits until next update tick after space frees up.
            }
        }
    }

    private void updatePatienceCounters(double deltaTime, java.util.function.Consumer<Process> onPatienceExpired) {
        // Using an iterator to safely remove elements while iterating
        java.util.Iterator<Process> iterator = processQueue.iterator();
        while (iterator.hasNext()) {
            Process p = iterator.next();
            if (!p.decrementPatience(deltaTime)) {
                // Patience ran out
                Log.i(TAG, "Patience ran out for " + p);
                onPatienceExpired.accept(p); // Notify listener (e.g., GameManager)
                iterator.remove(); // Remove from queue
            }
        }
    }

    private void scheduleNextSpawn() {
        nextSpawnTime = MIN_SPAWN_INTERVAL_S + (random.nextDouble() * (MAX_SPAWN_INTERVAL_S - MIN_SPAWN_INTERVAL_S));
        Log.d(TAG, String.format("Next spawn scheduled in %.2f seconds", nextSpawnTime));
    }

    private void spawnProcess() {
        // Determine Process Type
        boolean isIOProcess = random.nextDouble() < IO_PROCESS_PROBABILITY;

        // Determine Parameters
        int memory = generateMemoryRequirement();
        double patience = BASE_PATIENCE_S; // Fixed for now, could be randomized
        double cpuTime = MIN_CPU_TIME_S + (random.nextDouble() * (MAX_CPU_TIME_S - MIN_CPU_TIME_S));

        Process newProcess;
        if (isIOProcess) {
            double ioTime = MIN_IO_TIME_S + (random.nextDouble() * (MAX_IO_TIME_S - MIN_IO_TIME_S));
            newProcess = new IOProcess(memory, patience, cpuTime, ioTime);
             Log.i(TAG, "Spawning IOProcess: " + newProcess);
        } else {
            newProcess = new Process(memory, patience, cpuTime);
             Log.i(TAG, "Spawning Process: " + newProcess);
        }

        processQueue.offer(newProcess); // Add to the end of the queue
    }

    private int generateMemoryRequirement() {
        // Tiered approach for memory generation
        double roll = random.nextDouble();

        if (roll < 0.60) { // 60% chance for 1-4 GB
            return MIN_MEMORY_REQ + random.nextInt(4); // Generates 1, 2, 3, 4
        } else if (roll < 0.90) { // 30% chance for 5-8 GB (0.60 to 0.899...)
            return MIN_MEMORY_REQ + 4 + random.nextInt(4); // Generates 5, 6, 7, 8
        } else if (roll < 0.98) { // 8% chance for 9-12 GB (0.90 to 0.979...)
            return MIN_MEMORY_REQ + 8 + random.nextInt(4); // Generates 9, 10, 11, 12
        } else { // 2% chance for 13-16 GB (0.98 to 0.999...)
            return MIN_MEMORY_REQ + 12 + random.nextInt(4); // Generates 13, 14, 15, 16
        }
    }

    public Queue<Process> getProcessQueue() {
        return processQueue;
    }

    /**
     * Removes and returns the process at the head of the queue (FIFO).
     * Returns null if the queue is empty.
     */
    public Process takeProcessFromQueue() {
        return processQueue.poll();
    }

     /**
      * Checks if the process with the given ID is currently at the head of the queue.
      * Needed for the FCFS drag-and-drop validation.
      * @param processId The ID of the process being checked.
      * @return true if the process is at the head, false otherwise.
      */
     public boolean isProcessAtHead(int processId) {
         Process head = processQueue.peek();
         return head != null && head.getId() == processId;
     }

} 