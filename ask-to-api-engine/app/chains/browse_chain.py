from langchain_core.output_parsers import StrOutputParser
from langchain_core.runnables import RunnablePassthrough, RunnableLambda
from langchain_postgres import PGVector
from langchain_openai import ChatOpenAI

from app.swagger.catalog import SwaggerCatalog
from app.rag.retriever import retrieve_operations
from app.chains.prompts import BROWSE_PROMPT, format_operations_context


def create_browse_chain(
    llm: ChatOpenAI,
    vector_store: PGVector,
    catalog: SwaggerCatalog,
):
    """
    Build an LCEL chain: query → retrieve candidates → format context → prompt → LLM → string.

    Returns a Runnable that accepts {"query": str} and outputs a plain text string.
    """

    def retrieve_and_format(inputs: dict) -> dict:
        query = inputs["query"]
        operations = retrieve_operations(query, vector_store, catalog)
        context = format_operations_context(operations)
        return {"query": query, "context": context}

    chain = (
        RunnablePassthrough()
        | RunnableLambda(retrieve_and_format)
        | BROWSE_PROMPT
        | llm
        | StrOutputParser()
    )

    return chain
