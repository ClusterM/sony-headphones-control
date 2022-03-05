package com.clusterrr.sonyheadphonescontrol;

public class HeadsetPacket {
    private byte command;
    private boolean toggle;
    private byte[] data;

    public HeadsetPacket(byte command, boolean toggle, byte[] data){
        this.command = command;
        this.toggle = toggle;
        this.data = new byte[data.length];
        System.arraycopy(data, 0, this.data,0, data.length);
    }

    public byte getCommand(){
        return command;
    }

    public boolean getToggle() {
        return toggle;
    }

    public byte[] getData() {
        return data;
    }
}
