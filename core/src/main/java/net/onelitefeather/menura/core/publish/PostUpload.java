package net.onelitefeather.menura.core.publish;

/**
 * The resumable upload of a post on a platform that publishes in steps (change add-youtube-integration).
 *
 * @param session           the session location, sealed with the token key; null once the upload is complete or restarted
 * @param totalBytes        the size of the stored asset
 * @param confirmedBytes    the bytes the platform reported as held
 * @param appliedVisibility the visibility read back after upload, or null before the read-back
 * @param visibilityProblem the mismatch between the requested and the applied visibility, or null
 */
public record PostUpload(byte[] session, long totalBytes, long confirmedBytes, String appliedVisibility,
                         String visibilityProblem) {

    public PostUpload {
        session = session == null ? null : session.clone();
    }

    @Override
    public byte[] session() {
        return session == null ? null : session.clone();
    }

    /** The upload with its session location replaced; null discards it, so the next attempt starts a new upload. */
    public PostUpload withSession(byte[] location) {
        return new PostUpload(location, totalBytes, location == null ? 0 : confirmedBytes, appliedVisibility,
                visibilityProblem);
    }

    /** The upload with the bytes the platform confirmed. */
    public PostUpload confirmed(long bytes) {
        return new PostUpload(session, totalBytes, bytes, appliedVisibility, visibilityProblem);
    }

    /** The upload with the visibility read back and the mismatch that was found, if any. */
    public PostUpload applied(String visibility, String problem) {
        return new PostUpload(session, totalBytes, confirmedBytes, visibility, problem);
    }
}
