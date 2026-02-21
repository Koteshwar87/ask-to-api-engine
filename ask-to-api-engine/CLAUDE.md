# CLAUDE.md - Ask-to-API Engine (Python)

## Build & Run

**With Docker (recommended):**
```bash
cd ask-to-api-engine
cp .env.example .env   # edit with your OPENAI_API_KEY
docker compose up --build
```

**Without Docker:**
```bash
cd ask-to-api-engine
python -m venv venv && venv\Scripts\activate
pip install -r requirements.txt
# Requires a running PostgreSQL with pgvector extension
uvicorn app.main:app --reload --port 8000
```

Requires `OPENAI_API_KEY` in `.env`. Server on port **8000**.

## Architecture & Request Flow

```
POST /ai/browse (routes.py)
  └─> browse_chain.ainvoke({"query": ...})
        ├─> retrieve_operations()       → PGVector similarity search → catalog lookup
        ├─> format_operations_context() → build prompt context string
        ├─> BROWSE_PROMPT              → ChatPromptTemplate (system + human)
        ├─> ChatOpenAI                 → LLM call
        └─> StrOutputParser            → plain text output
```

## Startup Flow (lifespan in main.py)

1. `Settings` loaded from `.env`
2. `OpenAIEmbeddings` + `PGVector` vector store (PostgreSQL)
3. `load_all_operations()` parses `swagger_specs/*.json`
4. `SwaggerCatalog` built (dict keyed by operationId)
5. `index_operations()` → LangChain Documents into PGVector
6. `ChatOpenAI` + `create_browse_chain()` → stored in `app.state`

## Package Layout

```
app/
├── main.py              # FastAPI app + lifespan
├── config.py            # Pydantic BaseSettings
├── dependencies.py      # Depends() → app.state
├── api/routes.py        # POST /ai/browse
├── swagger/
│   ├── models.py        # ApiOperationDescriptor, ApiParameterDescriptor, ApiParameterLocation
│   ├── loader.py        # load_all_operations(specs_dir)
│   └── catalog.py       # SwaggerCatalog (find_by_id, find_by_tag, get_all)
├── rag/
│   ├── indexer.py       # index_operations(operations, vector_store)
│   └── retriever.py     # retrieve_operations(query, vector_store, catalog, top_k)
└── chains/
    ├── prompts.py       # BROWSE_PROMPT + format_operations_context()
    └── browse_chain.py  # create_browse_chain(llm, vector_store, catalog)
```

## Patterns & Conventions

- **Pydantic everywhere** — domain models, request DTOs, settings
- **LCEL chain** — single composable chain, no orchestrator service class
- **FastAPI lifespan** — all init in async context manager, components stored in `app.state`
- **FastAPI `Depends()`** for DI — dependency functions pull from `app.state`
- **Functional style** — loader/indexer/retriever are module-level functions, not classes
- **PGVector** — persistent vector store backed by PostgreSQL + pgvector extension
- **Financial-domain prompt** — system prompt says "expert API assistant for a financial data platform"
- **No placeholders** — only files that serve a purpose

## Config (.env)

| Variable | Default | Required |
|----------|---------|----------|
| `OPENAI_API_KEY` | — | Yes |
| `CHAT_MODEL` | `gpt-4o-mini` | No |
| `EMBEDDING_MODEL` | `text-embedding-3-small` | No |
| `SWAGGER_SPECS_DIR` | `swagger_specs` | No |
| `DATABASE_URL` | `postgresql+psycopg://postgres:postgres@localhost:5432/ask_to_api` | No |
| `CORS_ORIGINS` | `http://localhost:3000,http://localhost:5173,http://localhost:8000` | No |

## Current Phase

**Phase 1 — Browse only.** App discovers APIs via NLQ. Does NOT call real backend APIs. See README.md for full roadmap (Phase 2: execution, Phase 3: insights).

---

## AI Collaboration Contract

Guidelines for AI-assisted development on this project. Each section is annotated with the phase it becomes relevant.

### Technology Standards

| Standard | Status | Phase |
|----------|--------|-------|
| Python 3.11+ | In use | 1 |
| FastAPI (latest stable) | In use | 1 |
| Pydantic v2 | In use | 1 |
| Uvicorn (dev) / Gunicorn (prod) | Uvicorn in use | 1 (Gunicorn: deployment) |
| LCEL chains | In use — sufficient for single-path flows | 1 |
| LangGraph | Not yet needed — required for multi-step agent workflows with branching | 2+ |
| PGVector (PostgreSQL + pgvector) | In use — persistent vector store via Docker | 1 |
| Structured logging (logging / structlog) | Standard `logging` in use | 1 (structlog: 2+) |
| Dockerized deployment | Not yet — add when deploying beyond local | 2+ |

No deprecated APIs. No experimental unstable shortcuts. No magic abstractions without explanation.

### Architecture Principles

**All phases:**
- Separation of concerns (swagger/, rag/, chains/, api/)
- Clear boundary between retrieval, planning, execution, and response layers
- LLM reasoning must never directly execute external APIs without validation

**Phase 2+:**
- Explicit state transitions in AI workflows (LangGraph)
- Deterministic validation layers before API execution
- Tool allowlists (no arbitrary API calls)

### Agent Design Rules — Phase 2+

When building agent workflows (Phase 2: execution, Phase 3: orchestration):
- Use explicit graph/state transitions (LangGraph)
- Support retries and error nodes
- Log: retrieved documents, planned API calls, final execution results
- Enforce JSON structured outputs from LLM
- Validate schema before tool execution
- No free-form uncontrolled tool calling

### RAG Guidelines — Phase 1 (active)

- OpenAPI specs chunked by endpoint, not arbitrary token size
- Metadata stored per document: endpoint path, HTTP method, tags, required parameters
- Retrieval returns top-k endpoints with score filtering (threshold-based)
- Specs normalized before embedding via `SwaggerToDocumentMapper` logic in `indexer.py`

### Security & Guardrails

**Phase 1 (active):**
- Enforce timeout on LLM calls (30s `asyncio.wait_for`)
- Never expose internal stack traces to client (HTTP 502/504 with safe messages)
- Validate API key at startup (`min_length=20`)

**Phase 2+:**
- Enforce API allowlists for execution
- Enforce retry policies on external API calls
- Validate parameters before execution
- Mask secrets in logs

### Code Quality Requirements — All phases

- Simple and readable — avoid over-engineering
- Type hints everywhere (modern `list[T]` / `dict[K,V]` syntax)
- Pydantic models for request/response DTOs
- Meaningful logging via `logging` module (no `print()`)
- Docstrings for complex logic only — don't add noise
- No unnecessary abstraction layers
- No placeholder files or dead code

### Observability — Phase 2+

When moving toward production:
- Request tracing (correlation IDs)
- Tool call logging
- Prompt logging (sanitized — no user PII)
- Latency tracking
- Failure categorization

Not needed for Phase 1 dev/exploration.

### Long-Term Vision — Phase 3+

ask-to-api-engine should evolve into:
- An AI API Gateway
- Intelligent API planner
- Tool-augmented reasoning engine
- Enterprise-safe AI orchestration layer

The system must remain modular so that:
- LLM providers can be swapped (via `LlmClient`-style abstraction)
- Vector DB can be swapped (PGVector → other vector stores)
- Execution engine can be extended

### Non-Negotiables — All phases

- No skipping logging
- No skipping input validation
- No silent assumptions — ask clarifying questions before large design changes
- No vague architecture — provide folder structure and trade-off analysis
- POC-style shortcuts acceptable in Phase 1 dev (in-memory stores, `allow_origins=["*"]`) but must be hardened before any deployment
