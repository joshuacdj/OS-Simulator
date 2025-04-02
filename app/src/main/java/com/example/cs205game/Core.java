package com.example.cs205game;

import android.util.Log;

public class Core {
    private static final String TAG = "Core";
    private final int coreId;
    private Process currentProcess = null;
    private boolean isUtilized = false;

    public Core(int id) {
        this.coreId = id;
    }

    public int getCoreId() {
        return coreId;
    }

    public Process getCurrentProcess() {
        return currentProcess;
    }

    public boolean isUtilized() {
        return isUtilized;
    }

    /**
     * Assigns a process to this core if the core is free.
     * @param process The process to assign.
     * @return true if assignment was successful, false if the core was already busy.
     */
    public boolean assignProcess(Process process) {
        if (isUtilized) {
            Log.w(TAG, "Core " + coreId + " is already utilized. Cannot assign process " + process.getId());
            return false;
        }
        this.currentProcess = process;
        this.isUtilized = true;
        process.setCurrentState(Process.ProcessState.ON_CORE);
        Log.i(TAG, "Assigned Process " + process.getId() + " to Core " + coreId);
        return true;
    }

    /**
     * Removes the current process from the core, marking it as free.
     * Should be called when a process completes its CPU task or is moved to IO.
     * @return The process that was removed, or null if the core was already free.
     */
    public Process removeProcess() {
        if (!isUtilized) {
            // Log.d(TAG, "Core " + coreId + " is already free.");
            return null;
        }
        Process removedProcess = this.currentProcess;
        Log.i(TAG, "Removing Process " + removedProcess.getId() + " from Core " + coreId + " (State: " + removedProcess.getCurrentState() + ")");
        this.currentProcess = null;
        this.isUtilized = false;
        return removedProcess;
    }

    /**
     * Updates the state of the process currently on the core.
     * Decrements the CPU timer and handles completion or IO pauses.
     *
     * @param deltaTime Time elapsed since the last update in seconds.
     * @param onCpuCompleted Callback for when a process finishes its CPU work (including after returning from IO).
     * @param onIoRequired Callback for when an IOProcess reaches its IO trigger point.
     */
    public void update(double deltaTime, java.util.function.Consumer<Process> onCpuCompleted, java.util.function.Consumer<IOProcess> onIoRequired) {
        if (!isUtilized || currentProcess == null) {
            return;
        }

        // Handle IO Processes specifically (Refactored for Java 11)
        if (currentProcess instanceof IOProcess) {
            IOProcess ioProcess = (IOProcess) currentProcess; // Explicit cast

            // If returning from IO, just continue decrementing
            if (ioProcess.getCurrentState() == Process.ProcessState.IO_COMPLETED_WAITING_CORE) {
                 ioProcess.setCurrentState(Process.ProcessState.ON_CORE); // Officially back on core
                 Log.d(TAG, "IOProcess " + ioProcess.getId() + " resumed on Core " + coreId);
            }

            // Check if CPU is paused for this IO process
            if (ioProcess.isCpuPausedForIO()) {
                return; // CPU is paused, do nothing
            }

            // Decrement CPU time
            if (!ioProcess.decrementCpuTime(deltaTime)) {
                // CPU work finished (likely after returning from IO)
                 Log.i(TAG, "IOProcess " + ioProcess.getId() + " finished CPU on Core " + coreId);
                Process completedProcess = removeProcess();
                onCpuCompleted.accept(completedProcess);
            } else {
                // Check if it reached the halfway point for IO interrupt
                 double halfCpuTime = ioProcess.getCpuTimer() / 2.0;
                 double epsilon = 0.001;
                 if (!ioProcess.isIoCompleted() && (Math.abs(ioProcess.getRemainingCpuTime() - halfCpuTime) < epsilon || ioProcess.getRemainingCpuTime() < halfCpuTime)) {
                     if (!ioProcess.isCpuPausedForIO()) { // Check to prevent triggering multiple times
                        ioProcess.setCpuPausedForIO(true);
                        Log.i(TAG, "IOProcess " + ioProcess.getId() + " reached IO trigger point on Core " + coreId + ". Pausing CPU.");
                        onIoRequired.accept(ioProcess); // Notify GameManager/View that IO is needed
                     }
                 }
            }
        } else { // Handle Normal Processes
            if (!currentProcess.decrementCpuTime(deltaTime)) {
                // CPU work finished
                 Log.i(TAG, "Process " + currentProcess.getId() + " finished CPU on Core " + coreId);
                Process completedProcess = removeProcess();
                onCpuCompleted.accept(completedProcess);
            }
        }
    }
} 