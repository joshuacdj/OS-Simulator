package com.example.cs205game;

import android.util.Log;

import java.util.Random;

public class Client implements Runnable {
    private static final String TAG = "Client";
    private static final long MIN_CONSUME_TIME_MS = 500; // 0.5 seconds
    private static final long MAX_CONSUME_TIME_MS = 1500; // 1.5 seconds

    private final int clientId;
    private final SharedBuffer buffer;
    private final GameManager gameManager; // To notify when consumption is done
    private final Random random;
    private volatile boolean running = true; // Use volatile for thread visibility

    public Client(int id, SharedBuffer buffer, GameManager gameManager) {
        this.clientId = id;
        this.buffer = buffer;
        this.gameManager = gameManager;
        this.random = new Random();
    }

    @Override
    public void run() {
        Log.i(TAG, "Client " + clientId + " started.");
        try {
            while (running) {
                // 1. Take a process from the buffer (blocks if empty)
                Process process = buffer.take();
                Log.d(TAG, "Client " + clientId + " took Process " + process.getId());

                // 2. Simulate consumption time
                long consumeTime = MIN_CONSUME_TIME_MS + random.nextInt((int)(MAX_CONSUME_TIME_MS - MIN_CONSUME_TIME_MS + 1));
                Thread.sleep(consumeTime);

                // 3. Mark process as consumed and notify GameManager
                process.setProcessCompleted(true);
                process.setCurrentState(Process.ProcessState.CONSUMED);
                Log.i(TAG, "Client " + clientId + " consumed Process " + process.getId() + " in " + consumeTime + "ms.");
                gameManager.handleClientConsumed(process); // Notify GameManager
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Client " + clientId + " interrupted.");
            Thread.currentThread().interrupt(); // Preserve interrupt status
        } finally {
            Log.i(TAG, "Client " + clientId + " stopped.");
        }
    }

    public void stop() {
        running = false;
        // Note: Interrupting the thread might be necessary if it's blocked in buffer.take()
        // This requires the thread reference, managed externally (e.g., in GameManager)
    }
} 