package net.onelitefeather.menura.core.post;

/**
 * The known format values of an external post. The format is stored as free text, so a platform adapter may report
 * other values too; the values here are the ones Menura's own rules read: a {@link #SHORT} is a vertical short that the
 * series coverage counts.
 */
public final class ExternalPostFormat {

    /** A video as a whole, such as a stream archive or a long upload. */
    public static final String VIDEO = "video";

    /** A vertical short, which the platform adapters report for every short of the channel. */
    public static final String SHORT = "short";

    private ExternalPostFormat() {
    }
}
