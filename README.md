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
| POST | `/api/agent/chat` | Chat with the dashboard assistant; see [Swagger UI](#api-documentation) for the request/response shape |
| GET | `/swagger-ui.html` | Redirects to the Swagger UI |
| GET | `/v3/api-docs` | OpenAPI spec, JSON |
| GET | `/v3/api-docs.yaml` | OpenAPI spec, YAML |
| GET | `/actuator` | Actuator index |
| GET | `/actuator/health` | Liveness/readiness check |
| GET | `/actuator/info` | App name, description, version |
| GET | `/sse` | Spring AI MCP server transport (SSE) — present because `spring-ai-starter-mcp-server-webmvc` is on the classpath, but no `@Tool` beans are registered yet, so it has nothing to serve |
| POST | `/mcp/message` | Companion MCP transport endpoint to `/sse`, same caveat |

## Project dependencies

This microservice uses the following core dependencies:

- Spring Boot 4.1.0 and Spring Boot starter modules for web, webflux, REST client, actuator, and testing
- springdoc-openapi (Swagger UI / OpenAPI spec generation)
- Spring AI MCP and A2A integration libraries for agent and tool server capabilities
- Jackson and JSON Path for JSON processing and payload handling
- Spring REST Docs support for API documentation tests

## Enhancement phases for the framework

The agent/chat experience is being introduced in three incremental phases so the framework grows in a modular way:

### Phase 1: Structured chat and assistant responses

This phase adds a structured chat contract between the frontend and backend. Instead of the assistant replying with plain text only, the backend returns typed actions, suggested UI payloads, and follow-up prompts. This improves the framework by making the chat experience predictable, extensible, and easier to render in the dashboard UI.

### Phase 2: Dashboard and gadget actions

This phase teaches the assistant to create boards, suggest gadgets, and apply dashboard changes directly in the running UI. The framework becomes more capable because users can move from conversation to action without manually navigating the configuration panels. This makes the dashboard more conversational and reduces friction for common tasks.

### Phase 3: MCP app and tool integration

This phase adds support for surfacing MCP-backed apps and tools as part of the assistant response. The framework becomes more powerful because the chat panel can present external capabilities, app-like experiences, and tool-driven workflows alongside dashboard content. This creates a path to richer automation and more extensible integrations without forcing all functionality into the core UI.

## Notes

- The Angular frontend and the Spring Boot backend are typically run separately during development.
- If you are serving the compiled frontend from the backend, copy the frontend build output into `src/main/resources/static/` before packaging.


