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
        while (running) {
            try {
                // block until a ready process is available in the shared buffer
                Process process = buffer.take();
                // check if thread is still running after potentially blocking
                if (process != null && running) {
                    currentlyConsumingProcess = process; // track the process being consumed

                    // simulate consumption time/work
                    Thread.sleep(CONSUMPTION_DELAY_MS);

                    // check if still running after sleep
                    if (running) {
                        // notify game manager that consumption is complete
                        gameManager.handleClientConsumed(id, process);
                    }
                    currentlyConsumingProcess = null; // clear current process
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "client " + id + " interrupted.");
                running = false; // stop running if interrupted
                Thread.currentThread().interrupt(); // preserve interrupt status
            }
        }
        Log.i(TAG, "client " + id + " stopped.");
    }

    /** signals the client thread to stop its loop. */
    public void stop() {
        running = false;
    }

    public int getId() {
        return id;
    }

    /** returns the process currently being consumed, or null if idle. */
    public Process getCurrentProcess() {
        return currentlyConsumingProcess;
    }

    /** returns true if the client is currently busy consuming a process. */
    public boolean isConsuming() {
        return currentlyConsumingProcess != null; // derive state directly from process object
    }
} 