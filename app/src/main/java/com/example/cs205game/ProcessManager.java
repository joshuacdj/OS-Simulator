package com.example.cs205game;

import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

public class ProcessManager {
    private static final String TAG = "ProcessManager";
    private static final int MAX_QUEUE_CAPACITY = 10;
    private static final double MIN_SPAWN_INTERVAL = 3.0; // Minimum time between spawns
    private static final double MAX_SPAWN_INTERVAL = 5.0; // Maximum time between spawns
    private static final double IO_PROCESS_PROBABILITY = 0.5; // 50% chance for IO process (increased from 30%)
    private static final double BASE_PATIENCE_S = 15.0; // Base patience for processes
    private static final double MIN_CPU_TIME_S = 4.0; // Minimum CPU time
    private static final double MAX_CPU_TIME_S = 8.0; // Maximum CPU time
    private static final double MIN_IO_TIME_S = 3.0; // Minimum IO time
    private static final double MAX_IO_TIME_S = 6.0; // Maximum IO time
    private static final int MIN_MEMORY_REQ = 1; // Minimum memory requirement
    private static final int MAX_MEMORY_REQ = 16; // Maximum memory requirement
    private static final double HIGH_MEMORY_PROBABILITY_FACTOR = 0.2; // Controls rarity of high memory reqs

    private double spawnTimer; // Timer for spawning processes
    private final Random random; // Random number generator
    private final Queue<Process> processQueue; // Queue of processes
    private int nextProcessId = 1; // Next process ID

    public ProcessManager() {
        random = new Random();
        processQueue = new LinkedList<>();
        resetSpawnTimer();
    }

    /** Resets the spawn timer to a random value between MIN_SPAWN_INTERVAL and MAX_SPAWN_INTERVAL. */
    private void resetSpawnTimer() {
        spawnTimer = MIN_SPAWN_INTERVAL + random.nextDouble() * (MAX_SPAWN_INTERVAL - MIN_SPAWN_INTERVAL); 
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
        spawnTimer -= deltaTime;
        
        if (spawnTimer <= 0 && processQueue.size() < MAX_QUEUE_CAPACITY) {
            spawnProcess();
            resetSpawnTimer();
        }
    }

    /**
     * Updates the patience counters for all processes in the queue.
     * Removes processes whose patience has run out.
     * @param deltaTime Time elapsed since the last update in seconds.
     * @param onPatienceExpired A callback to handle processes whose patience runs out.
     */
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

    /**
     * Spawns a new process.
     * Determines if the process is an IO process based on a random roll.
     * Creates a new Process object with the appropriate parameters.
     */
    private void spawnProcess() {
        if (processQueue.size() >= MAX_QUEUE_CAPACITY) return;

        boolean isIOProcess = random.nextDouble() < IO_PROCESS_PROBABILITY;
        Process newProcess;
        
        // Memory requirements distribution based on process type and 16GB system memory
        int memory;
        double roll = random.nextDouble();
        
        if (isIOProcess) {
            // IO processes tend to need more memory
            if (roll < 0.50) {      // 50% chance: 3-5 GB
                memory = 3 + random.nextInt(3);
            } else if (roll < 0.80) { // 30% chance: 6-8 GB
                memory = 6 + random.nextInt(3);
            } else if (roll < 0.95) { // 15% chance: 9-12 GB
                memory = 9 + random.nextInt(4);
            } else {                // 5% chance: 13-16 GB (heavy workload)
                memory = 13 + random.nextInt(4);
            }
            
            double patience = BASE_PATIENCE_S;
            double cpuTime = 3 + random.nextDouble() * 3;  // CPU Time: 3-6 seconds
            double ioTime = 2 + random.nextDouble() * 3;   // IO Time: 2-5 seconds
            
            newProcess = new IOProcess(memory, patience, cpuTime, ioTime);
            Log.d(TAG, "Spawned IO Process with memory: " + memory + "GB, CPU time: " + 
                   String.format("%.1f", cpuTime) + "s, IO time: " + 
                   String.format("%.1f", ioTime) + "s");
        } else {
            // Regular processes typically need less memory
            if (roll < 0.60) {      // 60% chance: 1-2 GB
                memory = 1 + random.nextInt(2);
            } else if (roll < 0.85) { // 25% chance: 3-5 GB
                memory = 3 + random.nextInt(3);
            } else if (roll < 0.97) { // 12% chance: 6-8 GB
                memory = 6 + random.nextInt(3);
            } else {                // 3% chance: 9-12 GB (heavy processing)
                memory = 9 + random.nextInt(4);
            }
            
            double patience = BASE_PATIENCE_S;
            double cpuTime = 2 + random.nextDouble() * 2;  // CPU Time: 2-4 seconds
            
            newProcess = new Process(memory, patience, cpuTime);
            Log.d(TAG, "Spawned Regular Process with memory: " + memory + 
                   "GB, CPU time: " + String.format("%.1f", cpuTime) + "s");
        }
        
        processQueue.offer(newProcess);
        newProcess.setCurrentState(Process.ProcessState.IN_QUEUE);
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

    /** Resets the process manager, clearing the queue and resetting spawn timer. */
    public synchronized void reset() {
        processQueue.clear();
        resetSpawnTimer();
        nextProcessId = 1; // Reset process ID counter
        Log.d(TAG, "ProcessManager reset.");
    }
} 