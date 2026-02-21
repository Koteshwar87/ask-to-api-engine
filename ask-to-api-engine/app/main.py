import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from langchain_postgres import PGVector
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

from app.config import Settings
from app.swagger.loader import load_all_operations
from app.swagger.catalog import SwaggerCatalog
from app.rag.indexer import index_operations
from app.chains.browse_chain import create_browse_chain
from app.api.routes import router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s - %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = Settings()
    app.state.settings = settings

    # Configure CORS from settings (single Settings load)
    origins = [o.strip() for o in settings.cors_origins.split(",") if o.strip()]
    app.add_middleware(
        CORSMiddleware,
        allow_origins=origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Embeddings + vector store
    embeddings = OpenAIEmbeddings(
        model=settings.embedding_model,
        openai_api_key=settings.openai_api_key,
    )
    vector_store = PGVector(
        embeddings=embeddings,
        collection_name="swagger_operations",
        connection=settings.database_url,
        use_jsonb=True,
    )

    # Load swagger specs and build catalog
    operations = load_all_operations(settings.specs_path)
    catalog = SwaggerCatalog(operations)
    logger.info("Swagger catalog built with %d operations", len(operations))

    # Index into PGVector (uses deterministic IDs — safe to re-run on restart)
    index_operations(operations, vector_store)

    # LLM
    llm = ChatOpenAI(
        model=settings.chat_model,
        openai_api_key=settings.openai_api_key,
    )

    # Build the LCEL browse chain
    app.state.browse_chain = create_browse_chain(llm, vector_store, catalog)

    logger.info("Startup complete — ready to serve requests")
    yield
    logger.info("Shutting down")


app = FastAPI(title="Ask-to-API Engine", version="1.0.0", lifespan=lifespan)

app.include_router(router)
