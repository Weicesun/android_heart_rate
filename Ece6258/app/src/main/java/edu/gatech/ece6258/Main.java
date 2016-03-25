package edu.gatech.ece6258;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import static edu.gatech.ece6258.R.id.opnfrontbnt;
import static edu.gatech.ece6258.R.id.opnrearbnt;

public class Main extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button frontCamera = new Button(this);
        Button rearCamera = new Button(this);
        frontCamera = (Button) findViewById(opnfrontbnt);
        rearCamera = (Button) findViewById(opnrearbnt);
        frontCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Main.this,FrontCamera.class));
            }
        });
        rearCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Main.this,RearCamera.class));
            }
        });
    }
}
