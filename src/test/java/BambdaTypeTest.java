import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tripwires for prompt-content bugs — the kind that compile fine here but only surface
 * when generated code is pasted into Burp. These assert facts we got wrong before, not prompt
 * quality (only Burp can judge that).
 */
class BambdaTypeTest {

    /** The contexts that run in Burp's full-API wrapper; everything else has no {@code api()}. */
    private static final Set<BambdaType> API_TYPES = EnumSet.of(
            BambdaType.REPEATER_CUSTOM_ACTION,
            BambdaType.MATCH_REPLACE_REQUEST,
            BambdaType.MATCH_REPLACE_RESPONSE,
            BambdaType.CUSTOM_SCAN_CHECK);

    /** Guards against a stray {@code %} in any prompt text, which would crash {@code .formatted()}. */
    @Test
    void everyTypeRendersWithoutCrashing() {
        for (BambdaType type : BambdaType.values()) {
            String prompt = type.systemPrompt();
            assertFalse(prompt.isBlank(), type + " produced a blank prompt");
            assertTrue(prompt.contains("Respond with ONLY the Java code"),
                    type + " is missing the shared instructions");
        }
    }

    /** Locks the fix where filters and columns wrongly claimed an {@code api()} they don't have. */
    @Test
    void apiAvailabilityMatchesTheContext() {
        for (BambdaType type : BambdaType.values()) {
            String prompt = type.systemPrompt();
            boolean offersApi = prompt.contains("The Montoya API entry point is available");
            boolean forbidsApi = prompt.contains("There is NO `api()`");

            if (API_TYPES.contains(type)) {
                assertTrue(offersApi, type + " should expose api()");
                assertFalse(forbidsApi, type + " wrongly forbids api()");
            } else {
                assertTrue(forbidsApi, type + " should forbid api()");
                assertFalse(offersApi, type + " wrongly exposes api()");
            }
        }
    }

    /** Locks the editor API for custom actions: the model had invented a non-existent `setRequest`. */
    @Test
    void customActionDescribesTheRealEditorPaneApi() {
        String prompt = BambdaType.REPEATER_CUSTOM_ACTION.systemPrompt();
        assertTrue(prompt.contains("requestPane().set("), "must teach the real pane-set API");
    }

    /** Locks the compile-breaking wrong package: AuditResult is in scanner, not audit.issues. */
    @Test
    void scanCheckDoesNotUseTheWrongAuditResultPackage() {
        String prompt = BambdaType.CUSTOM_SCAN_CHECK.systemPrompt();
        assertFalse(prompt.contains("audit.issues.AuditResult"),
                "AuditResult lives in burp.api.montoya.scanner, not audit.issues");
        assertTrue(prompt.contains("AuditResult.auditResult()"));
    }
}
