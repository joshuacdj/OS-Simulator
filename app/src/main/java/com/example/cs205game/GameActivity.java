package com.example.cs205game;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.Window;

// the main activity for the actual game screen holds the gameview
public class GameActivity extends Activity {

    private static final String TAG = "GameActivity";
    private GameView gameView;

    // runs when the activity is created
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        // stop screen from turning off during game
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // create n set the game view
        gameView = new GameView(this);
        setContentView(gameView);

        // try to make it fullscreen removes title bar
        // getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        //         WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // try hide the system nav/status bars too
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                // Log.w(TAG, "Fallback to deprecated API");
                // getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                //         WindowManager.LayoutParams.FLAG_FULLSCREEN);
                Log.w(TAG, "WindowInsetsController is null");
            }
        } else {
            // older android versions might need different flags
            // flag_fullscreen might be enough tho
        }
    }

    // called when the game is paused (e.g user switches app)
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        if (gameView != null) {
            gameView.pause(); // tells the view to pause its game loop
        }
    }

    // called when the game is resumed
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (gameView != null) {
            gameView.resume(); // tells the view to resume its game loop
        }
    }

    // called when the activity is being destroyed
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        // gameview surface destroyed should handle thread stopping
        // maybe add more cleanup here later if needed
    }
} 