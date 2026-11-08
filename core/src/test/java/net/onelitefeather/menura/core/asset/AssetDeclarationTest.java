package net.onelitefeather.menura.core.asset;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetDeclarationTest {

    private static final String CHECKSUM = "q9Zt3J0pZk1l8k5m2n4b6v8c0x2z4a6s8d0f2g4h6j8=-3";

    @Test
    void completeVideoDeclarationIsAccepted() {
        AssetDeclaration declaration = AssetDeclaration.declare("video", "mp4", 95, 1920, 1080,
                3_000_000_000L, CHECKSUM, null, null);

        assertEquals(AssetKind.VIDEO, declaration.kind());
        assertEquals(1920, declaration.width());
        assertEquals(95, declaration.durationSeconds());
    }

    @Test
    void imageDeclarationHasNoDuration() {
        AssetDeclaration declaration = AssetDeclaration.declare("image", "png", null, 1280, 720,
                2_000_000L, CHECKSUM, null, null);

        assertEquals(AssetKind.IMAGE, declaration.kind());
        assertNull(declaration.durationSeconds(), "an image carries no duration");
    }

    @Test
    void missingResolutionIsRefusedNamingTheField() {
        ProblemException refused = assertThrows(ProblemException.class, () -> AssetDeclaration.declare(
                "video", "mp4", 95, null, 1080, 3_000_000L, CHECKSUM, null, null));

        assertEquals(400, refused.status());
        assertTrue(refused.detail().contains("width"), "the refusal must name the missing field: " + refused.detail());
    }

    @Test
    void videoWithoutDurationIsRefusedNamingTheField() {
        ProblemException refused = assertThrows(ProblemException.class, () -> AssetDeclaration.declare(
                "video", "mp4", null, 1920, 1080, 3_000_000L, CHECKSUM, null, null));

        assertTrue(refused.detail().contains("durationSeconds"), refused.detail());
    }

    @Test
    void imageWithDurationIsRefused() {
        ProblemException refused = assertThrows(ProblemException.class, () -> AssetDeclaration.declare(
                "image", "png", 10, 1280, 720, 2_000_000L, CHECKSUM, null, null));

        assertTrue(refused.detail().contains("durationSeconds"), refused.detail());
    }

    @Test
    void rawMaterialNeedsStreamTitle() {
        ProblemException refused = assertThrows(ProblemException.class, () -> AssetDeclaration.declare(
                "raw_material", "mkv", 7200, 1920, 1080, 40_000_000_000L, CHECKSUM, null, "2026-11-01"));

        assertTrue(refused.detail().contains("streamTitle"), refused.detail());
    }

    @Test
    void rawMaterialCarriesStreamTitleAndDate() {
        AssetDeclaration declaration = AssetDeclaration.declare("raw_material", "mkv", 7200, 1920, 1080,
                40_000_000_000L, CHECKSUM, "Coop night", "2026-11-01");

        assertEquals(AssetKind.RAW_MATERIAL, declaration.kind());
        assertEquals("Coop night", declaration.streamTitle());
        assertEquals("2026-11-01", declaration.streamDate().toString());
    }

    @Test
    void streamTitleWithControlCharactersIsRefused() {
        ProblemException refused = assertThrows(ProblemException.class, () -> AssetDeclaration.declare(
                "raw_material", "mkv", 7200, 1920, 1080, 40_000_000_000L, CHECKSUM, "Coop\u0000night", "2026-11-01"));

        assertTrue(refused.detail().contains("streamTitle"), refused.detail());
    }

    @Test
    void streamTitleWithBidiOverrideIsRefused() {
        ProblemException refused = assertThrows(ProblemException.class, () -> AssetDeclaration.declare(
                "raw_material", "mkv", 7200, 1920, 1080, 40_000_000_000L, CHECKSUM, "Coop‮night", "2026-11-01"));

        assertTrue(refused.detail().contains("streamTitle"), refused.detail());
    }

    @Test
    void unknownKindIsRefused() {
        ProblemException refused = assertThrows(ProblemException.class, () -> AssetDeclaration.declare(
                "audio", "mp3", 10, 1, 1, 10L, CHECKSUM, null, null));

        assertTrue(refused.detail().contains("kind"), refused.detail());
    }

    @Test
    void unlistedFormatIsRefused() {
        ProblemException refused = assertThrows(ProblemException.class, () -> AssetDeclaration.declare(
                "video", "exe", 10, 1920, 1080, 10L, CHECKSUM, null, null));

        assertTrue(refused.detail().contains("format"), refused.detail());
    }

    @Test
    void zeroSizeIsRefused() {
        ProblemException refused = assertThrows(ProblemException.class, () -> AssetDeclaration.declare(
                "video", "mp4", 10, 1920, 1080, 0L, CHECKSUM, null, null));

        assertTrue(refused.detail().contains("sizeBytes"), refused.detail());
    }

    @Test
    void checksumWithoutPartCountIsRefused() {
        ProblemException refused = assertThrows(ProblemException.class, () -> AssetDeclaration.declare(
                "video", "mp4", 10, 1920, 1080, 10L, "q9Zt3J0pZk1l8k5m2n4b6v8c0x2z4a6s8d0f2g4h6j8=", null, null));

        assertTrue(refused.detail().contains("checksum"), refused.detail());
    }
}
