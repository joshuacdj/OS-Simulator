package com.example.cs205game;

import java.util.concurrent.atomic.AtomicInteger;

public class Process {
    private static final AtomicInteger idCounter = new AtomicInteger(0);
    private static final double BUFFER_COOLDOWN = 1.5; // 1.5 seconds cooldown in buffer

    public enum ProcessState {
        IN_QUEUE,
        ON_CORE,
        IN_IO, // Only relevant for IOProcess
        IO_COMPLETED_WAITING_CORE, // Only relevant for IOProcess
        IN_BUFFER,
        CONSUMED
    }

    protected final int id;
    protected int memoryRequirement; // In simulated GB
    protected double patienceCounter; // In seconds
    protected double initialPatience; // Store initial value for drawing/calculations
    protected double cpuTimer; // Total CPU time needed (seconds)
    protected double remainingCpuTime; // CPU time left (seconds)
    protected boolean processCompleted; // Overall completion flag (after buffer)
    protected ProcessState currentState;
    private double bufferCooldown = BUFFER_COOLDOWN;
    private boolean readyForConsumption = false;
    private double bufferCooldownRemainingS;


    // Potential fields for drawing - added later if needed
    // public float x, y;
    // public int visualRepresentationId;

    public Process(int memoryRequirement, double patience, double cpuTime) {
        this.id = idCounter.incrementAndGet();
        this.memoryRequirement = memoryRequirement;
        this.initialPatience = patience;
        this.patienceCounter = patience;
        this.cpuTimer = cpuTime;
        this.remainingCpuTime = cpuTime;
        this.processCompleted = false;
        this.currentState = ProcessState.IN_QUEUE;
    }

    // --- Getters ---
    public int getId() {
        return id;
    }

    public int getMemoryRequirement() {
        return memoryRequirement;
    }

    public double getPatienceCounter() {
        return patienceCounter;
    }

     public double getInitialPatience() {
        return initialPatience;
    }

    public double getCpuTimer() {
        return cpuTimer;
    }

    public double getRemainingCpuTime() {
        return remainingCpuTime;
    }

    public boolean isProcessCompleted() {
        return processCompleted;
    }

    public ProcessState getCurrentState() {
        return currentState;
    }

    public double getBufferCooldownProgress() {
        return 1.0 - (bufferCooldown / BUFFER_COOLDOWN);
    }

    public double getBufferCooldownRemaining() {
        return bufferCooldownRemainingS;
    }

    // --- Setters / Modifiers ---
    public void setCurrentState(ProcessState newState) {
        this.currentState = newState;
    }

    public void setProcessCompleted(boolean completed) {
        this.processCompleted = completed;
    }

    /**
     * Decrements the patience counter by the given delta time.
     * @param deltaTime Time elapsed in seconds.
     * @return true if patience is still > 0, false if it ran out.
     */
    public boolean decrementPatience(double deltaTime) {
        if (this.currentState == ProcessState.IN_QUEUE) {
            this.patienceCounter -= deltaTime;
            if (this.patienceCounter <= 0) {
                this.patienceCounter = 0;
                return false; // Patience ran out
            }
        }
        return true; // Patience remains (or not in queue)
    }

     /**
      * Decrements the remaining CPU time.
      * @param deltaTime Time elapsed in seconds.
      * @return true if CPU time is still > 0, false if CPU processing is finished.
      */
     public boolean decrementCpuTime(double deltaTime) {
         this.remainingCpuTime -= deltaTime;
         if (this.remainingCpuTime <= 0) {
             this.remainingCpuTime = 0;
             return false; // CPU work done
         }
         return true;
     }

    public void updateBufferCooldown(double deltaTime) {
        if (!readyForConsumption && bufferCooldown > 0) {
            bufferCooldown -= deltaTime;
            if (bufferCooldown <= 0) {
                readyForConsumption = true;
            }
        }
    }

    public void resetBufferCooldown() {
        bufferCooldown = BUFFER_COOLDOWN;
        readyForConsumption = false;
    }

    public boolean isReadyForConsumption() {
        return readyForConsumption;
    }

    // Maybe add pause/resume methods for CPUTimer later if needed for IO

    @Override
    public String toString() {
        return "Process{" +
                "id=" + id +
                ", memory=" + memoryRequirement +
                ", patience=" + String.format("%.1f", patienceCounter) +
                ", cpuTime=" + String.format("%.1f", remainingCpuTime) + "/" + cpuTimer +
                ", state=" + currentState +
                '}';
    }
} 