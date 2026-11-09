package net.onelitefeather.menura.core.publish;

import java.io.IOException;

/**
 * Reads the bytes of a stored asset (change add-youtube-integration). The publication job streams a video to the platform
 * through this port one chunk at a time, so the whole file is never held in memory; the server never reads media bytes.
 */
public interface MediaStorage {

    /** Reads {@code length} bytes starting at {@code offset} of the stored object. */
    byte[] read(String storageKey, long offset, int length) throws IOException;
}
