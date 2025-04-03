package com.example.cs205game;

import android.graphics.Canvas;
import android.util.Log;
import android.view.SurfaceHolder;

/**
 * dedicated thread for running the main game loop.
 * handles timing, updates game state via gameview, and triggers drawing.
 * aims for a target fps and includes delta time capping for smoother updates.
 */
public class GameThread extends Thread {
    private static final String TAG = "GameThread"; // log tag
    private static final int TARGET_FPS = 60; // target frames per second
    // target time per frame in nanoseconds for fps calculation
    private static final long OPTIMAL_TIME_NS = 1_000_000_000 / TARGET_FPS; 
    // max delta time to prevent huge jumps if frame takes too long (e.g 30fps equivalent)
    private static final double MAX_DELTA_TIME_S = 1.0 / 30.0; 

    private final SurfaceHolder surfaceHolder; // handle to the drawing surface
    private final GameView gameView; // reference to the view for drawing and updating
    private volatile boolean running = false; // flag to control the loop
    private long lastLoopTimeNs = 0; // tracks time of the last loop iteration

    public GameThread(SurfaceHolder holder, GameView view) {
        super();
        this.surfaceHolder = holder;
        this.gameView = view;
    }

    public void setRunning(boolean isRunning) {
        this.running = isRunning;
    }

    // the main game loop
    @Override
    public void run() {
        Log.i(TAG, "Game thread starting...");
        long loopStartTimeNs;
        long elapsedTimeNs;
        long sleepTimeNs;
        lastLoopTimeNs = System.nanoTime(); // initialize time before loop

        while (running) {
            loopStartTimeNs = System.nanoTime(); // time at the start of this loop
            
            // calculate time elapsed since last loop in seconds
            double deltaTime = (loopStartTimeNs - lastLoopTimeNs) / 1_000_000_000.0;
            lastLoopTimeNs = loopStartTimeNs;
            
            // cap delta time prevents large jumps if theres a lag spike
            deltaTime = Math.min(deltaTime, MAX_DELTA_TIME_S);

            Canvas canvas = null;
            try {
                // lock the canvas for drawing
                canvas = this.surfaceHolder.lockCanvas();
                // synchronize drawing/updating on the surface holder 
                // ensures surface isn't destroyed mid-draw
                synchronized (surfaceHolder) {
                    // update game state using the calculated (and capped) delta time
                    this.gameView.update(deltaTime);

                    // draw the current game state to the canvas
                    if (canvas != null) {
                        this.gameView.drawGame(canvas);
                    }
                }
            } catch (Exception e) {
                // log errors during the game loop
                Log.e(TAG, "Error during game loop", e);
            } finally {
                // always ensure we unlock the canvas
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        // log errors during unlock too
                        Log.e(TAG, "Error unlocking canvas", e);
                    }
                }
            }

            // calculate how long the loop took
            elapsedTimeNs = System.nanoTime() - loopStartTimeNs;
            // calculate how long we need to wait to hit target fps
            sleepTimeNs = OPTIMAL_TIME_NS - elapsedTimeNs;

            // if we have time left sleep to save battery/cpu and maintain fps
            if (sleepTimeNs > 0) {
                try {
                    // convert ns to ms and remaining ns for thread.sleep
                    long sleepTimeMs = sleepTimeNs / 1_000_000;
                    int sleepTimeNsRemainder = (int)(sleepTimeNs % 1_000_000);
                    
                    // sleep the thread
                    Thread.sleep(sleepTimeMs, sleepTimeNsRemainder); 
                    
                } catch (InterruptedException e) {
                    // thread interrupted probably shutting down
                    Log.w(TAG, "Thread interrupted while sleeping", e);
                    running = false; // stop the loop
                    Thread.currentThread().interrupt(); // set interrupt flag
                }
            } // else: frame took longer than optimal time no need to sleep
        }
        Log.i(TAG, "Game thread stopped.");
    }
} 