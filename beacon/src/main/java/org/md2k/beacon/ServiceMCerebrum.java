package org.md2k.beacon;

import android.content.Intent;
import android.os.Bundle;

import org.md2k.beacon.configuration.Configuration;
import org.md2k.mcerebrum.commons.app_info.AppInfo;
import org.md2k.mcerebrum.core.access.AbstractServiceMCerebrum;

public class ServiceMCerebrum extends AbstractServiceMCerebrum {

    @Override
    protected boolean hasClear() {
        return false;
    }

    @Override
    public void initialize(Bundle bundle) {
        Intent intent=new Intent(this, ActivityPermission.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void launch(Bundle bundle) {
        Intent intent=new Intent(this, ActivityMain.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void startBackground(Bundle bundle) {
        if(!isRunning()) {
            startService(new Intent(getApplicationContext(), ServiceBeacon.class));
        }
    }

    @Override
    public void stopBackground(Bundle bundle) {
        if(isRunning()) {
            stopService(new Intent(getApplicationContext(), ServiceBeacon.class));
        }
    }

    @Override
    public void report(Bundle bundle) {
    }

    @Override
    public void clear(Bundle bundle) {

    }

    @Override
    public boolean hasReport() {
        return false;
    }

    @Override
    public boolean isRunInBackground() {
        return true;
    }

    @Override
    public long getRunningTime() {
        return AppInfo.serviceRunningTime(this, ServiceBeacon.class.getName());
    }

    @Override
    public boolean isRunning() {
        return AppInfo.isServiceRunning(this, ServiceBeacon.class.getName());
    }

    @Override
    public boolean isConfigured() {
        return Configuration.isConfigured();
    }
    @Override
    public boolean isEqualDefault() {
        return Configuration.isEqualDefault();
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean hasInitialize() {
        return true;
    }

    @Override
    public void configure(Bundle bundle) {
        Intent intent = new Intent(this, ActivityMain.class);
        if(bundle==null) bundle=new Bundle();
        bundle.putInt(ActivityMain.OPERATION,ActivityMain.OPERATION_SETTINGS);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

}
