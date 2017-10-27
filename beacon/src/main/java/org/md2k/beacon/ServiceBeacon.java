package org.md2k.beacon;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import org.md2k.beacon.devices.Devices;
import org.md2k.datakitapi.DataKitAPI;
import org.md2k.datakitapi.exception.DataKitException;
import org.md2k.datakitapi.time.DateTime;
import org.md2k.mcerebrum.commons.permission.Permission;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import br.com.goncalves.pugnotification.notification.PugNotification;
import es.dmoral.toasty.Toasty;

/*
 * Copyright (c) 2015, The University of Memphis, MD2K Center
 * - Syed Monowar Hossain <monowar.hossain@gmail.com>
 * - Nazir Saleheen <nazir.saleheen@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
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

public class ServiceBeacon extends Service {

    private static final String TAG = ServiceBeacon.class.getSimpleName();

    public static final String INTENT_STOP = "stop";

    private static final long SCAN_TIME = 2000;
    private static final long SCAN_PERIOD = 18000;
    private Devices devices;
    private DataKitAPI dataKitAPI = null;

    public static final String ACTION_LOCATION_CHANGED = "android.location.PROVIDERS_CHANGED";

    private static final int NOTIFICATION_ID = 31;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner mLEScanner;

    // ---- activity life cycle callbacks ----------------------------------------------------------
    @Override
    public void onCreate() {
        super.onCreate();

        // permission check
        if (!Permission.hasPermission(this)) {
            Toasty.error(getApplicationContext(),
                    "!PERMISSION is not GRANTED !!! Could not continue...", Toast.LENGTH_SHORT
            ).show();

            showNotification(
                    "Permission required",
                    "Beacon app can't continue. (Please click to grant permission)"
            );

            stopSelf();
        }

        // enable bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled())
            bluetoothAdapter.enable();

        load();
    }

    @Override
    public void onDestroy() {
        //TODO: what's the difference between the following two ways to unregister receivers?
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiverStop);
        try {
            unregisterReceiver(mReceiver);
        } catch (Exception ignored) {}

        disconnectDataKit();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // ---- service initialization -----------------------------------------------------------------
    private void load() {
        removeNotification();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(ACTION_LOCATION_CHANGED);
        registerReceiver(mReceiver, filter);

        LocalBroadcastManager.getInstance(getApplicationContext())
                .registerReceiver(mMessageReceiverStop, new IntentFilter(INTENT_STOP));
        devices = new Devices(getApplicationContext());

        connectDataKit();
    }

    // ---- DataKit connection ---------------------------------------------------------------------
    private void connectDataKit() {
        dataKitAPI = DataKitAPI.getInstance(getApplicationContext());
        try {
            dataKitAPI.connect(new org.md2k.datakitapi.messagehandler.OnConnectionListener() {
                @Override
                public void onConnected() {
                    try {
                        devices.register();
                        beaconStart();
                    } catch (DataKitException e) {
                        stopSelf();
                    }
                }
            });
        } catch (DataKitException e) {
            stopSelf();
        }
    }

    private void disconnectDataKit() {
        if (devices != null) {
            try {
                devices.unregister();
            } catch (DataKitException e) {
                e.printStackTrace();
            }
        }

        if (dataKitAPI != null) {
            dataKitAPI.disconnect();
            dataKitAPI = null;
        }

        beaconStop();
    }

    // ---- Bluetooth LE callback ------------------------------------------------------------------
    private void beaconStart() {
        final BluetoothManager bluetoothManager =
                (BluetoothManager) this.getApplicationContext().getSystemService(
                        Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        mLEScanner = bluetoothAdapter.getBluetoothLeScanner();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        List<ScanFilter> filters = new ArrayList<>();

        mLEScanner.startScan(filters, settings, mLEScanCallback);
    }

    private void beaconStop() {
        mLEScanner.stopScan(mLEScanCallback);
    }

    private ScanCallback mLEScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            parseLEScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                Log.i("ScanResult - Results", result.toString());
                parseLEScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    private void parseLEScanResult(ScanResult scanResult) {
        String address = scanResult.getDevice().getAddress();
        int rssi = scanResult.getRssi();
        byte[] data = scanResult.getScanRecord().getBytes();
        updateView(address, rssi);
        //TODO: Monowar, do I need to write the data back to DataKit/DataSource?
    }

    // ---- update view ----------------------------------------------------------------------------
    private void updateView(String platformId, int rssi) {
        Intent intent = new Intent(ActivityMain.INTENT_NAME);
        String text = String.format(Locale.US, "%d %s R=%d",
                DateTime.getDateTime() % (1000L * 60),  // second of the minute
                platformId, rssi);
        intent.putExtra("platform_id", platformId)
            .putExtra("rssi", rssi)
            .putExtra("text", text);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    // ---- broadcast receivers --------------------------------------------------------------------
    private BroadcastReceiver mMessageReceiverStop = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopSelf();
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Toasty.error(ServiceBeacon.this,
                                "Bluetooth is off. Please turn on bluetooth", Toast.LENGTH_SHORT)
                                .show();

                        showNotification(
                                "Turn on Bluetooth",
                                "Beacon data con't be recorded. Please click to turn on bluetooth"
                        );
                        stopSelf();
                }
            }
        }
    };

    // ---- notification helpers -------------------------------------------------------------------
    private void showNotification(String title, String message) {
        Bundle bundle = new Bundle();
        bundle.putInt(ActivityMain.OPERATION, ActivityMain.OPERATION_START_BACKGROUND);
        PugNotification.with(this)
                .load()
                .identifier(NOTIFICATION_ID)
                .title(title)
                .smallIcon(R.mipmap.ic_launcher)
                .message(message)
                .autoCancel(true)
                .click(ActivityMain.class, bundle)
                .simple()
                .build();
    }

    private void removeNotification() {
        PugNotification.with(this).cancel(NOTIFICATION_ID);
    }

}
