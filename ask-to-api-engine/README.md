# Ask-to-API Engine (Python)

FastAPI + LangChain app that loads Swagger/OpenAPI specs, indexes them into ChromaDB, and lets users ask natural language questions to discover relevant API endpoints.

## Setup

```bash
cd ask-to-api-engine

# Create virtual environment
python -m venv venv
venv\Scripts\activate        # Windows
# source venv/bin/activate   # macOS/Linux

# Install dependencies
pip install -r requirements.txt

# Configure environment
cp .env.example .env
# Edit .env and set your OPENAI_API_KEY
```

## Run

```bash
uvicorn app.main:app --reload --port 8000
```

## Test

```bash
curl -X POST http://localhost:8000/ai/browse -H "Content-Type: application/json" -d "{\"query\": \"How do I get index levels for NIFTY 50?\"}"
```

## API

### POST /ai/browse

Request:
```json
{ "query": "How do I get index levels for NIFTY 50?" }
```

Response: `text/plain` — LLM-generated explanation of matching API endpoints.
