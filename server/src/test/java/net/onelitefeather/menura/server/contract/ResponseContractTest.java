package net.onelitefeather.menura.server.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The response checker itself: it must report what the contract forbids, or the HTTP tests prove nothing. */
class ResponseContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void aBodyWithoutARequiredMemberIsReported() throws Exception {
        JsonNode page = JSON.readTree("{\"items\":[]}");

        List<String> violations = ResponseContract.load().violations("ChannelPage", page);

        assertEquals(List.of("ChannelPage.nextCursor is required by the contract but missing"), violations);
    }

    @Test
    void aNullMemberThatTheContractDoesNotAllowIsReported() throws Exception {
        JsonNode page = JSON.readTree("{\"items\":[],\"nextCursor\":null}");
        JsonNode channel = JSON.readTree("{\"items\":[{\"id\":null}],\"nextCursor\":null}");

        assertTrue(ResponseContract.load().violations("ChannelPage", page).isEmpty(),
                "a nullable member may be null");
        assertTrue(ResponseContract.load().violations("ChannelPage", channel).stream()
                        .anyMatch(v -> v.startsWith("ChannelPage.items[0].id is null")),
                "a non-nullable member must not be null");
    }
}
