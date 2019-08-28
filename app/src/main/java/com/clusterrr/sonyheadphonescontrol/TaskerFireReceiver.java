package com.clusterrr.sonyheadphonescontrol;

/*
 * Copyright 2013 two forty four a.m. LLC <http://www.twofortyfouram.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import com.twofortyfouram.spackle.bundle.BundleScrubber;

import net.dinglisch.android.tasker.TaskerPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public final class TaskerFireReceiver extends BroadcastReceiver {
    public static final String TAG = "SonyHeadphonesControl";
    public static final UUID uuid = UUID.fromString("96cc203e-5068-46ad-b32d-e316f5e069ba");
    public static final String ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"; //$NON-NLS-1$
    public static final String EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"; //$NON-NLS-1$
    public static final String EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"; //$NON-NLS-1$
    public static final String EXTRA_STRING_MODE = "mode";
    public static final String EXTRA_STRING_VOLUME = "volume";
    public static final String EXTRA_STRING_VOICE = "voice";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (!ACTION_FIRE_SETTING.equals(intent.getAction()))
            return;

        final Bundle bundle = intent.getBundleExtra(EXTRA_BUNDLE);
        BundleScrubber.scrub(bundle);

        boolean voice = false;
        int volume = 0;

        int mode = bundle.getInt(EXTRA_STRING_MODE, 0);
        volume = bundle.getInt(EXTRA_STRING_VOLUME, 20);
        voice = bundle.getBoolean(EXTRA_STRING_VOICE, false);

        execute(context, intent, mode, volume, voice);
    }

    public static void execute(Context context, Intent intent, int mode, int volume, boolean voice) {

        boolean enabled = false;
        int noiseCancelling = 0;
        switch (mode) {
            case 0:
                enabled = false;
                break;
            case 1:
                enabled = true;
                noiseCancelling = 2;
                break;
            case 2:
                enabled = true;
                noiseCancelling = 1;
                break;
            case 3:
                enabled = true;
                noiseCancelling = 0;
        }

        try {
            if (setAmbientSound(context, enabled, noiseCancelling, volume, voice)) {
                if (intent != null)
                    TaskerPlugin.Setting.signalFinish(context, intent, TaskerPlugin.Setting.RESULT_CODE_OK, null);
                else
                    Toast.makeText(context, "OK", Toast.LENGTH_SHORT).show();
            } else {
                final String message = "Sony Headset is not found";
                if (intent != null) {
                    Bundle vars = new Bundle();
                    vars.putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, message);
                    TaskerPlugin.Setting.signalFinish(context, intent, TaskerPlugin.Setting.RESULT_CODE_FAILED, vars);
                } else {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            final String message = "IO error (another application using a headset?)";
            if (intent != null) {
                Bundle vars = new Bundle();
                vars.putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, message);
                TaskerPlugin.Setting.signalFinish(context, intent, TaskerPlugin.Setting.RESULT_CODE_FAILED, vars);
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static boolean setAmbientSound(Context context, boolean enabled, int noiseCancelling, int volume, boolean voice) throws IOException, InterruptedException {
        return sendData(context, new byte[]{0x00, 0x00, 0x00, 0x08, 0x68, 0x02, (byte) (enabled ? 0x10 : 0x00), 0x02, (byte) (noiseCancelling), 0x01, (byte) (voice ? 1 : 0), (byte) volume});
    }

    static boolean sendData(Context context, byte[] data) throws IOException, InterruptedException {
        BluetoothDevice headset = null;
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bluetoothManager.getAdapter();

        Set<BluetoothDevice> devices = adapter.getBondedDevices();
        for (BluetoothDevice device : devices) {
            ParcelUuid[] uuids = device.getUuids();
            for (ParcelUuid u : uuids) {
                if (u.toString().equals(uuid.toString())) {
                    headset = device;
                    break;
                }
            }
            if (headset != null)
                break;
        }
        if (headset == null) {
            Log.e(TAG, "Headset not found");
            return false;
        } else {
            Log.d(TAG, "Headset found: " + headset.getAddress() + " " + headset.getName());
        }

        BluetoothSocket socket = headset.createRfcommSocketToServiceRecord(uuid);
        try {
            Log.i(TAG, "Socket connected: " + socket.isConnected());
            socket.connect();
            Log.i(TAG, "Socket connected: " + socket.isConnected());

            byte[] packet = new byte[data.length + 2];
            packet[0] = 0x0c;
            packet[1] = 0;
            for (int j = 0; j < data.length; j++) {
                packet[j + 2] = data[j];
            }
            sendPacket(socket, packet);
            packet[1] = 1;
            sendPacket(socket, packet);

            return true;
        } finally {
            socket.close();
        }
    }

    static void sendPacket(BluetoothSocket socket, byte[] data) throws IOException, InterruptedException {
        OutputStream o = socket.getOutputStream();
        InputStream i = socket.getInputStream();
        byte[] packet = new byte[data.length + 3];
        packet[0] = 0x3e;
        packet[packet.length - 1] = 0x3c;
        byte crc = 0;
        for (int j = 0; j < data.length; j++) {
            crc += data[j];
            packet[j + 1] = data[j];
        }
        packet[packet.length - 2] = crc;
        o.write(packet);

        byte[] buffer = new byte[256];
        Date date = new Date();
        while (new Date().getTime() - date.getTime() < 200) {
            if (i.available() > 0) {
                int r = i.read(buffer);
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < r; j++) {
                    sb.append(String.format(" %02x", buffer[j]));
                }
                Log.i(TAG, "Read: " + r + " bytes:" + sb);
                break;
            }
            Thread.sleep(50);
        }
    }
}