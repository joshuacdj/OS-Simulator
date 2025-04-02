package com.example.cs205game;

import android.util.Log;

public class IOArea {
    private static final String TAG = "IOArea";
    private IOProcess currentProcess = null;
    private boolean isBusy = false;
    private double remainingIoTimeS = 0;

    public IOProcess getCurrentProcess() {
        return currentProcess;
    }

    public synchronized boolean isBusy() {
        return currentProcess != null;
    }

    /**
     * Assigns an IOProcess to this area if it's free.
     * @param process The IOProcess to assign.
     * @return true if assignment was successful, false if the area was already busy.
     */
    public boolean assignProcess(IOProcess process) {
        if (isBusy) {
            Log.w(TAG, "IOArea is already busy with Process " + (currentProcess != null ? currentProcess.getId() : "?") + ". Cannot assign process " + process.getId());
            return false;
        }
        this.currentProcess = process;
        this.isBusy = true;
        process.setCurrentState(Process.ProcessState.IN_IO);
        Log.i(TAG, "Assigned IOProcess " + process.getId() + " to IOArea.");
        return true;
    }

    /**
     * Removes the current IOProcess from the area, marking it as free.
     * Should be called when the IO completes and the process needs to be moved back to a core.
     * @return The IOProcess that was removed, or null if the area was already free.
     */
    public IOProcess removeProcess() {
        if (!isBusy) {
            return null;
        }
        IOProcess removedProcess = this.currentProcess;
        Log.i(TAG, "Removing IOProcess " + removedProcess.getId() + " from IOArea.");
        this.currentProcess = null;
        this.isBusy = false;
        // State change (to IO_COMPLETED_WAITING_CORE) should happen in GameManager/Core when moved back
        return removedProcess;
    }

    /**
     * Updates the IO timer for the process currently in the IO area.
     * @param deltaTime Time elapsed since the last update in seconds.
     * @param onIoCompleted Callback for when the IO process finishes its IO work.
     */
    public void update(double deltaTime, java.util.function.Consumer<IOProcess> onIoCompleted) {
        if (!isBusy || currentProcess == null) {
            return;
        }

        if (!currentProcess.decrementIoTime(deltaTime)) {
            // IO work finished for this process
            Log.i(TAG, "IOProcess " + currentProcess.getId() + " finished IO in IOArea.");
            // Don't remove it here, wait for user to drag it back.
            // The process's internal state (isIoCompleted) is already set.
            onIoCompleted.accept(currentProcess); // Notify GameManager/View that it's ready to be moved back
        }
    }

    /** Clears the IO area, removing any current process. */
    public synchronized void clear() {
        currentProcess = null;
        remainingIoTimeS = 0;
        Log.d(TAG, "IOArea cleared.");
    }
} 