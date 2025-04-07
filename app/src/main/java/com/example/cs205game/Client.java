package com.example.cs205game;

import android.util.Log;

public class Client implements Runnable {
    private static final String TAG = "client";
    private static final long CONSUMPTION_DELAY_MS = 2000; // 2 second delay for consumption
    
    private final int id;
    private final SharedBuffer buffer;
    private final GameManager gameManager;
    private volatile boolean running = true;
    private volatile Process currentlyConsumingProcess = null;

    public Client(int id, SharedBuffer buffer, GameManager gameManager) {
        this.id = id;
        this.buffer = buffer;
        this.gameManager = gameManager;
    }

    @Override
    public void run() {
        Log.i(TAG, "Client " + id + " started.");
        while (running) {
            try {
                // Block until a ready process is available in the shared buffer
                Process process = buffer.take();
                
                // Process might be null during shutdown
                if (process == null) {
                    if (!running) {
                        Log.d(TAG, "Client " + id + " received null process during shutdown");
                        break; // Exit loop on shutdown
                    } else {
                        Log.w(TAG, "Client " + id + " received null process while running");
                        continue; // Try again if still running
                    }
                }
                
                // Check if thread is still running after potentially blocking
                if (!running) {
                    Log.d(TAG, "Client " + id + " stopped after buffer.take() but before processing");
                    // Put the process back if we didn't start consuming it
                    try {
                        buffer.put(process);
                        Log.d(TAG, "Client " + id + " returned Process " + process.getId() + " to buffer");
                    } catch (InterruptedException ie) {
                        // Ignore during shutdown
                        Thread.currentThread().interrupt();
                    }
                    break;
                }
                
                // Mark process as being consumed
                currentlyConsumingProcess = process;
                Log.d(TAG, "Client " + id + " consuming Process " + process.getId());

                try {
                    // Simulate consumption time/work
                    Thread.sleep(CONSUMPTION_DELAY_MS);
                } catch (InterruptedException e) {
                    // Handle interruption during consumption
                    if (running) {
                        Log.w(TAG, "Client " + id + " interrupted during consumption");
                    } else {
                        Log.d(TAG, "Client " + id + " shutdown during consumption");
                    }
                    Thread.currentThread().interrupt(); // Preserve interrupt status
                    break; // Exit run loop
                }

                // Check if still running after sleep
                if (running) {
                    try {
                        // Notify game manager that consumption is complete
                        gameManager.handleClientConsumed(id, process);
                        Log.d(TAG, "Client " + id + " completed consuming Process " + process.getId());
                    } catch (Exception e) {
                        // Catch any exceptions during callback to prevent thread death
                        Log.e(TAG, "Client " + id + " error in handleClientConsumed", e);
                    }
                } else {
                    Log.d(TAG, "Client " + id + " stopped during consumption, not notifying GameManager");
                }
                
                // Clear current process regardless of completion
                currentlyConsumingProcess = null;
            } catch (InterruptedException e) {
                // Expected during shutdown
                Log.d(TAG, "Client " + id + " interrupted during buffer.take()");
                running = false;
                Thread.currentThread().interrupt(); // Preserve interrupt status
            } catch (Exception e) {
                // Catch any unexpected exceptions to prevent thread death
                Log.e(TAG, "Client " + id + " unexpected error: " + e.getMessage(), e);
                // Continue running unless explicitly stopped
            }
        }
        // Clean up before thread exit
        currentlyConsumingProcess = null;
        Log.i(TAG, "Client " + id + " stopped.");
    }

    /** Signals the client thread to stop its loop. */
    public void stop() {
        running = false;
        // Interrupt the thread if it's blocked on buffer.take()
        Thread currentThread = Thread.currentThread();
        if (currentThread != null) {
            currentThread.interrupt();
        }
    }

    public int getId() {
        return id;
    }

    /** Returns the process currently being consumed, or null if idle. */
    public Process getCurrentProcess() {
        return currentlyConsumingProcess;
    }

    /** Returns true if the client is currently busy consuming a process. */
    public boolean isConsuming() {
        return currentlyConsumingProcess != null; // Derive state directly from process object
    }
} 