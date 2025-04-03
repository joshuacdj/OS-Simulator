package com.example.cs205game;

import android.util.Log;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * represents a single cpu core capable of processing one process at a time.
 * it manages the process's cpu execution timer and interacts with the gamemanager
 * for process completion and i/o handling.
 */
public class Core {
    private static final String TAG = "core"; // lowercase tag
    private final int coreId;
    private Process currentProcess = null;
    private boolean isUtilized = false;
    // callback when cpu work is fully done (passes coreid, process)
    private final BiConsumer<Integer, Process> onCpuCompleteCallback; 
    // callback when an io process needs to be moved to io (passes the ioprocess)
    private final Consumer<IOProcess> onIoRequiredCallback;   

    /**
     * constructs a new core.
     *
     * @param id the unique identifier for this core.
     * @param onCpuCompleteCallback callback function triggered when a process finishes cpu execution on this core.
     * @param onIoRequiredCallback callback function triggered when an io process on this core needs i/o.
     */
    public Core(int id, BiConsumer<Integer, Process> onCpuCompleteCallback, Consumer<IOProcess> onIoRequiredCallback) {
        this.coreId = id;
        this.onCpuCompleteCallback = onCpuCompleteCallback;
        this.onIoRequiredCallback = onIoRequiredCallback;
    }

    /** @return the unique id of this core. */
    public int getId() {
        return coreId;
    }

    /** @return the process currently assigned to this core, or null if free. */
    public synchronized Process getCurrentProcess() {
        return currentProcess;
    }

    /** @return true if the core is currently processing a task, false otherwise. */
    public synchronized boolean isUtilized() {
        return isUtilized;
    }

    /**
     * assigns a process to this core if the core is free.
     * sets the process state to on_core.
     *
     * @param process the process to assign.
     * @return true if assignment was successful, false if the core was already busy.
     */
    public synchronized boolean assignProcess(Process process) {
        if (isUtilized) {
            Log.w(TAG, "core " + coreId + " is already utilized. cannot assign process " + process.getId());
            return false;
        }
        this.currentProcess = process;
        this.isUtilized = true;
        process.setCurrentState(Process.ProcessState.ON_CORE);
        Log.i(TAG, "assigned process " + process.getId() + " to core " + coreId);
        return true;
    }

    /**
     * removes the current process from the core, marking it as free.
     * should be called when a process completes its cpu task or is moved to io.
     *
     * @return the process that was removed, or null if the core was already free.
     */
    public synchronized Process removeProcess() {
        if (!isUtilized) {
            return null;
        }
        Process removedProcess = this.currentProcess;
        Log.i(TAG, "removing process " + removedProcess.getId() + " from core " + coreId + " (state: " + removedProcess.getCurrentState() + ")");
        this.currentProcess = null;
        this.isUtilized = false;
        return removedProcess;
    }

    /**
     * updates the state of the process currently on the core based on elapsed time.
     * decrements the cpu timer, checks for completion, and handles i/o interruptions for ioprocesses.
     *
     * @param deltaTime time elapsed since the last update in seconds.
     * @param onCpuCompleted callback provided by caller (gamemanager) - this parameter is ignored, use member callback.
     * @param onIoRequired callback provided by caller (gamemanager) - this parameter is ignored, use member callback.
     */
    @Deprecated // Mark as deprecated to encourage using the internal callbacks
    public synchronized void update(double deltaTime, java.util.function.Consumer<Process> onCpuCompleted, java.util.function.Consumer<IOProcess> onIoRequired) {
        // Redirect to the internal method that uses the member callbacks
        update(deltaTime);
    }
    
    /**
     * updates the state of the process currently on the core based on elapsed time.
     * uses the internal callback members initialized in the constructor.
     *
     * @param deltaTime time elapsed since the last update in seconds.
     */
    public synchronized void update(double deltaTime) {
        if (!isUtilized || currentProcess == null) {
            return; // nothing to update if core is free
        }

        // handle i/o processes specifically
        if (currentProcess instanceof IOProcess) {
            IOProcess ioProcess = (IOProcess) currentProcess; // safe cast

            // if returning from i/o, mark as back on core and continue processing
            if (ioProcess.getCurrentState() == Process.ProcessState.IO_COMPLETED_WAITING_CORE) {
                 ioProcess.setCurrentState(Process.ProcessState.ON_CORE); // officially back on core
                 Log.d(TAG, "ioprocess " + ioProcess.getId() + " resumed on core " + coreId);
            }

            // if cpu is paused for this i/o process, do not decrement cpu time
            if (ioProcess.isCpuPausedForIO()) {
                return; 
            }

            // decrement cpu time for the i/o process
            if (!ioProcess.decrementCpuTime(deltaTime)) {
                // i/o process finished remaining cpu work (after returning from i/o)
                Log.i(TAG, "ioprocess " + ioProcess.getId() + " finished cpu on core " + coreId);
                Process completedProcess = removeProcess();
                if (completedProcess != null && onCpuCompleteCallback != null) { // Use member callback
                     onCpuCompleteCallback.accept(coreId, completedProcess);
                }
            } else {
                // cpu work still remaining, check if i/o interrupt needed (only if i/o not already done)
                 // We need a method in IOProcess to check if IO interrupt is due
                 if (!ioProcess.isIoCompleted() && ioProcess.needsIOInterrupt()) { 
                     ioProcess.setCpuPausedForIO(true); // pause cpu execution
                     Log.i(TAG, "ioprocess " + ioProcess.getId() + " reached io trigger point on core " + coreId + ". pausing cpu.");
                     if (onIoRequiredCallback != null) { // Use member callback
                        onIoRequiredCallback.accept(ioProcess);
                     }
                 }
            }
        } else { // handle normal processes
            // decrement cpu time for the normal process
            if (!currentProcess.decrementCpuTime(deltaTime)) {
                // normal process finished cpu work
                 Log.i(TAG, "process " + currentProcess.getId() + " finished cpu on core " + coreId);
                Process completedProcess = removeProcess();
                 if (completedProcess != null && onCpuCompleteCallback != null) { // Use member callback
                    onCpuCompleteCallback.accept(coreId, completedProcess);
                 }
            }
        }
    }

    /** clears the core, removing any current process and marking it as free. */
    public synchronized void clear() {
        currentProcess = null;
        isUtilized = false;
        Log.d("core_" + coreId, "cleared."); // adjusted tag for clarity
    }

    /** provides a string representation of the core's current state. */
    public synchronized String getState() {
        return "core " + coreId + ": " + (isUtilized ? "utilized by p" + (currentProcess != null ? currentProcess.getId() : "?") : "free");
    }
} 