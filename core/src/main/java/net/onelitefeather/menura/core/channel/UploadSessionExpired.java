package net.onelitefeather.menura.core.channel;



/**
 * The resumable upload session of YouTube no longer exists (HTTP 404 on the session location). The bytes are not on the
 * platform, so the caller starts a new upload from the first byte. Documented: the resumable upload guide, checked
 * 2026-11-08.
 */
public final class UploadSessionExpired extends PlatformException {

    public UploadSessionExpired() {
        super(Kind.REJECTED, "The YouTube upload session has expired; the upload starts again.", null);
    }
}
