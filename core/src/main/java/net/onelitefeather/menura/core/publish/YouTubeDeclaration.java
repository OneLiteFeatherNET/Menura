package net.onelitefeather.menura.core.publish;

/**
 * The explicit declarations of a post on YouTube: whether a person chose its visibility, and the made-for-kids answer. The
 * dry run refuses scheduling while either is missing (change add-youtube-integration).
 *
 * @param visibilityDeclared whether the visibility was chosen explicitly rather than taken from a default
 * @param madeForKids        the made-for-kids declaration, or null while it is not answered
 */
public record YouTubeDeclaration(boolean visibilityDeclared, Boolean madeForKids) {

    /** No declaration yet. */
    public static YouTubeDeclaration none() {
        return new YouTubeDeclaration(false, null);
    }
}
