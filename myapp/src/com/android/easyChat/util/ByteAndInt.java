
package com.android.easyChat.util;

public class ByteAndInt {

    public static byte[] int2ByteArray(int value) {
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            int offset = (b.length - 1 - i) * 8;
            b[i] = (byte) ((value >>> offset) & 0xFF);
        }
        return b;
    }

    public static final int byteArray2Int(byte[] b) {
        return (b[0] << 24) + ((b[1] & 0xFF) << 16) + ((b[2] & 0xFF) << 8)
                + (b[3] & 0xFF);
    }

    public static byte[] longToByteArray(long a) {
        byte[] bArray = new byte[8];
        for (int i = 0; i < bArray.length; i++) {
            bArray[i] = new Long(a & 0XFF).byteValue();
            a >>= 8;
        }
        return bArray;
    }

    public static long byteArrayToLong(byte[] bArray) {
        long a = 0;
        for (int i = 0; i < bArray.length; i++) {
            a += (long) ((bArray[i] & 0XFF) << (8 * i));
        }
        return a;
    }
}
