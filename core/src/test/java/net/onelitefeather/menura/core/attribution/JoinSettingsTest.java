package net.onelitefeather.menura.core.attribution;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The join domain and the attribution window: what an admin may store. */
class JoinSettingsTest {

    @Test
    void aLowerCaseHostNameIsAcceptedAsTheJoinDomain() {
        assertEquals("onelitefeather.net", JoinSettings.checkedDomain("onelitefeather.net"),
                "a lower-case host name is the join domain");
    }

    @Test
    void anUpperCaseDomainIsRefusedWith422() {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> JoinSettings.checkedDomain("OneLiteFeather.net"));

        assertEquals(422, refused.status(), "an upper-case domain is refused as invalid");
    }

    @Test
    void aValueThatIsNotAHostNameIsRefusedWith422() {
        assertEquals(422, assertThrows(ProblemException.class,
                () -> JoinSettings.checkedDomain("https://onelitefeather.net/")).status(),
                "a URL with scheme and path is not a host name");
        assertEquals(422, assertThrows(ProblemException.class,
                () -> JoinSettings.checkedDomain("onelitefeather.net:8080")).status(), "a port is not part of the domain");
        assertEquals(422, assertThrows(ProblemException.class,
                () -> JoinSettings.checkedDomain("one..feather.net")).status(), "an empty label is refused");
        assertEquals(422, assertThrows(ProblemException.class,
                () -> JoinSettings.checkedDomain(null)).status(), "a missing domain is refused");
    }

    @Test
    void theWindowIsAWholeNumberOfHoursFromOneToSevenHundredTwenty() {
        assertEquals(24, JoinSettings.checkedWindowHours(24), "24 hours is a valid window");
        assertEquals(422, assertThrows(ProblemException.class, () -> JoinSettings.checkedWindowHours(0)).status(),
                "zero hours is refused");
        assertEquals(422, assertThrows(ProblemException.class, () -> JoinSettings.checkedWindowHours(721)).status(),
                "more than 720 hours is refused");
    }

    @Test
    void theUnsetSettingsHaveNoDomainAndTheDefaultWindowOf72Hours() {
        JoinSettings unset = JoinSettings.unset();

        assertNull(unset.joinDomain(), "no domain until an admin sets one");
        assertEquals(72, unset.attributionWindowHours(), "the default window is 72 hours");
    }
}
