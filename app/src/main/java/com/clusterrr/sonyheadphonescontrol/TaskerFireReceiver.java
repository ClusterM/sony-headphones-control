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

import android.app.ActivityManager;
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

import androidx.annotation.NonNull;

import com.twofortyfouram.spackle.bundle.BundleScrubber;

import net.dinglisch.android.tasker.TaskerPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public final class TaskerFireReceiver extends BroadcastReceiver {
    public static final String TAG = "SonyHeadphonesControl";
    public static final UUID[] HEADSET_UUIDS = new UUID[]{
            UUID.fromString("96cc203e-5068-46ad-b32d-e316f5e069ba"),
            UUID.fromString("ba69e0f5-16e3-2db3-ad46-68503e20cc96")
    };
    public static final String ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"; //$NON-NLS-1$
    public static final String EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"; //$NON-NLS-1$
    public static final String EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"; //$NON-NLS-1$
    public static final String EXTRA_STRING_MODE = "mode";
    public static final String EXTRA_STRING_VOLUME = "volume";
    public static final String EXTRA_STRING_VOICE = "voice";
    public static final int RECV_TIMEOUT = 200;
    public static final byte MAGIC_PACKET_START = 0x3e;
    public static final byte MAGIC_PACKET_END = 0x3c;
    public static final byte COMMAND_ACK = 0x00;
    public static final byte COMMAND_SET_MODE = 0x08;
    public static final byte MODE_NOISE_CANCELLING = 2;
    public static final byte MODE_WIND_CANCELLING = 1;
    public static final byte MODE_AMBIENT_SOUND = 0;
    public static final byte KEY_OFF = 0;
    public static final byte KEY_NOISE_CANCELLING = 1;
    public static final byte KEY_WIND_CANCELLING = 2;
    public static final byte KEY_AMBIENT_SOUND = 3;

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

    public static void execute(Context context, Intent intent, int mode, int volume, boolean voiceOptimized) {
        boolean enabled = false;
        byte noiseCancelling;
        switch (mode) {
            case KEY_NOISE_CANCELLING:
                enabled = true;
                noiseCancelling = MODE_NOISE_CANCELLING;
                volume = 0;
                voiceOptimized = false;
                break;
            case KEY_WIND_CANCELLING:
                enabled = true;
                noiseCancelling = MODE_WIND_CANCELLING;
                volume = 0;
                voiceOptimized = false;
                break;
            case KEY_AMBIENT_SOUND:
                enabled = true;
                noiseCancelling = MODE_AMBIENT_SOUND;
                break;
            default:
                enabled = false;
                noiseCancelling = 0;
                volume = 0;
                voiceOptimized = false;
                break;
        }

        try {
            if (setAmbientSound(context, enabled, noiseCancelling, volume, voiceOptimized)) {
                if (intent != null)
                    TaskerPlugin.Setting.signalFinish(context, intent, TaskerPlugin.Setting.RESULT_CODE_OK, null);
                else
                    Toast.makeText(context, context.getString(R.string.ok), Toast.LENGTH_SHORT).show();
            } else {
                final String message = context.getString(R.string.headset_not_found);
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
            final String message = context.getString(R.string.io_error) + ": " + e.getMessage() +
                    "\r\n" + context.getString(R.string.io_error_hint);
            if (intent != null) {
                Bundle vars = new Bundle();
                vars.putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, message);
                TaskerPlugin.Setting.signalFinish(context, intent, TaskerPlugin.Setting.RESULT_CODE_FAILED, vars);
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            final String message = context.getString(R.string.interrupted_error);
            if (intent != null) {
                Bundle vars = new Bundle();
                vars.putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, message);
                TaskerPlugin.Setting.signalFinish(context, intent, TaskerPlugin.Setting.RESULT_CODE_FAILED, vars);
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        } catch (TimeoutException e) {
            e.printStackTrace();
            final String message = context.getString(R.string.timeout_error);
            if (intent != null) {
                Bundle vars = new Bundle();
                vars.putString(TaskerPlugin.Setting.VARNAME_ERROR_MESSAGE, message);
                TaskerPlugin.Setting.signalFinish(context, intent, TaskerPlugin.Setting.RESULT_CODE_FAILED, vars);
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        }
    }

    static boolean setAmbientSound(Context context, boolean enabled, byte noiseCancelling, int volume, boolean voice)
            throws IOException, InterruptedException, TimeoutException {
        return findByUUIDAndSend(context, HEADSET_UUIDS, COMMAND_SET_MODE,
                new byte[]{0x68, 0x02,
                (byte) (enabled ? 0x10 : 0x00), 0x02, (byte) (noiseCancelling), 0x01,
                (byte) (voice ? 1 : 0), (byte) volume});
    }

    static boolean findByUUIDAndSend(Context context, UUID[] uuids, byte command, byte[] data)
            throws IOException, InterruptedException, TimeoutException {
        BluetoothDevice headset = null;
        UUID uuid = null;

        ActivityManager activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        activityManager.killBackgroundProcesses("com.sony.songpal.mdr");

        BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bluetoothManager.getAdapter();
        Set<BluetoothDevice> devices = adapter.getBondedDevices();
        for (BluetoothDevice device : devices) {
            if (!isDeviceConnected(device)) continue;
            ParcelUuid[] DeviceUUIDs = device.getUuids();
            if (uuids == null) continue;
            for (ParcelUuid deviceUUID : DeviceUUIDs) {
                for (UUID allowedUUIDs : uuids) {
                    if (deviceUUID.toString().equals(allowedUUIDs.toString())) {
                        headset = device;
                        uuid = allowedUUIDs;
                        break;
                    }
                }
                if (headset != null) break;
            }
            if (headset != null) break;
        }
        if (headset == null) {
            Log.e(TAG, "Headset not found");
            return false;
        } else {
            Log.i(TAG, "Headset found: " + headset.getAddress() + " " + headset.getName());
        }

        BluetoothSocket socket = headset.createRfcommSocketToServiceRecord(uuid);
        try {
            socket.connect();
            Log.d(TAG, "BluetoothSocket connected: " + socket.isConnected());
            OutputStream o = socket.getOutputStream();
            InputStream i = socket.getInputStream();
            HeadsetPacket r;
            sendPacket(o, command, false, data);
            r = recvPacket(i, RECV_TIMEOUT);
            if ((r.getCommand() != COMMAND_ACK) ||
                    (r.getData().length < 1) ||
                    (r.getData()[0] != 2))
                throw new IOException("Invalid answer");
        } finally {
            socket.close();
        }
        return true;
    }

    static void sendPacket(@NonNull OutputStream o, @NonNull HeadsetPacket packet) throws IOException, InterruptedException {
        sendPacket(o, packet.getCommand(), packet.getToggle(), packet.getData());
    }

    static void sendPacket(@NonNull OutputStream o, @NonNull byte command, @NonNull boolean toggle, @NonNull byte[] data)
            throws IOException, InterruptedException {
        byte[] packet = new byte[data.length + 9];
        packet[0] = MAGIC_PACKET_START;
        packet[1] = (byte) (data.length + 4);
        packet[2] = toggle ? (byte) 1 : (byte) 0;
        packet[3] = packet[4] = packet[5] = 0;
        packet[6] = command;
        for (int j = 0; j < data.length; j++) {
            packet[j + 7] = data[j];
        }
        byte crc = 0;
        for (int j = 1; j < packet.length - 2; j++) {
            crc += packet[j];
        }
        packet[packet.length - 2] = (byte)crc;
        packet[packet.length - 1] = MAGIC_PACKET_END;

        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < packet.length; j++) {
            sb.append(String.format(" %02x", packet[j]));
        }
        Log.d(TAG, "write:" + sb);
        o.write(packet);
    }

    public static HeadsetPacket recvPacket(@NonNull InputStream i, @NonNull int timeout) throws TimeoutException, IOException {
        byte[] recvBuffer = new byte[256];
        int received = 0;
        int packetLength = -1;
        int dataLength = -1;
        int time = 0;
        while ((time < timeout) && ((packetLength < 0) || (received < packetLength))) {
            if (i.available() > 0) {
                int r = i.read(recvBuffer, received, recvBuffer.length - received);
                received += r;
                time = 0;
                if ((received >= 1) && (recvBuffer[0] != MAGIC_PACKET_START))
                    throw new IOException("Invalid start magic");
                if (received >= 2) {
                    dataLength = recvBuffer[1];
                    packetLength = recvBuffer[1] + 8;
                }
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
            time++;
        }
        if ((packetLength < 0) || (received < packetLength))
            throw new TimeoutException();

        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < packetLength; j++) {
            sb.append(String.format(" %02x", recvBuffer[j]));
        }
        Log.d(TAG, "read:" + sb);

        if (recvBuffer[packetLength-1] != MAGIC_PACKET_END)
            throw new IOException("Invalid end magic");
        byte crc = 0;
        for (int j = 1; j < packetLength - 2; j++) {
            crc += recvBuffer[j];
        }
        if (crc != recvBuffer[packetLength - 2])
            throw new IOException("Invalid CRC");
        boolean toggle = recvBuffer[2] != 0;
        byte command = recvBuffer[6];
        byte[] data = new byte[dataLength];
        System.arraycopy(recvBuffer, 7, data, 0, dataLength);
        return new HeadsetPacket(command, toggle, data);
    }

    public static boolean isDeviceConnected(BluetoothDevice device) {
        try {
            Method m = device.getClass().getMethod("isConnected", (Class[]) null);
            boolean connected = (boolean) m.invoke(device, (Object[]) null);
            return connected;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}