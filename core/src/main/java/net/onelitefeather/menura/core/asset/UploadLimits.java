package net.onelitefeather.menura.core.asset;

import net.onelitefeather.menura.core.problem.ProblemException;

/**
 * The configured limits of uploads: the part size (at most 95 MB, principle 15) and the size of raw material, which
 * defaults to 50 GiB and is set by environment variable.
 */
public record UploadLimits(long partSizeBytes, long rawMaterialMaxBytes) {

    public static final long DEFAULT_PART_SIZE = PartPlan.MAX_PART_SIZE;
    public static final long DEFAULT_RAW_MATERIAL_MAX_BYTES = 50L * 1024 * 1024 * 1024;

    public UploadLimits {
        if (partSizeBytes < PartPlan.MIN_PART_SIZE || partSizeBytes > PartPlan.MAX_PART_SIZE) {
            throw new IllegalArgumentException("the part size must be between 5 MiB and 95 MiB");
        }
        if (rawMaterialMaxBytes < 1) {
            throw new IllegalArgumentException("the raw material size limit must be positive");
        }
    }

    /** Refuses a raw recording above the limit, naming the limit; finished assets have no size limit here. */
    public void requireWithinLimit(AssetKind kind, long sizeBytes) {
        if (kind == AssetKind.RAW_MATERIAL && sizeBytes > rawMaterialMaxBytes) {
            throw new ProblemException(413, "Payload Too Large",
                    "The raw material exceeds the configured limit of " + rawMaterialMaxBytes + " bytes.");
        }
    }
}
