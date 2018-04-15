package com.damn.uglass.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;

import damn.com.shared.Constants;


public abstract class BluetoothHost {

    private static final int STATE_CONNECTION_STARTED = 0;
    private static final int STATE_CONNECTION_LOST = 1;
    private static final int STATE_WAITING_FOR_CONNECT = 2;
    private static final int MSG_DATA_RECEIVED = 3;

    private static final String NAME = "UGlass";
    private static final String TAG = "GlassHost";

    private BluetoothAdapter mBT;
    private Handler mHandler;

    private volatile WorkerThread mWorkerThread;
    private volatile boolean mActive;

    // abstract to avoid creating Listener interface, just override these in-place
    public abstract void onWaiting();
    public abstract void onConnectionStarted(String device);
    public abstract void onDataReceived(String data);
    public abstract void onConnectionLost(@Nullable String error);

    public BluetoothHost(@NonNull Context context) {
        mBT = BluetoothAdapter.getDefaultAdapter();
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case STATE_CONNECTION_STARTED:
                        final String device = msg.obj.toString();
                        Log.d(TAG, "STATE_CONNECTION_STARTED: " + device);
                        onConnectionStarted(device);
                        break;
                    case MSG_DATA_RECEIVED:
                        final String data = msg.obj.toString();
                        Log.d(TAG, "MSG_DATA_RECEIVED: " + data);
                        onDataReceived(data);
                        break;
                    case STATE_CONNECTION_LOST:
                        final String error = null !=  msg.obj ? msg.obj.toString() : null;
                        Log.d(TAG, "STATE_CONNECTION_LOST: " + (null != error ? error : " no errors"));
                        onConnectionLost(error);
                        break;
                    case STATE_WAITING_FOR_CONNECT:
                        onWaiting();
                        break;
                    default:
                        break;
                }
            }
        };

    }

    public void start() {
        mActive = true;
        mWorkerThread = new WorkerThread();
        mWorkerThread.start();
    }

    public void stop() {
        mActive = false;
        if(null != mWorkerThread) {
            try {
                mWorkerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mWorkerThread = null;
        }
    }

    public boolean isStarted() {
        return mActive;
    }

    private class WorkerThread extends Thread {

        public void run() {
            String error = null;
            while (mActive) {
                BluetoothServerSocket serverSocket;
                try {
                    serverSocket = mBT.listenUsingInsecureRfcommWithServiceRecord(NAME, Constants.uuid);
                } catch (IOException e) {
                    error = e.getLocalizedMessage();
                    break;
                }

                Message msg = mHandler.obtainMessage(STATE_WAITING_FOR_CONNECT);
                mHandler.sendMessage(msg);

                BluetoothSocket socket;
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    error = e.getLocalizedMessage();
                    break;
                }

                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                }

                if (socket == null)
                    break;

                try {
                    runLoop(socket);
                } catch (Exception e) {
                    error = e.getLocalizedMessage();
                    e.printStackTrace();
                }
            }
            mWorkerThread = null;
            mActive = false;
            connectionLost(error);
        }

        private void runLoop(BluetoothSocket socket) throws IOException {
            Log.d(TAG, "create ConnectedDevice");

            InputStream mmInStream = socket.getInputStream();
            OutputStream mmOutStream = socket.getOutputStream();

            final Message deviceMsg = mHandler.obtainMessage(STATE_CONNECTION_STARTED, socket.getRemoteDevice().getName());
            mHandler.sendMessage(deviceMsg);

            Scanner scanner = new Scanner(mmInStream);
            // Keep listening to the InputStream while connected
            while (mActive) {
                while (mmInStream.available() > 0 && scanner.hasNext()) {
                    String line = scanner.nextLine();
                    Message msg = mHandler.obtainMessage(MSG_DATA_RECEIVED, line);
                    // I should try to parse it there to check, but meh
                    mHandler.sendMessage(msg);
                    mmOutStream.write("OK\n".getBytes());
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void connectionLost(@Nullable String error) {
        Message msg = mHandler.obtainMessage(STATE_CONNECTION_LOST, error);
        mHandler.sendMessage(msg);
    }


}