package net.onelitefeather.menura.core.publish;

/**
 * The explicit declarations of a post on YouTube: whether a person chose its visibility, the made-for-kids answer, and whether
 * the post is planned as a Short. The dry run refuses scheduling while a required declaration is missing (change
 * add-youtube-integration).
 *
 * @param visibilityDeclared whether the visibility was chosen explicitly rather than taken from a default
 * @param madeForKids        the made-for-kids declaration, or null while it is not answered
 * @param plannedShort       whether the post is planned as a YouTube Short (vertical or square, at most three minutes)
 */
public record YouTubeDeclaration(boolean visibilityDeclared, Boolean madeForKids, boolean plannedShort) {

    /** No declaration yet. */
    public static YouTubeDeclaration none() {
        return new YouTubeDeclaration(false, null, false);
    }
}
