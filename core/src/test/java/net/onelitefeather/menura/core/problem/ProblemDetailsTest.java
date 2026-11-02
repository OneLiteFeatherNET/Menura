package net.onelitefeather.menura.core.problem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProblemDetailsTest {

    @Test
    void problemDetailsCarriesTypeTitleStatusDetailAndInstance() {
        ProblemDetails problem = ProblemDetails.of(404, "Not Found", "No item has this id.", "/items/1");

        assertEquals("about:blank", problem.type(), "without a specific problem type the RFC 9457 default applies");
        assertEquals("Not Found", problem.title());
        assertEquals(404, problem.status());
        assertEquals("No item has this id.", problem.detail());
        assertEquals("/items/1", problem.instance());
    }

    @Test
    void problemDetailsMediaTypeIsApplicationProblemJson() {
        assertEquals("application/problem+json", ProblemDetails.MEDIA_TYPE,
                "problem bodies are sent as application/problem+json (RFC 9457)");
    }
}
