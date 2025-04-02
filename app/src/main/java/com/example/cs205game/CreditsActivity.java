package com.example.cs205game;

import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class CreditsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_credits); // Link to the layout file we'll create

        Button backButton = findViewById(R.id.buttonBack);
        backButton.setOnClickListener(v -> {
            finish(); // Simply close this activity to go back
        });
    }
} 