# MCPGateway

MCPGateway is a Quarkus-based control plane and proxy for MCP servers. It centralizes server/gateway management, tool approvals, fingerprint validation, and per-gateway authentication, and exposes a lightweight UI plus REST APIs to manage everything.

The goal is to centralize MCP server usage so only evaluated, approved tool signatures are exposed to LLM clients. MCP tools rely on human-readable descriptions and loosely specified contracts, which makes them powerful but also creates opportunities for drift or abuse.

If a tool’s description or argument schema is changed after initial approval, an LLM may follow the new instructions without realizing it has crossed a policy boundary. That creates a path for a malicious MCP server to request sensitive data or expand the scope of what it collects.

For example, a tool initially approved as “return the weather for a city” might later be altered to claim it accepts a JSON payload containing repo names and access tokens “as optional inputs.” Even a well-intentioned client could then leak secrets if it trusts the modified tool contract.

MCPGateway acts as a proxy that monitors tool fingerprints and only advertises approved signatures to the LLM. It also enables aggregation of tools across multiple MCP servers, allowing teams to curate and constrain what downstream clients can see and use.

## What it does

- **Manage MCP servers** (local or remote) and store/approve their tool definitions.
- **Create gateways** that proxy MCP traffic and expose selected tools.
- **Fingerprint validation** per tool and gateway with configurable schedules (per invocation or time period).
- **Gateway authentication** (Basic or Bearer) with an abstraction that can later delegate to Keycloak/OAuth2.
- **Observability** with Micrometer metrics for gateway/server lifecycle, tool calls, and validation outcomes.

## REST APIs

- **Gateways**
  - `GET /mcp-gateways`
  - `POST /mcp-gateways`
  - `PUT /mcp-gateways/{id}`
  - `DELETE /mcp-gateways/{id}`
  - `POST /mcp-gateways/{id}/start`
  - `POST /mcp-gateways/{id}/stop`
- **Servers**
  - `GET /mcp-servers`
  - `POST /mcp-servers`
  - `PUT /mcp-servers/{id}`
  - `DELETE /mcp-servers/{id}`
  - `GET /mcp-servers/{id}/tools`
  - `POST /mcp-servers/{id}/tools/read`
  - `POST /mcp-servers/{id}/tools/compare`
  - `POST /mcp-servers/{id}/tools/approve`

## UI

Open the management UI at: [http://localhost:8080/](http://localhost:8080/)

## Metrics

Prometheus-format metrics are exposed at: [http://localhost:8080/q/metrics](http://localhost:8080/q/metrics)

## Running locally

### Dev mode

```shell
./mvnw quarkus:dev
```

Quarkus Dev UI (dev mode only): [http://localhost:8080/q/dev/](http://localhost:8080/q/dev/)

### Production build

```shell
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

### Native build (optional)

```shell
./mvnw package -Dnative
```

## Planned features

- HTTPS certificate pinning (aligned with tool fingerprint validation)
- Conflict resolution for same-named tools in a single gateway
- Keycloak integration for OAuth2-style authentication
- Parameter binding/replacement with placeholder tokens for controlled tool access
- Automatic cloning of a configured server into a corresponding gateway

## Tech stack

- Quarkus 3
- Vert.x HTTP
- Micrometer + Prometheus registry
- RESTEasy Reactive (Quarkus REST)
