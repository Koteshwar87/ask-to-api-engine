# CLAUDE.md - Ask-to-API Engine (Python)

## What This App Does

A FastAPI app that loads Swagger/OpenAPI specs, indexes them into ChromaDB, and lets users ask natural language questions to discover relevant API endpoints. Browse/discovery only (Phase 1).

## Tech Stack

- **Python 3.11+**, **FastAPI**, **uvicorn**
- **LangChain LCEL** — chain composition (`prompt | llm | parser`)
- **langchain-openai** — `ChatOpenAI` + `OpenAIEmbeddings`
- **langchain-chroma** + **chromadb** — vector store
- **Pydantic v2** + **pydantic-settings** — models, validation, config

## Build & Run

```bash
cd ask-to-api-engine
python -m venv venv && venv\Scripts\activate
pip install -r requirements.txt
# Set OPENAI_API_KEY in .env
uvicorn app.main:app --reload --port 8000
```

## Key Endpoint

```
POST /ai/browse
Content-Type: application/json

{ "query": "How do I get index levels for NIFTY 50?" }

Response: text/plain (LLM-generated explanation of matching endpoints)
```

## Architecture & Request Flow

```
POST /ai/browse (routes.py)
  └─> browse_chain.ainvoke({"query": ...})
        ├─> retrieve_operations(query, vector_store, catalog)   [similarity search]
        │     ├─> Chroma.similarity_search(query, k=5)
        │     └─> SwaggerCatalog.find_by_id(op_id)             [map Document → descriptor]
        ├─> format_operations_context(operations)               [build prompt context]
        ├─> BROWSE_PROMPT (ChatPromptTemplate)                  [system + human messages]
        ├─> ChatOpenAI                                          [LLM call]
        └─> StrOutputParser                                     [extract string]
```

## Startup Flow (lifespan in main.py)

1. Load `Settings` from `.env` via pydantic-settings
2. Create `OpenAIEmbeddings` and `Chroma` vector store (in-memory)
3. `load_all_operations()` parses `swagger_specs/*.json` → list of `ApiOperationDescriptor`
4. `SwaggerCatalog` built from operations (dict keyed by operationId)
5. `index_operations()` converts to LangChain Documents, adds to ChromaDB
6. `ChatOpenAI` LLM initialized
7. `create_browse_chain()` wires the LCEL chain, stored in `app.state`

## Package Layout

```
app/
├── main.py                # FastAPI app, lifespan (startup/shutdown), router include
├── config.py              # Pydantic BaseSettings (OPENAI_API_KEY, model names, paths)
├── dependencies.py        # Depends() factories — get_browse_chain from app.state
├── api/
│   └── routes.py          # POST /ai/browse — thin handler, delegates to chain
├── swagger/
│   ├── models.py          # Pydantic: ApiOperationDescriptor, ApiParameterDescriptor, enums
│   ├── loader.py          # load_all_operations(specs_dir) → list[ApiOperationDescriptor]
│   └── catalog.py         # SwaggerCatalog: dict-based lookup (by id, tag)
├── rag/
│   ├── indexer.py         # operations → LangChain Documents → ChromaDB
│   └── retriever.py       # similarity_search → map back to ApiOperationDescriptor
└── chains/
    ├── prompts.py         # ChatPromptTemplate + format_operations_context()
    └── browse_chain.py    # LCEL chain: retrieve | format | prompt | llm | StrOutputParser
```

## Important Patterns & Conventions

- **Pydantic everywhere** — domain models, request DTOs, and settings all use Pydantic BaseModel/BaseSettings
- **LCEL chain** — browse logic is a single composable chain, no orchestrator service class
- **FastAPI lifespan** for startup/shutdown — replaces Spring's `@PostConstruct`
- **FastAPI `Depends()`** for DI — components stored in `app.state`, accessed via dependency functions
- **Functional style** — loader and indexer are module-level functions, not classes
- **ChromaDB in-memory** — data re-indexed each startup, no persistence
- **Prompt is financial-domain-specific** — system prompt says "expert API assistant for a financial data platform"
- **No empty placeholder files** — only create what's needed

## Config (.env)

- `OPENAI_API_KEY` — required
- `CHAT_MODEL` — default: `gpt-4o-mini`
- `EMBEDDING_MODEL` — default: `text-embedding-3-small`
- `SWAGGER_SPECS_DIR` — default: `swagger_specs`
- `CHROMA_COLLECTION_NAME` — default: `swagger_operations`

## Swagger Sample Data

Three specs in `swagger_specs/`:
- `levels.json` — Index Levels API (historical index levels between dates)
- `const.json` — Index Constituents API
- `corp-actions.json` — Corporate Actions API

## Planned Phases

- **Phase 1 (current)**: Browse/discover APIs via NLQ
- **Phase 2**: Actually invoke the discovered APIs
- **Phase 3**: Insights layer — summarize responses, multi-API orchestration
