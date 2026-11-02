package net.onelitefeather.menura.server.support;

import java.security.SecureRandom;

/**
 * A repeatable stand-in for the secure random source in unit tests. Every call yields different bytes, so the
 * identifiers and secrets drawn from it are distinct, and the same test always draws the same sequence.
 */
public final class DeterministicRandom extends SecureRandom {

    private long calls;

    @Override
    public void nextBytes(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (calls * 31 + i * 7 + 1);
        }
        calls++;
    }
}
