package net.onelitefeather.menura.server.prototype;

import net.onelitefeather.menura.server.prototype.Model.Channel;
import net.onelitefeather.menura.server.prototype.Model.JoinEvent;
import net.onelitefeather.menura.server.prototype.Model.Localized;
import net.onelitefeather.menura.server.prototype.Model.Mark;
import net.onelitefeather.menura.server.prototype.Model.SafeZone;
import net.onelitefeather.menura.server.prototype.Model.SeedPost;
import net.onelitefeather.menura.server.prototype.Model.Series;
import net.onelitefeather.menura.server.prototype.Model.Slot;
import net.onelitefeather.menura.server.prototype.Model.Template;
import net.onelitefeather.menura.server.prototype.Model.Token;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The fixed demo data: channels, brand kit, four series, eighteen shorts and a list of join events. Nothing is random
 * and nothing identifies a player; the joins carry only the answer to the in-game question. All values are demo data.
 */
final class DemoSeed {

    private DemoSeed() {
    }

    /** Every player joins over this one domain; there are no per-channel join hosts. */
    static final String JOIN_DOMAIN = "onelitefeather.net";

    /** The one call to action of all channels. */
    static final Localized CTA = new Localized("Spiel mit uns: onelitefeather.net",
            "Play with us: onelitefeather.net");

    static List<Channel> channels() {
        return List.of(
                new Channel("yt-de", "youtube", "OneLiteFeather (YouTube)"),
                new Channel("tt-de", "tiktok", "OneLiteFeather (TikTok)"),
                new Channel("ig-de", "instagram", "OneLiteFeather (Instagram)"));
    }

    static Model.BrandKit brandKit() {
        List<Token> tokens = List.of(
                new Token("primary", "#6638E4", "#CCBDFF"),
                new Token("series-k8s", "#2F6FEB", "#7AA7FF"),
                new Token("series-live-migration", "#6638E4", "#A994FF"),
                new Token("series-wir-bauen-olf", "#0E7C66", "#5EDCC0"),
                new Token("series-survival", "#B45309", "#FBBF77"));
        Template shortVertical = new Template("short-9x16", 1080, 1920, new SafeZone(14, 35), List.of(
                new Slot("badge", 0, null, null, 90, 300, 900, 90, List.of()),
                new Slot("title", 0, 3, null, 90, 420, 900, 240, List.of()),
                new Slot("captions", 0, null, null, 90, 700, 900, 400, List.of()),
                new Slot("session-tiles", 3, null, null, 90, 1280, 900, 300, List.of()),
                new Slot("cta", null, null, 2, 90, 1120, 900, 110, List.of("join_domain"))));
        return new Model.BrandKit(1, "overlay-theme", tokens, List.of(new Mark("feather", 10)),
                List.of(shortVertical), JOIN_DOMAIN, CTA);
    }

    static List<Series> series() {
        return List.of(
                new Series("k8s-30s", "Kubernetes in 30 s", new Localized("Kubernetes in 30 s", "Kubernetes in 30 s"),
                        "series-k8s", "#2F6FEB", "#FFFFFF", "Kurz erklärt:", "#k8sin30", "developers", "TUESDAY",
                        "18:00", Map.of("yt-de", "https://www.youtube.com/playlist?list=DEMO-K8S"), 0, true),
                new Series("live-migration", "Live-Migration", new Localized("Live-Migration", "Live-Migration"),
                        "series-live-migration", "#6638E4", "#FFFFFF", "Live erklärt:", "#livemigration",
                        "developers", "THURSDAY", "18:00",
                        Map.of("yt-de", "https://www.youtube.com/playlist?list=DEMO-LIVE"), 0, true),
                new Series("wir-bauen-olf", "Wir bauen OneLiteFeather",
                        new Localized("Wir bauen OneLiteFeather", "Wir bauen OneLiteFeather"),
                        "series-wir-bauen-olf", "#0E7C66", "#FFFFFF", "Aus dem Maschinenraum:", "#wirbauenolf",
                        "mixed", "FRIDAY", "18:00",
                        Map.of("yt-de", "https://www.youtube.com/playlist?list=DEMO-WIR"), 0, true),
                new Series("survival", "Survival (Arbeitstitel)",
                        new Localized("Survival (Arbeitstitel)", "Survival (Arbeitstitel)"),
                        "series-survival", "#B45309", "#FFFFFF", "Tag im Survival:", "#olfsurvival", "players",
                        "SATURDAY", "20:00",
                        Map.of("yt-de", "https://www.youtube.com/playlist?list=DEMO-SURVIVAL"), 0, true));
    }

    /** The shorts in publishing order of the seed; the store assigns series and episode numbers from these. */
    static List<SeedPost> posts() {
        return List.of(
                post("p-41", "yt-de", "2026-10-27T17:00:00Z", "Flannel fliegt aus der Bar",
                        List.of("#k8sin30", "#kubernetes"), false, 1180, 70.2, true),
                post("p-42", "tt-de", "2026-10-29T17:00:00Z", "Self-Healing im Cluster",
                        List.of("#k8sin30", "#devops"), false, 2100, null, false),
                post("p-45", "yt-de", "2026-10-29T16:00:00Z", "Live-Migration ohne Ausfall",
                        List.of("#livemigration"), false, 890, 64.0, true),
                post("p-48", "ig-de", "2026-10-30T17:00:00Z", "Ein Schritt zurück",
                        List.of("#wirbauenolf"), false, 430, 55.0, false),
                post("p-51", "yt-de", "2026-10-31T19:00:00Z", "Erster Abend im Survival",
                        List.of("#olfsurvival"), false, 2300, 48.2, true),
                post("p-52", "tt-de", "2026-11-01T19:00:00Z", "Basis am Fluss",
                        List.of("#olfsurvival"), false, 3100, null, false),
                post("p-47", "tt-de", "2026-11-02T16:00:00Z", "Kubelet unter der Lupe",
                        List.of("#livemigration"), false, 610, null, false),
                post("p-54", "yt-de", "2026-11-02T17:00:00Z", "Enterprise-Tests in fünf Minuten",
                        List.of("#devops"), true, 520, 60.0, false),
                post("p-55", "yt-de", "2026-11-02T18:00:00Z", "Helm-Charts ohne Umwege",
                        List.of("#kubernetes"), false, 470, 58.0, false),
                post("p-46", "yt-de", "2026-11-03T16:00:00Z", "Autoscaling live",
                        List.of("#livemigration", "#autoscaling"), false, 760, 61.3, false),
                post("p-56", "yt-de", "2026-11-03T18:00:00Z", "Backups per Zeitplan",
                        List.of("#wirbauenolf"), false, 300, 52.0, true),
                post("p-49", "yt-de", "2026-11-04T17:00:00Z", "Komplexe Infrastruktur, einfach erklärt",
                        List.of("#wirbauenolf"), false, 1010, 66.7, true),
                post("p-57", "yt-de", "2026-11-04T19:00:00Z", "Sicherheit im Pod",
                        List.of("#livemigration"), false, 980, 66.5, true),
                post("p-43", "yt-de", "2026-11-05T17:00:00Z", "Tetragon: Malware sicher ausführen?",
                        List.of("#k8sin30", "#security"), false, 1240, 71.5, true),
                post("p-58", "yt-de", "2026-11-05T19:00:00Z", "Erste Nacht am Fluss",
                        List.of("#olfsurvival"), false, 1100, 44.0, true),
                post("p-50", "tt-de", "2026-11-06T18:00:00Z", "OpenTelemetry trifft eBPF",
                        List.of("#wirbauenolf"), false, 1520, null, false),
                post("p-59", "yt-de", "2026-11-06T16:00:00Z", "Vorräte für die zweite Nacht",
                        List.of("#olfsurvival"), false, 1400, null, true),
                post("p-44", "ig-de", "2026-11-06T17:00:00Z", "Netzwerk tauschen im laufenden Cluster",
                        List.of("#k8sin30"), false, 640, 58.0, false));
    }

    /**
     * The joins of the seed: twenty first joins in two weeks, eight of them unanswered, and one repeat join that is
     * not counted. Only the answer, the flag and the time are kept; there is no player identifier. A null answer is
     * an unanswered first join.
     */
    static List<JoinEvent> joins() {
        return List.of(
                join("youtube", true, "2026-10-27T18:00:00Z"),
                join("instagram", true, "2026-10-27T06:00:00Z"),
                join(null, true, "2026-10-28T09:00:00Z"),
                join("tiktok", true, "2026-10-30T10:00:00Z"),
                join("youtube", true, "2026-10-30T12:00:00Z"),
                join(null, true, "2026-10-31T09:00:00Z"),
                join("instagram", true, "2026-10-31T12:00:00Z"),
                join(null, true, "2026-10-31T20:00:00Z"),
                join(null, true, "2026-11-01T08:00:00Z"),
                join(null, true, "2026-11-02T12:00:00Z"),
                join("twitch", true, "2026-11-03T15:00:00Z"),
                join("youtube", true, "2026-11-04T10:00:00Z"),
                join("youtube", true, "2026-11-05T10:00:00Z"),
                join("youtube", false, "2026-11-05T11:00:00Z"),
                join("friends", true, "2026-11-05T14:00:00Z"),
                join(null, true, "2026-11-05T16:00:00Z"),
                join(null, true, "2026-11-06T09:00:00Z"),
                join("other", true, "2026-11-06T12:00:00Z"),
                join("tiktok", true, "2026-11-06T20:00:00Z"),
                join("youtube", true, "2026-11-07T08:00:00Z"),
                join(null, true, "2026-11-07T09:00:00Z"));
    }

    private static SeedPost post(String id, String channelId, String publishedAt, String title,
                                 List<String> hashtags, boolean noSeries, long engaged, Double avg,
                                 boolean inPlaylist) {
        return new SeedPost(id, channelId, publishedAt, title, hashtags, noSeries, engaged, avg, inPlaylist);
    }

    private static JoinEvent join(String answer, boolean firstJoin, String at) {
        return new JoinEvent(answer, firstJoin, Instant.parse(at));
    }
}
