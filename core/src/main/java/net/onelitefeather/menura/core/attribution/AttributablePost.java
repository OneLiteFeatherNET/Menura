package net.onelitefeather.menura.core.attribution;

import net.onelitefeather.menura.core.auth.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * An external post as the attribution sees it: when it was published, on which platform, and the series it belongs to
 * with the roles that may see that series. A post without a series is visible to every role.
 */
public record AttributablePost(UUID postId, String platform, Instant publishedAt, UUID seriesId, String seriesName,
                               Set<Role> seriesRoles) {

    public AttributablePost {
        seriesRoles = seriesRoles == null ? Set.of() : Set.copyOf(seriesRoles);
    }

    /** Whether a caller holding these roles may see the post, which depends only on the visibility of its series. */
    public boolean visibleTo(Set<Role> roles) {
        return seriesId == null || seriesRoles.stream().anyMatch(roles::contains);
    }
}
