package net.onelitefeather.menura.core.library;

/**
 * One timed piece of speech of a raw item. The speaker is set only for segments of a JSON transcript that names one;
 * WebVTT and SRT segments carry none.
 */
public record TranscriptSegment(long startMillis, long endMillis, String speaker, String text) {
}
