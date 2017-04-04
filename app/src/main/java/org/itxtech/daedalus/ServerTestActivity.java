package org.itxtech.daedalus;

import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class ServerTestActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_test);

        final Button startTestBut = (Button) findViewById(R.id.button_start_test);
        startTestBut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(v, R.string.start_test, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                startTestBut.setVisibility(View.INVISIBLE);
            }
        });
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }
}
