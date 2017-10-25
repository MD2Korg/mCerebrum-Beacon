package org.md2k.beacon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.md2k.datakitapi.time.DateTime;
import org.md2k.mcerebrum.core.access.appinfo.AppInfo;

import java.util.ArrayList;


/**
 * Copyright (c) 2015, The University of Memphis, MD2K Center
 * - Syed Monowar Hossain <monowar.hossain@gmail.com>
 * All rights reserved.
 * <p/>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * <p/>
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * <p/>
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p/>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

public class ActivityMain extends AppCompatActivity {
    private static final String TAG = ActivityMain.class.getSimpleName();
    public static final String INTENT_NAME="beacon_data";

    public static final int OPERATION_RUN = 0;
    public static final int OPERATION_SETTINGS = 1;
    public static final int OPERATION_PLOT = 2;
    public static final int OPERATION_START_BACKGROUND = 3;
    public static final int OPERATION_STOP_BACKGROUND = 4;
    public static final String OPERATION = "operation";

    boolean isEverythingOk = false;
    int operation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readIntent();
        checkRequirement();
        // Bo test
    }

    void load() {
        isEverythingOk = true;
        Intent intent;
        switch (operation) {
            case OPERATION_RUN:
                initializeUI();
                break;
            case OPERATION_START_BACKGROUND:
                if(!AppInfo.isServiceRunning(this, ServiceBeacon.class.getName())) {
                    intent = new Intent(ActivityMain.this, ServiceBeacon.class);
                    startService(intent);
                }
                finish();
                break;
            case OPERATION_STOP_BACKGROUND:
                if(AppInfo.isServiceRunning(this, ServiceBeacon.class.getName())) {
                    intent = new Intent(ActivityMain.this, ServiceBeacon.class);
                    stopService(intent);
                }
                finish();
                break;
            case OPERATION_PLOT:
                break;
            case OPERATION_SETTINGS:
                intent = new Intent(this, ActivitySettings.class);
                startActivity(intent);
                finish();
                break;
            default:
//                Toasty.error(getApplicationContext(), "Invalid argument. Operation = " + operation, Toast.LENGTH_SHORT).show();
                initializeUI();
        }
    }
    void initializeUI() {
        setContentView(R.layout.activity_main);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        final Button buttonService = (Button) findViewById(R.id.button_app_status);
        buttonService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), ServiceBeacon.class);
                if (AppInfo.isServiceRunning(getBaseContext(), ServiceBeacon.class.getName())) {
                    stopService(intent);
                } else {
                    startService(intent);
                }
            }
        });

    }
    private Handler mHandler = new Handler();
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            {
                long time = AppInfo.serviceRunningTime(ActivityMain.this, Constants.SERVICE_NAME);
                if (time < 0) {
                    ((Button) findViewById(R.id.button_app_status)).setText("START");
                    findViewById(R.id.button_app_status).setBackground(ContextCompat.getDrawable(ActivityMain.this, R.drawable.button_status_off));

                } else {
                    findViewById(R.id.button_app_status).setBackground(ContextCompat.getDrawable(ActivityMain.this, R.drawable.button_status_on));
                    ((Button) findViewById(R.id.button_app_status)).setText(DateTime.convertTimestampToTimeStr(time));

                }
                mHandler.postDelayed(this, 1000);
            }
        }
    };
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateTable(intent);
        }
    };
    ArrayList<String> results=new ArrayList<>();
    int count=0;
    private void updateTable(Intent intent) {
        count=(count+1)%1000;
        results.add(String.format("%04d ",count)+"  "+intent.getStringExtra("text"));
        if(results.size()>20) results.remove(0);
        TextView textView= (TextView) findViewById(R.id.textview_result);
        String f="";
        for(int i=0;i<results.size();i++) {
            f += results.get(i) + "\n";
        }
        textView.setText(f.replace("\\n","\n"));
    }
    @Override
    public void onResume() {
        if (isEverythingOk) {
            LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                    new IntentFilter(INTENT_NAME));
            mHandler.post(runnable);
        }
        super.onResume();
    }

    @Override
    public void onPause() {
        if (isEverythingOk) {
            mHandler.removeCallbacks(runnable);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()...");
        super.onDestroy();
        Log.d(TAG, "...onDestroy()");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        Intent intent;
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                break;
            case R.id.action_settings:
                intent = new Intent(this, ActivitySettings.class);
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    private void checkRequirement() {
        Intent intent = new Intent(this, ActivityPermission.class);
        startActivityForResult(intent, 1111);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1111) {
            if (resultCode != RESULT_OK) {
                finish();
            } else {
                load();
            }
        }
    }

    void readIntent() {
        if(getIntent().getExtras()!=null) {
            operation = getIntent().getExtras().getInt(OPERATION, 0);
        }else operation=0;
    }
 /*
        Permission.requestPermission(this, new PermissionCallback() {
            @Override
            public void OnResponse(boolean isGranted) {
                if (!isGranted) {
                    Toasty.error(getApplicationContext(), "!PERMISSION DENIED !!! Could not continue...", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    if(getIntent().hasExtra("RUN") && getIntent().getBooleanExtra("RUN", false)) {
                        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                        if (!mBluetoothAdapter.isEnabled())
                            mBluetoothAdapter.enable();
                        Intent intent = new Intent(ActivityMain.this, ServiceBeacon.class);
                        startService(intent);
                        finish();
                    }else if(getIntent().hasExtra("PERMISSION") && getIntent().getBooleanExtra("PERMISSION", false)) {
                        finish();
                    } else {
                        setContentView(R.layout.activity_main);
                        load();
                    }
                }
            }
        });

    }

     */
}
