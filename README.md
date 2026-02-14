# Ask-to-API Engine

Ask-to-API Engine is a Spring Boot application that helps you browse and understand APIs from Swagger/OpenAPI specifications using natural language.

**Current focus:**

- Loading Swagger specs
- Building an internal API catalog
- Browsing/searching endpoints (text + NLQ)
- Preparing the foundation for future API insights & orchestration

This project is meant as an AI-aware backend utility that will later evolve into a full NLQ-to-API router, but for the moment it is intentionally limited to API discovery and exploration.

---

## Current Scope (Phase 1 - API Browse Only)

### 1. Swagger / OpenAPI Loading

- Load one or more Swagger/OpenAPI documents (local file, URL, or Git repo in future).
- Parse:
  - Paths & HTTP methods
  - Operation IDs
  - Parameters, request bodies, responses
  - Tags, summaries, descriptions
- Normalize everything into an internal API catalog model.

### 2. API Catalog & Search

- Build an in-memory representation of all available endpoints.
- Support basic filtering & search:
  - By path
  - By HTTP method
  - By tag
  - By free-text (summary, description, operationId)
- Expose REST endpoints:
  - `GET /api/catalog/endpoints`
  - `GET /api/catalog/endpoints/{id}`
  - `GET /api/catalog/search?q=...`

### 3. NLQ-Assisted Browse (Early AI Use)

- Use Spring AI + OpenAI to interpret natural language queries like:
  - *"Show me all endpoints that return index levels"*
  - *"What APIs do we have for order details?"*
- Map the query to:
  - Tags
  - Keywords in descriptions
  - Likely relevant endpoints in the catalog
- **Important:** In this phase, the app does not call the real backend APIs; it only helps discover them.

---

## Tech Stack

| Category | Technology |
|----------|------------|
| Framework | Spring Boot 3.x (Java 17) |
| Web | Spring Web, Spring WebFlux |
| AI | Spring AI - OpenAI (`gpt-4o-mini`, `text-embedding-3-small`) |
| Vector DB | Spring AI - PGVector |
| Database | PostgreSQL + PGVector |
| Data Access | JDBC API (`spring-boot-starter-jdbc`) |
| API Parsing | Swagger Parser v3 |
| Utilities | Lombok, DevTools, Validation, Actuator |
| Build | Maven |

---

## Project Structure

```
ask-to-api-engine/
├── src/main/java/com/asktoapiengine/engine/ai/browse/
│   ├── api/                  # REST controllers (BrowseController)
│   ├── config/               # Spring config (OpenAI, VectorStore, AiBrowse)
│   ├── core/                 # Business logic (BrowseService, formatting, models)
│   ├── llm/                  # LLM integration (WebClient-based OpenAI calls)
│   ├── rag/                  # Vector store indexing & semantic retrieval
│   └── swagger/              # Swagger/OpenAPI parsing & catalog
├── src/main/resources/
│   ├── application.yaml      # App configuration
│   └── swagger/              # Sample Swagger spec files (const, corp-actions, levels)
├── src/test/                 # Unit tests
├── pom.xml
└── README.md
```

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.x
- PostgreSQL with PGVector extension
- OpenAI API key

### Configuration

Set the following in `application.yaml` or as environment variables:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
      embedding:
        options:
          model: text-embedding-3-small
```

### Run

```bash
./mvnw spring-boot:run
```

The application starts on port **8080** by default.

---

## Roadmap

### Phase 2 - API Execution (Planned)

1. **Request Planning** - From an NLQ, identify target endpoints and required parameters. Ask the user for missing values if needed.
2. **Safe API Invocation** - Invoke backend APIs using WebClient with pluggable authentication, pagination, error handling, and timeouts.
3. **Raw Result Delivery** - Return raw JSON or lightly normalized structures. Log all invocations for observability.

### Phase 3 - API Insights Layer (Planned)

1. **Result Normalization** - Normalize responses into a consistent internal model. Optionally store recent results for short-term context.
2. **Insight Generation** - Use the LLM to summarize responses, extract key metrics, and answer follow-up questions grounded in fetched data (RAG pattern).
3. **Multi-API Orchestration** - Combine multiple endpoints into a single answer. Define simple recipes/workflows for common insight patterns.
4. **Guardrails & Observability** - Rate limiting, safety rules, metrics & traces via Actuator / OpenTelemetry.

---

## Design Intention for AI Assistants

This repository is designed so that LLM-based copilots can:

- Recognize that the current scope is limited to API browsing (no real backend calls yet).
- Understand that Swagger/OpenAPI parsing and API catalog are core concepts, and NLQ is currently used only for discovery/search, not execution.
- Propose future code that aligns with the planned phases (Phase 2: invocation, Phase 3: insights).
