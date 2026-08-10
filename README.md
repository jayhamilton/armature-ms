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
| GET | `/sse` | Spring AI MCP server transport (SSE) — exposes the 6 registered `@Tool` methods (`list_boards`, `add_gadget`, `move_gadget`, `remove_gadget`, `add_row`, `change_row_layout`) to MCP clients |
| POST | `/mcp/message` | Companion MCP transport endpoint to `/sse` |

## Project dependencies

This microservice uses the following core dependencies:

- Spring Boot 4.1.0 and Spring Boot starter modules for web, webflux, REST client, actuator, and testing
- springdoc-openapi (Swagger UI / OpenAPI spec generation)
- Spring AI MCP and A2A integration libraries for agent and tool server capabilities
- Jackson and JSON Path for JSON processing and payload handling
- Spring REST Docs support for API documentation tests

## Agent and model integration status

The conversational assistant is real, not a stub: Ollama-backed tool-calling (`list_boards`, `add_gadget`, `move_gadget`, `remove_gadget`, `add_row`, `change_row_layout`) and schema-constrained structured output are both done, and `/api/agent/chat` streams real AG-UI events as the model actually generates them rather than returning one blocking response. An Anthropic-backed alternative is wired in for comparison (see [Model provider](#model-provider)), though only as a manual toggle — automatic fallback, MCP client support (consuming third-party servers), and MCP Apps rendered in the panel are not yet built.

See [`MODEL_INTEGRATION.md`](MODEL_INTEGRATION.md) for the full phased plan with a status note on each phase, and [`AGENTIC_PROTOCOLS.md`](AGENTIC_PROTOCOLS.md) for MCP/A2A/AG-UI protocol specifics.

## Notes

- The Angular frontend and the Spring Boot backend are typically run separately during development.
- If you are serving the compiled frontend from the backend, copy the frontend build output into `src/main/resources/static/` before packaging.


