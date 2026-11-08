package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.asset.AssetKind;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.web.ObjectAccess;

import java.util.UUID;

/**
 * Who may see which asset. Finished videos and images are shared with every signed-in person. Raw material is a
 * stream recording a person still works on, so it is visible to its uploader and to the admin role only. Hidden
 * assets answer 404, never 403 (principle 11).
 */
public final class AssetAccess {

    private AssetAccess() {
    }

    /** The rule, shared with the SQL of {@link AssetRepository#page}: raw material is visible to its uploader or an admin. */
    public static boolean mayList(AssetRecord asset, UUID viewerId, boolean admin) {
        return asset.kind() != AssetKind.RAW_MATERIAL || asset.uploaderId().equals(viewerId) || admin;
    }

    public static boolean isAdmin(CurrentPerson viewer) {
        return viewer.roles().contains(Role.ADMIN);
    }

    /** The visibility rule of one asset for the guard. */
    public static ObjectAccess<AssetRecord> assetAccess(CurrentPerson viewer) {
        return new ObjectAccess<>() {
            @Override
            public boolean isVisible(AssetRecord asset) {
                return mayList(asset, viewer.personId(), isAdmin(viewer));
            }

            @Override
            public boolean mayRead(AssetRecord asset) {
                return true;
            }
        };
    }

    /** Upload sessions belong to their uploader alone. */
    public static ObjectAccess<UploadSession> sessionAccess(UUID viewerId) {
        return new ObjectAccess<>() {
            @Override
            public boolean isVisible(UploadSession session) {
                return session.uploaderId().equals(viewerId);
            }

            @Override
            public boolean mayRead(UploadSession session) {
                return true;
            }
        };
    }
}
