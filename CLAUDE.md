# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **armature-ms**, the Spring Boot backend for **Armature**, a runtime for interfaces that are described rather than built. The companion Angular front end is **armature-ui**, normally checked out as a sibling directory (`../armature-ui`).

The service backs the dashboard's conversational assistant. Armature's boards, layouts, and gadget instances persist to `localStorage` in the browser, so the UI runs standalone without this service; the assistant is what depends on it.

Both projects were formerly named NGX Dynamic Dashboard Framework (`ngxdd`, `ngx-dd-svc`, `plm-svc`). Use **Armature** and the `com.addf.backend.armature` package in all new code.

## Build and Run

**Critical: this project targets Java 25, and the system default JDK on the dev machine is Java 18.** Builds fail confusingly if `JAVA_HOME` is not set first.

```bash
export JAVA_HOME="$HOME/.jdks/jdk-25.0.2/jdk-25.0.2+10/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

./mvnw clean test          # tests
./mvnw spring-boot:run     # dev server on http://localhost:8080
./mvnw clean package       # produces target/armature-ms-0.2.4.jar
```

`../start-armature.sh` starts this service and the Angular UI together, handles the `JAVA_HOME` export, and tails both logs. Prefer it when working across both repos.

Swagger UI is at `/swagger-ui.html`. Actuator exposes `health` and `info` only.

## Architecture

Spring Boot 4.1.0, Java 25, Maven wrapper. Servlet stack (`spring-boot-starter-web`), though `spring-boot-starter-webflux` is also on the classpath; the MCP server starter chosen (`-webmvc`) deliberately matches the servlet stack.

### Source layout

```
com.addf.backend.armature
├── ArmatureApplication.java      Spring Boot main class
├── HelloController.java
├── config/OpenApiConfig.java     springdoc metadata
└── agent/                        the assistant
    ├── AgentController.java      POST /api/agent/chat
    ├── AgentService.java         reply logic (see below)
    ├── AgentToolRegistry.java    tool definitions + message parsing helpers
    ├── AgentRequest.java         { message, boardContext }
    ├── AgentBoardContext.java    { boardId, boardTitle, activeTab }
    ├── AgentResponse.java        { message, toolCalls, parts }
    ├── AgentUiPart.java          { id, type, text, componentType, payload }
    ├── ToolCall.java             { name, arguments }
    └── ToolDefinition.java       { name, description }
```

### AgentService is a keyword stub, not a model

`AgentService.chat()` contains **no LLM call of any kind**. It is deterministic `String.contains()` branching that returns canned replies and hardcoded UI parts. Do not mistake it for a working agent.

Branch order is load-bearing and there is a comment in the code explaining why: `move` is tested before `add`/`create`/`chart`, because "move the bar chart right" contains "chart" and would otherwise be misrouted to `add_gadget`. Preserve that ordering, or replace the whole dispatch with real tool calling.

`AgentToolRegistry` declares `list_boards` and `add_gadget` as `ToolDefinition` records. They are **not** Spring AI `@Tool` methods, which is why the MCP server logs `No tool methods found in the provided tool objects: []` at boot and exposes nothing.

## Frontend contract

`armature-ui`'s `AgentService` (`src/app/agent/agent.service.ts`) posts to `${environment.apihost}/api/agent/chat` and types the response as `{ message, toolCalls, parts }`. The panel renders `parts` by `type` and `componentType`.

Two known mismatches between the two sides. Verify before relying on either:

1. **`AgentUiPart` has no `src` or `title` field on the Java side.** The Angular interface declares both, and the panel renders `type: 'iframe'` parts using `part.src` and `part.title`. The backend record cannot currently produce a valid iframe part. This blocks MCP Apps work until the record gains those fields.

2. **`gadget-move` is undeclared in the OpenAPI schema.** `AgentService` emits `componentType: "gadget-move"` and the Angular panel handles it, but `AgentUiPart`'s `@Schema(allowableValues = ...)` lists only `gadget-suggestion`, `board-list`, and `a2ui-card`. The annotation is stale rather than the code being wrong.

Also note `payload` is a **JSON-encoded String** on the Java side, built by string concatenation in `AgentService`, while Angular types it as `unknown`. Values with quotes or braces in them will produce malformed JSON. Prefer serializing with Jackson over concatenating.

`AgentController` is annotated `@CrossOrigin` with no origin restriction. Fine for local dev against `localhost:4200`, needs tightening before any real deployment.

## Spring AI state

- `spring-ai-bom:2.0.0` imported, matching Spring Boot 4.1.0.
- `spring-ai-starter-mcp-server-webmvc` present and booting. Autoconfiguration enables tools/resources/prompts/completions capabilities, but no tools are registered yet (see above). `application.properties` has no `spring.ai.mcp.server.*` settings.
- `org.springaicommunity:spring-ai-a2a-server-autoconfigure:0.3.0` present but inactive. It needs `ChatClient`, `AgentCard`, and `AgentExecutor` beans before it will serve `/.well-known/agent-card.json`. Community-maintained, not an official `org.springframework.ai` module.
- **No model provider dependency and no `ChatModel` or `ChatClient` bean exists anywhere.** This is the root blocker: it is why the assistant is a stub and why A2A is inactive.

## Planned work

Two documents in this repo, both current:

- `AGENTIC_PROTOCOLS.md` — MCP, A2A, and AG-UI integration status and open questions.
- `MODEL_INTEGRATION.md` — the phased plan for the model layer (Ollama primary, Claude and OpenAI-compatible endpoints secondary) and the MCP client direction.

Phase 1 in `MODEL_INTEGRATION.md` is the highest-leverage change: converting `list_boards` and `add_gadget` to `@Tool` methods simultaneously makes them visible over MCP, callable by a configured model, and available to satisfy A2A's bean requirement.

## Deployment artifacts

- `systemd/dashboard.service` runs `/opt/SPP/lib/armature-ms.jar` on port 8084 (local dev uses 8080).
- `RPM/SPEC/dashboard.spec` defines `%define microservice armature-ms`.

Both reference the jar by the Maven `artifactId`, so renaming the artifact means updating all three files together.

`info.app.version=@project.version@` in `application.properties` relies on Maven resource filtering. It renders literally if the app is run outside a Maven build.

## Conventions

- Records for DTOs, constructor injection, no field injection.
- springdoc `@Schema` annotations on DTOs and `@Operation` on endpoints. Keep them accurate; see the stale `allowableValues` above for what happens otherwise.
- Historical Copilot Java-upgrade logs live under `.github/modernize/`. They contain old paths and package names by design. Do not rewrite them.
