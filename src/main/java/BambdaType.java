/**
 * The kinds of Bambda that Burp Suite supports. Each value carries the method signature Burp shows
 * in its Bambda editor (the return type, method name, and the variables the snippet is given), a
 * short note on the intended behaviour, and an API reference describing how to navigate the input.
 * These are combined with a shared base prompt to form the system prompt sent to Burp AI.
 *
 * <p>Showing the model the signature it is completing — rather than describing it in prose — lets
 * it produce just the method body, which is all Burp expects.
 *
 * <p>The taxonomy mirrors PortSwigger's official Bambda library:
 * table filters, table custom columns, Repeater custom actions, match and replace rules,
 * and custom scan checks.
 */
public enum BambdaType {
    // ---- Table filters — return a boolean: true shows the row ----
    PROXY_HTTP_FILTER(
            "Filter — Proxy HTTP history",
            "boolean matches(ProxyHttpRequestResponse requestResponse)",
            "Filters the Proxy > HTTP history table. Return true to keep the row visible, false to hide it.",
            Refs.PROXY_HTTP, false),

    PROXY_WEBSOCKET_FILTER(
            "Filter — Proxy WebSockets history",
            "boolean matches(ProxyWebSocketMessage message)",
            "Filters the Proxy > WebSockets history table. Return true to keep the message visible, false to hide it.",
            Refs.WEBSOCKET, false),

    SITEMAP_FILTER(
            "Filter — Site map",
            "boolean matches(SiteMapNode node)",
            """
            Filters the Target > Site map tree/table. Return true to show the node, false to hide it. \
            `node` exposes `node.url()`, `node.issues()` (audit issues found on it), and \
            `node.requestResponse()` for the underlying exchange — its response may be absent, so \
            guard with `node.requestResponse().hasResponse()`.""",
            Refs.HTTP, false),

    LOGGER_FILTER(
            "Filter — Logger view",
            "boolean matches(LoggerHttpRequestResponse requestResponse)",
            "Filters the Logger table. Return true to keep the row visible, false to hide it.",
            Refs.LOGGER, false),

    LOGGER_CAPTURE_FILTER(
            "Filter — Logger capture",
            "boolean matches(LoggerCaptureHttpRequestResponse requestResponse)",
            """
            Evaluated as each exchange is captured, before it is logged — distinct from the Logger \
            view filter, which only hides already-captured rows. Return true to capture the \
            exchange, false to drop it.""",
            Refs.LOGGER_CAPTURE, false),

    // ---- Table custom columns — return the cell value (Object) ----
    PROXY_HTTP_COLUMN(
            "Custom column — Proxy HTTP history",
            "Object cellValue(ProxyHttpRequestResponse requestResponse)",
            """
            Computes a custom column for the Proxy > HTTP history table. Return the cell value to \
            display — typically a String (return an empty string when there is nothing to show); \
            numbers and booleans also render.""",
            Refs.PROXY_HTTP, false),

    PROXY_WEBSOCKET_COLUMN(
            "Custom column — Proxy WebSockets",
            "Object cellValue(ProxyWebSocketMessage message)",
            """
            Computes a custom column for the Proxy > WebSockets history table. Return the cell value \
            to display — typically a String (return an empty string when there is nothing to show).""",
            Refs.WEBSOCKET, false),

    LOGGER_COLUMN(
            "Custom column — Logger",
            "Object cellValue(LoggerHttpRequestResponse requestResponse)",
            """
            Computes a custom column for the Logger table. Return the cell value to display — \
            typically a String (return an empty string when there is nothing to show).""",
            Refs.LOGGER, false),

    // ---- Repeater custom action ----
    REPEATER_CUSTOM_ACTION(
            "Custom action — Repeater",
            "void analyze(HttpRequestResponse requestResponse, RequestResponseSelection selection, HttpEditor httpEditor)",
            """
            Runs against the current Repeater tab. Do the work via side effects — send requests, log \
            output, transform the message, or prompt AI with `ai()`. There is no return value; use a \
            bare `return;` to exit early. `selection` is the user's current selection within the \
            message; `httpEditor` is the editor showing it (write changes back through its panes — \
            see the reference below).""",
            Refs.REPEATER_ACTION, true),

    // ---- Match and replace ----
    MATCH_REPLACE_REQUEST(
            "Match and replace — Request",
            "HttpRequest replace(ProxyHttpRequestResponse requestResponse)",
            """
            Applied as requests pass through the proxy. Return the request to forward — usually \
            `requestResponse.request()` transformed with builders like `withUpdatedHeader`, \
            `withRemovedHeader`, `withBody`, or `withPath`; return it unchanged when the rule does \
            not apply. There is no response yet at request time, so do not call `response()` or \
            `hasResponse()`.""",
            Refs.PROXY_HTTP, true),

    MATCH_REPLACE_RESPONSE(
            "Match and replace — Response",
            "HttpResponse replace(ProxyHttpRequestResponse requestResponse)",
            """
            Applied as responses pass through the proxy. Return the response to forward — usually \
            `requestResponse.response()` transformed with builders like `withUpdatedHeader`, \
            `withRemovedHeader`, or `withBody`; return it unchanged when the rule does not apply.""",
            Refs.PROXY_HTTP, true),

    // ---- Custom scan checks — return an AuditResult ----
    CUSTOM_SCAN_CHECK(
            "Custom scan check",
            "AuditResult doCheck(HttpRequestResponse requestResponse, AuditInsertionPoint insertionPoint, Http http, CollaboratorClient collaboratorClient)",
            """
            Runs in Scanner. The signature above is the fullest form (an active, per-insertion-point \
            check with the Collaborator option enabled); the kind you choose in Burp fixes which \
            variables you get, and simpler checks drop the parameters they do not use:
              - Passive (per request or per host): `doCheck(HttpRequestResponse requestResponse)` — \
            inspect `requestResponse` only and send no new requests.
              - Active (per request, per host, or per insertion point): also given `http` — \
            `http.sendRequest(request)` returns an HttpRequestResponse. A per-insertion-point check \
            additionally gets `insertionPoint` — read `insertionPoint.baseValue()` and build \
            payloads with `insertionPoint.buildHttpRequestWithPayload(ByteArray.byteArray(payload))`.
              - Enabling the Collaborator option on an active check appends a `collaboratorClient` \
            parameter for out-of-band detection: `collaboratorClient.generatePayload()` gives a \
            payload whose `.toString()` is the domain to embed in a request, and \
            `collaboratorClient.getAllInteractions()` (or `getInteractions(...)`) returns any \
            interactions it received.
            Every variant returns an `AuditResult`: `AuditResult.auditResult()` when nothing is \
            found, or `AuditResult.auditResult(AuditIssue.auditIssue(name, detail, remediation, \
            baseUrl, AuditIssueSeverity.<LEVEL>, AuditIssueConfidence.<LEVEL>, background, \
            remediationBackground, AuditIssueSeverity.<LEVEL>, requestResponse))` when an issue is \
            identified — `baseUrl` must be a valid absolute URL (e.g. `requestResponse.request().url()`) \
            and `name`, `detail`, `severity`, and `confidence` should describe the finding; pass null \
            only for extras you cannot fill (`remediation`, `background`, `remediationBackground`, \
            `typicalSeverity`). Evidence the issue with a marked request/response: mark BOTH the \
            injected payload in the request AND the response content that confirms the finding (the \
            reflected payload, error text, or whatever indicator you tested for). Mark the exchange \
            you actually sent (the `http.sendRequest` result), never the base `requestResponse`. \
            Request markers: pass the SAME `ByteArray` payload you gave `buildHttpRequestWithPayload` \
            to `insertionPoint.issueHighlights(payload)` (both take a `ByteArray`, not a String); it \
            returns a `List<Range>` for the built request, so map it with `Marker::marker` \
            (`issueHighlights(payload).stream().map(Marker::marker).toList()`). Response markers: \
            the offset must be a position in the WHOLE response — `int s = \
            sent.response().toByteArray().indexOf(indicator)` — NOT a `body()` or `bodyToString()` \
            index, which is relative to the body and lands the marker up in the headers; then \
            `Marker.marker(s, s + indicator.length())`. \
            `withRequestMarkers`/`withResponseMarkers` each return a new copy, so chain both — \
            `sent.withRequestMarkers(reqMarkers).withResponseMarkers(respMarkers)` — and pass that \
            single value into `auditIssue`. The scanner types (`AuditResult`, `AuditIssue`, \
            `AuditIssueSeverity`, `AuditIssueConfidence`, `AuditInsertionPoint`) are imported — use \
            them by simple name. \
            Because the check kind is chosen in Burp's UI rather than in the code, begin the snippet \
            with a comment naming the configuration it assumes — passive or active, the scope (per \
            request, per host, or per insertion point), and whether it uses Collaborator — so the \
            user knows which options to select, e.g. `// Config: active, per insertion point, uses \
            Collaborator`.""",
            Refs.HTTP, true);

    private final String displayName;
    private final String signature;
    private final String contextGuidance;
    private final String apiReference;
    private final boolean hasApi;

    BambdaType(String displayName, String signature, String contextGuidance, String apiReference, boolean hasApi) {
        this.displayName = displayName;
        this.signature = signature;
        this.contextGuidance = contextGuidance;
        this.apiReference = apiReference;
        this.hasApi = hasApi;
    }

    /**
     * Builds the full system prompt: the method shell Burp compiles the snippet into, the entry
     * points available in this context, the shared conventions (imports, utilities), the
     * context-specific behaviour note, and an API reference for navigating the input.
     */
    public String systemPrompt() {
        return """
                You are an expert in writing Bambdas for PortSwigger Burp Suite. A Bambda is a \
                self-contained snippet of Java that Burp compiles as the body of the following \
                method. Write ONLY the statements that go inside the braces — do not repeat the \
                method signature, and do not add package declarations, import statements, or any \
                extra methods, fields, or classes:

                    %s {
                        // your code here
                    }

                %s

                Burp's wrapper already imports these packages, so use their types by simple name: \
                java.util, java.util.regex, java.util.stream, java.util.function, java.time, \
                java.io, java.math; and the Montoya `core`, `http`, `http.message` (plus `params`, \
                `requests`, `responses`), `utilities`, and `logging` packages, along with this \
                Bambda's own context packages (the types named in the signature and API reference \
                below). Fully-qualify any type outside that set inline at every use — never assume \
                it is imported — e.g. `java.net.InetAddress.getByName(host)`, \
                `java.nio.charset.StandardCharsets.UTF_8`, `java.security.MessageDigest`, or any \
                java.awt/javax.swing class. Better still, avoid naming a type at all by reaching \
                values through accessor chains off the variable you are given (see the API \
                reference below).

                For encoding, decoding, hashing, and compression, prefer the Montoya `utilities()` \
                helpers over standard-library equivalents — they need no imports and work with \
                `ByteArray`/`String` directly:
                  - Base64: `utilities().base64Utils().decode(data)` and `.encode(data)` return a \
                ByteArray (use `.toString()` for text); `.encodeToString(data)` returns a String.
                  - URL: `utilities().urlUtils().decode(string)` and `.encode(string)`.
                  - HTML: `utilities().htmlUtils().decode(html)` and `.encode(html)`.
                  - Compression: `utilities().compressionUtils().decompress(data, type)` and \
                `.compress(data, type)`, where `type` is e.g. \
                `burp.api.montoya.utilities.CompressionType.GZIP`.
                  - Hashing: `utilities().cryptoUtils().generateDigest(data, \
                burp.api.montoya.utilities.DigestAlgorithm.SHA_256)` (returns a ByteArray).
                  - Also available via `utilities()`, each narrow in scope — do not assume methods \
                beyond these: `randomUtils()` (e.g. `randomUtils().randomString(length)`); \
                `byteUtils()` for raw `byte[]` (`convertToString(bytes)`, `convertFromString(string)`, \
                `indexOf(...)`, `countMatches(...)`); `stringUtils()`, which only converts ASCII \
                to/from hex (`convertAsciiToHexString(s)`, `convertHexStringToAscii(s)`) — for other \
                string work use plain Java; `numberUtils()` for base conversions between \
                binary/octal/decimal/hex (e.g. `convertHexToDecimal(s)`, `convertDecimalToHex(s)`, or \
                `convertHex(s, radix)`); and `jsonUtils()`, which reads and edits JSON by a location \
                path rather than parsing it into a Map or object (`readString(json, location)`, also \
                `read`, `readLong`, `readDouble`, `readBoolean`; `add(json, location, newJson)`, \
                `update(json, location, newJson)`, `remove(json, location)`, and `isValidJson(json)`). \
                Reach for these before java.util.Base64, java.net.URLDecoder, \
                java.security.MessageDigest, or java.util.zip.

                %s

                %s

                Respond with ONLY the Java code for the Bambda body. Do not include any explanation, \
                prose, or Markdown fences. Concise inline comments are welcome. Make the code \
                correct and ready to paste directly into Burp. Guard against null and missing \
                values: a response can be absent (check `hasResponse()`), and `headerValue(name)` \
                returns null when the header is absent, so guard it with `hasHeader(name)` (or \
                null-check the result) before using the value. A Bambda with a non-void return type \
                must produce its value with an explicit `return` statement, and every statement must \
                be terminated with a semicolon. Keep the code as simple and elegant as possible: prefer the most direct, \
                readable solution, avoid unnecessary variables, branching, or abstraction, and write \
                no more code than the task requires."""
                .formatted(signature, apiAvailability(), contextGuidance, apiReference);
    }

    /**
     * The sentence describing what entry points the snippet can call, which differs by context:
     * filters and custom columns run in a restricted wrapper with no {@code api()}, while
     * match/replace rules, custom actions, and scan checks receive the full Montoya API.
     */
    private String apiAvailability() {
        return hasApi
                ? "The Montoya API entry point is available as `api()` (also `api`), with "
                        + "convenience accessors `logging()` and `utilities()`."
                : "There is NO `api()` entry point in this context — only the `utilities()` and "
                        + "`logging()` helpers are available. Do not call `api()`, send HTTP "
                        + "requests, or use Collaborator; operate solely on the input variable you "
                        + "are given.";
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Reusable API-navigation references shared across Bambda types that receive the same input.
     * Held in a nested class so enum constants can reference them in their constructor arguments
     * (a value can't forward-reference a static field declared in the same enum, but a field in a
     * separate class is fine).
     */
    private static final class Refs {
        /** Navigation for an HttpRequestResponse and the request/response/service it exposes. */
        private static final String HTTP = """
                Navigate the input by chaining accessors off the provided variable rather than \
                naming types. From an HttpRequestResponse:
                  - `.request()` gives the HttpRequest; `.response()` gives the HttpResponse (may be \
                absent — guard with `.hasResponse()`); `.httpService()` gives the endpoint; \
                `.annotations()` gives notes/highlight; `.url()` and `.timingData()` are also available.
                  - HttpService: `.host()`, `.port()`, `.secure()` (true for HTTPS), and \
                `.ipAddress()` (the resolved server IP). For a message's server IP, use its \
                `httpService().ipAddress()`, e.g. `requestResponse.httpService().ipAddress()`.
                  - HttpRequest: `.method()`, `.url()`, `.path()`, `.query()`, `.fileExtension()`, \
                `.httpVersion()`, `.isInScope()`, `.body()`, `.bodyToString()`, `.parameters()`, \
                `.parameterValue(name)`, `.contains(text, caseSensitive)` (headers below). It is \
                immutable — derive a new one with \
                `.withAddedHeader(name, value)`, `.withUpdatedHeader`, `.withRemovedHeader`, \
                `.withBody`, `.withPath`, or `.withMethod`; build one with \
                `HttpRequest.httpRequest(service, text)` or `HttpRequest.httpRequestFromUrl(url)`.
                  - HttpResponse: `.statusCode()` (a short), `.reasonPhrase()`, `.mimeType()`, \
                `.statedMimeType()`, `.body()`, `.bodyToString()`, `.cookies()`, `.cookieValue(name)`, \
                `.contains(text, caseSensitive)` (headers below). It is immutable — derive a new one \
                with `.withStatusCode(code)`, `.withReasonPhrase(text)`, `.withBody(text)`, \
                `.withAddedHeader(name, value)`, `.withUpdatedHeader(name, value)`, or \
                `.withRemovedHeader(name)`.
                  - Headers (on request and response): to look up a single header prefer the direct \
                accessors over iterating — `.hasHeader(name)` tests presence, `.headerValue(name)` \
                returns its value String (null when absent, so guard or null-check), `.header(name)` \
                returns the whole HttpHeader, and `.hasHeader(name, value)` tests an exact match. \
                Stream `.headers()` — a `List<HttpHeader>`, each with `.name()`, `.value()`, and \
                `.toString()` (the full line) — only to scan or collect across multiple headers.
                  - Annotations: `.notes()`, `.setNotes(String)`, `.highlightColor()`, \
                `.setHighlightColor(HighlightColor.RED)`.
                  - Body as text: for string work on the body — regex, `substring`, `split`, \
                `contains`, or any String method — call `.bodyToString()` (a String), NOT `.body()`. \
                `.body()` returns a ByteArray (raw bytes, not a String), useful for byte-level work: \
                `.length()`, `.toString()` (decode to text), `.indexOf(text)`, `.countMatches(text)`, \
                `.subArray(start, end)`; build one with `ByteArray.byteArray("text")`.""";

        /** ProxyHttpRequestResponse — the HTTP navigation plus Proxy-only convenience accessors. */
        private static final String PROXY_HTTP = HTTP + """


                The variable is a ProxyHttpRequestResponse, which adds convenience accessors on top \
                of the above: `.method()`, `.path()`, `.url()`, `.host()`, `.port()`, `.secure()`, \
                `.mimeType()`, `.listenerPort()`, `.edited()`, `.time()`, `.finalRequest()`, and \
                `.originalResponse()`.""";

        /** HttpRequestResponse navigation plus the custom action's editor and selection variables. */
        private static final String REPEATER_ACTION = HTTP + """


                Besides `requestResponse`, the custom action provides two variables:
                  - `httpEditor` (an HttpEditor) has exactly two methods, `requestPane()` and \
                `responsePane()`, each returning a write-only EditorPane. Change what the user sees \
                by setting a pane's contents: `httpEditor.requestPane().set(request)` and \
                `httpEditor.responsePane().set(response)`. `set` is overloaded for a String, a \
                ByteArray, or any object (its `toString()` is used), so a derived HttpRequest or \
                HttpResponse can be passed straight in. `requestPane().replace(search, replacement)` \
                does a find-and-replace. There are NO getters and NO `setRequest`/`setResponse` \
                methods — read the message from `requestResponse` and write changes back through the \
                panes.
                  - `selection` (a RequestResponseSelection) is the user's current selection: guard \
                with `.hasRequestSelection()` / `.hasResponseSelection()`, then `.requestSelection()` \
                / `.responseSelection()` each give a selection whose `.contents()` is a ByteArray and \
                whose `.offsets()` exposes `.startIndexInclusive()` and `.endIndexExclusive()`.""";

        /** ProxyWebSocketMessage navigation. */
        private static final String WEBSOCKET = """
                The variable `message` is a ProxyWebSocketMessage:
                  - `.payload()` returns a ByteArray with the message content; use \
                `.payload().toString()` for text, or `.payload().length()`, \
                `.payload().indexOf(text)`, `.payload().countMatches(text)`.
                  - `.direction()` returns a `burp.api.montoya.websocket.Direction` — compare against \
                the fully qualified constants `burp.api.montoya.websocket.Direction.CLIENT_TO_SERVER` \
                and `burp.api.montoya.websocket.Direction.SERVER_TO_CLIENT`.
                  - `.upgradeRequest()` returns the HttpRequest that opened the connection; navigate \
                it with the usual accessors (`.url()`, `.path()`, `.headerValue(name)`, \
                `.httpService().host()`, etc.).
                  - `.annotations()` gives notes/highlight (`.setHighlightColor(HighlightColor.RED)`, \
                `.setNotes(String)`); `.webSocketId()`, `.time()`, `.listenerPort()`, and \
                `.contains(text, caseSensitive)` are also available.""";

        /** LoggerHttpRequestResponse navigation (the Logger table row type). */
        private static final String LOGGER = """
                The variable `requestResponse` is a LoggerHttpRequestResponse:
                  - `.request()` gives the HttpRequest; `.response()` gives the HttpResponse (may be \
                absent — guard with `.hasResponse()`); `.httpService()` gives the endpoint \
                (`.host()`, `.port()`, `.ipAddress()`).
                  - `.toolSource()` identifies the originating Burp tool: \
                `.toolSource().toolType()` returns a ToolType (e.g. `ToolType.PROXY`), and \
                `.toolSource().isFromTool(ToolType.SCANNER)` tests it.
                  - `.annotations()` (notes/highlight), `.time()` (a java.time.ZonedDateTime), \
                `.timingData()`, `.mimeType()`, `.pageTitle()`, and `.contains(text, caseSensitive)` \
                are also available.
                  - Navigate request/response details through `.request().<...>` and \
                `.response().<...>` — e.g. `.request().method()`, `.request().url()`, \
                `.response().statusCode()`, `.response().bodyToString()`.""";

        /** LoggerCaptureHttpRequestResponse navigation (the Logger capture-time row type). */
        private static final String LOGGER_CAPTURE = """
                The variable `requestResponse` is a LoggerCaptureHttpRequestResponse, evaluated at \
                capture time:
                  - `.request()` gives the HttpRequest; `.response()` gives the HttpResponse (may be \
                absent — guard with `.hasResponse()`); `.httpService()` gives the endpoint \
                (`.host()`, `.port()`, `.ipAddress()`).
                  - `.toolSource()` identifies the originating Burp tool: \
                `.toolSource().toolType()` returns a ToolType (e.g. `ToolType.PROXY`), and \
                `.toolSource().isFromTool(ToolType.SCANNER)` tests it.
                  - `.time()` (a java.time.ZonedDateTime), `.timingData()`, `.mimeType()`, \
                `.pageTitle()`, `.isSessionHandlingEvent()`, and `.contains(text, caseSensitive)` \
                are also available. There are no annotations at capture time.
                  - Navigate request/response details through `.request().<...>` and \
                `.response().<...>` — e.g. `.request().method()`, `.request().url()`, \
                `.response().statusCode()`, `.response().bodyToString()`.""";

        private Refs() {
        }
    }
}
