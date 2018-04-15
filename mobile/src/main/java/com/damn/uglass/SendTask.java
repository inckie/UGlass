package com.damn.uglass;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Scanner;
import java.util.Set;

import damn.com.shared.Constants;
import damn.com.shared.TokenMessage;

/**
 * Very dirty token sender
 */
// todo: add at least a picker for device name
// todo: handle BT state (on/off) in activity, too
public class SendTask extends AsyncTask<String, Void, String> {

    // part of the BT name, should be picked from list
    private static final String GLASS_BT_NAME_MARKER = "Glass";

    private final WeakReference<Activity> mActivity;

    // we should scan pairedDevices outside and just pass BluetoothDevice
    private SendTask(@NonNull Activity activity) {
        mActivity = new WeakReference<>(activity);
    }

    @Nullable
    @Override
    protected String doInBackground(String... msgs) {
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> pairedDevices = bt.getBondedDevices();
            if(null == pairedDevices)
                return null;
            for (BluetoothDevice device : pairedDevices) {
                final String deviceName = device.getName();
                if (deviceName.contains(GLASS_BT_NAME_MARKER))
                    return send(device, msgs[0]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String send(BluetoothDevice device, String msg) throws IOException {
        // we use EOL as terminator
        if (!msg.endsWith("\n"))
            msg = msg + '\n';
        try (BluetoothSocket socket = device.createInsecureRfcommSocketToServiceRecord(Constants.uuid)) {
            socket.connect();
            try (OutputStream outputStream = socket.getOutputStream()) {
                outputStream.write(msg.getBytes());
                try (final InputStream inputStream = socket.getInputStream()) {
                    Scanner scanner = new Scanner(inputStream);
                    return scanner.nextLine();
                }
            }
        }
    }

    @Override
    protected void onPostExecute(@Nullable String result) {
        super.onPostExecute(result);
        final Activity activity = mActivity.get();
        if(null == activity || activity.isFinishing())
            return;
        // not really cool to have UI stuff there
        if (null == result)
            Toast.makeText(activity, R.string.msg_send_failed, Toast.LENGTH_LONG).show();
        else
            Toast.makeText(activity, result, Toast.LENGTH_LONG).show();
    }

    public static void send(@NonNull Activity activity, @NonNull TokenMessage token) {
        final String json = token.toJson();
        if(null != json)
            new SendTask(activity).execute(json);
        else
            Toast.makeText(activity, R.string.msg_send_failed, Toast.LENGTH_LONG).show();
    }
}
