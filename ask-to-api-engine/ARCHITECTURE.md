# Ask-to-API Engine — Architecture & Flow Guide

## What This App Does (Big Picture)

You have OpenAPI/Swagger JSON specs describing financial APIs. A user asks a natural language question like *"How do I get index levels for NIFTY 50?"* and the app:

1. **Searches** a vector database for the most relevant API endpoints
2. **Asks an LLM** (GPT-4o-mini) to explain those endpoints in plain English
3. **Returns** the explanation as text

It does NOT call the actual APIs — it's a discovery/browsing tool.

---

## The Tech Stack

| Component | Technology | Why |
|-----------|-----------|-----|
| Web framework | **FastAPI** | Modern async Python web framework, auto-generates OpenAPI docs, built-in validation |
| LLM orchestration | **LangChain (LCEL)** | Composable chains for RAG (Retrieval-Augmented Generation) pipelines |
| Vector database | **PGVector** (PostgreSQL + pgvector extension) | Stores embeddings for similarity search, persistent across restarts |
| LLM provider | **OpenAI** (gpt-4o-mini) | Generates natural language answers |
| Embeddings | **OpenAI** (text-embedding-3-small) | Converts text → numbers (vectors) for similarity search |
| Validation/Config | **Pydantic** | Type-safe data models and settings from environment |
| Deployment | **Docker Compose** | Runs the app + PostgreSQL together |

---

## Project Structure

```
app/
├── main.py              ← App entry point. Sets everything up at startup.
├── config.py            ← Reads .env file into a typed Settings object
├── dependencies.py      ← FastAPI dependency injection helper
│
├── api/
│   └── routes.py        ← HTTP endpoint: POST /ai/browse, GET /ai/health
│
├── swagger/
│   ├── models.py        ← Data classes: what an API endpoint looks like
│   ├── loader.py        ← Reads swagger JSON files, parses into models
│   └── catalog.py       ← In-memory lookup table (operationId → endpoint)
│
├── rag/
│   ├── indexer.py       ← Converts endpoints → Documents, stores in PGVector
│   └── retriever.py     ← Searches PGVector for relevant endpoints
│
└── chains/
    ├── prompts.py       ← The LLM prompt template + context formatting
    └── browse_chain.py  ← Wires everything into a single LangChain pipeline
```

The separation is intentional — each folder handles one concern:
- `swagger/` — knows about OpenAPI specs, nothing about LLMs
- `rag/` — knows about vector search, nothing about HTTP
- `chains/` — knows about LLM prompts, nothing about Swagger parsing
- `api/` — knows about HTTP, delegates everything else

---

## Startup Flow (what happens when the app boots)

This all happens in `main.py` → `lifespan()`:

```
┌─────────────────────────────────────────────────────┐
│  1. Load Settings from .env                         │
│     (API key, database URL, CORS origins, etc.)     │
│                                                     │
│  2. Configure CORS middleware                       │
│     (which frontend origins can call this API)      │
│                                                     │
│  3. Create OpenAI Embeddings client                 │
│     (will convert text → 1536-dim vectors)          │
│                                                     │
│  4. Connect to PGVector (PostgreSQL)                │
│     (vector database for similarity search)         │
│                                                     │
│  5. Load Swagger specs from swagger_specs/*.json    │
│     ┌──────────────────────────────────────┐        │
│     │ levels.json → 2 endpoints            │        │
│     │ const.json  → 3 endpoints            │        │
│     │ corp-actions.json → 4 endpoints      │        │
│     └──────────────────────────────────────┘        │
│     Each becomes an ApiOperationDescriptor          │
│                                                     │
│  6. Build SwaggerCatalog                            │
│     (dict: operationId → descriptor, for lookup)    │
│                                                     │
│  7. Index into PGVector                             │
│     Each endpoint → text description → embedding    │
│     → stored in PostgreSQL with metadata            │
│     (uses deterministic IDs, so restarts don't      │
│      create duplicates)                             │
│                                                     │
│  8. Create ChatOpenAI client (gpt-4o-mini)          │
│                                                     │
│  9. Build the LCEL browse chain                     │
│     (wires retriever + prompt + LLM together)       │
│                                                     │
│  10. Store chain in app.state → ready to serve!     │
└─────────────────────────────────────────────────────┘
```

**Why `lifespan`?** FastAPI's `lifespan` is an async context manager that runs once at startup (before any requests) and once at shutdown. It's the recommended way to initialize expensive resources like DB connections and ML clients.

---

## Request Flow (what happens when a user asks a question)

```
User sends:
POST /ai/browse
{"query": "How do I get index levels for NIFTY 50?"}
```

### Step 1: `routes.py` — HTTP layer

```python
@router.post("/browse")
async def browse(body: BrowseRequest, chain = Depends(get_browse_chain)):
    result = await asyncio.wait_for(
        chain.ainvoke({"query": body.query}),
        timeout=30,
    )
    return result
```

- **Pydantic validates** the request — `query` must be a non-empty string
- **`Depends(get_browse_chain)`** — FastAPI's dependency injection pulls the pre-built chain from `app.state` (set during startup)
- **`asyncio.wait_for`** — if the LLM takes longer than 30s, return HTTP 504 instead of hanging forever
- **`try/except`** — any other error returns HTTP 502 with a safe message (no stack traces to the client)

### Step 2: `browse_chain.py` — the LCEL chain executes

The chain is a pipeline of steps. Think of it like Unix pipes:

```
input → retrieve & format → prompt template → LLM → parse output
```

In code:

```python
chain = (
    RunnablePassthrough()                    # pass {"query": "..."} through
    | RunnableLambda(retrieve_and_format)    # search DB + format results
    | BROWSE_PROMPT                          # fill in the prompt template
    | llm                                    # call GPT-4o-mini
    | StrOutputParser()                      # extract text from response
)
```

**What's LCEL?** LangChain Expression Language. The `|` operator chains steps together. Each step takes input from the previous one. It's LangChain's way of building composable pipelines.

### Step 3: `retriever.py` — vector similarity search

```python
def retrieve_operations(query, vector_store, catalog, top_k=5):
    docs_with_scores = vector_store.similarity_search_with_score(query, k=5)
```

What happens under the hood:
1. The `query` string gets converted to a 1536-dimensional vector using OpenAI's embedding model
2. PGVector runs a SQL query using cosine distance to find the 5 most similar documents
3. Each result has a score (lower = more similar) — results above the threshold are filtered out
4. The `operationId` from each result is used to look up the full `ApiOperationDescriptor` from the catalog

**Why two lookups (vector store + catalog)?** The vector store holds simplified text for searching. The catalog holds the full structured data. This way you get the best of both: fast fuzzy search AND complete endpoint details.

### Step 4: `prompts.py` — build the LLM prompt

```python
BROWSE_PROMPT = ChatPromptTemplate.from_messages([
    ("system", "You are an expert API assistant..."),
    ("human", "User question: {query}\n\nCandidate operations:\n{context}\n\n..."),
])
```

The retrieved endpoints are formatted into structured text:

```
1) ID: getIndexLevels
   Method: GET
   Path: /api/v1/index/{indexName}/levels
   Summary: Get historical index levels
   Path parameters:
     - indexName [required] (type: string) - Name of the index (example: NIFTY 50)
   Query parameters:
     - fromDate [required] (type: string(date)) - Start date (example: 2024-01-01)
   ...
```

This context + the user's question go into the prompt template, which tells the LLM to explain which endpoints to call and how.

### Step 5: LLM responds → plain text returned to user

The LLM generates a natural language answer. `StrOutputParser` extracts the text. FastAPI returns it as `text/plain`.

---

## Key Design Patterns

### 1. Dependency Injection via `app.state`

```python
# At startup (main.py):
app.state.browse_chain = create_browse_chain(llm, vector_store, catalog)

# At request time (dependencies.py):
def get_browse_chain(request: Request) -> Runnable:
    return request.app.state.browse_chain

# In the route (routes.py):
async def browse(chain: Runnable = Depends(get_browse_chain)):
```

**Why?** Expensive objects (DB connections, LLM clients, chains) are created once at startup and shared across all requests. `Depends()` is FastAPI's way of injecting them cleanly — you never import globals or pass things manually.

### 2. Pydantic for Everything

- **`Settings`** — validates env vars at startup (API key must be 20+ chars)
- **`ApiOperationDescriptor`** — structured model for parsed endpoints
- **`BrowseRequest`** — validates incoming HTTP requests (query must be non-empty)

**Why?** Pydantic catches bad data early with clear error messages. If someone sends `{"query": ""}`, they get a 422 with "String should have at least 1 character" — no manual validation code needed.

### 3. RAG (Retrieval-Augmented Generation)

Instead of sending ALL endpoints to the LLM (expensive, may exceed token limits), we:
1. **Embed** endpoint descriptions as vectors at startup
2. **Search** for the most relevant ones per query (top 5)
3. **Only send those 5** to the LLM as context

This is the core RAG pattern: retrieve first, then generate. It keeps LLM costs low and answers focused.

### 4. Functional Modules, Not Classes

Notice that `loader.py`, `indexer.py`, `retriever.py` are all **module-level functions**, not classes:

```python
# Not this:
class SwaggerIndexer:
    def index(self, ...): ...

# Instead:
def index_operations(operations, vector_store): ...
```

**Why?** These modules are stateless — they take inputs and produce outputs. No need for class instances when a plain function does the job. This is idiomatic Python for stateless operations.

### 5. Docker Compose for Local Dev

```
┌─────────────────┐     ┌─────────────────────┐
│   app (Python)  │────>│  db (PostgreSQL +    │
│   port 8000     │     │  pgvector, port 5432)│
└─────────────────┘     └─────────────────────┘
```

- `db` starts first (healthcheck ensures it's ready)
- `app` starts after, connects to `db` via internal Docker network
- `pgdata` volume persists data across `docker compose down/up`
- `restart: unless-stopped` auto-restarts the app on crashes

---

## The Config Flow

```
.env file                          config.py (Settings)
┌──────────────────────┐          ┌─────────────────────────────┐
│ OPENAI_API_KEY=sk-.. │──────>   │ openai_api_key: str         │
│ DATABASE_URL=post... │──────>   │ database_url: str           │
│ CORS_ORIGINS=http... │──────>   │ cors_origins: str           │
│ CHAT_MODEL=gpt-4o.. │──────>   │ chat_model: str             │
└──────────────────────┘          └─────────────────────────────┘
                                     pydantic-settings reads .env
                                     validates types + constraints
                                     provides defaults for optional fields
```

`pydantic-settings` automatically maps `OPENAI_API_KEY` env var → `openai_api_key` field (case-insensitive). No manual `os.getenv()` calls needed.
