<img src="https://github.com/jayhamilton/armature-ms/blob/main/documentation/logo.png?raw=true" alt="Armature logo" width="96">

# armature-ms

This is the backend service for Armature, the Angular UI runtime, in your local workspace at:

[../armature-ui](../armature-ui)

The service is built with Spring Boot 4.1.0 and targets Java 25.

## Prerequisites

- Java 25 JDK installed and available on your `PATH`
- The project uses the Maven wrapper, so a separate Maven installation is not required
- If you want the backend to serve the Angular frontend assets directly, build the frontend and copy the generated files into `src/main/resources/static/`

## Build and test

From the project root, run:

```bash
./mvnw -q test
./mvnw -q clean package
```

The packaged application will be created in `target/`.

## Run the service

Start the service locally with:

```bash
java -jar target/armature-ms-0.2.4.jar
```

Or, for development mode:

```bash
./mvnw spring-boot:run
```

The API will be available at:

- `http://localhost:8080`

### Model provider

The assistant defaults to a local [Ollama](https://ollama.com) model (`qwen3.5:4b`, see `spring.ai.ollama.chat.model` in `application.properties`), so it runs with no API key or account. An Anthropic-backed alternative is available for comparison, toggled via environment variables:

```bash
export AGENT_CHAT_MODEL=anthropic   # defaults to "ollama" if unset
export ANTHROPIC_API_KEY=<your key>
./mvnw spring-boot:run
```

Only one provider is ever active at a time — Spring AI's autoconfiguration for each is mutually exclusive on `spring.ai.model.chat`, not a fallback chain, so this is a manual toggle for comparison rather than automatic failover. The schema-constrained structured-output call used to auto-populate `add_gadget`'s property values is Ollama-specific and quietly skips itself when a different provider is active.

## API documentation

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI spec (JSON): `http://localhost:8080/v3/api-docs`
- Basic service info: `http://localhost:8080/actuator/info`
- Health check: `http://localhost:8080/actuator/health`

## Currently exposed endpoints

Everything below is live on `http://localhost:8080` today (confirmed against `/actuator/mappings`). It'll drift as the agent phases land, so treat this as a snapshot rather than a contract.

| Method | Path | Purpose |
|---|---|---|
| GET | `/hello` | Sanity-check endpoint |
| POST | `/api/agent/chat` | Chat with the dashboard assistant — streams a hand-rolled AG-UI event sequence over SSE (see [Model provider](#model-provider)) rather than returning one JSON response; see [Swagger UI](#api-documentation) for the request/event shape |
| GET | `/swagger-ui.html` | Redirects to the Swagger UI |
| GET | `/v3/api-docs` | OpenAPI spec, JSON |
| GET | `/v3/api-docs.yaml` | OpenAPI spec, YAML |
| GET | `/actuator` | Actuator index |
| GET | `/actuator/health` | Liveness/readiness check |
| GET | `/actuator/info` | App name, description, version |
| GET | `/sse` | Spring AI MCP server transport (SSE) — exposes 7 tools to MCP clients: 6 board-mutation `@Tool` methods (`list_boards`, `add_gadget`, `move_gadget`, `remove_gadget`, `add_row`, `change_row_layout`) plus `present_board_summary`, a read-only `@McpTool` that renders as an MCP App (see below) |
| POST | `/mcp/message` | Companion MCP transport endpoint to `/sse` |

## Project dependencies

This microservice uses the following core dependencies:

- Spring Boot 4.1.0 and Spring Boot starter modules for web, webflux, REST client, actuator, and testing
- springdoc-openapi (Swagger UI / OpenAPI spec generation)
- Spring AI MCP and A2A integration libraries for agent and tool server capabilities
- Jackson and JSON Path for JSON processing and payload handling
- Spring REST Docs support for API documentation tests

## Agent and model integration status

The conversational assistant is real, not a stub: Ollama-backed tool-calling (`list_boards`, `add_gadget`, `move_gadget`, `remove_gadget`, `add_row`, `change_row_layout`) and schema-constrained structured output are both done, and `/api/agent/chat` streams real AG-UI events as the model actually generates them rather than returning one blocking response. An Anthropic-backed alternative is wired in for comparison (see [Model provider](#model-provider)), though only as a manual toggle — automatic fallback is not yet built.

See [`MODEL_INTEGRATION.md`](MODEL_INTEGRATION.md) for the full phased plan with a status note on each phase, and [`AGENTIC_PROTOCOLS.md`](AGENTIC_PROTOCOLS.md) for MCP/A2A/AG-UI protocol specifics.

## MCP Apps

`present_board_summary` is Armature's first [MCP App](https://modelcontextprotocol.io/extensions/apps/overview) — [SEP-1865](https://modelcontextprotocol.io/seps/1865-mcp-apps-interactive-user-interfaces-for-mcp), final since 2026-01-26. Where a normal MCP tool call returns text or JSON, an MCP App tool also declares a `ui://` resource: an interactive HTML view that the *host* (not Armature) renders inline, sandboxed in an iframe, communicating back over a JSON-RPC/postMessage bridge.

Armature already exposes 6 board-mutation tools over MCP (`list_boards`, `add_gadget`, `move_gadget`, `remove_gadget`, `add_row`, `change_row_layout`) — all plain text/JSON-intent replies. `present_board_summary` is deliberately different: it's read-only (never changes the board) and renders as a clickable, expandable gadget list instead of text. Calling both from the same MCP client side by side is the point — it demonstrates the tool-type distinction, not just that MCP Apps work in isolation.

**How it's built:** `com.addf.backend.armature.mcpapp.BoardSummaryApp` pairs an `@McpTool` with a `@McpResource` (`spring-ai-mcp-annotations`, on the classpath transitively via `spring-ai-starter-mcp-server-webmvc` since Spring AI 2.0.0-M3) — a separate registration path from the `@Tool`/`ToolCallbackProvider` mechanism the other 6 tools use, though both compose onto the same MCP server without conflict. The UI resource (`src/main/resources/mcp-apps/board-summary.html`) is a single self-contained file — a `ui://` resource has no base URL for a relative `<script src>` to resolve against once a host loads it — with the official [`@modelcontextprotocol/ext-apps`](https://github.com/modelcontextprotocol/ext-apps) browser SDK inlined as a base64 `data:` URI rather than hand-rolled, so it speaks the real postMessage protocol.

**Data source:** boards only ever lived in the browser's `localStorage` (see [Currently exposed endpoints](#currently-exposed-endpoints) — there's no board persistence in this service). `BoardSnapshotStore` caches the `boardGadgets` list `armature-ui` already sends with every `/api/agent/chat` message, in memory, keyed by board id. **This means a board must be chatted with at least once from Armature's own panel before an external MCP client can see it** — the tool shows "no board synced yet" until then.

**Try it:**

1. Start this service (`./mvnw spring-boot:run`) and `armature-ui`, then send at least one chat message from Armature's assistant panel so a board snapshot exists.
2. Point any [MCP Apps-capable host](https://modelcontextprotocol.io/extensions/client-matrix) — Claude, Claude Desktop, VS Code GitHub Copilot, Goose, Postman, MCPJam, Archestra.AI — at `http://localhost:8080/sse` as a remote MCP server (check that host's own docs for the exact config syntax for a URL-based server).
3. Ask it to mutate the board ("add a bar chart") — a plain reply. Then ask it to show the board ("what's on my dashboard?") — `present_board_summary` renders inline.

For quick protocol-level testing without a full host, the [MCP Inspector CLI](https://github.com/modelcontextprotocol/inspector) works without any config:

```bash
npx @modelcontextprotocol/inspector --cli http://localhost:8080/sse --method tools/list
npx @modelcontextprotocol/inspector --cli http://localhost:8080/sse --method tools/call --tool-name present_board_summary
npx @modelcontextprotocol/inspector --cli http://localhost:8080/sse --method resources/read --uri "ui://armature/board-summary.html"
```

**Not yet built:** consuming a third-party server's MCP App (Armature as an MCP *client*), and rendering any MCP App — Armature's own or a third party's — inside Armature's own chat panel (needs `armature-ui` to become an MCP Apps *host*). See `MODEL_INTEGRATION.md`'s Phase 4.5 section for the full account, including bugs hit along the way worth knowing about before extending this.

## Notes

- The Angular frontend and the Spring Boot backend are typically run separately during development.
- If you are serving the compiled frontend from the backend, copy the frontend build output into `src/main/resources/static/` before packaging.


