package org.md2k.beacon;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.md2k.beacon.devices.Devices;
import org.md2k.datakitapi.DataKitAPI;
import org.md2k.datakitapi.datatype.DataType;
import org.md2k.datakitapi.datatype.DataTypeDoubleArray;
import org.md2k.datakitapi.exception.DataKitException;
import org.md2k.datakitapi.messagehandler.ResultCallback;
import org.md2k.datakitapi.time.DateTime;
import org.md2k.utilities.Report.Log;
import org.md2k.utilities.Report.LogStorage;
import org.md2k.utilities.permission.PermissionInfo;

import java.util.ArrayList;
import java.util.Collection;

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
    private Devices devices;
    private DataKitAPI dataKitAPI = null;
    private BeaconManager beaconManager;

    @Override
    public void onCreate() {
        super.onCreate();
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.getPermissions(this, new ResultCallback<Boolean>() {
            @Override
            public void onResult(Boolean result) {
                if (!result) {
                    Toast.makeText(getApplicationContext(), "!PERMISSION DENIED !!! Could not continue...", Toast.LENGTH_SHORT).show();
                    stopSelf();
                } else {
                    load();
                }
            }
        });
    }

    void load() {

        LogStorage.startLogFileStorageProcess(getApplicationContext().getPackageName());

        Log.d(TAG, "onCreate()...");
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mMessageReceiverStop,
                new IntentFilter(INTENT_STOP));
        devices = new Devices(getApplicationContext());

        connectDataKit();
    }
    void beaconStart(){
        beaconManager = BeaconManager.getInstanceForApplication(this.getApplicationContext());
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
        beaconManager.bind(this);
    }

    private void connectDataKit() {
        dataKitAPI = DataKitAPI.getInstance(getApplicationContext());
        Log.d(TAG, "datakitapi connected=" + dataKitAPI.isConnected());
        try {
            dataKitAPI.connect(new org.md2k.datakitapi.messagehandler.OnConnectionListener() {
                @Override
                public void onConnected() {
                    try {
                        devices.register();
                        beaconStart();
                    } catch (DataKitException e) {
                        stopSelf();
                        e.printStackTrace();
                    }
                }
            });
        } catch (DataKitException e) {
            Log.d(TAG, "onException...");
            stopSelf();
        }
    }

    private void disconnectDataKit() {
        Log.d(TAG, "disconnectDataKit()...");
        if (devices != null)
            try {
                devices.unregister();
            } catch (DataKitException e) {
                e.printStackTrace();
            }
        if (dataKitAPI != null) {
            dataKitAPI.disconnect();
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()...");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiverStop);
        clearDataKitSettingsBluetooth();
        beaconManager.unbind(this);
        super.onDestroy();
    }
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
    private void clearDataKitSettingsBluetooth() {
        Log.d(TAG, "clearDataKitSettingsBluetooth...");
        disconnectDataKit();
    }


    private BroadcastReceiver mMessageReceiverStop = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            org.md2k.utilities.Report.Log.d(TAG, "Stop");
            org.md2k.utilities.Report.Log.w(TAG, "time=" + DateTime.convertTimeStampToDateTime(DateTime.getDateTime()) + ",timestamp=" + DateTime.getDateTime() + ",broadcast_receiver_stop_service");
            stopSelf();
        }
    };
    @Override
    public void onBeaconServiceConnect() {
        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                int flag = 0;
                String platformId;
                ArrayList<Beacon> list = new ArrayList(beacons);
                for(int i=0;i<list.size();i++){
                    String adr=list.get(i).getBluetoothAddress();
                    double dist=list.get(i).getDistance();
                    double rssi=list.get(i).getRssi();
                    double tx = list.get(i).getTxPower();
                    double[] data=new double[]{dist,rssi,tx};
                    DataTypeDoubleArray dataTypeDoubleArray=new DataTypeDoubleArray(DateTime.getDateTime(), data);
                    try {
                        if(devices.find(adr)==null)
                            platformId=adr;
                        else platformId=devices.find(adr).getPlatformId();
                        devices.insert(adr,dataTypeDoubleArray);
                        updateView(platformId, dist, rssi, tx);
                    } catch (DataKitException e) {
                        stopSelf();
                    }
                }
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
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
        intent.putExtra("text",platformId+" D="+String.format("%.2f",dist)+" R="+String.format("%.0f",rssi)+" T="+String.format("%.0f",tx));
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

    }

}
