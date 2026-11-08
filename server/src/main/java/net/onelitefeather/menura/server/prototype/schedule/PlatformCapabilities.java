package net.onelitefeather.menura.server.prototype.schedule;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The fixed capabilities of the three platforms, from the platform docs, keyed by platform. */
public final class PlatformCapabilities {

    @Serdeable
    public record Capability(boolean nativeSchedule, String note) {
    }

    private static final Map<String, Capability> ALL;

    static {
        Map<String, Capability> capabilities = new LinkedHashMap<>();
        capabilities.put("youtube", new Capability(true,
                "Upload privat mit publishAt; YouTube schaltet zur Zeit öffentlich."));
        capabilities.put("tiktok", new Capability(false,
                "Menura veröffentlicht zur Zeit selbst. Ohne API-Audit nur privat."));
        capabilities.put("instagram", new Capability(false,
                "Menura veröffentlicht zur Zeit selbst. Höchstens 50 API-Posts pro 24 h."));
        ALL = Collections.unmodifiableMap(capabilities);
    }

    private PlatformCapabilities() {
    }

    public static Map<String, Capability> all() {
        return ALL;
    }

    /** Native when the platform schedules by itself, hub when Menura publishes. Unknown platforms are hub. */
    public static String modeOf(String platform) {
        Capability capability = ALL.get(platform);
        return capability != null && capability.nativeSchedule() ? Publication.NATIVE : Publication.HUB;
    }
}
