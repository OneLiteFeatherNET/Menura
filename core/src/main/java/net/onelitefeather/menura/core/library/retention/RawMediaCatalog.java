package net.onelitefeather.menura.core.library.retention;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The raw items the retention run works on. The row of a raw item is never removed here, only its media state. */
public interface RawMediaCatalog {

    /** Raw items whose media is still available and that were created before the cutoff, oldest first. */
    List<RawMediaRef> olderThan(Instant cutoff, int limit) throws Exception;

    /** Records the media state of a raw item: cold or removed. */
    void setMediaState(UUID rawItem, String state) throws Exception;
}
