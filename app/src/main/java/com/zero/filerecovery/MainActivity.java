package com.zero.filerecovery;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void observe(View view) {
        Intent service = new Intent(FileRecoverService.ACTION_OBSERVE);
        service.setPackage(getPackageName());
        service.putExtra(FileRecoverService.EXTRA_PATH, Environment.getExternalStorageDirectory() + "/BiuBiu/video");
        startService(service);
    }

    public void recover(View view) {
        Intent service = new Intent(FileRecoverService.ACTION_RECOVER);
        service.setPackage(getPackageName());
        startService(service);
    }
}
