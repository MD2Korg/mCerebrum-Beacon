package org.md2k.beacon;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.md2k.beacon.configuration.Configuration;
import org.md2k.beacon.devices.Devices;
import org.md2k.datakitapi.source.datasource.DataSource;
import org.md2k.datakitapi.source.platform.PlatformType;
import org.md2k.mcerebrum.commons.dialog.Dialog;
import org.md2k.mcerebrum.commons.dialog.DialogCallback;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import es.dmoral.toasty.Toasty;

import static android.R.attr.category;

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
public class PrefsFragmentSettings extends PreferenceFragment  implements BeaconConsumer {
    private static final String TAG = PrefsFragmentSettings.class.getSimpleName();
    Devices devices;
    private BeaconManager beaconManager;
    CharSequence[] entries;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        devices = new Devices(getActivity());
        addPreferencesFromResource(R.xml.pref_settings);
        setPreferenceScreenConfigured();
        setEntries();
    }
    void setEntries(){
        ArrayList<DataSource> dataSources = Configuration.readDefault();
        if(dataSources==null) return;
        ArrayList<String> temp=new ArrayList<>();
        boolean flag;
        for(int i=0;i<dataSources.size();i++){
            String id = dataSources.get(i).getPlatform().getId();
            flag=false;
            for(int j=0;j<temp.size();j++)
                if(temp.get(j).equals(id))
                    flag=true;
            if(!flag) {
                temp.add(id);
            }
        }
        if(temp.size()==0) entries=null;
        else {
            entries=new CharSequence[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                entries[i]=temp.get(i);
            }
        }
    }
    @Override
    public void onResume(){
        scan();
        super.onResume();
    }
    @Override
    public void onPause(){
        beaconManager.unbind(this);
        beaconManager=null;
        super.onPause();
    }
    void scan(){
        beaconManager = BeaconManager.getInstanceForApplication(getActivity().getApplicationContext());
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
        beaconManager.bind(this);

    }

    void setPreferenceScreenConfigured() {
        PreferenceCategory category = (PreferenceCategory) findPreference("key_device_configured");
        category.removeAll();
        for (int i = 0; i < devices.size(); i++) {
            Preference preference = new Preference(getActivity());
            preference.setKey(devices.get(i).getDeviceId());
            if (devices.get(i).getName() == null || devices.get(i).getName().length() == 0)
                preference.setTitle(devices.get(i).getDeviceId());
            else
                preference.setTitle(devices.get(i).getName() + " (" + devices.get(i).getDeviceId() + ")");
            preference.setSummary(devices.get(i).getPlatformId());
            preference.setIcon(R.drawable.ic_beacon_48dp);

            preference.setOnPreferenceClickListener(preferenceListenerConfigured());
            category.addPreference(preference);
        }
    }
    void addToPreferenceScreenAvailable(String deviceId) {
        if(beaconManager==null) return;
        final PreferenceCategory category = (PreferenceCategory) findPreference("key_device_available");
        for (int i = 0; i < category.getPreferenceCount(); i++)
            if (category.getPreference(i).getKey().equals(deviceId))
                return;
        ListPreference listPreference = new ListPreference(getActivity());
        if (entries!=null) {
            listPreference.setEntryValues(entries);
            listPreference.setEntries(entries);
        } else {
            listPreference.setEntryValues(R.array.beacon_entryValues);
            listPreference.setEntries(R.array.beacon_entries);
        }
        listPreference.setKey(deviceId);
        listPreference.setTitle(deviceId);
        listPreference.setIcon(R.drawable.ic_beacon);
        listPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if(newValue.toString().toUpperCase().equals("OTHER")){
                Dialog.editboxText(getActivity(), "Beacon Location", "Please Type..,", new DialogCallback() {
                    @Override
                    public void onSelected(String value) {
                        if(value!=null && value.length()!=0){
                            tryToInsert(deviceId, value, preference);
                        }
                    }
                }).show();
            }else
                tryToInsert(deviceId, newValue.toString(), preference);
            return false;
        });
        category.addPreference(listPreference);
    }
    void tryToInsert(String deviceId, String location, Preference preference){
        if (devices.find(deviceId) != null)
            Toasty.error(getActivity(), "Device is already configured...", Toast.LENGTH_SHORT).show();
        else if (devices.find(null, location) != null)
            Toasty.error(getActivity(), "Error: A device is already configured with same placement...", Toast.LENGTH_SHORT).show();
        else {
            devices.add(PlatformType.BEACON, location, deviceId, null);
            try {
                devices.writeDataSourceToFile();
            } catch (Exception e) {
                Toasty.error(getActivity(), "Error: Could not Save..."+e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            setPreferenceScreenConfigured();
            final PreferenceCategory category = (PreferenceCategory) findPreference("key_device_available");
            category.removePreference(preference);
        }

    }

    private Preference.OnPreferenceClickListener preferenceListenerConfigured() {
        return preference -> {
            final String deviceId = preference.getKey();
            Dialog.simple(getActivity(), "Delete Device", "Delete Device (" + preference.getTitle() + ")?", "Delete", "Cancel", value -> {
                if(value.equals("Delete")){
                    try {
                        devices.delete(deviceId);
                        devices.writeDataSourceToFile();
                        setPreferenceScreenConfigured();
                    } catch (Exception e) {
                        Toasty.error(getActivity(), "Error: Could not Save..."+e.getMessage(), Toast.LENGTH_SHORT).show();

                    }
                }

            }).show();
            return true;
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        assert v != null;
        ListView lv = (ListView) v.findViewById(android.R.id.list);
        lv.setPadding(0, 0, 0, 0);
        return v;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                getActivity().finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
    }
    boolean inConfigured(String address){
        for(int i=0;i<devices.size();i++)
            if(devices.get(i).getDeviceId().equals(address)) return true;
        return false;
    }
    @Override
    public void onBeaconServiceConnect() {
        beaconManager.addRangeNotifier((beacons, region) -> {
            List<Beacon> list = new ArrayList(beacons);
            for(int i=0;i<list.size();i++) {
                if (list.get(i).getBluetoothAddress() != null) {
                    if(!inConfigured(list.get(i).getBluetoothAddress()))
                        addToPreferenceScreenAvailable(list.get(i).getBluetoothAddress());
                }
            }
        });
        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException ignored) {
        }
/*
        beaconManager.addMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
//                Log.d(TAG,region.getBluetoothAddress());
                Log.i(TAG, "I just saw an beacon for the first time!");
                if(region.getBluetoothAddress()==null) return;
                devices.add(region.getBluetoothAddress());
                adapterDevices.notifyDataSetChanged();
            }

            @Override
            public void didExitRegion(Region region) {
//                Log.d(TAG,region.getBluetoothAddress());
                Log.i(TAG, "I no longer see an beacon");

                if(region.getBluetoothAddress()==null) return;
                devices.remove(region.getBluetoothAddress());
                adapterDevices.notifyDataSetChanged();
            }

            @Override
            public void didDetermineStateForRegion(int state, Region region) {
//                Log.d(TAG,region.getBluetoothAddress());
                Log.i(TAG, "I have just switched from seeing/not seeing beacons: "+state);
                if(region.getBluetoothAddress()==null) return;
                devices.add(region.getBluetoothAddress());
                adapterDevices.notifyDataSetChanged();
            }

        };

/*
        try {
            beaconManager.startMonitoringBeaconsInRegion(new Region("myMonitoringUniqueId", null, null, null));
        } catch (RemoteException e) {
            Log.d(TAG,"abc");
        }
*/
    }

    @Override
    public Context getApplicationContext() {

        return getActivity().getApplicationContext();
    }

    @Override
    public void unbindService(ServiceConnection serviceConnection) {
        Log.d(TAG, "abc");
        getActivity().unbindService(serviceConnection);

    }

    @Override
    public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
        return getActivity().bindService(intent, serviceConnection, i);
    }

}
