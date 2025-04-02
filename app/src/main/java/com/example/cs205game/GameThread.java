package com.example.cs205game;

import android.graphics.Canvas;
import android.util.Log;
import android.view.SurfaceHolder;

public class GameThread extends Thread {
    private static final String TAG = "GameThread";
    private static final int TARGET_FPS = 60; // Target updates per second
    private static final long OPTIMAL_TIME = 1000 / TARGET_FPS; // Target time per frame in ms

    private final SurfaceHolder surfaceHolder;
    private final GameView gameView; // Reference to the view for drawing and updating
    private volatile boolean running = false;
    private long lastUpdateTime = 0;

    public GameThread(SurfaceHolder holder, GameView view) {
        super();
        this.surfaceHolder = holder;
        this.gameView = view;
    }

    public void setRunning(boolean isRunning) {
        this.running = isRunning;
    }

    @Override
    public void run() {
        Log.i(TAG, "Game thread starting...");
        long startTime;
        long timeMillis;
        long waitTime;
        lastUpdateTime = System.currentTimeMillis(); // Initialize before loop

        while (running) {
            startTime = System.currentTimeMillis();
            Canvas canvas = null;

            try {
                // Try locking the canvas for exclusive pixel editing on the surface.
                canvas = this.surfaceHolder.lockCanvas();
                synchronized (surfaceHolder) {
                    // Calculate delta time in seconds
                    long now = System.currentTimeMillis();
                    double deltaTime = (now - lastUpdateTime) / 1000.0;
                    lastUpdateTime = now;

                    // Update game state
                    this.gameView.update(deltaTime);

                    // Draw the canvas on the panel
                    if (canvas != null) {
                        this.gameView.drawGame(canvas);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during game loop", e);
            } finally {
                // When done drawing, unlock canvas posting the changes
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        Log.e(TAG, "Error unlocking canvas", e);
                    }
                }
            }

            // Calculate loop time and sleep if necessary
            timeMillis = System.currentTimeMillis() - startTime;
            waitTime = OPTIMAL_TIME - timeMillis;

            if (waitTime > 0) {
                try {
                    // Send the thread to sleep for a short period
                    // Useful for saving battery and CPU cycles
                    sleep(waitTime);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Thread interrupted while sleeping", e);
                    Thread.currentThread().interrupt(); // Preserve interrupt status
                    running = false; // Stop loop if interrupted
                }
            }
        }
        Log.i(TAG, "Game thread stopped.");
    }
} 