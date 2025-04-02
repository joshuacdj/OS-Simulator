package com.example.cs205game;

import android.util.Log;

public class Client implements Runnable {
    private static final String TAG = "Client";
    private static final long CONSUMPTION_DELAY_MS = 2000; // 2 second delay for consumption
    
    private final int id;
    private final SharedBuffer buffer;
    private final GameManager gameManager;
    private volatile boolean running = true;
    private volatile Process currentlyConsumingProcess = null;
    private volatile boolean isConsuming = false;

    public Client(int id, SharedBuffer buffer, GameManager gameManager) {
        this.id = id;
        this.buffer = buffer;
        this.gameManager = gameManager;
    }

    @Override
    public void run() {
        while (running) {
            try {
                // Try to take a process from the buffer
                Process process = buffer.take();
                if (process != null && running) {
                    currentlyConsumingProcess = process;
                    isConsuming = true;
                    
                    // Simulate consumption time
                    Thread.sleep(CONSUMPTION_DELAY_MS);
                    
                    // Process consumed
                    if (running) {
                        // Notify GameManager about consumption completion
                        // This might involve score updates, removing from UI, etc.
                        gameManager.handleClientConsumed(id, process);
                    }
                    currentlyConsumingProcess = null;
                    isConsuming = false;
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "Client " + id + " interrupted while consuming.");
                Thread.currentThread().interrupt();
                break;
            }
        }
        Log.i(TAG, "Client " + id + " stopped.");
    }

    public void stop() {
        running = false;
    }

    public int getId() {
        return id;
    }

    public Process getCurrentProcess() {
        return currentlyConsumingProcess;
    }

    public boolean isConsuming() {
        return isConsuming;
    }
} 