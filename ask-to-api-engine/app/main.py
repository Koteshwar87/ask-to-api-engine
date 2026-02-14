import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from langchain_chroma import Chroma
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

    # Embeddings + vector store
    embeddings = OpenAIEmbeddings(
        model=settings.embedding_model,
        openai_api_key=settings.openai_api_key,
    )
    vector_store = Chroma(
        collection_name=settings.chroma_collection_name,
        embedding_function=embeddings,
    )

    # Load swagger specs and build catalog
    operations = load_all_operations(settings.specs_path)
    catalog = SwaggerCatalog(operations)
    logger.info("Swagger catalog built with %d operations", len(operations))

    # Index into ChromaDB
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
