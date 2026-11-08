package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.web.ObjectAccess;

import java.util.UUID;

/**
 * Who may see which asset. The library is one shared pool: every verified asset, finished or raw material, is visible
 * to every signed-in person who holds a Menura role, so a person without any role reads nothing. Writes and deletes
 * keep their own role rules (the editor role). Hidden assets answer 404, never 403 (principle 11).
 */
public final class AssetAccess {

    private AssetAccess() {
    }

    /** Whether the viewer may read the shared pool: any Menura role is enough. */
    public static boolean mayRead(CurrentPerson viewer) {
        return !viewer.roles().isEmpty();
    }

    /** The visibility rule of one asset for the guard. */
    public static ObjectAccess<AssetRecord> assetAccess(CurrentPerson viewer) {
        return new ObjectAccess<>() {
            @Override
            public boolean isVisible(AssetRecord asset) {
                return AssetAccess.mayRead(viewer);
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
