package org.md2k.beacon;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.md2k.beacon.devices.Devices;
import org.md2k.datakitapi.DataKitAPI;
import org.md2k.datakitapi.datatype.DataTypeDoubleArray;
import org.md2k.datakitapi.exception.DataKitException;
import org.md2k.datakitapi.time.DateTime;
import org.md2k.mcerebrum.commons.permission.Permission;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

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

public class ServiceBeacon extends Service  implements BeaconConsumer {
    private static final String TAG = ServiceBeacon.class.getSimpleName();
    public static final String INTENT_STOP = "stop";
    private static final long SCAN_TIME = 2000;
    private static final long SCAN_PERIOD=18000;
    private Devices devices;
    private DataKitAPI dataKitAPI = null;
    public static final String ACTION_LOCATION_CHANGED = "android.location.PROVIDERS_CHANGED";

    @Override
    public void onCreate() {
        super.onCreate();
        if (Permission.hasPermission(this)) {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if(!mBluetoothAdapter.isEnabled())
                mBluetoothAdapter.enable();
            LocationManager locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
            if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                removeNotification();
                load();
            }
            else {
                showNotification("Turn on GPS", "Beacon data can't be recorded. (Please click to turn on GPS)");
                stopSelf();
            }
        } else {
            Toasty.error(getApplicationContext(), "!PERMISSION is not GRANTED !!! Could not continue...", Toast.LENGTH_SHORT).show();
            showNotification("Permission required", "Beacon app can't continue. (Please click to grant permission)");
            stopSelf();
        }
    }
    private void showNotification(String title, String message) {
        Bundle bundle = new Bundle();
        bundle.putInt(ActivityMain.OPERATION, ActivityMain.OPERATION_START_BACKGROUND);
        PugNotification.with(this).load().identifier(31).title(title).smallIcon(R.mipmap.ic_launcher)
                .message(message).autoCancel(true).click(ActivityMain.class,bundle).simple().build();
    }
    private void removeNotification() {
        PugNotification.with(this).cancel(31);
    }

    void load() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(ACTION_LOCATION_CHANGED);
        registerReceiver(mReceiver, filter);

        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mMessageReceiverStop,
                new IntentFilter(INTENT_STOP));
        devices = new Devices(getApplicationContext());

        connectDataKit();
    }
    void clearBeacon(){
        BeaconManager beaconManager = BeaconManager.getInstanceForApplication(this.getApplicationContext());
        beaconManager.getBeaconParsers().clear();
        beaconManager.removeAllRangeNotifiers();
        try {
            Region region=new Region("myRangingUniqueId", null, null, null);
            beaconManager.stopRangingBeaconsInRegion(region);
        } catch (RemoteException ignored) {
        }
        try {
        beaconManager.unbind(this);
        } catch (Exception ignored) {
        }
    }
    void setBeacon(){
        BeaconManager beaconManager = BeaconManager.getInstanceForApplication(this.getApplicationContext());
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));

        beaconManager.setBackgroundScanPeriod(SCAN_TIME);
        beaconManager.setForegroundScanPeriod(SCAN_TIME);
        beaconManager.setBackgroundBetweenScanPeriod(SCAN_PERIOD);
        beaconManager.setForegroundBetweenScanPeriod(SCAN_PERIOD);
        beaconManager.bind(this);
    }
    void beaconStart() {
        clearBeacon();
        setBeacon();
    }

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
        if (devices != null)
            try {
                devices.unregister();
            } catch (DataKitException e) {
            }
        if (dataKitAPI != null) {
            dataKitAPI.disconnect();
        }
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiverStop);
        try {
            unregisterReceiver(mReceiver);
        }catch (Exception ignored){

        }
        try {
            clearBeacon();
        }catch (Exception ignored){

        }
        disconnectDataKit();

        super.onDestroy();
    }
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }


    private BroadcastReceiver mMessageReceiverStop = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopSelf();
        }
    };
    @Override
    public void onBeaconServiceConnect() {
        BeaconManager beaconManager = BeaconManager.getInstanceForApplication(this.getApplicationContext());
        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                int flag = 0;
                String platformId;
                Object[] list= beacons.toArray();
                Log.d("abc","list="+beacons.size());

                HashSet<String> hashSet= new HashSet<>();
                for (Object o : list) {
                    Beacon aList = (Beacon) o;
                    String adr = aList.getBluetoothAddress();
                    double dist = aList.getDistance();
                    double rssi = aList.getRssi();
                    double tx = aList.getTxPower();
                    if (hashSet.contains(adr)) continue;
                    hashSet.add(adr);
                    double[] data = new double[]{dist, rssi, tx};
                    DataTypeDoubleArray dataTypeDoubleArray = new DataTypeDoubleArray(DateTime.getDateTime(), data);
                    try {
                        if (devices.find(adr) == null)
                            platformId = adr;
                        else platformId = devices.find(adr).getPlatformId();
                        devices.insert(adr, dataTypeDoubleArray);
                        Log.d(TAG,"adr="+adr);
                        updateView(platformId, dist, rssi, tx);
                    } catch (DataKitException e) {
                        stopSelf();
                    }
                }
            }
        });

        try {
            Region region=new Region("myRangingUniqueId", null, null, null);
            beaconManager.startRangingBeaconsInRegion(region);
        } catch (RemoteException e) {
            stopSelf();
        }
    }
    private void updateView(String platformId, double dist, double rssi, double tx) {
        Intent intent = new Intent(ActivityMain.INTENT_NAME);
        intent.putExtra("platform_id", platformId);
        intent.putExtra("distance", dist);
        intent.putExtra("rssi", rssi);
        intent.putExtra("tx", tx);
        intent.putExtra("text",String.valueOf(DateTime.getDateTime()%(1000*60))+" "+platformId+" D="+String.format("%.2f",dist)+" R="+String.format("%.0f",rssi)+" T="+String.format("%.0f",tx));
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

    }
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Toasty.error(ServiceBeacon.this, "Bluetooth is off. Please turn on bluetooth", Toast.LENGTH_SHORT).show();
                        showNotification("Turn on Bluetooth", "Beacon data con't be recorded. Please click to turn on bluetooth");
                        stopSelf();
                }
            } else if (action.equals(ACTION_LOCATION_CHANGED)) {
                LocationManager locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                } else {
                    Toasty.error(context, "Please turn on GPS", Toast.LENGTH_SHORT).show();
                    showNotification("Turn on GPS", "Beacon data can't be recorded. (Please click to turn on GPS)");
                    stopSelf();
                }
            }
        }
    };
}
