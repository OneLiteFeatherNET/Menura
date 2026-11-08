package net.onelitefeather.menura.core.series;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/** The audience a series addresses. The shorts must reach players as well as developers. */
public enum Audience {
    PLAYERS("players"),
    DEVELOPERS("developers"),
    MIXED("mixed");

    private final String value;

    Audience(String value) {
        this.value = value;
    }

    /** The name the API and the database use. */
    public String value() {
        return value;
    }

    /** The audience of this name, or empty when the name is none of the allowed values. */
    public static Optional<Audience> parse(String value) {
        return Arrays.stream(values()).filter(audience -> audience.value.equals(value)).findFirst();
    }

    /** The allowed names, for error messages. */
    public static String allowedValues() {
        return Arrays.stream(values()).map(Audience::value).collect(Collectors.joining(", "));
    }
}
