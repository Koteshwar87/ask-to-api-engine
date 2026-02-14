# CLAUDE.md - Project Reference for Ask-to-API Engine

## What This App Does

A Spring Boot app that loads Swagger/OpenAPI specs, indexes them into a vector store, and lets users ask natural language questions to discover relevant API endpoints. It does NOT call the actual APIs — browse/discovery only (Phase 1).

## Build & Run

- **Java 17**, **Maven**, **Spring Boot 3.5.7**
- Build: `./mvnw clean package`
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw test`
- Server starts on port **8080**
- Requires `OPENAI_API_KEY` environment variable

## Key Endpoint

```
POST /ai/browse
Content-Type: application/json

{ "query": "How do I get index levels for NIFTY 50?" }

Response: text/plain (LLM-generated explanation of matching endpoints)
```

## Architecture & Request Flow

```
BrowseController (POST /ai/browse)
  └─> BrowseService.handleBrowseQuery(query)
        ├─> SwaggerRetrievalService.retrieveRelevantOperations(query)  [RAG retrieval]
        │     ├─> VectorStore.similaritySearch(query, topK=5)
        │     └─> SwaggerApiCatalog.findByOperationId(id)  [map Document back to descriptor]
        └─> BrowseWebClientLlmService.getBrowseAnswer(query, candidates)  [active path]
              ├─> BrowsePromptBuilder.buildPrompt(query, candidates)
              └─> OpenAiWebClientLlmClient.generate(prompt)  [WebClient → OpenAI HTTP API]
```

## Startup Flow

1. **SwaggerLoader** scans `classpath:/swagger/*.json`, parses with swagger-parser-v3, creates `ApiOperationDescriptor` list
2. **SwaggerApiCatalog** (`@PostConstruct`) calls SwaggerLoader, builds in-memory map (operationId → descriptor)
3. **SwaggerDocumentIndexer** (`@PostConstruct`) converts descriptors → Spring AI `Document` objects via **SwaggerToDocumentMapper**, adds to **VectorStore**

## Package Layout

```
com.asktoapiengine.engine
├── AskToApiEngineApplication.java          # Main entry point
└── ai.browse
    ├── api/
    │   ├── BrowseController.java           # REST endpoint (POST /ai/browse)
    │   └── BrowseRequest.java              # { "query": "..." } DTO
    ├── config/
    │   ├── AiBrowseConfig.java             # Empty placeholder
    │   ├── OpenAiConfig.java               # WebClient bean for OpenAI (baseUrl, auth header)
    │   └── VectorStoreConfig.java          # SimpleVectorStore (in-memory) bean
    ├── core/
    │   ├── BrowseService.java              # Orchestrator: RAG retrieval → LLM
    │   ├── ApiMatch.java                   # Empty placeholder
    │   ├── BrowseResultFormatter.java      # Empty placeholder
    │   └── ParamDescriptor.java            # Empty placeholder
    ├── llm/
    │   ├── LlmClient.java                  # Interface: generate(prompt) → String
    │   ├── OpenAiWebClientLlmClient.java   # LlmClient impl using WebClient (ACTIVE)
    │   ├── BrowseWebClientLlmService.java  # Builds prompt + calls LlmClient (ACTIVE)
    │   ├── BrowseLlmService.java           # Alternative: uses Spring AI ChatModel (INACTIVE)
    │   ├── BrowsePromptBuilder.java        # Constructs structured LLM prompt
    │   └── LlmOutputSanitizer.java         # Empty placeholder
    ├── rag/
    │   ├── SwaggerDocumentIndexer.java     # Indexes swagger docs into VectorStore at startup
    │   └── SwaggerRetrievalService.java    # Similarity search → returns ApiOperationDescriptor list
    └── swagger/
        ├── SwaggerLoader.java              # Loads & parses classpath:/swagger/*.json
        ├── SwaggerApiCatalog.java          # In-memory catalog (ConcurrentHashMap), lookup by ID/tag/path
        ├── SwaggerToDocumentMapper.java    # ApiOperationDescriptor → Spring AI Document
        ├── ApiOperationDescriptor.java     # Core model: one endpoint + one HTTP method
        ├── ApiParameterDescriptor.java     # Parameter model: name, location, type, required, example
        └── ApiParameterLocation.java       # Enum: PATH, QUERY, HEADER, COOKIE
```

## Important Patterns & Conventions

- **Lombok everywhere**: `@Data`, `@RequiredArgsConstructor`, `@Slf4j`, `@Getter` — do not write boilerplate getters/setters
- **Two LLM paths exist**: `BrowseLlmService` (Spring AI ChatModel) and `BrowseWebClientLlmService` (raw WebClient). The WebClient path is currently active in `BrowseService`. The ChatModel path is commented out but kept for reference
- **LlmClient interface** abstracts the LLM provider — designed to swap OpenAI for other providers (e.g., SparkAssist) without touching higher-level services
- **SimpleVectorStore** (in-memory) is used for dev. PGVector dependency exists in pom.xml but is commented out
- **JDBC and PostgreSQL** dependencies are commented out in pom.xml — not yet needed
- **Several placeholder classes** exist with empty bodies: `AiBrowseConfig`, `ApiMatch`, `BrowseResultFormatter`, `ParamDescriptor`, `LlmOutputSanitizer` — these are scaffolding for future work
- **System.out/err** used in SwaggerLoader, SwaggerApiCatalog, SwaggerDocumentIndexer instead of SLF4J logger — should be migrated to `@Slf4j`
- **Prompt is financial-domain-specific**: the system prompt says "expert API assistant for a financial data platform"

## Config (application.yaml)

- OpenAI chat model: `gpt-4o-mini`
- OpenAI embedding model: `text-embedding-3-small`
- Logging: `INFO` for Spring AI, `DEBUG` for app code

## Swagger Sample Data

Three sample specs in `src/main/resources/swagger/`:
- `levels.json` — Index Levels API (historical index levels between dates)
- `const.json` — Constants/constituents API
- `corp-actions.json` — Corporate actions API

## Current Limitations / Known Issues

1. No tests beyond the default context-loads test
2. No error handling middleware / global exception handler
3. No input validation on BrowseRequest (no `@NotBlank` on query field)
4. Swagger specs must be on classpath — no dynamic loading
5. Vector store is in-memory — data lost on restart (re-indexed each startup)
6. Blocking `.block()` call on WebClient in OpenAiWebClientLlmClient
7. Several empty placeholder classes that may cause confusion
8. Mixed logging: some classes use `@Slf4j`, others use `System.out/err`

## Planned Phases

- **Phase 1 (current)**: Browse/discover APIs via NLQ
- **Phase 2**: Actually invoke the discovered APIs (WebClient, auth, pagination)
- **Phase 3**: Insights layer — summarize responses, multi-API orchestration, RAG over results
