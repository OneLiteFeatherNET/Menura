package net.onelitefeather.menura.core.publish;

import java.util.Set;

/**
 * The limits one platform puts on a post: title and text length, hashtags and the kinds of finished asset it takes. The
 * dry run reads them; the platform adapters do not, so a platform change with other limits changes only this table.
 */
public record PublishRules(int maxTitle, int maxDescription, int maxHashtags, Set<String> kinds) {

    public PublishRules {
        kinds = Set.copyOf(kinds);
    }

    /** YouTube: 100 characters of title, 5000 of description, 15 hashtags in the description, videos only. */
    public static final PublishRules YOUTUBE = new PublishRules(100, 5000, 15, Set.of("video"));
}
