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
    }

    @Override
    public Set<EnhancedCapability> enhancedCapabilities() {
        return Set.of(AI_FEATURES);
    }
}
