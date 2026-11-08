package net.onelitefeather.menura.core.asset;

import net.onelitefeather.menura.core.problem.ProblemException;

import java.util.Locale;
import java.util.Set;

/**
 * The kinds of library asset that the upload accepts. A finished video or image is a finished asset; a raw stream
 * recording is raw material, which add-content-library registers as a pool item. The value is the one stored in the
 * database and sent in the API.
 */
public enum AssetKind {
    VIDEO("video", Set.of("mp4", "mov", "webm", "mkv")),
    IMAGE("image", Set.of("png", "jpg", "jpeg", "webp", "gif")),
    RAW_MATERIAL("raw_material", Set.of("mp4", "mov", "webm", "mkv"));

    private final String value;
    private final Set<String> formats;

    AssetKind(String value, Set<String> formats) {
        this.value = value;
        this.formats = formats;
    }

    public String value() {
        return value;
    }

    /** The formats this kind accepts, in lower case. */
    public Set<String> formats() {
        return formats;
    }

    /** Videos and raw recordings have a duration; images do not. */
    public boolean hasDuration() {
        return this != IMAGE;
    }

    /** Parses the kind named by a client. An unknown name is refused as a problem with the field {@code kind}. */
    public static AssetKind parse(String name) {
        if (name != null) {
            for (AssetKind kind : values()) {
                if (kind.value.equals(name.trim().toLowerCase(Locale.ROOT))) {
                    return kind;
                }
            }
        }
        throw new ProblemException(400, "Invalid asset", "The field 'kind' must be one of video, image or raw_material.");
    }
}
