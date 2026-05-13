package br.com.cezarcirqueira.mirror.app.application.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public final class TokenEquals {

    private TokenEquals() {}

    public static boolean timingSafeEqual(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        return MessageDigest.isEqual(a, b);
    }

    public static boolean timingSafeEqualBytes(byte[] expected, byte[] actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return Arrays.equals(expected, actual);
    }
}
