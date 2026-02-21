import asyncio
import logging

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel, Field

from langchain_core.runnables import Runnable

from app.dependencies import get_browse_chain

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ai")


@router.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}

BROWSE_TIMEOUT_SECONDS = 30


class BrowseRequest(BaseModel):
    query: str = Field(..., min_length=1)


@router.post("/browse", response_class=PlainTextResponse)
async def browse(
    body: BrowseRequest,
    chain: Runnable = Depends(get_browse_chain),
) -> str:
    try:
        result = await asyncio.wait_for(
            chain.ainvoke({"query": body.query}),
            timeout=BROWSE_TIMEOUT_SECONDS,
        )
        return result
    except asyncio.TimeoutError:
        logger.error("Browse request timed out after %ds for query: %s", BROWSE_TIMEOUT_SECONDS, body.query)
        raise HTTPException(status_code=504, detail="Request timed out. Please try again.")
    except Exception:
        logger.exception("Browse request failed for query: %s", body.query)
        raise HTTPException(status_code=502, detail="Failed to generate response. Please try again later.")
