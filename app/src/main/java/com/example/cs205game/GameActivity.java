package com.example.cs205game;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

public class GameActivity extends Activity {

    private static final String TAG = "GameActivity";
    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        // Set the content view *first*
        gameView = new GameView(this);
        setContentView(gameView);

        // Make fullscreen *after* setting content view
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Hide system bars *after* setting content view
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                 Log.w(TAG, "WindowInsetsController is null");
            }
        } else {
            // Handle older versions if necessary
            // Note: FLAG_FULLSCREEN might be sufficient for older APIs
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        if (gameView != null) {
            gameView.pause(); // Pauses the game loop
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (gameView != null) {
            gameView.resume(); // Resumes the game loop
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        // Ensure game resources are cleaned up if activity is destroyed
        if (gameView != null) {
             // Consider if explicit cleanup needed beyond stopping thread in surfaceDestroyed
        }
    }
} 