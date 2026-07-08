import burp.api.montoya.ai.Ai;
import burp.api.montoya.ai.chat.Message;
import burp.api.montoya.ai.chat.PromptException;
import burp.api.montoya.ai.chat.PromptOptions;
import burp.api.montoya.ai.chat.PromptResponse;

/**
 * Generates Bambda source by prompting Burp AI. Owns the prompt assembly and response parsing so
 * the UI layer only deals with threading and display. Kept free of Swing so its logic — notably
 * {@link #extractCode(String)} — can be unit-tested without a UI.
 */
final class BambdaGenerator {
    private final Ai ai;

    BambdaGenerator(Ai ai) {
        this.ai = ai;
    }

    /** Whether Burp AI is available; {@link #generate} will fail if this is false. */
    boolean isEnabled() {
        return ai.isEnabled();
    }

    /**
     * Prompts Burp AI for a Bambda of the given type and returns the code, stripped of any Markdown
     * fence the model may have added.
     *
     * @throws burp.api.montoya.ai.chat.PromptException if Burp AI cannot produce a response
     */
    String generate(BambdaType type, String description, double temperature) {
        PromptResponse response = ai.prompt().execute(
                PromptOptions.promptOptions().withTemperature(temperature),
                Message.systemMessage(type.systemPrompt()),
                Message.userMessage(description));
        return extractCode(response.content());
    }

    /**
     * Turns whatever the background worker threw into a user-facing message. SwingWorker wraps the
     * real failure in an ExecutionException, so the cause is unwrapped first; a PromptException
     * (AI unavailable, rate-limited, backend error) gets a Burp-AI-specific message.
     */
    static String failureMessage(Throwable thrown) {
        Throwable cause = thrown.getCause() != null ? thrown.getCause() : thrown;
        return cause instanceof PromptException
                ? "Burp AI could not generate a response: " + cause.getMessage()
                : "Generation failed: " + cause.getMessage();
    }

    /**
     * Returns the Java inside a Markdown code fence (e.g. {@code ```java ... ```}), or the trimmed
     * input when there is no fence. Defensive: the model is asked not to fence its answer, but
     * sometimes does anyway.
     */
    static String extractCode(String content) {
        if (content == null) {
            return "";
        }

        String trimmed = content.strip();
        int open = trimmed.indexOf("```");
        if (open < 0) {
            return trimmed;                     // no fence — the model followed instructions
        }
        int close = trimmed.lastIndexOf("```");
        if (close == open) {
            return trimmed;                     // a lone, unclosed fence — leave it untouched
        }

        String inner = trimmed.substring(open + 3, close);
        int firstBreak = inner.indexOf('\n');
        if (firstBreak >= 0 && isInfoString(inner.substring(0, firstBreak))) {
            inner = inner.substring(firstBreak + 1);   // drop the opening ```-line's language tag
        }
        return inner.strip();
    }

    /**
     * Whether a fence's opening line is only an info string — blank or a bare language token such
     * as {@code java} — and so safe to drop. A line carrying anything else (e.g. code on the same
     * line as the fence) is kept, so we never silently discard the body.
     */
    private static boolean isInfoString(String openingLine) {
        String token = openingLine.strip();
        return token.isEmpty() || token.matches("[A-Za-z0-9+#._-]+");
    }
}
