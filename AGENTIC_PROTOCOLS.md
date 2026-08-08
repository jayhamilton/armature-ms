# Agentic protocol integration

Tracking the effort to expose this service's dashboard tools over MCP, A2A, and AG-UI.

## Done

- Added `org.springframework.ai:spring-ai-bom:2.0.0` (compatible with this project's Spring Boot 4.1.0).
- Added `spring-ai-starter-mcp-server-webmvc` — MCP server, matches the app's existing servlet/MVC stack. Boots cleanly; autoconfiguration enables tools/resources/prompts/completions capabilities.
- Added `org.springaicommunity:spring-ai-a2a-server-autoconfigure:0.3.0` — A2A server support (community-maintained, not an official `org.springframework.ai` module).
- Existing hand-rolled `com.addf.backend.armature.agent` package (`AgentController`, `AgentService`, `AgentToolRegistry`) defines two placeholder tools — `list_boards`, `add_gadget` — behind a custom `/api/agent/chat` REST endpoint, not yet connected to Spring AI.

## Next steps

1. **Wire the existing tools into Spring AI's `@Tool` model.** Right now the MCP server logs `No tool methods found in the provided tool objects: []` at boot because `AgentToolRegistry`'s tools aren't `@Tool`-annotated Spring AI beans. Converting `list_boards`/`add_gadget` (or their real implementations) to `@Tool` methods on a registered bean is what will actually make them visible over MCP.
2. **Activate A2A.** Its autoconfiguration is on the classpath but inactive — it needs `ChatClient`, `AgentCard`, and `AgentExecutor` beans defined before it exposes `/.well-known/agent-card.json` and starts handling A2A messages.
3. **Resolve AG-UI coordinates before adding it.** Two candidates were found, neither confirmed against Maven Central:
   - `com.ag-ui:core` / `client` / `http` (v0.0.1) — official but very early; publish location undocumented.
   - `com.ag-ui.community:spring-ai` / `spring-server` (v1.0.1) — has a Spring AI-backed `LocalAgent`, but appears to be distributed via GitHub Packages rather than Maven Central, which would need extra `<repository>`/auth config in `pom.xml`.
   Check both repos' current READMEs directly before picking one — this space was moving fast as of research time (Aug 2026).
4. **Set MCP server config** in `application.properties` (currently empty) — e.g. `spring.ai.mcp.server.name`, transport/endpoint settings — once there's something real to expose.
5. **Decide the relationship between `/api/agent/chat` and the MCP/A2A-exposed tools** — keep it as a simple custom chat API alongside the protocol-standard interfaces, or fold it into one of them.
