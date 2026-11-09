package net.onelitefeather.menura.core.library;

/**
 * The transcript formats a file may be uploaded in. WebVTT and SRT are subtitle files; JSON is an array of segments
 * that may name a speaker for each segment. The format is detected from the content.
 */
public enum TranscriptFormat {
    WEBVTT,
    SRT,
    JSON;

    /** A file that starts with an array is JSON; one that starts with the WEBVTT signature is WebVTT; anything else is SRT. */
    public static TranscriptFormat detect(String content) {
        String text = content.startsWith("﻿") ? content.substring(1) : content;
        if (text.stripLeading().startsWith("[")) {
            return JSON;
        }
        return text.startsWith("WEBVTT") ? WEBVTT : SRT;
    }
}
