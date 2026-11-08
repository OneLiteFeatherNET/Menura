package net.onelitefeather.menura.server.library;

/** The states of an upload session. The value is the one stored in the database and sent in the API. */
public enum UploadState {
    FLAGGED("flagged"),
    UPLOADING("uploading"),
    AVAILABLE("available"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String value;

    UploadState(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static UploadState parse(String value) {
        for (UploadState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("unknown upload state: " + value);
    }
}
