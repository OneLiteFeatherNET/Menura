package net.onelitefeather.menura.core.asset;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartPlanTest {

    private static final long GIB = 1024L * 1024 * 1024;

    @Test
    void twelveGigabyteFileYieldsPartsOfAtMost95MegabytesEach() {
        PartPlan plan = PartPlan.of(12 * GIB, PartPlan.MAX_PART_SIZE);

        for (int part = 1; part <= plan.partCount(); part++) {
            assertTrue(plan.sizeOf(part) <= PartPlan.MAX_PART_SIZE,
                    "part " + part + " must not exceed 95 MB but has " + plan.sizeOf(part) + " bytes");
        }
    }

    @Test
    void partsAddUpToTheFileSize() {
        PartPlan plan = PartPlan.of(12 * GIB + 17, PartPlan.MAX_PART_SIZE);

        long total = 0;
        for (int part = 1; part <= plan.partCount(); part++) {
            total += plan.sizeOf(part);
        }
        assertEquals(12 * GIB + 17, total, "the parts must cover the file exactly");
    }

    @Test
    void lastPartHoldsTheRemainder() {
        PartPlan plan = PartPlan.of(250, 100);

        assertEquals(3, plan.partCount());
        assertEquals(50, plan.sizeOf(3), "the last part holds what is left");
        assertEquals(200, plan.offsetOf(3));
    }

    @Test
    void partSizeAboveTheCloudflareLimitIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PartPlan.of(GIB, PartPlan.MAX_PART_SIZE + 1),
                "a part above 95 MB would cross the request-body limit of the proxy");
    }

    @Test
    void fileNeedingMoreThanTenThousandPartsIsRefused() {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> PartPlan.of(10_001L * PartPlan.MIN_PART_SIZE, PartPlan.MIN_PART_SIZE));

        assertEquals(413, refused.status(), "the S3 limit of parts is a size problem");
    }

    @Test
    void partNumberOutOfRangeIsRefused() {
        PartPlan plan = PartPlan.of(250, 100);

        assertThrows(IllegalArgumentException.class, () -> plan.sizeOf(4));
        assertThrows(IllegalArgumentException.class, () -> plan.sizeOf(0));
    }
}
