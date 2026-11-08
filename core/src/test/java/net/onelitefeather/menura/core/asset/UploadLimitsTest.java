package net.onelitefeather.menura.core.asset;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadLimitsTest {

    private static final long GIB = 1024L * 1024 * 1024;

    private final UploadLimits limits = new UploadLimits(UploadLimits.DEFAULT_PART_SIZE, UploadLimits.DEFAULT_RAW_MATERIAL_MAX_BYTES);

    @Test
    void defaultRawMaterialLimitIsFiftyGigabytes() {
        assertEquals(50 * GIB, UploadLimits.DEFAULT_RAW_MATERIAL_MAX_BYTES);
    }

    @Test
    void rawRecordingAboveTheLimitIsRefusedNamingTheLimit() {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> limits.requireWithinLimit(AssetKind.RAW_MATERIAL, 60 * GIB));

        assertEquals(413, refused.status());
        assertTrue(refused.detail().contains(String.valueOf(50 * GIB)), "the refusal must name the limit: " + refused.detail());
    }

    @Test
    void rawRecordingAtTheLimitIsAccepted() {
        limits.requireWithinLimit(AssetKind.RAW_MATERIAL, 50 * GIB);
    }

    @Test
    void finishedAssetIsNotLimitedByTheRawMaterialLimit() {
        limits.requireWithinLimit(AssetKind.VIDEO, 60 * GIB);
    }

    @Test
    void partSizeAboveTheCloudflareLimitIsRefusedAtConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadLimits(PartPlan.MAX_PART_SIZE + 1, GIB));
    }
}
