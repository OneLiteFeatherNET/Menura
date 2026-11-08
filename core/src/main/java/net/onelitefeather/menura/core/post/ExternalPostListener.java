package net.onelitefeather.menura.core.post;

import net.onelitefeather.menura.core.channel.PlatformAdapter.PublishedItem;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Reacts to a post that an import has just stored (change add-content-series assigns posts to series here). It runs on
 * the connection of the import's transaction, so what it writes commits or rolls back together with the post.
 */
@FunctionalInterface
public interface ExternalPostListener {

    /** A listener that reacts to nothing. */
    ExternalPostListener NONE = (connection, post, item) -> {
    };

    /** Called once for each post the import inserted, with the platform item it was listed as. */
    void imported(Connection connection, ExternalPost post, PublishedItem item) throws SQLException;
}
