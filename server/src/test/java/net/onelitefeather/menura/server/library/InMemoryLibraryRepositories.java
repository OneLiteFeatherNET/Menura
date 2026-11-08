package net.onelitefeather.menura.server.library;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory stand-ins for the library repositories. The asset page applies the same visibility rule as the SQL and
 * the same keyset order (created_at desc, id desc), so paging tests run on the real rule.
 */
public final class InMemoryLibraryRepositories {

    private InMemoryLibraryRepositories() {
    }

    @Singleton
    @Replaces(JdbcUploadSessionRepository.class)
    public static class Sessions implements UploadSessionRepository {

        private final Map<UUID, UploadSession> rows = new LinkedHashMap<>();
        private final Map<UUID, Map<Integer, ObjectStorage.StoredPart>> parts = new LinkedHashMap<>();

        @Override
        public Optional<UploadSession> find(Connection connection, UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<UploadSession> findForUpdate(Connection connection, UUID id) {
            return find(connection, id);
        }

        @Override
        public void insert(Connection connection, UploadSession session) {
            rows.put(session.id(), session);
        }

        @Override
        public void update(Connection connection, UploadSession session) {
            rows.put(session.id(), session);
        }

        @Override
        public void recordParts(Connection connection, UUID sessionId, Collection<ObjectStorage.StoredPart> stored) {
            Map<Integer, ObjectStorage.StoredPart> held = parts.computeIfAbsent(sessionId, id -> new LinkedHashMap<>());
            for (ObjectStorage.StoredPart part : stored) {
                held.put(part.partNumber(), part);
            }
        }

        public List<UploadSession> rows() {
            return List.copyOf(rows.values());
        }

        public Map<Integer, ObjectStorage.StoredPart> recordedParts(UUID sessionId) {
            return Map.copyOf(parts.getOrDefault(sessionId, Map.of()));
        }
    }

    @Singleton
    @Replaces(JdbcAssetRepository.class)
    public static class Assets implements AssetRepository {

        private final Map<UUID, AssetRecord> rows = new LinkedHashMap<>();

        @Override
        public Optional<AssetRecord> find(Connection connection, UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<AssetRecord> findDuplicate(Connection connection, UUID uploaderId, String checksum) {
            return rows.values().stream()
                    .filter(asset -> asset.uploaderId().equals(uploaderId))
                    .filter(asset -> asset.declaration().checksum().value().equals(checksum))
                    .min(Comparator.comparing(AssetRecord::createdAt));
        }

        @Override
        public List<AssetRecord> page(Connection connection, Instant afterCreatedAt, UUID afterId, int limit) {
            List<AssetRecord> visible = new ArrayList<>();
            for (AssetRecord asset : rows.values()) {
                boolean after = afterCreatedAt == null
                        || asset.createdAt().isBefore(afterCreatedAt)
                        || (asset.createdAt().equals(afterCreatedAt) && asset.id().compareTo(afterId) < 0);
                if (after) {
                    visible.add(asset);
                }
            }
            visible.sort(Comparator.comparing(AssetRecord::createdAt).thenComparing(AssetRecord::id).reversed());
            return visible.subList(0, Math.min(limit, visible.size()));
        }

        @Override
        public void insert(Connection connection, AssetRecord asset) {
            rows.put(asset.id(), asset);
        }

        @Override
        public void delete(Connection connection, UUID id) {
            rows.remove(id);
        }

        public List<AssetRecord> rows() {
            return List.copyOf(rows.values());
        }
    }
}
