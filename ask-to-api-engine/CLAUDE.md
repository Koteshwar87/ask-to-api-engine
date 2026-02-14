# CLAUDE.md - Ask-to-API Engine (Python)

## Build & Run

```bash
cd ask-to-api-engine
python -m venv venv && venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Requires `OPENAI_API_KEY` in `.env`. Server on port **8000**.

## Architecture & Request Flow

```
POST /ai/browse (routes.py)
  └─> browse_chain.ainvoke({"query": ...})
        ├─> retrieve_operations()       → Chroma similarity search → catalog lookup
        ├─> format_operations_context() → build prompt context string
        ├─> BROWSE_PROMPT              → ChatPromptTemplate (system + human)
        ├─> ChatOpenAI                 → LLM call
        └─> StrOutputParser            → plain text output
```

## Startup Flow (lifespan in main.py)

1. `Settings` loaded from `.env`
2. `OpenAIEmbeddings` + `Chroma` vector store (in-memory)
3. `load_all_operations()` parses `swagger_specs/*.json`
4. `SwaggerCatalog` built (dict keyed by operationId)
5. `index_operations()` → LangChain Documents into ChromaDB
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
- **ChromaDB in-memory** — re-indexed each startup, no persistence
- **Financial-domain prompt** — system prompt says "expert API assistant for a financial data platform"
- **No placeholders** — only files that serve a purpose

## Config (.env)

| Variable | Default | Required |
|----------|---------|----------|
| `OPENAI_API_KEY` | — | Yes |
| `CHAT_MODEL` | `gpt-4o-mini` | No |
| `EMBEDDING_MODEL` | `text-embedding-3-small` | No |
| `SWAGGER_SPECS_DIR` | `swagger_specs` | No |
| `CHROMA_COLLECTION_NAME` | `swagger_operations` | No |

## Current Phase

**Phase 1 — Browse only.** App discovers APIs via NLQ. Does NOT call real backend APIs. See README.md for full roadmap (Phase 2: execution, Phase 3: insights).
