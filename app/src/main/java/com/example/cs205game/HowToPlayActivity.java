package com.example.cs205game;

import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class HowToPlayActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_how_to_play); // Link to the layout file

        Button backButton = findViewById(R.id.buttonBackHowToPlay);
        backButton.setOnClickListener(v -> finish()); // Close this activity
    }
} 