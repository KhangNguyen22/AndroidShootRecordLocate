package comp5216.sydney.edu.au.mediarecordingapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.widget.Toast;


public class NetworkReceiver extends BroadcastReceiver {
    MainActivity activity;

    public NetworkReceiver(MainActivity activity) {
        this.activity = activity;
    }

    // Acknowledgement: This code was taken from the following developer documentation (https://developer.android.com/training/basics/network-ops/managing#respond-changes) and modified for personal use.
    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager conn = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = conn.getActiveNetworkInfo();

        if (networkInfo != null
                && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            Toast.makeText(context, "Wifi connected", Toast.LENGTH_LONG).show();
            activity.setWifiConnected(true);
            activity.askForBackup();
        } else if (networkInfo != null) { // Mobile is available, but wifi is not
            Toast.makeText(context, "Wifi failed", Toast.LENGTH_LONG).show();
            activity.setWifiConnected(false);
            if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                activity.setMobileNetworkConnected(true);
            }
        } else {
            activity.setWifiConnected(false);
            activity.setMobileNetworkConnected(false);
            Toast.makeText(context, "Wifi and network disconnected!", Toast.LENGTH_LONG).show();
        }

    }
}

