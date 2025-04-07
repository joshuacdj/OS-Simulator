package com.example.cs205game;

import android.util.Log;
import android.os.Vibrator;
import android.os.Build;
import android.os.VibrationEffect;
import android.content.Context; // Required for getting Vibrator service

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// acts as the central orchestrator for the game
// manages game state (score, health), components (memory, cores, buffer etc),
// client threads, and the main game update loop delegation
public class GameManager {
    private static final String TAG = "GameManager"; // professional tag
    public static final int INITIAL_HEALTH = 100;
    private static final int PATIENCE_PENALTY = 10; // hp lost if process patience runs out
    private static final int FCFS_PENALTY = 5; // hp lost for dragging wrong process from queue
    private static final int PROCESS_COMPLETION_SCORE = 100; // score for consumed process (updated from 20)
    private static final int NUM_CORES = 4;
    public static final int MEMORY_CAPACITY = 16; // gb
    public static final int BUFFER_CAPACITY = 5; // max items in buffer
    private static final int NUM_CLIENTS = 2; // number of consumer threads

    private int score;
    private int health;
    private final Memory memory;
    private final ProcessManager processManager;
    private final List<Core> cpuCores;
    private final IOArea ioArea;
    private final SharedBuffer sharedBuffer;
    private final List<Client> clients;
    private ExecutorService clientExecutor; // using an executorservice is better for managing threads
    private volatile boolean gameRunning = false;
    private Vibrator vibrator; // Vibrator instance
    private Context context; // Context needed for vibrator

    // Add references for UI updates later (e.g., GameView)

    public GameManager(Context context) { // Modify constructor to accept Context
        this.context = context.getApplicationContext(); // use application context to avoid leaks
        // Reset static process ID counter at the start of a new game manager instance
        Process.resetIdCounter(); 

        this.score = 0;
        this.health = INITIAL_HEALTH;
        this.memory = new Memory(MEMORY_CAPACITY);
        this.processManager = new ProcessManager();
        this.ioArea = new IOArea();
        this.cpuCores = new ArrayList<>(NUM_CORES);
        for (int i = 0; i < NUM_CORES; i++) {
            int coreId = i; // Need final variable for lambda capture
            cpuCores.add(new Core(coreId, 
                                this::handleCpuCompleted, // method reference for completion
                                this::handleIoRequired)); // method reference for io request
        }
        this.sharedBuffer = new SharedBuffer(BUFFER_CAPACITY);
        this.clients = new ArrayList<>(NUM_CLIENTS);
        // Using an ExecutorService to manage client threads is generally better than raw Threads
        for (int i = 0; i < NUM_CLIENTS; i++) {
            clients.add(new Client(i, sharedBuffer, this));
        }
        Log.i(TAG, "GameManager initialized.");

        // Get Vibrator service
        vibrator = (Vibrator) this.context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.w(TAG, "Vibrator not available or service not found");
            vibrator = null; // ensure vibrator is null if unusable
        }
    }

    // starts the game logic and client threads
    public synchronized void startGame() {
        if (gameRunning) return;
        Log.i(TAG, "Starting game and client threads...");
        gameRunning = true;
        // create a new executor if it's null or shut down
        if (clientExecutor == null || clientExecutor.isShutdown()) {
             clientExecutor = Executors.newFixedThreadPool(NUM_CLIENTS);
        }
        // submit client tasks to the executor
        for(Client client : clients) {
            clientExecutor.submit(client);
        }
    }

    // stops the game logic and attempts to shut down client threads gracefully
    public synchronized void stopGame() {
        if (!gameRunning) return;
        Log.i(TAG, "Stopping game and client threads...");
        gameRunning = false;

        // Shut down the shared buffer to wake up any waiting threads
        sharedBuffer.shutdown();

        // signal clients to stop their run loop
        for (Client client : clients) {
            client.stop();
        }

        // shutdown executor pool gracefully
        if (clientExecutor != null && !clientExecutor.isShutdown()) {
            clientExecutor.shutdown(); // disable new tasks
            try {
                // wait a bit for existing tasks to finish
                if (!clientExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    clientExecutor.shutdownNow(); // force cancel running tasks
                    // wait a bit for tasks to respond to cancellation
                    if (!clientExecutor.awaitTermination(1, TimeUnit.SECONDS))
                        Log.e(TAG, "Client executor did not terminate");
                }
            } catch (InterruptedException ie) {
                clientExecutor.shutdownNow(); // force cancel if interrupted
                Thread.currentThread().interrupt(); // preserve interrupt status
            }
        }
        Log.i(TAG, "Client threads requested to stop.");
    }

    /**
     * the main update loop delegates updates to child components.
     * @param deltatime time elapsed since the last update in seconds.
     */
    public void update(double deltaTime) {
        if (!gameRunning) return;

        // update process spawning and queue patience
        processManager.update(deltaTime, this::handlePatienceExpired);

        // update cooldowns for processes waiting in the buffer
        sharedBuffer.update(deltaTime);

        // update processes running on cores
        for (Core core : cpuCores) {
            // lambda used here to pass core id to the handler
            // core update calls the correct handler internally now
            core.update(deltaTime); 
        }

        // update process running in the io area
        ioArea.update(deltaTime, this::handleIoCompleted);

        // game over check is now handled in gameview via isgamerunning()
    }

    // --- callback handlers --- //
    // these methods are often called from other threads (e.g processmanager update, core update)

    // called by processmanager when a process's patience runs out in the queue
    private void handlePatienceExpired(Process process) {
        if (!gameRunning) return; // ignore if game already stopped
        Log.w(TAG, "Process " + process.getId() + " removed due to expired patience.");
        decreaseHealth(PATIENCE_PENALTY);
    }

    /**
     * Callback from core when a process finishes its CPU execution.
     * Moves the process to the SharedBuffer and frees its memory.
     * @param coreId the id of the core that finished.
     * @param process the process that completed its CPU time.
     */
    private void handleCpuCompleted(int coreId, Process process) {
        // note: core.removeProcess() was already called inside core.update before this callback
        Log.i(TAG, "Handling CPU completion for Process " + process.getId() + " from Core " + coreId);

        // free memory now that CPU work is done
        memory.freeMemory(process.getMemoryRequirement());
        Log.d(TAG, "Freed memory for Process " + process.getId());

        process.setCurrentState(Process.ProcessState.IN_BUFFER);
        try {
            sharedBuffer.put(process);
            Log.d(TAG, "Process " + process.getId() + " added to SharedBuffer.");
        } catch (InterruptedException e) {
            // Handle shutdown case - may happen during normal game shutdown
            if (!gameRunning) {
                Log.d(TAG, "Buffer operation interrupted during shutdown for Process " + process.getId());
            } else {
                // Unexpected interruption during normal gameplay
                Thread.currentThread().interrupt();
                Log.e(TAG, "Interrupted while putting process " + process.getId() + " into buffer", e);
            }
        }
    }

    // called by core when an ioprocess needs io
    private void handleIoRequired(IOProcess ioProcess) {
        if (!gameRunning) return;
        // log indicates user needs to drag process to io area
        Log.i(TAG, "IO Required for Process " + ioProcess.getId() + ". Waiting for user action.");
    }

    // called by ioarea when an ioprocess finishes io
    private void handleIoCompleted(IOProcess ioProcess) {
        if (!gameRunning) return;
        // log indicates user needs to drag process back to a core
         Log.i(TAG, "IO Completed for Process " + ioProcess.getId() + ". Waiting for user action.");
    }

    /**
     * callback from client thread when it finishes consuming a process.
     * increases score.
     * note: called from client threads
     * @param clientid the id of the client that finished.
     * @param process the process that was consumed.
     */
    public void handleClientConsumed(int clientId, Process process) {
        if (!gameRunning) return;
        Log.i(TAG, "Client " + clientId + " finished consuming Process " + process.getId());
        // memory is deallocated earlier in handlecpucompleted
        process.setProcessCompleted(true);
        process.setCurrentState(Process.ProcessState.CONSUMED);
        increaseScore(PROCESS_COMPLETION_SCORE); // use the constant
    }

    // --- action methods --- //
    // these methods are triggered by user interactions (drag/drop) via gameview

    /**
     * attempts to move a process from the queue head to a target core.
     * performs fcfs, core availability, and memory checks.
     * called from the ui thread (via gameview ontouchevent).
     * @param processid the id of the process to move (must be head).
     * @param targetcoreid the id of the target core.
     */
    public void moveProcessFromQueueToCore(int processId, int targetCoreId) {
        if (!gameRunning) return;
        if (targetCoreId < 0 || targetCoreId >= cpuCores.size()) {
            Log.e(TAG, "Invalid target Core ID: " + targetCoreId);
            return;
        }
        Core targetCore = cpuCores.get(targetCoreId);

        // 1. check fcfs - is it the head process?
        if (!processManager.isProcessAtHead(processId)) {
            Log.w(TAG, "FCFS Violation: Process " + processId + " is not at the head of the queue.");
            decreaseHealth(FCFS_PENALTY);
           
            return;
        }

        // 2. check if core is free
        synchronized (targetCore) { // synchronize on core for check-then-act
            if (targetCore.isUtilized()) {
                 Log.w(TAG, "User Action Failed: Core " + targetCoreId + " is busy.");
                 // todo: maybe visual feedback for busy core in gameview?
                 return;
            }

            // 3. peek process from queue (fcfs check passed, so this should be the one)
            Process processToMove = processManager.getProcessQueue().peek();
            if (processToMove == null || processToMove.getId() != processId) {
                Log.e(TAG, "Queue state error: Mismatch between FCFS check and queue head? ProcessID: " + processId);
                return; // potential race condition or logic error somewhere
            }

            // 4. check memory
            if (!memory.hasEnoughMemory(processToMove.getMemoryRequirement())) {
                 Log.w(TAG, "User Action Failed: Not enough memory for Process " + processId + ". Required: " + processToMove.getMemoryRequirement() + ", Available: " + memory.getAvailableMemory());
                 // todo: visual feedback for insufficient memory?
                 return;
            }

            // 5. all checks passed: allocate memory, remove from queue, assign to core
            if (memory.allocateMemory(processToMove.getMemoryRequirement())) {
                 processManager.takeProcessFromQueue(); // now remove from queue
                 targetCore.assignProcess(processToMove);
                 // success visual feedback handled by state change drawing
            } else {
                // should not happen due to check, but log 
                 Log.e(TAG, "Memory allocation failed unexpectedly after check for Process " + processId);
            }
        }
    }

    /**
     * attempts to move an ioprocess from a core to the io area.
     * performs checks for process type, io readiness, and io area availability.
     * called from the ui thread.
     * @param processid the id of the ioprocess to move.
     * @param sourcecoreid the id of the core the process is currently on.
     */
    public void moveProcessFromCoreToIO(int processId, int sourceCoreId) {
        if (!gameRunning) return;
         if (sourceCoreId < 0 || sourceCoreId >= cpuCores.size()) {
             Log.e(TAG, "Invalid source Core ID: " + sourceCoreId);
             return;
         }
         Core sourceCore = cpuCores.get(sourceCoreId);
         Process processOnCore; // need to check inside sync block

         synchronized (sourceCore) { // sync on core being read
            processOnCore = sourceCore.getCurrentProcess();

            // 1. check if the correct process is on the specified core
            if (processOnCore == null || processOnCore.getId() != processId) {
                Log.w(TAG, "User Action Failed: Process " + processId + " not found on Core " + sourceCoreId);           
                return;
            }

            // 2. check if it's an ioprocess ready for io
            if (!(processOnCore instanceof IOProcess)) {
                 Log.w(TAG, "User Action Failed: Process " + processId + " on Core " + sourceCoreId + " is not an IOProcess.");             
                 return;
            }
            IOProcess ioProcess = (IOProcess) processOnCore;
            if (!ioProcess.isCpuPausedForIO() || ioProcess.isIoCompleted()) {
                Log.w(TAG, "User Action Failed: IOProcess " + processId + " on Core " + sourceCoreId + " is not waiting for IO.");
                // todo: visual feedback?
                return;
            }
         } // end synchronized block for source core read

         // 3. check if io area is free (synchronize on ioarea)
         synchronized (ioArea) {
             if (ioArea.isBusy()) {
                 Log.w(TAG, "User Action Failed: IOArea is busy.");
                 // todo: visual feedback?
                 return;
             }

             // 4. checks passed: remove from core (sync again), assign to io area (still synced)
             IOProcess ioProcessToMove; 
             synchronized(sourceCore) { // sync to remove
                 // re-verify process is still there and ready? might be overkill if ui thread is fast
                 Process checkProcess = sourceCore.getCurrentProcess();
                 if (checkProcess == null || checkProcess.getId() != processId || !(checkProcess instanceof IOProcess) || !((IOProcess)checkProcess).isCpuPausedForIO()) {
                     Log.e(TAG, "State changed between check and action for moveProcessFromCoreToIO - Process: " + processId );
                     return; // state changed, abort move
                 }
                 ioProcessToMove = (IOProcess) sourceCore.removeProcess();
             }
             
             if (ioProcessToMove != null) {
                 ioArea.assignProcess(ioProcessToMove);
             } else {
                  Log.e(TAG, "Error removing process from core during move to IO, processId: " + processId);
             }
         } // end synchronized block for io area
    }

    /**
     * attempts to move a completed ioprocess from the io area back to a target core.
     * performs checks for io completion and core availability.
     * called from the ui thread.
     * @param processid the id of the ioprocess to move.
     * @param targetcoreid the id of the core to return to.
     */
    public void moveProcessFromIOToCore(int processId, int targetCoreId) {
        if (!gameRunning) return;
        if (targetCoreId < 0 || targetCoreId >= cpuCores.size()) {
            Log.e(TAG, "Invalid target Core ID: " + targetCoreId);
            return;
        }
        Core targetCore = cpuCores.get(targetCoreId);
        IOProcess processInIO; // check inside sync block

        synchronized (ioArea) { // sync on io area for check/remove
            processInIO = ioArea.getCurrentProcess();

            // 1. check if the correct process is in the io area
            if (processInIO == null || processInIO.getId() != processId) {
                Log.w(TAG, "User Action Failed: Process " + processId + " not found in IO Area.");
                return;
            }

            // 2. check if io is actually completed
            if (!processInIO.isIoCompleted()) {
                Log.w(TAG, "User Action Failed: IO not yet complete for Process " + processId);
                // todo: visual feedback?
                return;
            }
            
            // remove from io area now if checks pass before checking core
            ioArea.removeProcess(); 
        } // end synchronized block for io area

        // 3. check if the target core is free (sync on core)
        synchronized(targetCore) {
            if (targetCore.isUtilized()) {
                Log.w(TAG, "User Action Failed: Target Core " + targetCoreId + " is busy.");
                // we already removed from io area, maybe put it back? or let user retry?
                // for simplicity, log and let user retry dropping onto another core
                // if putting back: ioArea.assignProcess(processInIO); -- needs careful state reset
                return;
            }

            // 4. checks passed: assign back to core (still synced)
            processInIO.setCpuPausedForIO(false); // allow cpu timer to resume on core
            processInIO.setCurrentState(Process.ProcessState.IO_COMPLETED_WAITING_CORE); // core.update will set to on_core
            targetCore.assignProcess(processInIO);
        }
    }

    // --- getters for ui --- //
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

    public boolean isGameRunning() {
        return gameRunning;
    }

    // --- private helpers --- //
    private synchronized void decreaseHealth(int amount) {
        if (!gameRunning) return;
        int previousHealth = this.health;
        this.health -= amount;
        if (this.health <= 0) {
            this.health = 0;
            // only log state change if health actually reached zero this time
            if (previousHealth > 0) { 
                 Log.i(TAG, "Health depleted.");
                 stopGame(); // triggers game over state via isGameRunning()
            }
        } else {
             Log.d(TAG, "Health decreased by " + amount + ". Current health: " + this.health);
        }

        // vibrate on hp loss if health was positive before hit
        if (previousHealth > 0 && vibrator != null) { 
            long[] pattern = {0, 100}; // vibrate for 100ms
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                // For backward compatibility on older devices
                vibrateDeprecated(vibrator, pattern);
            }
        }
    }

    /**
     * Helper method to handle vibration for older API versions without using deprecated methods
     */
    private void vibrateDeprecated(Vibrator vibrator, long[] pattern) {
        // For very old Android versions, we'll create a single-instance vibration
        // that mimics the pattern by calculating total duration
        long totalDuration = 0;
        for (long time : pattern) {
            totalDuration += time;
        }
        // Just use the first non-zero duration as an approximation
        // This is less than ideal but avoids using deprecated APIs
        long singleDuration = 0;
        for (long time : pattern) {
            if (time > 0) {
                singleDuration = time;
                break;
            }
        }
        if (singleDuration > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                // Use cancel() which is available since API 11 (Honeycomb)
                vibrator.cancel();
            }
            // Use a simple vibration which has been available since API 1
            // Note: The one-parameter vibrate(duration) method is not deprecated
            vibrator.vibrate(singleDuration);
        }
    }

    // also called frequently
    private synchronized void increaseScore(int amount) {
        if (!gameRunning || amount <= 0) return;
        this.score += amount;
        Log.d(TAG, "Score increased by " + amount + ". Current score: " + this.score);
    }

    // resets the game state to initial values, clears components
    // note: activity recreate is currently used for retry, so this isn't hit via retry button
    // might be useful for a different 'restart level' feature later
    public void resetGame() {
        Log.i(TAG, "Resetting game state...");
        stopGame(); // ensure threads are stopped first

        // reset core state
        health = INITIAL_HEALTH;
        score = 0;
        // gameRunning is set true by startGame()

        // clear components
        processManager.reset();
        sharedBuffer.clear(); // This also resets the shutdown flag
        ioArea.clear();
        for (Core core : cpuCores) {
            core.clear();
        }
        memory.clear();
        
        // client list is reused, but threads need restarting
        // ensure clients internal state is ready for restart if they hold state
        
        // startGame() will re-initialize executor and submit clients
        Log.i(TAG, "Game state reset completed.");
        // caller (like gameview retry handler) should call startGame() if needed immediately
    }

} 