package net.onelitefeather.menura.core.problem;

import java.util.Objects;

/**
 * RFC 9457 problem details. Every error response of the API carries these five members and is sent
 * with the media type {@link #MEDIA_TYPE}.
 */
public record ProblemDetails(String type, String title, int status, String detail, String instance) {

    public static final String MEDIA_TYPE = "application/problem+json";
    public static final String DEFAULT_TYPE = "about:blank";

    public ProblemDetails {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(instance, "instance");
    }

    public static ProblemDetails of(int status, String title, String detail, String instance) {
        return new ProblemDetails(DEFAULT_TYPE, title, status, detail, instance);
    }
}
