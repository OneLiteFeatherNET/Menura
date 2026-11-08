package net.onelitefeather.menura.core.channel;

import net.onelitefeather.menura.core.problem.ProblemException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** The adapters of this build, one per platform identifier. Each platform appears once. */
public final class PlatformRegistry {

    private final Map<String, PlatformAdapter> adapters = new TreeMap<>();

    public PlatformRegistry(Collection<PlatformAdapter> registered) {
        for (PlatformAdapter adapter : registered) {
            if (adapters.put(adapter.platform(), adapter) != null) {
                throw new IllegalArgumentException("two adapters register the platform " + adapter.platform());
            }
        }
    }

    public Optional<PlatformAdapter> find(String platform) {
        return Optional.ofNullable(adapters.get(platform));
    }

    /** The adapter of a platform, or a 400 Problem Details error when this build has none. */
    public PlatformAdapter require(String platform) {
        return find(platform).orElseThrow(() -> new ProblemException(400, "Bad Request", "The platform is not supported."));
    }

    public List<PlatformAdapter> all() {
        return List.copyOf(adapters.values());
    }
}
