package net.onelitefeather.menura.server.prototype.pool;

import net.onelitefeather.menura.server.prototype.pool.Asset.Kind;
import net.onelitefeather.menura.server.prototype.pool.Asset.Stage;
import net.onelitefeather.menura.server.prototype.pool.Asset.Transcript;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The fixed demo data of the pool: four stream recordings, fourteen clips cut from them (six rendered, five cut, three
 * raw snippets) and their transcripts. Nothing is random and nothing identifies a real person; the people are the
 * demo persons A to D. The tags are tech topics only.
 */
final class PoolSeed {

    private PoolSeed() {
    }

    /** Size estimate of the simulated files: about 6 Mbit/s of source footage. */
    private static final long BYTES_PER_SECOND = 740_000L;

    static List<Person> people() {
        return List.of(
                new Person("p-a", "Demo-Person A"),
                new Person("p-b", "Demo-Person B"),
                new Person("p-c", "Demo-Person C"),
                new Person("p-d", "Demo-Person D"));
    }

    static List<Asset> assets() {
        return List.of(
                // Stream recordings (raw material).
                stream("a-100", "Stream 29.10. – Kubernetes-Grundlagen (Rohmaterial)", "p-a", List.of("p-a", "p-b"),
                        List.of("kubernetes", "autoscaling"), 6_480, "2026-10-29T19:00:00Z", Transcript.ready),
                stream("a-101", "Stream 02.11. – Tetragon-Policies (Rohmaterial)", "p-b", List.of("p-b", "p-c"),
                        List.of("tetragon", "ebpf"), 9_000, "2026-11-02T19:00:00Z", Transcript.ready),
                stream("a-102", "Stream 05.11. – Cilium-Umbau (Rohmaterial)", "p-c", List.of("p-a", "p-c"),
                        List.of("cilium", "kubernetes"), 7_260, "2026-11-05T19:00:00Z", Transcript.ready),
                stream("a-103", "Stream 07.11. – OpenTelemetry-Pipeline (Rohmaterial)", "p-d", List.of("p-a", "p-d"),
                        List.of("opentelemetry", "kubernetes"), 10_800, "2026-11-07T19:00:00Z", Transcript.none),

                // Rendered clips: final 9:16 with overlay, ready to publish.
                clip("a-104", "a-102", "Cilium in 30 s: Policy-Check", "p-c", List.of("p-c"),
                        List.of("cilium", "kubernetes"), 45, "2026-11-05T21:00:00Z", "k8s-30s", Stage.rendered,
                        Transcript.ready),
                clip("a-105", "a-101", "Tetragon in 30 s: Prozess-Events", "p-b", List.of("p-b"),
                        List.of("tetragon", "ebpf"), 50, "2026-11-02T21:30:00Z", "k8s-30s", Stage.rendered,
                        Transcript.ready),
                clip("a-106", "a-102", "Live-Migration ohne Pause: Übersicht", "p-a", List.of("p-a", "p-c"),
                        List.of("live-migration", "kubernetes"), 240, "2026-11-05T21:30:00Z", "live-migration",
                        Stage.rendered, Transcript.ready),
                clip("a-107", "a-100", "Live-Migration: Speicher synchronisieren", "p-d", List.of("p-d", "p-a"),
                        List.of("live-migration", "autoscaling"), 180, "2026-11-01T12:00:00Z", "live-migration",
                        Stage.rendered, Transcript.pending),
                clip("a-108", "a-103", "Survival: Die erste Nacht", "p-b", List.of("p-b", "p-d"),
                        List.of("survival", "minecraft"), 150, "2026-11-07T21:00:00Z", "survival", Stage.rendered,
                        Transcript.ready),
                clip("a-109", "a-103", "Survival: Basenbau in 60 s", "p-c", List.of("p-c", "p-d"),
                        List.of("survival", "minecraft"), 60, "2026-11-07T22:00:00Z", "survival", Stage.rendered,
                        Transcript.ready),

                // Cut clips: trimmed, not yet rendered.
                clip("a-110", "a-100", "Autoscaling: HPA-Schwellwerte (Schnitt)", "p-d", List.of("p-d", "p-a"),
                        List.of("autoscaling", "kubernetes"), 95, "2026-10-31T09:00:00Z", null, Stage.cut,
                        Transcript.ready),
                clip("a-111", "a-101", "eBPF-Hooks erklärt (Schnitt)", "p-c", List.of("p-c", "p-b"),
                        List.of("ebpf", "tetragon"), 120, "2026-11-03T10:00:00Z", null, Stage.cut,
                        Transcript.ready),
                clip("a-112", "a-102", "Cilium-Netzwerk: Hubble-Blick (Schnitt)", "p-a", List.of("p-a", "p-c"),
                        List.of("cilium"), 140, "2026-11-06T10:00:00Z", null, Stage.cut, Transcript.ready),
                clip("a-113", "a-103", "OpenTelemetry-Traces im Dashboard (Schnitt)", "p-b", List.of("p-b"),
                        List.of("opentelemetry"), 110, "2026-11-08T08:00:00Z", null, Stage.cut, Transcript.pending),
                clip("a-114", "a-100", "Live-Migration: Fehlerfall (Schnitt)", "p-b", List.of("p-b"),
                        List.of("live-migration"), 200, "2026-11-01T10:00:00Z", "live-migration", Stage.cut,
                        Transcript.pending),

                // Raw snippets: short pieces of footage, still raw.
                snippet("a-115", "a-100", "Snippet: Rollout-Lärm (Rohausschnitt)", "p-d", List.of("p-d", "p-a"),
                        List.of("kubernetes"), 35, "2026-10-30T11:00:00Z", Transcript.ready),
                snippet("a-116", "a-101", "Snippet: Tetragon-Abbruch (Rohausschnitt)", "p-a", List.of("p-a", "p-b"),
                        List.of("tetragon"), 22, "2026-11-02T20:00:00Z", Transcript.ready),
                snippet("a-117", "a-102", "Snippet: Hubble-Flow (Rohausschnitt)", "p-d", List.of("p-d", "p-c"),
                        List.of("cilium"), 28, "2026-11-05T20:00:00Z", Transcript.ready));
    }

    /** The transcript segments of every ready asset, six to twelve per asset. Other assets have none. */
    static Map<String, List<TranscriptSegment>> transcripts() {
        Map<String, List<TranscriptSegment>> transcripts = new LinkedHashMap<>();
        transcripts.put("a-100", List.of(
                seg(0, 40, "p-a", "Willkommen zum Stream. Heute geht es um Kubernetes-Grundlagen und Autoscaling."),
                seg(300, 360, "p-b", "Der Horizontal Pod Autoscaler liest Metriken aus dem Metrics-Server."),
                seg(900, 980, "p-a", "Ohne Requests und Limits kann der Autoscaler nicht sauber rechnen."),
                seg(1800, 1870, "p-b", "Ein Scale-Down zu früh kostet bei Lastspitzen mehr, als er spart."),
                seg(2600, 2690, "p-a", "Stabilisierungsfenster dämpfen das Flattern der Replikazahl."),
                seg(3700, 3790, "p-b", "Custom Metrics aus Prometheus gehen über den Adapter in den HPA."),
                seg(5000, 5080, "p-a", "Zum Schluss ein Blick auf Cluster-Autoscaler und Node-Pools."),
                seg(6300, 6380, "p-b", "Fragen aus dem Chat beantworte ich im nächsten Teil.")));
        transcripts.put("a-101", List.of(
                seg(0, 30, "p-b", "Heute schauen wir uns Tetragon-Policies an."),
                seg(420, 500, "p-c", "Eine TracingPolicy beschreibt, welche Kernel-Funktionen beobachtet werden."),
                seg(1200, 1290, "p-b", "Mit eBPF hängen wir uns an Syscalls, ohne den Kernel zu patchen."),
                seg(2100, 2180, "p-c", "Ein Match auf Dateizugriffe erzeugt Events mit Prozess-Kontext."),
                seg(3300, 3380, "p-b", "Enforcement bricht den Systemaufruf ab, bevor er ausgeführt wird."),
                seg(4700, 4790, "p-c", "Für die Produktion lassen wir die Policies zuerst im Beobachtungsmodus laufen."),
                seg(6800, 6880, "p-b", "Die Events exportieren wir als JSON in den Log-Stack."),
                seg(8400, 8480, "p-c", "Nächste Woche geht es um Netzwerk-Policies mit Cilium.")));
        transcripts.put("a-102", List.of(
                seg(0, 35, "p-c", "Cilium ersetzt heute den alten kube-proxy im Testcluster."),
                seg(500, 580, "p-a", "Der Umbau läuft ohne Downtime, wenn wir Node für Node vorgehen."),
                seg(1100, 1190, "p-c", "Die Datapath-Programme laufen im Kernel über eBPF-Maps."),
                seg(2000, 2080, "p-a", "Hubble zeigt uns die Flows zwischen den Pods in Echtzeit."),
                seg(2900, 2990, "p-c", "Eine CiliumNetworkPolicy erlaubt nur den Verkehr zum Backend."),
                seg(3800, 3880, "p-a", "Der Health-Check der Endpoints hatte einen Fehler, den wir behoben haben."),
                seg(5200, 5290, "p-c", "Mit der Gateway API liegt der Ingress nun auf demselben Datapath."),
                seg(6700, 6790, "p-a", "Die Migration ist fertig, die Metriken bleiben stabil.")));
        transcripts.put("a-104", List.of(
                seg(0, 6, "p-c", "Eine Policy in 30 Sekunden: Wir erlauben nur Frontend zu Backend."),
                seg(6, 14, "p-c", "Die Regel steht als Kubernetes-Objekt, Cilium übersetzt sie in eBPF."),
                seg(14, 22, "p-c", "Ein kurzer Test mit curl zeigt den Unterschied."),
                seg(22, 30, "p-c", "Die Verbindung zur Datenbank bleibt blockiert."),
                seg(30, 37, "p-c", "Alles andere wird verworfen, ohne den Pod zu stören."),
                seg(37, 45, "p-c", "Mehr dazu im Stream vom 5. November.")));
        transcripts.put("a-105", List.of(
                seg(0, 7, "p-b", "Tetragon in 30 Sekunden: Was passiert im Kernel?"),
                seg(7, 15, "p-b", "Eine TracingPolicy hängt sich an einen Systemaufruf."),
                seg(15, 25, "p-b", "Jeder Event kommt mit Prozess, Nutzer und Pfad."),
                seg(25, 35, "p-b", "Enforcement blockiert den Aufruf, bevor er wirkt."),
                seg(35, 43, "p-b", "Erst im Beobachtungsmodus testen, dann scharf schalten."),
                seg(43, 50, "p-b", "Mehr dazu im Stream vom 2. November.")));
        transcripts.put("a-106", List.of(
                seg(0, 20, "p-a", "Live-Migration ohne Pause: Was passiert beim Umzug eines Pods?"),
                seg(20, 60, "p-c", "Zuerst wird der Speicher vorkopiert, während der Prozess weiterläuft."),
                seg(60, 100, "p-a", "Danach werden die geänderten Seiten in einer zweiten Runde nachkopiert."),
                seg(100, 140, "p-c", "Der letzte Schritt friert den Prozess für wenige Millisekunden ein."),
                seg(140, 180, "p-a", "Die Netzwerkverbindungen folgen über die neue Route im Datapath."),
                seg(180, 210, "p-c", "Im Stream zeigen wir die Messwerte der Kopierrate."),
                seg(210, 240, "p-a", "Den Fehlerfall bauen wir im nächsten Clip nach.")));
        transcripts.put("a-108", List.of(
                seg(0, 15, "p-b", "Tag eins im Survival: Wir starten ohne Ausrüstung."),
                seg(15, 35, "p-d", "Holz sammeln ist der erste Schritt, bevor die Werkbank steht."),
                seg(35, 60, "p-b", "Das Dashboard zeigt die Ressourcen als Metriken."),
                seg(60, 90, "p-d", "Die Nacht kommt früher, als wir gedacht haben."),
                seg(90, 120, "p-b", "Ein Zombie-Spawn an der Hütte, wir verteidigen mit Pfeil und Bogen."),
                seg(120, 150, "p-d", "Mehr vom Survival-Tag gibt es in der Serie.")));
        transcripts.put("a-109", List.of(
                seg(0, 8, "p-c", "Basenbau in 60 Sekunden."),
                seg(8, 18, "p-c", "Erst die Fundamente, dann die Wände."),
                seg(18, 30, "p-d", "Die Koordinaten der Basis schreiben wir in den Chat."),
                seg(30, 40, "p-c", "Wir bauen mit Stein, weil Holz bei Regen verrottet."),
                seg(40, 50, "p-c", "Der Eingang zeigt nach Osten, damit die Sonne den Raum erhellt."),
                seg(50, 60, "p-d", "Teil zwei kommt am Samstag.")));
        transcripts.put("a-110", List.of(
                seg(0, 10, "p-d", "HPA-Schwellwerte: Wann skaliert der Pod wirklich?"),
                seg(10, 25, "p-d", "Der Standard ist ein Ziel von achtzig Prozent CPU-Auslastung."),
                seg(25, 40, "p-a", "Die Berechnung nutzt den Durchschnitt über alle Replikas."),
                seg(40, 60, "p-d", "Stabilisierungsfenster verhindern unnötiges Herunterskalieren."),
                seg(60, 80, "p-a", "Requests müssen gesetzt sein, sonst fehlt die Bezugsgröße."),
                seg(80, 95, "p-d", "Mehr dazu im Stream vom 29. Oktober.")));
        transcripts.put("a-111", List.of(
                seg(0, 15, "p-c", "Was ist ein eBPF-Hook? Ein Programm am Kernel-Ereignis."),
                seg(15, 35, "p-b", "Der Verifier prüft, ob das Programm sicher terminiert."),
                seg(35, 55, "p-c", "Maps tauschen Daten zwischen Kernel und Userspace aus."),
                seg(55, 75, "p-b", "Kprobes und Tracepoints sind die häufigsten Anker."),
                seg(75, 100, "p-c", "Ohne Neustart lässt sich ein Programm nachladen."),
                seg(100, 120, "p-b", "Im nächsten Schnitt zeigen wir eine Map live.")));
        transcripts.put("a-112", List.of(
                seg(0, 20, "p-a", "Hubble zeigt, welche Verbindungen ein Pod aufbaut."),
                seg(20, 40, "p-c", "Jeder Flow trägt Quelle, Ziel und Verdict."),
                seg(40, 60, "p-a", "Dropped-Flows sind der schnellste Hinweis auf eine falsche Policy."),
                seg(60, 85, "p-c", "Im Dashboard filtern wir nach Namespace."),
                seg(85, 110, "p-a", "Die DNS-Anfragen sehen wir ebenfalls, ohne Sidecar."),
                seg(110, 140, "p-c", "Das reicht für die Analyse im Testcluster.")));
        transcripts.put("a-115", List.of(
                seg(0, 6, "p-d", "Rollout-Lärm: Der Status springt kurz auf Degraded."),
                seg(6, 12, "p-a", "Das liegt an einem Readiness-Probe mit zu kurzem Timeout."),
                seg(12, 18, "p-d", "Der Pod ist bereit, nur die Probe wartet zu lange."),
                seg(18, 24, "p-a", "Wir erhöhen den Timeout um zwei Sekunden."),
                seg(24, 30, "p-d", "Dann wiederholen wir den Rollout im Staging."),
                seg(30, 35, "p-a", "Der Status bleibt grün.")));
        transcripts.put("a-116", List.of(
                seg(0, 4, "p-a", "Tetragon meldet den Abbruch sofort."),
                seg(4, 8, "p-b", "Der Event kommt mit dem Prozessnamen."),
                seg(8, 12, "p-a", "Der Aufruf wird nicht ausgeführt."),
                seg(12, 16, "p-b", "Der Prozess bekommt einen Fehler zurück."),
                seg(16, 19, "p-a", "Das ist der Enforcement-Modus."),
                seg(19, 22, "p-b", "Ohne Blockade wären wir nur Zuschauer.")));
        transcripts.put("a-117", List.of(
                seg(0, 5, "p-d", "Hubble-Flow: Die Verbindung wird grün markiert."),
                seg(5, 10, "p-c", "Das Verdict lautet forwarded."),
                seg(10, 15, "p-d", "Ein rotes Verdict hätte die Policy gezeigt."),
                seg(15, 20, "p-c", "Wir sehen die Ports im Flow-Log."),
                seg(20, 24, "p-d", "Der Ziel-Pod antwortet mit HTTP 200."),
                seg(24, 28, "p-c", "Passt, weiter im Schnitt.")));
        return Map.copyOf(transcripts);
    }

    private static TranscriptSegment seg(double startSeconds, double endSeconds, String personId, String text) {
        return new TranscriptSegment(startSeconds, endSeconds, personId, text);
    }

    private static Asset stream(String id, String title, String ownerId, List<String> people, List<String> tags,
                                long durationSeconds, String createdAt, Transcript transcript) {
        return new Asset(id, Kind.raw, title, ownerId, people, tags, durationSeconds, Instant.parse(createdAt), null,
                null, Stage.raw, transcript, size(durationSeconds));
    }

    private static Asset clip(String id, String sourceAssetId, String title, String ownerId, List<String> people,
                              List<String> tags, long durationSeconds, String createdAt, String seriesId, Stage stage,
                              Transcript transcript) {
        return new Asset(id, Kind.clip, title, ownerId, people, tags, durationSeconds, Instant.parse(createdAt),
                sourceAssetId, seriesId, stage, transcript, size(durationSeconds));
    }

    /** A short piece of raw footage cut from a recording: kind raw, stage raw, no series. */
    private static Asset snippet(String id, String sourceAssetId, String title, String ownerId, List<String> people,
                                 List<String> tags, long durationSeconds, String createdAt, Transcript transcript) {
        return new Asset(id, Kind.raw, title, ownerId, people, tags, durationSeconds, Instant.parse(createdAt),
                sourceAssetId, null, Stage.raw, transcript, size(durationSeconds));
    }

    private static long size(long durationSeconds) {
        return durationSeconds * BYTES_PER_SECOND;
    }
}
