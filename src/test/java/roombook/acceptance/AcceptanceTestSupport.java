package roombook.acceptance;

import org.junit.jupiter.api.AfterEach;
import roombook.testsupport.HttpTestSupport;
import roombook.testsupport.InvariantAudit;

/** Audit scenario data before the next fixture resets it, including the final scenario. */
abstract class AcceptanceTestSupport extends HttpTestSupport {
    @AfterEach void auditScenarioState() { InvariantAudit.assertAllInvariantsHold(); }
}
