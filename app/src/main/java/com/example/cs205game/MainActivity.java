package com.example.cs205game;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Find buttons
        Button buttonStartGame = findViewById(R.id.buttonStartGame);
        Button buttonCredits = findViewById(R.id.buttonCredits);
        Button buttonQuit = findViewById(R.id.buttonQuit);

        // Set click listener for Start Game
        buttonStartGame.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, GameActivity.class);
            startActivity(intent);
        });

        // Set click listener for Credits (placeholder)
        buttonCredits.setOnClickListener(v -> {
            // TODO: Implement Credits Activity/Screen
            android.widget.Toast.makeText(this, "Credits screen not implemented yet", android.widget.Toast.LENGTH_SHORT).show();
        });

        // Set click listener for Quit
        buttonQuit.setOnClickListener(v -> {
            finish(); // Close the activity
        });

        // Apply window insets listener
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}