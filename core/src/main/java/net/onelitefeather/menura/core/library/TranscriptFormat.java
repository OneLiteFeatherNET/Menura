package net.onelitefeather.menura.core.library;

/** The subtitle formats a transcript may be uploaded in. JSON with speakers is read by the server. */
public enum TranscriptFormat {
    WEBVTT,
    SRT;

    /** A file that starts with the WEBVTT signature is WebVTT; anything else is read as SRT. */
    public static TranscriptFormat detect(String content) {
        String text = content.startsWith("﻿") ? content.substring(1) : content;
        return text.startsWith("WEBVTT") ? WEBVTT : SRT;
    }
}
