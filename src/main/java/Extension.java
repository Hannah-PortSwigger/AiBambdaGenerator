import burp.api.montoya.BurpExtension;
import burp.api.montoya.EnhancedCapability;
import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import java.util.Set;

import static burp.api.montoya.EnhancedCapability.AI_FEATURES;

public class Extension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("AI Bambda Generator");

        JPanel panel = new GeneratorPanel(api);
        api.userInterface().registerSuiteTab("Bambda Generator", panel);

        api.logging().logToOutput("""
                AI Bambda Generator loaded.

                ✧˚･ﾟ。 ──────────────── ｡ﾟ･˚✧

                AI is non-deterministic — expect results to vary and the first attempt not \
                always to be right. Regenerate, tweak your description, or adjust the temperature. \
                For better results, describe the behaviour clearly and include concrete details \
                (header/parameter names, status codes, URL patterns).

                Something not working as expected? (╯°□°)╯︵ ┻━┻
                Please raise an issue (pull requests welcome): \
                https://github.com/Hannah-PortSwigger/AiBambdaGenerator/issues""");
    }

    @Override
    public Set<EnhancedCapability> enhancedCapabilities() {
        return Set.of(AI_FEATURES);
    }
}
