package net.onelitefeather.menura.core.asset;

import net.onelitefeather.menura.core.problem.ProblemException;

/**
 * The split of an upload into parts. Every part but the last has the configured size; the last holds the rest.
 * Part numbers start at 1, as S3 numbers them. Plans above the S3 limit of 10 000 parts are refused.
 */
public record PartPlan(long sizeBytes, long partSizeBytes, int partCount) {

    static final long MIN_PART_SIZE = 5L * 1024 * 1024;
    /** Principle 15: every request body stays below the Cloudflare limit of 100 MB. */
    public static final long MAX_PART_SIZE = 95L * 1024 * 1024;
    static final int MAX_PARTS = 10_000;

    public static PartPlan of(long sizeBytes, long partSizeBytes) {
        if (partSizeBytes < 1 || partSizeBytes > MAX_PART_SIZE) {
            throw new IllegalArgumentException("part size must be between 1 byte and 95 MiB");
        }
        long count = (sizeBytes + partSizeBytes - 1) / partSizeBytes;
        if (count > MAX_PARTS) {
            throw new ProblemException(413, "Payload Too Large",
                    "The file needs more than " + MAX_PARTS + " upload parts of " + partSizeBytes + " bytes.");
        }
        return new PartPlan(sizeBytes, partSizeBytes, (int) count);
    }

    /** The size in bytes of one part, numbered from 1. */
    public long sizeOf(int partNumber) {
        requireNumber(partNumber);
        if (partNumber < partCount) {
            return partSizeBytes;
        }
        return sizeBytes - partSizeBytes * (partCount - 1L);
    }

    /** The byte offset at which one part starts, numbered from 1. */
    public long offsetOf(int partNumber) {
        requireNumber(partNumber);
        return partSizeBytes * (partNumber - 1L);
    }

    private void requireNumber(int partNumber) {
        if (partNumber < 1 || partNumber > partCount) {
            throw new IllegalArgumentException("part number out of range: " + partNumber);
        }
    }
}
