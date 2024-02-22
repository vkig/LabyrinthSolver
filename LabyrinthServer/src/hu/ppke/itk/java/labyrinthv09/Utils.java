package hu.ppke.itk.java.labyrinthv09;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.Random;
import hu.ppke.itk.java.labyrinthv09.LabyrinthProtos.ViewElement;

public final class Utils {
    static byte[] intToBytes(int n) {
        return new byte[] {
            (byte)(n >>> 24),
            (byte)(n >>> 16),
            (byte)(n >>>  8),
            (byte)(n)
        };
    }

    static int bytesToInt(byte[] bytes) {
        if (bytes.length != 4)
            throw new RuntimeException("Bytes to int conversion requires 4 bytes.");

        return ((bytes[0] & 0xFF) << 24)
             | ((bytes[1] & 0xFF) << 16)
             | ((bytes[2] & 0xFF) << 8)
             | ((bytes[3] & 0xFF));
    }

    public static long stringToSeed(String s) {
        if (s == null) {
            return 0;
        }
        long hash = 0;
        for (char c : s.toCharArray()) {
            hash = 31L*hash + c;
        }
        return hash;
    }

    public static String randomString(int length) {
        int leftLimit = 97; // 'a'
        int rightLimit = 122; // 'z'
        Random random = new Random();

        return random
            .ints(leftLimit, rightLimit + 1)
            .limit(length)
            .collect(
                StringBuilder::new,
                StringBuilder::appendCodePoint,
                StringBuilder::append)
            .toString();
    }
}
