package net.onelitefeather.menura.core.asset;

import net.onelitefeather.menura.core.problem.ProblemException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;

/**
 * What an editor declares before an upload starts. Every field is checked here, so an invalid declaration creates
 * no session. Duration and resolution are declared by the editor's client; the server never probes the media.
 */
public record AssetDeclaration(AssetKind kind, String format, Integer durationSeconds, int width, int height,
                               long sizeBytes, Checksum checksum, String streamTitle, LocalDate streamDate) {

    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_DIMENSION = 16384;

    public AssetDeclaration {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(checksum, "checksum");
    }

    /**
     * Validates a declaration as the client sent it. A missing or invalid field is refused with a Problem Details
     * response that names the field.
     */
    public static AssetDeclaration declare(String kind, String format, Integer durationSeconds, Integer width,
                                           Integer height, Long sizeBytes, String checksum, String streamTitle,
                                           String streamDate) {
        AssetKind parsedKind = AssetKind.parse(kind);
        String normalisedFormat = requireFormat(parsedKind, format);
        Integer duration = requireDuration(parsedKind, durationSeconds);
        int parsedWidth = requireDimension("width", width);
        int parsedHeight = requireDimension("height", height);
        long size = requireSize(sizeBytes);
        Checksum parsedChecksum = Checksum.parse(checksum);
        String title = null;
        LocalDate date = null;
        if (parsedKind == AssetKind.RAW_MATERIAL) {
            title = requirePlainText("streamTitle", streamTitle);
            date = requireDate(streamDate);
        }
        return new AssetDeclaration(parsedKind, normalisedFormat, duration, parsedWidth, parsedHeight, size,
                parsedChecksum, title, date);
    }

    private static String requireFormat(AssetKind kind, String format) {
        String normalised = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if (!kind.formats().contains(normalised)) {
            throw invalid("The field 'format' is missing or not supported for this kind.");
        }
        return normalised;
    }

    private static Integer requireDuration(AssetKind kind, Integer durationSeconds) {
        if (!kind.hasDuration()) {
            if (durationSeconds != null) {
                throw invalid("The field 'durationSeconds' is only allowed for videos and raw material.");
            }
            return null;
        }
        if (durationSeconds == null || durationSeconds < 1) {
            throw invalid("The field 'durationSeconds' is required for videos and must be at least 1.");
        }
        return durationSeconds;
    }

    private static int requireDimension(String field, Integer value) {
        if (value == null || value < 1 || value > MAX_DIMENSION) {
            throw invalid("The field '" + field + "' is required and must be between 1 and " + MAX_DIMENSION + ".");
        }
        return value;
    }

    private static long requireSize(Long sizeBytes) {
        if (sizeBytes == null || sizeBytes < 1) {
            throw invalid("The field 'sizeBytes' is required and must be at least 1.");
        }
        return sizeBytes;
    }

    private static String requirePlainText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw invalid("The field '" + field + "' is required for raw material.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw invalid("The field '" + field + "' must be at most " + MAX_TITLE_LENGTH + " characters.");
        }
        trimmed.codePoints().forEach(code -> {
            int type = Character.getType(code);
            if (Character.isISOControl(code) || type == Character.FORMAT) {
                throw invalid("The field '" + field + "' must not contain control or format characters.");
            }
        });
        return trimmed;
    }

    private static LocalDate requireDate(String value) {
        if (value == null) {
            throw invalid("The field 'streamDate' is required for raw material and must be a date (YYYY-MM-DD).");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw invalid("The field 'streamDate' must be a date (YYYY-MM-DD).");
        }
    }

    private static ProblemException invalid(String detail) {
        return new ProblemException(400, "Invalid asset", detail);
    }
}
