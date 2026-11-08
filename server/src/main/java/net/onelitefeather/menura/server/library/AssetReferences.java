package net.onelitefeather.menura.server.library;

import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * The reference check that guards deletion: the post that still refers to an asset, if any. The distribution change
 * (add-content-distribution) provides the implementation that looks at posts; until it is available the default
 * below reports no references, so deletion depends only on the other rules.
 */
public interface AssetReferences {

    Optional<UUID> findReferencingPost(Connection connection, UUID assetId) throws SQLException;

    /** Reports no references. Replaced by the implementation of the distribution change, which is not yet available. */
    @Singleton
    @Secondary
    class NoAssetReferences implements AssetReferences {

        @Override
        public Optional<UUID> findReferencingPost(Connection connection, UUID assetId) {
            return Optional.empty();
        }
    }
}
