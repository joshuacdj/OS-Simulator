package com.example.cs205game;

import android.graphics.Canvas;
import android.util.Log;
import android.view.SurfaceHolder;

public class GameThread extends Thread {
    private static final String TAG = "GameThread";
    private static final int TARGET_FPS = 60; // Target updates per second
    private static final long OPTIMAL_TIME_NS = 1_000_000_000 / TARGET_FPS; // Target time per frame in nanoseconds
    private static final double MAX_DELTA_TIME_S = 1.0 / 30.0; // Cap delta time to prevent large jumps (e.g., at 30fps equivalent)

    private final SurfaceHolder surfaceHolder;
    private final GameView gameView; // Reference to the view for drawing and updating
    private volatile boolean running = false;
    private long lastLoopTimeNs = 0; // Use nanoTime

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
        long loopStartTimeNs;
        long elapsedTimeNs;
        long sleepTimeNs;
        lastLoopTimeNs = System.nanoTime(); // Initialize with nanoTime before loop

        while (running) {
            loopStartTimeNs = System.nanoTime();
            
            // Calculate delta time in seconds using nanoTime
            double deltaTime = (loopStartTimeNs - lastLoopTimeNs) / 1_000_000_000.0;
            lastLoopTimeNs = loopStartTimeNs;
            
            // Cap delta time to prevent large jumps on frame drops/stalls
            deltaTime = Math.min(deltaTime, MAX_DELTA_TIME_S);

            Canvas canvas = null;
            try {
                canvas = this.surfaceHolder.lockCanvas();
                synchronized (surfaceHolder) {
                    // Update game state using capped delta time
                    this.gameView.update(deltaTime);

                    // Draw the canvas on the panel
                    if (canvas != null) {
                        this.gameView.drawGame(canvas);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during game loop", e);
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        Log.e(TAG, "Error unlocking canvas", e);
                    }
                }
            }

            // Calculate loop time and sleep if necessary (using nanoTime)
            elapsedTimeNs = System.nanoTime() - loopStartTimeNs;
            sleepTimeNs = OPTIMAL_TIME_NS - elapsedTimeNs;

            if (sleepTimeNs > 0) {
                try {
                    // Use TimeUnit for potentially more accurate nano sleep, though Thread.sleep uses ms
                    // For simplicity, converting ns to ms for Thread.sleep
                    long sleepTimeMs = sleepTimeNs / 1_000_000;
                    long sleepTimeNsRemainder = sleepTimeNs % 1_000_000;
                    // Thread.sleep takes ms and optionally ns (0-999999)
                    // Only use sleep if ms part > 0, handle very short sleeps carefully
                    if (sleepTimeMs > 0 || sleepTimeNsRemainder > 0) { 
                        Thread.sleep(sleepTimeMs, (int)sleepTimeNsRemainder); 
                    } 
                    
                } catch (InterruptedException e) {
                    Log.w(TAG, "Thread interrupted while sleeping", e);
                    Thread.currentThread().interrupt(); // Preserve interrupt status
                    running = false; // Stop loop if interrupted
                }
            } // else: Frame took longer than optimal time, don't sleep
        }
        Log.i(TAG, "Game thread stopped.");
    }
} 