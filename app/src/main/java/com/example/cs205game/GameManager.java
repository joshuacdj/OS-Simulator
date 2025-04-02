package com.example.cs205game;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GameManager {
    private static final String TAG = "GameManager";
    // Make constants public so GameView can access them for drawing
    public static final int INITIAL_HEALTH = 100; // Example value
    private static final int PATIENCE_PENALTY = 10; // Example value
    private static final int FCFS_PENALTY = 5; // Example value
    private static final int PROCESS_COMPLETION_SCORE = 20; // Example value
    private static final int NUM_CORES = 4;
    public static final int MEMORY_CAPACITY = 16;
    public static final int BUFFER_CAPACITY = 5; // Example buffer size
    private static final int NUM_CLIENTS = 2; // Example number of clients

    private int score;
    private int health;
    private final Memory memory;
    private final ProcessManager processManager;
    private final List<Core> cpuCores;
    private final IOArea ioArea;
    private final SharedBuffer sharedBuffer;
    private final List<Client> clients;
    private final ExecutorService clientExecutor; // Executor for client threads
    private volatile boolean gameRunning = false;

    // Add references for UI updates later (e.g., GameView)

    public GameManager() {
        this.score = 0;
        this.health = INITIAL_HEALTH;
        this.memory = new Memory(MEMORY_CAPACITY);
        this.processManager = new ProcessManager();
        this.ioArea = new IOArea();
        this.cpuCores = new ArrayList<>(NUM_CORES);
        for (int i = 0; i < NUM_CORES; i++) {
            cpuCores.add(new Core(i));
        }
        this.sharedBuffer = new SharedBuffer(BUFFER_CAPACITY);
        this.clients = new ArrayList<>(NUM_CLIENTS);
        // Using an ExecutorService to manage client threads is generally better than raw Threads
        this.clientExecutor = Executors.newFixedThreadPool(NUM_CLIENTS);
        for (int i = 0; i < NUM_CLIENTS; i++) {
            clients.add(new Client(i, sharedBuffer, this));
        }
        Log.i(TAG, "GameManager initialized.");
    }

    public void startGame() {
        if (gameRunning) return;
        Log.i(TAG, "Starting game and client threads...");
        gameRunning = true;
        // Submit client tasks to the executor
        for(Client client : clients) {
            clientExecutor.submit(client);
        }
    }

    public void stopGame() {
        if (!gameRunning) return;
        Log.i(TAG, "Stopping game and client threads...");
        gameRunning = false;

        // Signal clients to stop
        for (Client client : clients) {
            client.stop();
        }

        // Shutdown executor pool
        clientExecutor.shutdown(); // Disable new tasks from being submitted
        try {
            // Give tasks time to complete
            if (!clientExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                clientExecutor.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!clientExecutor.awaitTermination(1, TimeUnit.SECONDS))
                    Log.e(TAG, "Client executor did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            clientExecutor.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
        Log.i(TAG, "Client threads stopped.");
    }

    /**
     * The main update loop for the game logic.
     * Should only run if gameRunning is true.
     * @param deltaTime Time elapsed since the last update in seconds.
     */
    public void update(double deltaTime) {
        if (!gameRunning) return;

        // Update process spawning and queue
        processManager.update(deltaTime, this::handlePatienceExpired);

        // Update buffer cooldowns
        sharedBuffer.update(deltaTime);

        // Update cores
        for (Core core : cpuCores) {
            core.update(deltaTime, this::handleCpuCompleted, this::handleIoRequired);
        }

        // Update IO Area
        ioArea.update(deltaTime, this::handleIoCompleted);

        // Memory doesn't need update
        // memory.update(); // Remove this line as Memory class doesn't have an update method

        // Check for game over condition
        if (health <= 0) {
            Log.wtf(TAG, "GAME OVER! Health reached zero.");
            stopGame();
        }
    }

    // --- Callback Handlers ---

    private void handlePatienceExpired(Process process) {
        if (!gameRunning) return; // Don't penalize if game stopped
        Log.w(TAG, "Process " + process.getId() + " removed due to expired patience.");
        decreaseHealth(PATIENCE_PENALTY);
        // TODO: Update UI to remove the process visually
    }

    // This method is called from the main game thread (Core update)
    private void handleCpuCompleted(Process process) {
        if (!gameRunning) return;
        Log.i(TAG, "Process " + process.getId() + " completed CPU work. Attempting to add to buffer.");

        // Attempt to put the process in the buffer asynchronously
        // to avoid blocking the main game loop if the buffer is full.
        // Using a simple approach here: If buffer is instantly full, log warning.
        // A more robust solution might use a separate thread or queue for putting.
        new Thread(() -> {
            try {
                sharedBuffer.put(process); // This might block the new thread if buffer full
                // TODO: Update UI to show process in buffer area (needs thread-safe UI update)
            } catch (InterruptedException e) {
                Log.w(TAG, "Thread interrupted while trying to put Process " + process.getId() + " into buffer.");
                Thread.currentThread().interrupt();
                // Handle potential state inconsistency if needed
            }
        }).start();
    }

    private void handleIoRequired(IOProcess ioProcess) {
        if (!gameRunning) return;
        Log.i(TAG, "IO Required for Process " + ioProcess.getId() + ". Waiting for user action.");
        // TODO: Update UI to indicate IO is required (e.g., highlight process/IO area)
    }

    private void handleIoCompleted(IOProcess ioProcess) {
        if (!gameRunning) return;
         Log.i(TAG, "IO Completed for Process " + ioProcess.getId() + ". Waiting for user action.");
         // TODO: Update UI to indicate IO is finished and ready to move back.
    }

    /**
     * Callback method executed by a Client thread after consuming a process.
     * MUST be thread-safe if accessing shared GameManager state directly.
     * @param consumedProcess The process that was successfully consumed.
     */
    // Note: This is called from Client threads
    public synchronized void handleClientConsumed(Process consumedProcess) {
        if (!gameRunning) return; // Ignore if game stopped between consumption and callback
        Log.i(TAG, "Handling consumption of Process " + consumedProcess.getId());
        increaseScore(PROCESS_COMPLETION_SCORE);
        memory.freeMemory(consumedProcess.getMemoryRequirement());
        // TODO: Update UI to remove process from buffer/client area
    }

    // --- Action Methods (Called by UI/Input Handler - Main Thread) ---

    /**
     * Attempts to move a process from the queue to a specific core.
     * Handles FCFS check, memory allocation, and core assignment.
     * @param processId The ID of the process to move.
     * @param targetCoreId The ID of the target core.
     */
    public void moveProcessFromQueueToCore(int processId, int targetCoreId) {
        if (!gameRunning) return;
        if (targetCoreId < 0 || targetCoreId >= cpuCores.size()) {
            Log.e(TAG, "Invalid target Core ID: " + targetCoreId);
            return;
        }
        Core targetCore = cpuCores.get(targetCoreId);

        // 1. Check FCFS
        if (!processManager.isProcessAtHead(processId)) {
            Log.w(TAG, "FCFS Violation: Process " + processId + " is not at the head of the queue.");
            decreaseHealth(FCFS_PENALTY);
            // TODO: Provide visual feedback for FCFS error
            return;
        }

        // 2. Check if core is free
        if (targetCore.isUtilized()) {
             Log.w(TAG, "User Action Failed: Core " + targetCoreId + " is busy.");
             // TODO: Provide visual feedback (e.g., cannot drop here)
             return;
        }

        // 3. Peek process from queue (don't remove yet)
        Process processToMove = processManager.getProcessQueue().peek(); // Assuming FCFS check passed
        if (processToMove == null || processToMove.getId() != processId) {
            Log.e(TAG, "Mismatch between FCFS check and queue head? ProcessID: " + processId);
            return; // Should not happen if FCFS check passed
        }

        // 4. Check memory
        if (!memory.hasEnoughMemory(processToMove.getMemoryRequirement())) {
             Log.w(TAG, "User Action Failed: Not enough memory for Process " + processId + ". Required: " + processToMove.getMemoryRequirement() + ", Available: " + memory.getAvailableMemory());
             // TODO: Provide visual feedback (e.g., memory full indicator)
             return;
        }

        // 5. All checks passed: Allocate memory, remove from queue, assign to core
        if (memory.allocateMemory(processToMove.getMemoryRequirement())) {
             processManager.takeProcessFromQueue(); // Now remove from queue
             targetCore.assignProcess(processToMove);
             // TODO: Update UI - Animate process moving, update memory visuals
        } else {
            // Should not happen due to check, but handle defensively
             Log.e(TAG, "Memory allocation failed unexpectedly after check for Process " + processId);
        }
    }

    /**
     * Attempts to move an IOProcess from a core to the IO area.
     * @param processId The ID of the IOProcess to move.
     * @param sourceCoreId The ID of the core the process is currently on.
     */
    public void moveProcessFromCoreToIO(int processId, int sourceCoreId) {
        if (!gameRunning) return;
         if (sourceCoreId < 0 || sourceCoreId >= cpuCores.size()) {
             Log.e(TAG, "Invalid source Core ID: " + sourceCoreId);
             return;
         }
         Core sourceCore = cpuCores.get(sourceCoreId);
         Process processOnCore = sourceCore.getCurrentProcess();

         // 1. Check if the correct process is on the specified core
         if (processOnCore == null || processOnCore.getId() != processId) {
             Log.w(TAG, "User Action Failed: Process " + processId + " not found on Core " + sourceCoreId);
             return;
         }

         // 2. Check if it's actually an IOProcess and if IO is required (Refactored for Java 11)
         if (processOnCore instanceof IOProcess) {
             IOProcess ioProcess = (IOProcess) processOnCore;
             if (!ioProcess.isCpuPausedForIO() || ioProcess.isIoCompleted()) {
                 Log.w(TAG, "User Action Failed: Process " + processId + " on Core " + sourceCoreId + " is not an IOProcess waiting for IO.");
                 return;
             }
             // If we get here, it IS an IOProcess ready for IO
         } else {
             // It's not an IOProcess at all
             Log.w(TAG, "User Action Failed: Process " + processId + " on Core " + sourceCoreId + " is not an IOProcess.");
             return;
         }

         // 3. Check if IO Area is free
         if (ioArea.isBusy()) {
             Log.w(TAG, "User Action Failed: IOArea is busy.");
             return;
         }

         // 4. All checks passed: Remove from core, assign to IO Area
         IOProcess ioProcessToMove = (IOProcess) sourceCore.removeProcess(); // Safe cast now
         if (ioProcessToMove != null) {
             ioArea.assignProcess(ioProcessToMove);
         } else {
              Log.e(TAG, "Error removing process from core during move to IO.");
         }
    }

    /**
     * Attempts to move a completed IOProcess from the IO area back to its original core.
     * @param processId The ID of the IOProcess to move.
     * @param targetCoreId The ID of the core to return to.
     */
    public void moveProcessFromIOToCore(int processId, int targetCoreId) {
        if (!gameRunning) return;
        if (targetCoreId < 0 || targetCoreId >= cpuCores.size()) {
            Log.e(TAG, "Invalid target Core ID: " + targetCoreId);
            return;
        }
        Core targetCore = cpuCores.get(targetCoreId);
        IOProcess processInIO = ioArea.getCurrentProcess();

        // 1. Check if the correct process is in the IO area
        if (processInIO == null || processInIO.getId() != processId) {
            Log.w(TAG, "User Action Failed: Process " + processId + " not found in IO Area.");
            return;
        }

        // 2. Check if IO is actually completed
        if (!processInIO.isIoCompleted()) {
            Log.w(TAG, "User Action Failed: IO not yet complete for Process " + processId);
            // TODO: Provide visual feedback (IO still running)
            return;
        }

         // 3. Check if the target core is free
        if (targetCore.isUtilized()) {
            Log.w(TAG, "User Action Failed: Target Core " + targetCoreId + " is busy.");
            // TODO: Provide visual feedback (Core busy)
            return;
        }

        // 4. All checks passed: Remove from IO area, assign back to core
        ioArea.removeProcess();
        processInIO.setCpuPausedForIO(false); // Allow CPU timer to resume
        processInIO.setCurrentState(Process.ProcessState.IO_COMPLETED_WAITING_CORE); // State before core update confirms it's ON_CORE
        targetCore.assignProcess(processInIO);
        // Memory remains allocated
        // TODO: Update UI - Animate process moving
    }

    // --- Getters for UI ---
    public int getScore() {
        return score;
    }

    public int getHealth() {
        return health;
    }

    public Memory getMemory() {
        return memory;
    }

    public ProcessManager getProcessManager() {
        return processManager;
    }

    public List<Core> getCpuCores() {
        return cpuCores;
    }

     public IOArea getIoArea() {
        return ioArea;
     }

     public SharedBuffer getSharedBuffer() {
        return sharedBuffer;
     }

    // Add getter for clients
     public List<Client> getClients() {
         return clients;
     }

    // --- Private Helpers ---
    private synchronized void decreaseHealth(int amount) {
        if (!gameRunning) return;
        this.health -= amount;
        if (this.health < 0) {
            this.health = 0;
        }
        Log.i(TAG, "Health decreased by " + amount + ". Current health: " + this.health);
        // TODO: Update Health UI
    }

    private synchronized void increaseScore(int amount) {
        if (!gameRunning) return;
        this.score += amount;
        Log.i(TAG, "Score increased by " + amount + ". Current score: " + this.score);
        // TODO: Update Score UI
    }

} 