import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.RawEditor;

import javax.swing.*;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;

public class GeneratorPanel extends JPanel {
    /** Burp's highlight orange, and a darker shade shown while hovering. */
    private static final Color HIGHLIGHT = new Color(0xFF6633);
    private static final Color HIGHLIGHT_HOVER = HIGHLIGHT.darker();

    /**
     * Temperature bounds accepted by the Montoya AI API, and the default the spinner starts at.
     * A low default keeps code generation focused and deterministic, which PortSwigger recommends
     * for accurate, reliable security-related output.
     */
    private static final double MIN_TEMPERATURE = 0.0;
    private static final double MAX_TEMPERATURE = 2.0;
    private static final double DEFAULT_TEMPERATURE = 0.2;

    private final MontoyaApi api;
    private final BambdaGenerator generator;

    private final JComboBox<BambdaType> typeCombo;
    private final JTextArea descriptionArea;
    private final JSpinner temperatureSpinner;
    private final JButton generateButton;
    private final JButton copyButton;
    private final RawEditor outputEditor;

    /** In-memory input/output per Bambda type. Not persisted across Burp reloads. */
    private final Map<BambdaType, BambdaState> states = new EnumMap<>(BambdaType.class);
    private BambdaType currentType;

    public GeneratorPanel(MontoyaApi api) {
        this.api = api;
        this.generator = new BambdaGenerator(api.ai());

        typeCombo = new JComboBox<>(BambdaType.values());
        currentType = selectedType();
        typeCombo.addActionListener(e -> onTypeChanged());

        temperatureSpinner = buildTemperatureSpinner();

        generateButton = highlightButton("Generate", this::generate);
        setButtonActive(generateButton, true);

        descriptionArea = new JTextArea(5, 60);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        outputEditor = api.userInterface().createRawEditor(EditorOptions.WRAP_LINES);
        outputEditor.setEditable(true);

        copyButton = highlightButton("Copy output", this::copyOutput);
        updateCopyButtonState();

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildDescriptionPanel(), buildOutputPanel());
        split.setResizeWeight(0.3);
        split.setBorder(null);

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(buildControls(), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }

    /**
     * Top row: "Bambda type:" label and dropdown on the left; temperature spinner and generate
     * button on the right.
     */
    private JPanel buildControls() {
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(new JLabel("Bambda type:"));
        left.add(typeCombo);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(new JLabel("Temperature:"));
        right.add(temperatureSpinner);
        right.add(generateButton);

        JPanel controls = new JPanel(new BorderLayout());
        controls.add(left, BorderLayout.WEST);
        controls.add(right, BorderLayout.EAST);
        return controls;
    }

    /**
     * A spinner constrained to [{@value #MIN_TEMPERATURE}, {@value #MAX_TEMPERATURE}] in 0.1 steps.
     * The model bounds the up/down arrows; the formatter clamps typed input to the same range so
     * out-of-range values can never reach the prompt.
     */
    private static JSpinner buildTemperatureSpinner() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                DEFAULT_TEMPERATURE, MIN_TEMPERATURE, MAX_TEMPERATURE, 0.1));

        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0.0");
        NumberFormatter formatter = (NumberFormatter) editor.getTextField().getFormatter();
        formatter.setMinimum(MIN_TEMPERATURE);
        formatter.setMaximum(MAX_TEMPERATURE);
        formatter.setAllowsInvalid(false);
        spinner.setEditor(editor);
        return spinner;
    }

    private JPanel buildDescriptionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JLabel("Describe the purpose of the Bambda:"), BorderLayout.NORTH);
        panel.add(new JScrollPane(descriptionArea), BorderLayout.CENTER);
        return panel;
    }

    /** Editable, line-wrapped output editor with a header row carrying the copy button. */
    private JPanel buildOutputPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel("Generated Bambda:"), BorderLayout.WEST);
        header.add(copyButton, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(header, BorderLayout.NORTH);
        panel.add(outputEditor.uiComponent(), BorderLayout.CENTER);
        return panel;
    }

    private void generate() {
        if (!generator.isEnabled()) {
            JOptionPane.showMessageDialog(this,
                    "Burp AI is not enabled for this extension. Enable AI features in Burp's settings.",
                    "AI unavailable", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String description = descriptionArea.getText().trim();
        if (description.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please describe the purpose of the Bambda.",
                    "Missing description", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setGenerating(true);
        generateAsync(selectedType(), description, selectedTemperature());
    }

    /** Prompts Burp AI off the event thread, then shows the generated code (or an error dialog). */
    private void generateAsync(BambdaType type, String description, double temperature) {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return generator.generate(type, description, temperature);
            }

            @Override
            protected void done() {
                try {
                    showOutput(get());
                } catch (Exception ex) {
                    showGenerationError(ex);
                } finally {
                    setGenerating(false);
                }
            }
        }.execute();
    }

    /** Toggles the generate button between its idle and in-flight (disabled, relabelled) states. */
    private void setGenerating(boolean generating) {
        setButtonActive(generateButton, !generating);
        generateButton.setText(generating ? "Generating…" : "Generate");
    }

    /** Replaces the editor contents and refreshes the copy button. */
    private void showOutput(String code) {
        outputEditor.setContents(ByteArray.byteArray(code));
        updateCopyButtonState();
    }

    private void showGenerationError(Exception ex) {
        api.logging().logToError("Bambda generation failed", ex);
        JOptionPane.showMessageDialog(this, BambdaGenerator.failureMessage(ex),
                "Generation failed", JOptionPane.ERROR_MESSAGE);
    }

    private BambdaType selectedType() {
        return (BambdaType) typeCombo.getSelectedItem();
    }

    private double selectedTemperature() {
        return ((Number) temperatureSpinner.getValue()).doubleValue();
    }

    /**
     * Snapshots the current input/output under the previously-selected type, then restores the
     * newly-selected type's snapshot (or clears the fields if it has none yet).
     */
    private void onTypeChanged() {
        BambdaType selected = selectedType();
        if (selected == currentType) {
            return;
        }

        saveCurrentState();
        currentType = selected;

        BambdaState restored = states.getOrDefault(selected, new BambdaState());
        descriptionArea.setText(restored.description);
        showOutput(restored.output);
    }

    private void saveCurrentState() {
        BambdaState state = states.computeIfAbsent(currentType, k -> new BambdaState());
        state.description = descriptionArea.getText();
        state.output = outputEditor.getContents().toString();
    }

    private void copyOutput() {
        String output = outputEditor.getContents().toString();
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(output), null);
    }

    /**
     * Enables a button and gives it Burp's highlight orange, or disables it and restores the
     * default look. A disabled button is never highlighted, keeping the look consistent across
     * both action buttons.
     */
    private static void setButtonActive(AbstractButton button, boolean active) {
        button.setEnabled(active);
        if (active) {
            // Base colour; the hover handler darkens it while the cursor is over the button.
            button.setBackground(HIGHLIGHT);
            button.setForeground(Color.WHITE);
            button.setOpaque(true);
            button.setBorderPainted(false);
        } else {
            button.setBackground(null);
            button.setForeground(null);
            button.setOpaque(false);
            button.setBorderPainted(true);
        }
    }

    /**
     * Creates a button wired to {@code onClick}, with a hover effect that darkens the highlight
     * orange while the cursor is over it rather than letting the look-and-feel paint its default
     * rollover colours.
     */
    private static JButton highlightButton(String text, Runnable onClick) {
        JButton button = new JButton(text);
        button.addActionListener(e -> onClick.run());
        button.setRolloverEnabled(false);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(HIGHLIGHT_HOVER);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(HIGHLIGHT);
                }
            }
        });
        return button;
    }

    /** The copy button is only active when the editor has content to copy. */
    private void updateCopyButtonState() {
        setButtonActive(copyButton, outputEditor.getContents().length() > 0);
    }

    /** Holds the input description and generated output for a single Bambda type. */
    private static final class BambdaState {
        private String description = "";
        private String output = "";
    }
}