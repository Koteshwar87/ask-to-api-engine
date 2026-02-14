# Ask-to-API Engine

Ask-to-API Engine is a FastAPI application that helps you browse and understand APIs from Swagger/OpenAPI specifications using natural language.

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
- Index operations into ChromaDB for semantic similarity search.
- Support NLQ-based retrieval of relevant endpoints.

### 3. NLQ-Assisted Browse (Early AI Use)

- Use LangChain + OpenAI to interpret natural language queries like:
  - *"Show me all endpoints that return index levels"*
  - *"What APIs do we have for order details?"*
- Map the query to relevant endpoints in the catalog via vector similarity search.
- **Important:** In this phase, the app does not call the real backend APIs; it only helps discover them.

---

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Python 3.11+ |
| Framework | FastAPI + uvicorn |
| AI / LLM | LangChain LCEL, langchain-openai (`gpt-4o-mini`, `text-embedding-3-small`) |
| Vector DB | ChromaDB (langchain-chroma) |
| Validation | Pydantic v2 + pydantic-settings |
| Config | python-dotenv (.env file) |

---

## Project Structure

```
ask-to-api-engine/
├── app/
│   ├── main.py                # FastAPI app, lifespan (startup/shutdown), router include
│   ├── config.py              # Pydantic BaseSettings (env vars, model names, paths)
│   ├── dependencies.py        # Depends() factories — pull from app.state
│   ├── api/
│   │   └── routes.py          # POST /ai/browse — thin handler, delegates to chain
│   ├── swagger/
│   │   ├── models.py          # Pydantic: ApiOperationDescriptor, ApiParameterDescriptor, enums
│   │   ├── loader.py          # Load & parse swagger_specs/*.json → list[ApiOperationDescriptor]
│   │   └── catalog.py         # SwaggerCatalog: dict-based lookup (by id, tag)
│   ├── rag/
│   │   ├── indexer.py         # operations → LangChain Documents → ChromaDB
│   │   └── retriever.py       # similarity_search → map back to ApiOperationDescriptor
│   └── chains/
│       ├── prompts.py         # ChatPromptTemplate + context formatter
│       └── browse_chain.py    # LCEL chain: retrieve | format | prompt | llm | StrOutputParser
├── swagger_specs/             # Swagger/OpenAPI JSON files
│   ├── const.json
│   ├── corp-actions.json
│   └── levels.json
├── requirements.txt
├── .env.example
└── README.md
```

---

## Getting Started

### Prerequisites

- Python 3.11+
- OpenAI API key

### Configuration

```bash
cp .env.example .env
```

Set the following in `.env`:

```
OPENAI_API_KEY=sk-your-key-here
CHAT_MODEL=gpt-4o-mini
EMBEDDING_MODEL=text-embedding-3-small
```

### Setup & Run

```bash
# Create virtual environment
python -m venv venv
venv\Scripts\activate        # Windows
# source venv/bin/activate   # macOS/Linux

# Install dependencies
pip install -r requirements.txt

# Start the server
uvicorn app.main:app --reload --port 8000
```

The application starts on port **8000** by default.

### Try It

```bash
curl -X POST http://localhost:8000/ai/browse \
  -H "Content-Type: application/json" \
  -d '{"query": "How do I get index levels for NIFTY 50?"}'
```

### API

#### POST /ai/browse

Request:
```json
{ "query": "How do I get index levels for NIFTY 50?" }
```

Response: `text/plain` — LLM-generated explanation of matching API endpoints.

---

## Roadmap

### Phase 2 - API Execution (Planned)

1. **Request Planning** - From an NLQ, identify target endpoints and required parameters. Ask the user for missing values if needed.
2. **Safe API Invocation** - Invoke backend APIs using httpx with pluggable authentication, pagination, error handling, and timeouts.
3. **Raw Result Delivery** - Return raw JSON or lightly normalized structures. Log all invocations for observability.

### Phase 3 - API Insights Layer (Planned)

1. **Result Normalization** - Normalize responses into a consistent internal model. Optionally store recent results for short-term context.
2. **Insight Generation** - Use the LLM to summarize responses, extract key metrics, and answer follow-up questions grounded in fetched data (RAG pattern).
3. **Multi-API Orchestration** - Combine multiple endpoints into a single answer. Define simple recipes/workflows for common insight patterns.
4. **Guardrails & Observability** - Rate limiting, safety rules, metrics & traces.

---

## Design Intention for AI Assistants

This repository is designed so that LLM-based copilots can:

- Recognize that the current scope is limited to API browsing (no real backend calls yet).
- Understand that Swagger/OpenAPI parsing and API catalog are core concepts, and NLQ is currently used only for discovery/search, not execution.
- Propose future code that aligns with the planned phases (Phase 2: invocation, Phase 3: insights).
