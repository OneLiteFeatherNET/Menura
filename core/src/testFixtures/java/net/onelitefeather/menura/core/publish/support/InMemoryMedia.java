package net.onelitefeather.menura.core.publish.support;

import net.onelitefeather.menura.core.publish.MediaStorage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Stored objects by key with their sizes; a read returns zero bytes of the requested length. No real storage is used. */
public final class InMemoryMedia implements MediaStorage {

    private final Map<String, Long> sizes = new HashMap<>();
    private final Map<String, Integer> reads = new HashMap<>();

    public void put(String key, long sizeBytes) {
        sizes.put(key, sizeBytes);
    }

    @Override
    public byte[] read(String storageKey, long offset, int length) throws IOException {
        Long size = sizes.get(storageKey);
        if (size == null || offset < 0 || offset + length > size) {
            throw new IOException("no such range in the test storage");
        }
        reads.merge(storageKey, 1, Integer::sum);
        return new byte[length];
    }

    /** How many reads were made of an object, for asserting that a retry does not stream the file again. */
    public int reads(String storageKey) {
        return reads.getOrDefault(storageKey, 0);
    }
}
