package com.constantin.wilson.FPV_VR;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.widget.Toast;

public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        String fileNameVideoSource=sharedPreferences.getString("fileNameVideoSource","rpi960mal810.h264");
        String fileName=sharedPreferences.getString("fileName", "mGroundRecording.h264");
        if(fileNameVideoSource.equals(fileName)){
            Toast.makeText(this,"You mustn't select same file for video source and ground recording",Toast.LENGTH_LONG).show();
            SharedPreferences.Editor editor=sharedPreferences.edit();
            boolean different=false;
            while(!different){
                fileName=("X"+fileName);
                if( ! fileNameVideoSource.equals(fileName)){
                    different=true;
                }
            }
            editor.putString("fileName",fileName);
            editor.commit();
        }
    }
}
