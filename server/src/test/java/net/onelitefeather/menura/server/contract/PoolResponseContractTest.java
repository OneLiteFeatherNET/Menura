package net.onelitefeather.menura.server.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.menura.server.library.ContentViews;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pool answer of an item without tags: the contract requires personTags and freeTags on every item, so an empty list
 * must be sent, not left out. The pool page of the web app reads both on every row. The body is written by the server's
 * own serializer, the one the HTTP layer uses.
 */
@MicronautTest
class PoolResponseContractTest {

    @Inject
    io.micronaut.serde.ObjectMapper serializer;

    @Test
    void aPoolItemWithoutTagsCarriesEmptyTagListsAsTheContractRequires() throws Exception {
        ContentViews.PoolItemView untagged = new ContentViews.PoolItemView(UUID.randomUUID(), "clip", "rendered", "active",
                UUID.randomUUID(), "Ohne Tags", null, UUID.randomUUID(), null, null, "never_used", List.of(), List.of(),
                Instant.parse("2026-11-01T10:00:00Z"));
        JsonNode item = new ObjectMapper().readTree(
                serializer.writeValueAsString(new ContentViews.PoolPageView(List.of(untagged), null)))
                .get("items").get(0);

        assertTrue(item.has("personTags") && item.get("personTags").isArray(),
                "the contract requires personTags as an array, even when it is empty");
        assertTrue(item.has("freeTags") && item.get("freeTags").isArray(),
                "the contract requires freeTags as an array, even when it is empty");
        assertEquals(0, item.get("freeTags").size(), "an untagged item has no free tags");
    }
}
