package net.onelitefeather.menura.core.library;

import net.onelitefeather.menura.core.problem.ProblemException;

import java.util.Locale;

/**
 * The stage of a pool item: raw material as recorded, a clip that is cut, or a finished asset that is rendered. The
 * value is the one stored in the database and sent in the API.
 */
public enum Stage {
    RAW("raw"),
    CUT("cut"),
    RENDERED("rendered");

    private final String value;

    Stage(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** Parses a stage named by a client; an unknown name is refused as a problem with the field {@code stage}. */
    public static Stage parse(String name) {
        if (name != null) {
            for (Stage stage : values()) {
                if (stage.value.equals(name.trim().toLowerCase(Locale.ROOT))) {
                    return stage;
                }
            }
        }
        throw new ProblemException(400, "Invalid stage", "The field 'stage' must be one of raw, cut or rendered.");
    }
}
