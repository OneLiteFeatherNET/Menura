package net.onelitefeather.menura.core.channel.support;

import java.security.SecureRandom;
import java.util.Random;

/** A seeded random source, so generated states and ids are the same on every run. */
public final class DeterministicRandom extends SecureRandom {

    private static final long serialVersionUID = 1L;

    private final transient Random source;

    public DeterministicRandom(long seed) {
        this.source = new Random(seed);
    }

    @Override
    public void nextBytes(byte[] bytes) {
        source.nextBytes(bytes);
    }

    @Override
    public int nextInt(int bound) {
        return source.nextInt(bound);
    }
}
