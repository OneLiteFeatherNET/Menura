package net.onelitefeather.menura.core.problem;

/**
 * An error the caller can act on. Thrown by the core and by controllers; the server turns it into a
 * {@link ProblemDetails} response, adding the request path as the instance.
 */
public class ProblemException extends RuntimeException {

    private final int status;
    private final String title;
    private final String detail;

    public ProblemException(int status, String title, String detail) {
        super(title + ": " + detail);
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }

    public ProblemDetails toDetails(String instance) {
        return ProblemDetails.of(status, title, detail, instance);
    }
}
