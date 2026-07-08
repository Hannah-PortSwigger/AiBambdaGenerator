import burp.api.montoya.ai.chat.PromptException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BambdaGeneratorTest {

    @Test
    void promptExceptionGetsAnAiSpecificMessage() {
        assertEquals("Burp AI could not generate a response: rate limited",
                BambdaGenerator.failureMessage(new PromptException("rate limited")));
    }

    @Test
    void wrappedPromptExceptionIsUnwrapped() {
        // SwingWorker.get() surfaces the real failure wrapped in an ExecutionException.
        Throwable wrapped = new ExecutionException(new PromptException("boom"));
        assertEquals("Burp AI could not generate a response: boom",
                BambdaGenerator.failureMessage(wrapped));
    }

    @Test
    void nonPromptFailureFallsBackToAGenericMessage() {
        Throwable wrapped = new ExecutionException(new IllegalStateException("bad state"));
        assertEquals("Generation failed: bad state", BambdaGenerator.failureMessage(wrapped));
    }

    @Test
    void nullContentBecomesEmpty() {
        assertEquals("", BambdaGenerator.extractCode(null));
    }

    @Test
    void unfencedContentIsReturnedTrimmed() {
        assertEquals("return requestResponse.request().isInScope();",
                BambdaGenerator.extractCode("  return requestResponse.request().isInScope();  "));
    }

    @Test
    void stripsFenceWithLanguageTag() {
        String fenced = "```java\nreturn requestResponse.request().isInScope();\n```";
        assertEquals("return requestResponse.request().isInScope();",
                BambdaGenerator.extractCode(fenced));
    }

    @Test
    void stripsFenceWithoutLanguageTag() {
        assertEquals("return x;", BambdaGenerator.extractCode("```\nreturn x;\n```"));
    }

    @Test
    void keepsMultiLineBodyIntact() {
        String fenced = "```java\nif (requestResponse.hasResponse()) {\n    return true;\n}\nreturn false;\n```";
        assertEquals("if (requestResponse.hasResponse()) {\n    return true;\n}\nreturn false;",
                BambdaGenerator.extractCode(fenced));
    }

    @Test
    void ignoresProseAroundTheFence() {
        String reply = "Here is your Bambda:\n```java\nreturn true;\n```\nHope this helps!";
        assertEquals("return true;", BambdaGenerator.extractCode(reply));
    }

    @Test
    void singleLineBareFenceKeepsTheReturn() {
        // Regression: the old parser dropped the body for single-line fences, losing the return.
        assertEquals("return x;", BambdaGenerator.extractCode("```return x;```"));
    }

    @Test
    void codeOnFenceLineIsNotSilentlyDiscarded() {
        // Can't cleanly separate a language tag from inline code here, but the body must survive.
        assertEquals("java return x;", BambdaGenerator.extractCode("```java return x;\n```"));
    }

    @Test
    void loneUnclosedFenceIsLeftUntouched() {
        assertEquals("```java\nreturn x;", BambdaGenerator.extractCode("```java\nreturn x;"));
    }
}
