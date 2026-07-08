# AI Bambda Generator

A Burp Suite extension that uses [Burp AI](https://portswigger.net/burp/documentation/desktop/extend-burp/extensions/creating/ai-extensibility) to generate [Bambdas](https://portswigger.net/burp/documentation/desktop/bambdas) from a plain-language description. Pick the kind of Bambda you want, describe what it should do, and the extension returns ready-to-paste Java for the snippet body.

> **Disclaimer:** This extension is intended as a **starting point**, not a guarantee. AI-generated Bambdas will not always be correct or complete on the first attempt — treat the output as a draft to review, test, and refine rather than a finished snippet. Always check the generated code before running it against real traffic.

## Features

- **Generates Bambdas for every supported context** — table filters, custom columns, Repeater custom actions, match-and-replace rules, and custom scan checks (see the full list below).
- **Context-aware prompts** — each Bambda type sends the AI the correct input variable, its type, and the required return value, so the generated code targets the right API.
- **Adjustable temperature** — a spinner (0.0–2.0, default 0.2) controls how focused or varied the output is. Low values keep generation deterministic and accurate; raise it for more variety.
- **Editable output** — the result appears in a Burp editor you can tweak before copying.
- **Copy to clipboard** — one click to grab the generated snippet.
- **Per-type memory** — your description and last result are remembered separately for each Bambda type while Burp is running, so switching types doesn't lose your work.

## Requirements

- **Burp Suite 2025.2 or later** (the release that introduced the AI extensibility API).
- **Burp AI enabled for the extension.** Enable AI features in Burp's settings; without it, generation is disabled and the extension explains why.

## Installation

<!--
### From the BApp Store

Search for "AI Bambda Generator" in **Extensions > BApp Store** and click **Install**.
-->

### From a local build

1. Build the extension JAR (see below).
2. In Burp, go to **Extensions > Installed > Add**.
3. Set **Extension type** to **Java** and select `build/libs/AiBambdaGenerator-1.0.0.jar`.

## Usage

1. Open the **Bambda Generator** tab.
2. Choose the **Bambda type** that matches where you'll use the snippet.
3. Describe the purpose of the Bambda in plain language (e.g. *"show only in-scope requests that returned a 500 status"*).
4. Optionally adjust the **Temperature**.
5. Click **Generate**.
6. Review and edit the generated code, then **Copy output** and paste it into Burp's Bambda editor.

## Supported Bambda types

| Category | Type |
|---|---|
| Table filter | Proxy HTTP history, Proxy WebSockets history, Site map, Logger (view and capture) |
| Custom column | Proxy HTTP history, Proxy WebSockets, Logger |
| Custom action | Repeater |
| Match and replace | Request, Response |
| Custom scan check | Passive (per-request or per-host) and active (per-request, per-host, or per-insertion-point), with an optional Collaborator client |

## Building

This project uses Gradle with the [Montoya API](https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html).

```bash
./gradlew build
```

The extension JAR is written to `build/libs/`. The Montoya API is referenced as `compileOnly` (pinned to `2025.6`) because Burp provides it at runtime — there are no bundled dependencies.

## How it works

The extension sends two messages to Burp AI: a system message describing what a Bambda is and the conventions for the selected type, and a user message containing your description. It uses Burp AI as the provider (no third-party configuration), runs the request off the Swing event dispatch thread to keep the UI responsive, and surfaces any failure (including AI being disabled) without disrupting Burp.

The AI's response is treated as untrusted text: it is shown only in a plain-text editor — never executed by the extension — and any surrounding Markdown code fence is stripped before display.

## License

See [LICENSE](LICENSE).
