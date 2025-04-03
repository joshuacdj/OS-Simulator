package com.example.cs205game;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

// simple activity just shows the credits screen
public class CreditsActivity extends AppCompatActivity {

    // called when the activity starts up
    // sets the layout n wires up the back button
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_credits); // hook up the xml layout

        Button backButton = findViewById(R.id.buttonBack);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // just closes this screen goes back to main menu
                finish(); 
            }
        });
    }
} 