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

## Phase 5: MCP Apps into the panel

The Angular side already has the landing zone. `AgentUiPart` supports an `iframe` type with `src` and `title`, and the panel renders it. So a third-party MCP App becomes: fetch the `ui://` resource from the connected server, serve it to the browser, emit an `iframe` part pointing at it.

The work here is mostly security rather than plumbing: iframe sandbox attributes, an origin allowlist, and a `postMessage` bridge that validates message shape before letting an embedded app call back into board state. An MCP App is third-party code running inside the dashboard, so this phase should not be rushed for a demo.

## Phase 6: AG-UI and A2UI

As already captured in `AGENTIC_PROTOCOLS.md`. AG-UI replaces the hand-rolled streaming on `/api/agent/chat`; A2UI renders at the `a2ui-card` seam. Both are more valuable once phases 1 through 3 give the assistant something real to stream.

## Suggested order

Phases 1 and 2 together produce a working, demoable, key-free assistant, and they're the ones that most change how the project reads to a newcomer. Phase 3 is what makes it deployable. Phase 4 is the one that delivers the "consolidate other applications" thesis, and it's the most interesting to write about once it works.

Phase 5 carries the most risk relative to its demo value and should follow rather than lead.
