# Model and MCP integration plan

Companion to `AGENTIC_PROTOCOLS.md`. Covers the model layer (Ollama primary, hosted providers secondary) and the MCP client direction.

## Design constraints

Two goals that pull against each other, so they get named up front:

1. **Demoable with zero setup.** Clone, run Ollama, start the app. No API keys, no accounts, nothing to sign up for. This is also what makes the project credible as open source.
2. **Production capable.** A hosted provider behind the local one, with real failure handling.

The resolution is Spring profiles, not two codebases. The `demo` profile runs Ollama alone. The `prod` profile adds a fallback chain. The application code is identical in both because everything goes through `ChatClient`.

## Provider strategy

Three starters cover every case discussed, including the work scenario:

| Starter | Covers |
|---|---|
| `spring-ai-starter-model-ollama` | Primary. Local small model, no network, no credentials. |
| `spring-ai-starter-model-anthropic` | Claude API. |
| `spring-ai-starter-model-openai` | OpenAI, Azure AI Foundry, and **any corporate OpenAI-compatible gateway** via a `base-url` override. |

That third row is the important one. An enterprise abstraction layer in front of OpenAI or Azure AI Foundry is not a fourth integration, it's the OpenAI starter pointed at a different host. Worth building that way deliberately so the work case needs configuration rather than code.

Note on GitHub Copilot specifically: GitHub Models was retired on 30 July 2026, taking the inference API and BYOK with it. Copilot is not callable as a general chat API from a backend. Where Copilot fits in this architecture is as an **MCP client** driving Armature from outside, which is phase 4 below.

## Phase 1: Make the assistant real

The highest-leverage change in the whole plan, because one refactor unblocks three separate things.

Convert `AgentToolRegistry`'s `list_boards` and `add_gadget` to `@Tool`-annotated methods on a registered bean. Today the MCP server logs `No tool methods found in the provided tool objects: []` at boot because they aren't Spring AI tools. After the conversion the same annotated methods are simultaneously:

- visible over MCP to external clients
- callable by whatever `ChatModel` is configured
- available to satisfy A2A's bean requirements

Then add Ollama and wire `AgentService` to a real `ChatClient`:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-ollama</artifactId>
</dependency>
```

```properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.model=qwen3.5:4b
spring.ai.ollama.init.pull-model-strategy=when_missing
```

Model candidates worth benchmarking against the real tool set: Qwen3.5 4B, Nemotron Nano 4B, Ministral-3-3B, and Phi-4-mini (MIT licensed, which matters if this ships).

**Exit criteria:** `/api/agent/chat` returns a model-generated response that actually calls a tool, with no API key anywhere in the config.

**Status: done**, and hardened past the original exit criteria. Real usage against qwen3.5:4b surfaced a reliability gap the exit criteria didn't cover: the model would sometimes call an extra, unrelated tool alongside the one actually requested (e.g. "remove the bar chart" also triggering `list_boards` or `add_row`, unprompted). Prompt instructions ("only call a tool that directly corresponds to what was asked") reduced but didn't eliminate this; disabling the model's extended thinking made it measurably worse, not better. What actually fixed it is a deterministic cap in `AgentToolCallRecorder`: at most one tool call is recorded per request regardless of how many the model attempts, with any further attempt returning a deflection message instead of silently acting. This trades away genuine multi-part requests in one message ("add a chart and remove another") for never doing something nobody asked for - see `AgentToolCallRecorder.canRecord()`'s javadoc.

## Phase 2: Structured output as the correctness mechanism

This is what makes a 4B model viable rather than merely cheap, and it deserves to be built early rather than bolted on.

`library.json` already carries each gadget's property-page schema. Convert that to JSON Schema at request time and hand it to the decoder rather than to the prompt:

```java
OllamaChatOptions.builder()
    .outputSchema(schemaForGadget(componentType))
    .build()
```

Tokens that would break the shape are never sampled, so a malformed gadget config becomes structurally impossible instead of merely unlikely. The equivalent on the hosted providers is their structured output or tool-schema mode, so this needs a small abstraction rather than an Ollama-specific call.

**Exit criteria:** a fuzz run of a few hundred generations produces zero configs that fail `library.json` validation.

**Status: done**, scoped to Ollama rather than built as a cross-provider abstraction yet - `AgentService.enrichAddGadgetPartsWithPropertyValues` calls `OllamaChatOptions.outputSchema()` directly. No fuzz harness was built; correctness has been exercised via the live-model integration tests in `AgentServiceTest` instead (e.g. `addGadgetRequestPopulatesStructuredPropertyValues` asserting the real nested `chartData` shape). Also needed `.disableThinking()` alongside `outputSchema()` - qwen3.5:4b's default extended chain-of-thought fought the grammar constraint into runaway generation otherwise (observed directly: 6000+ tokens vs ~2-3s with thinking disabled, for the same prompt).

## Phase 3: Provider abstraction and fallback

Multiple `ChatModel` beans, named `ChatClient` wrappers, selection by configuration:

```java
@Bean @Primary
ChatClient localChatClient(OllamaChatModel model) { return ChatClient.create(model); }

@Bean
ChatClient hostedChatClient(AnthropicChatModel model) { return ChatClient.create(model); }
```

Then a delegating `ChatClient` that tries local first and falls through on failure or timeout, active only under the `prod` profile. Spring Retry plus a circuit breaker is the conventional shape; Spring AI has no built-in cross-provider fallback as of now, so this is application code.

Two traps to watch:

- **Autoconfiguration conflicts.** With several model starters on the classpath, Spring AI needs `spring.ai.model.chat` to disambiguate which one autoconfigures as primary. Getting several active at once for fallback purposes needs verification against Spring AI 2.0 specifically, since most published examples target 1.x.
- **Fallback silently becoming the default.** If Ollama is misconfigured in production, a fallback chain will quietly send every request to a paid API. Instrument the fallback rate and alarm on it.

**Exit criteria:** killing Ollama mid-session degrades to the hosted provider without a user-visible error, and the switch is visible in metrics.

**Status: partial.** `spring-ai-starter-model-anthropic` is on the classpath alongside Ollama's, and `spring.ai.model.chat` (env var `AGENT_CHAT_MODEL`, plus `ANTHROPIC_API_KEY`) selects which one autoconfigures - confirmed directly against Spring AI 2.0's actual bytecode (`@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "ollama", matchIfMissing = true)` on `OllamaChatAutoConfiguration`, mirrored on Anthropic's with `havingValue = "anthropic"`), settling the "needs verification" trap above: this is a genuine either/or, not "several active at once" - only one `ChatModel` bean ever exists. That's as far as this phase went. Still missing everything the exit criteria actually asks for: no delegating/fallback `ChatClient`, no retry or circuit breaker, no `demo`/`prod` profiles, no OpenAI-compatible starter, no fallback-rate metrics. What exists today is a boot-time manual toggle for comparing providers, not runtime fallback - killing Ollama mid-session currently just errors rather than degrading to Anthropic. Built this far specifically to test whether a larger hosted model is more reliable at strict tool-call scoping than the small local one (see Phase 1's status note).

## Phase 4: MCP client, the "apps developed by others" piece

Armature is currently an MCP server. Consuming third-party context and apps means also being an MCP **client**.

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

```properties
spring.ai.mcp.client.type=SYNC
spring.ai.mcp.client.toolcallback.enabled=true
spring.ai.mcp.client.streamable-http.connections.example.url=http://localhost:8080
```

Discovered tools arrive as a `ToolCallbackProvider` and can be handed straight to the `ChatClient`, which means a third-party MCP server's capabilities become things the dashboard assistant can call without any per-server code. Use `McpToolNamePrefixGenerator` to avoid collisions with Armature's own tools, and `McpToolFilter` to keep an untrusted server from flooding the tool list.

Keeping the server side too is worthwhile and cheap, since that's how Claude Desktop, Claude Code, and Copilot drive the dashboard from outside. The two directions are independent.

**Caveat worth scoping before committing:** Spring AI's MCP client integration is tools-centric. Resource and prompt support exists mainly as change notifications rather than a documented path for pulling a `ui://` resource out and rendering it. Consuming MCP Apps may need the MCP Java SDK directly rather than the Spring starter. Worth a spike before promising it.

## Phase 4.5: Armature as an MCP Apps producer (not in the original plan)

Phases 4 and 5 as originally scoped were both about *consuming* other servers' MCP Apps. There's a third direction neither anticipated: Armature's own MCP server (already built in Phase 1) producing an MCP App of its own, for an external host to render - no client-side work needed at all, since the host does the rendering.

`present_board_summary` (`com.addf.backend.armature.mcpapp.BoardSummaryApp`) is that: a read-only `@McpTool` paired with a `ui://armature/board-summary.html` `@McpResource`, returning an interactive gadget list instead of the plain text/JSON-intent shape every other tool returns. It exists specifically to demo the tool-type distinction side by side in a real MCP Apps host (Claude Desktop, Claude.ai, VS Code Copilot, etc. - see the [client matrix](https://modelcontextprotocol.io/extensions/client-matrix)): ask it to mutate the board and get a plain reply; ask it to show the board and get a rendered app.

**Status: done**, scoped narrowly. Notes for whoever picks up Phase 4 or 5 next:

- **Two tool-registration mechanisms now coexist.** `AgentToolRegistry`'s six tools are Spring AI `@Tool` methods surfaced via a `MethodToolCallbackProvider` bean (`AgentToolConfig`) - the mechanism Phase 1 built. `present_board_summary` uses `@McpTool`/`@McpResource` from `spring-ai-mcp-annotations` instead, a separate annotation-scanning path that ships inside `spring-ai-starter-mcp-server-webmvc` as of 2.0.0-M3 specifically to carry MCP Apps' `_meta.ui` metadata, which the older `ToolCallback` abstraction has no field for. Confirmed directly (not just from docs) that both register onto the same underlying MCP server with no conflict - `McpServerAutoConfiguration` logged `Registered tools: 7` and `Registered resources: 1` at boot with both mechanisms present.
- **The `@McpTool` annotation's default `annotations()` hints are wrong for a read-only tool** - `readOnlyHint` defaults `false` and `destructiveHint` defaults `true`. Left alone, a host that surfaces these (e.g. a confirmation prompt before calling) would flag `present_board_summary` as destructive, undermining the exact distinction it exists to demonstrate. `BoardSummaryApp` overrides them explicitly (`readOnlyHint=true, destructiveHint=false, idempotentHint=true, openWorldHint=false`); any future `@McpTool` should set these deliberately rather than trust the default.
- **The UI resource is a single self-contained HTML file**, not a separate HTML+JS+CSS trio - `@McpResource` methods return one string, and a `ui://` resource has no base URL a relative `<script src>` could resolve against once the host loads it (typically into a sandboxed iframe via `srcdoc`, not a real HTTP-served page). `board-summary.html` inlines the official `@modelcontextprotocol/ext-apps` browser bundle (`app-with-deps.js`, vendored at `src/main/resources/mcp-apps/`, ~340KB minified) as a base64 `data:` URI loaded via dynamic `import()`, rather than hand-rolling the `ui/initialize` postMessage handshake - avoids both a fragile reimplementation of SEP-1865's JSON-RPC dialect and a live CDN dependency at demo time. Verified the bundle loads and exports `App` correctly via a Node ESM smoke test; the full handshake against a real host (Claude Desktop) is the one verification step this couldn't do headlessly and is still owed before calling this fully proven.
- **`BoardSnapshotStore` is a stopgap, not new fetch infrastructure.** The backend still has no independent view of board state (see `AgentToolRegistry`'s class javadoc). Rather than build a pull path, `AgentController` now also feeds the `boardGadgets` list `armature-ui` already sends on every `/api/agent/chat` message into an in-memory, boardId-keyed cache; `present_board_summary` reads the most-recently-updated entry (no `boardId` argument, since an external client has no way to supply one). This means the data is only as fresh as the last chat message sent from Armature's own panel, not truly live - acceptable for a demo, not for Phase 4/5's eventual "real app" bar.

What this phase deliberately does **not** attempt: consuming a third-party server's MCP App (still Phase 4/5, still unstarted), and rendering *any* MCP App - Armature's own or third-party's - inside Armature's own chat panel, which needs `armature-ui` to become an MCP Apps *host* (an `AppBridge` instance wired to a sandboxed iframe, per the [official host guide](https://modelcontextprotocol.io/extensions/apps/overview#framework-support)) - a genuinely separate, larger piece of frontend work, not a byproduct of this one. A further idea raised alongside this work - a board-display mode that renders gadgets as MCP Apps generated from the gadget library rather than native Angular components - is a distinct, bigger initiative on top of that and isn't scoped here either.

## Phase 5: MCP Apps into the panel

The Angular side already has the landing zone. `AgentUiPart` supports an `iframe` type with `src` and `title`, and the panel renders it. So a third-party MCP App becomes: fetch the `ui://` resource from the connected server, serve it to the browser, emit an `iframe` part pointing at it.

The work here is mostly security rather than plumbing: iframe sandbox attributes, an origin allowlist, and a `postMessage` bridge that validates message shape before letting an embedded app call back into board state. An MCP App is third-party code running inside the dashboard, so this phase should not be rushed for a demo.

## Phase 6: AG-UI and A2UI

As already captured in `AGENTIC_PROTOCOLS.md`. AG-UI replaces the hand-rolled streaming on `/api/agent/chat`; A2UI renders at the `a2ui-card` seam. Both are more valuable once phases 1 through 3 give the assistant something real to stream.

**Status: done**, taken deliberately out of this order - moved up ahead of phases 3 through 5 by explicit request, before Phase 3 was more than partially built. No official AG-UI Java SDK turned out to be resolvable from Maven Central under any candidate groupId, so AG-UI's event shapes are hand-rolled plain records (`com.addf.backend.armature.agent.agui`) rather than a dependency, serialized over `SseEmitter`. `/api/agent/chat` now streams real token-level deltas from whichever `ChatModel` is active (RUN_STARTED, TEXT_MESSAGE_START/CONTENT/END, TOOL_CALL_START/ARGS/END, CUSTOM ui-part events, RUN_FINISHED) instead of the old blocking single-JSON response. A2UI's component-catalog rendering (`A2uiComponent` on the backend, `A2uiRendererComponent` on the frontend) is built and working, but not currently wired to anything live - `add_gadget` briefly required a confirm/cancel click through it, then went back to auto-applying immediately after the extra click proved to be unwanted friction in practice. Kept in place for a future flow that genuinely needs a confirm step, e.g. a destructive remove.

Real streaming also surfaced a bug worth recording here since it's not specific to any one phase: `AgentToolCallRecorder` was `@RequestScope`, which is ThreadLocal-backed and broke the moment tool execution could happen on a Reactor worker thread with no servlet request bound to it (`ScopeNotActiveException`, silently dropped by Reactor, hanging every request until the client's read timeout). Fixed by making it a plain per-request object threaded through `AgentToolRegistry`'s tool methods via Spring AI's `ToolContext` instead of relying on request-scope machinery - worth remembering for any future `@RequestScope` bean that a streaming/reactive call path might touch off the original request thread.

## Suggested order

Phases 1 and 2 together produce a working, demoable, key-free assistant, and they're the ones that most change how the project reads to a newcomer. Phase 3 is what makes it deployable. Phase 4 is the one that delivers the "consolidate other applications" thesis, and it's the most interesting to write about once it works.

Phase 5 carries the most risk relative to its demo value and should follow rather than lead.

**Actual order taken so far:** 1, 2, 6, then partial 3, then 4.5 (this doc's suggested order for 1 and 2 held; 6 was pulled forward ahead of 3-5 by explicit request, 3 only got as far as the provider toggle described in its status note above, and 4.5 - not in the original plan at all - was done next by explicit request, ahead of finishing 3 or starting 4/5). 4 and 5 remain entirely unstarted.
