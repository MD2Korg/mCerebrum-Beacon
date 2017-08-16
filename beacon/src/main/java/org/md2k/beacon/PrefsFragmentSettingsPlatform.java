package org.md2k.beacon;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.md2k.beacon.configuration.Configuration;
import org.md2k.datakitapi.source.METADATA;
import org.md2k.datakitapi.source.datasource.DataSource;
import org.md2k.datakitapi.source.platform.PlatformId;
import org.md2k.datakitapi.source.platform.PlatformType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/*
 * Copyright (c) 2015, The University of Memphis, MD2K Center
 * - Syed Monowar Hossain <monowar.hossain@gmail.com>
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

public class PrefsFragmentSettingsPlatform extends PreferenceFragment implements BeaconConsumer {
    public static final String TAG = PrefsFragmentSettingsPlatform.class.getSimpleName();
    String deviceId = "", platformId = "", platformType;
    private ArrayAdapter<String> adapterDevices;
    private ArrayList<String> devices = new ArrayList<>();
    private BeaconManager beaconManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        platformType = getActivity().getIntent().getStringExtra(PlatformType.class.getSimpleName());
        devices = new ArrayList<>();
        addPreferencesFromResource(R.xml.pref_settings_platform);
        setupListViewDevices();
        setupPreferencePlatformId();
        setupPreferenceDeviceId();
        setAddButton();
        setCancelButton();
        beaconManager = BeaconManager.getInstanceForApplication(getActivity().getApplicationContext());
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
        beaconManager.bind(this);
    }
    void setEntries(){
        ArrayList<String> entries=new ArrayList<>();
        ArrayList<DataSource> dataSources = Configuration.readDefault(getActivity());
        boolean flag;
        for(int i=0;i<dataSources.size();i++){
            String id = dataSources.get(i).getPlatform().getId();
            flag=false;
            for(int j=0;j<entries.size();j++)
                if(entries.get(j).equals(id))
                    flag=true;
            if(flag==false)
                entries.add(id);
        }
        ListPreference listPreference= (ListPreference) findPreference("platformId");
        String[] e=new String[entries.size()];
        for(int i=0;i<entries.size();i++)
            e[i]=entries.get(i);
        listPreference.setEntries(e);
        listPreference.setEntryValues(e);

    }

    void setupListViewDevices() {
        adapterDevices = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_single_choice,
                android.R.id.text1, devices);
        ListView listViewDevices = (ListView) getActivity().findViewById(R.id.listView_devices);
        listViewDevices.setAdapter(adapterDevices);
        listViewDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String item = ((TextView) view).getText().toString().trim();
                Preference preference = findPreference("deviceId");
                deviceId = item;
                preference.setSummary(item);
            }
        });
    }

    private void setupPreferencePlatformId() {
        ListPreference platformIdPreference = (ListPreference) findPreference("platformId");
        platformIdPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                platformId = newValue.toString();
                preference.setSummary(newValue.toString());
                return false;
            }
        });
        setEntries();
    }

    private void setupPreferenceDeviceId() {
        Preference preference = findPreference("deviceId");
        preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Log.d(TAG, preference.getKey() + " " + newValue.toString());
                deviceId = newValue.toString().trim();
                preference.setSummary(newValue.toString().trim());
                return false;
            }
        });

    }

    private void setAddButton() {
        final Button button = (Button) getActivity().findViewById(R.id.button_2);
        button.setText("Add");

        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (deviceId == null || deviceId.equals(""))
                    Toast.makeText(getActivity(), "!!! Device ID is missing !!!", Toast.LENGTH_LONG).show();
                else if (platformId == null || platformId.equals(""))
                    Toast.makeText(getActivity(), "!!! Placement is missing !!!", Toast.LENGTH_LONG).show();
                else {
                    Intent returnIntent = new Intent();
                    String name = getName(deviceId);
                    returnIntent.putExtra(PlatformType.class.getSimpleName(), PlatformType.BEACON);
                    returnIntent.putExtra(PlatformId.class.getSimpleName(), platformId);
                    returnIntent.putExtra(METADATA.DEVICE_ID, getDeviceId(deviceId));
                    returnIntent.putExtra(METADATA.NAME, name);
                    getActivity().setResult(Activity.RESULT_OK, returnIntent);
                    getActivity().finish();
                }
            }
        });
    }

    private String getName(String str) {
        if (str.endsWith(")")) {
            String[] arr = str.split(" ");
            return arr[0];
        } else
            return null;
    }

    private String getDeviceId(String str) {
        if (str.endsWith(")")) {
            String[] arr = deviceId.split(" ");
            return arr[1].substring(1, arr[1].length() - 1);
        } else
            return str;
    }

    private void setCancelButton() {
        final Button button = (Button) getActivity().findViewById(R.id.button_1);
        button.setText("Close");

        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent returnIntent = new Intent();
                getActivity().setResult(getActivity().RESULT_CANCELED, returnIntent);
                getActivity().finish();
            }
        });
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
        beaconManager.unbind(this);

    }

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                int flag = 0;
                List<Beacon> list = new ArrayList(beacons);
                ArrayList<String> temp = new ArrayList<String>();
                for (int i = 0; i < list.size(); i++)
                    temp.add(list.get(i).getBluetoothAddress());
                if (temp.size() != devices.size())
                    flag = 1;
                else {
                    for (int i = 0; i < temp.size(); i++) {
                        boolean f = false;
                        for (int j = 0; j < devices.size(); j++) {
                            if (devices.get(j).equals(temp.get(i))) {
                                f = true;
                                break;
                            }
                        }
                        if (f == false) {
                            flag = 1;
                            break;
                        }
                    }
                }
                if (flag == 1) {
                    try {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                devices.clear();
                                for (int i = 0; i < temp.size(); i++)
                                    devices.add(temp.get(i));
//                            devices.add("abc");
//                            devices = temp;
                                adapterDevices.notifyDataSetChanged();
                            }
                        });
                    }catch (Exception e){

                    }
                }

            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) {
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
