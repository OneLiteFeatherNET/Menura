package net.onelitefeather.menura.core.channel;

/**
 * The platform refused a custom thumbnail for this channel because the channel may not set one (change
 * add-youtube-integration, task 4.6). It is final: the same channel is refused again until the platform allows it, so the
 * publication does not retry it. The eligibility is recorded on the channel from this answer.
 */
public final class ThumbnailNotAllowed extends PlatformException {

    public ThumbnailNotAllowed(String message) {
        super(Kind.REJECTED, message, null);
    }
}
