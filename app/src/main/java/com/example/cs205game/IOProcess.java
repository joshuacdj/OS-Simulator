package com.example.cs205game;

public class IOProcess extends Process {

    private double ioTimer; // Total IO time needed (seconds)
    private double remainingIoTime; // IO time left (seconds)
    private boolean ioCompleted; // Flag specifically for IO completion
    private boolean cpuPausedForIO; // Track if CPU is paused

    public IOProcess(int memoryRequirement, double patience, double cpuTime, double ioTime) {
        super(memoryRequirement, patience, cpuTime);
        this.ioTimer = ioTime;
        this.remainingIoTime = ioTime;
        this.ioCompleted = false;
        this.cpuPausedForIO = false;
    }

    // --- Getters ---
    public double getIoTimer() {
        return ioTimer;
    }

    public double getRemainingIoTime() {
        return remainingIoTime;
    }

    public boolean isIoCompleted() {
        return ioCompleted;
    }

    public boolean isCpuPausedForIO() {
        return cpuPausedForIO;
    }

    // --- Setters / Modifiers ---
    public void setIoCompleted(boolean completed) {
        this.ioCompleted = completed;
    }

    public void setCpuPausedForIO(boolean paused) {
        this.cpuPausedForIO = paused;
    }

    /**
     * Decrements the remaining IO time.
     * @param deltaTime Time elapsed in seconds.
     * @return true if IO time is still > 0, false if IO processing is finished.
     */
    public boolean decrementIoTime(double deltaTime) {
        if (this.currentState == ProcessState.IN_IO) {
             this.remainingIoTime -= deltaTime;
             if (this.remainingIoTime <= 0) {
                this.remainingIoTime = 0;
                this.ioCompleted = true;
                return false; // IO work done
             }
        }
         return true; // IO still running or not in IO state
     }

    /**
     * checks if the process has reached the point where its cpu execution
     * should be interrupted for i/o.
     * assumes i/o interrupt happens at or below the halfway point of cpu time.
     * @return true if i/o interrupt is due, false otherwise.
     */
    public boolean needsIOInterrupt() {
        // check if cpu is already paused or io is already done
        if (cpuPausedForIO || ioCompleted) {
            return false;
        }
        // check if remaining cpu time is at or past the halfway mark
        return remainingCpuTime <= (cpuTimer / 2.0);
    }

    @Override
    public String toString() {
        return "IOProcess{" +
                "id=" + id +
                ", memory=" + memoryRequirement +
                ", patience=" + String.format("%.1f", patienceCounter) +
                ", cpuTime=" + String.format("%.1f", remainingCpuTime) + "/" + cpuTimer +
                ", ioTime=" + String.format("%.1f", remainingIoTime) + "/" + ioTimer +
                ", state=" + currentState +
                ", cpuPaused=" + cpuPausedForIO +
                '}';
    }
} 