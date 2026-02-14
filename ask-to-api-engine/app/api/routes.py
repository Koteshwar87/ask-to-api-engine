from fastapi import APIRouter, Depends
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel, Field

from langchain_core.runnables import Runnable

from app.dependencies import get_browse_chain

router = APIRouter(prefix="/ai")


class BrowseRequest(BaseModel):
    query: str = Field(..., min_length=1)


@router.post("/browse", response_class=PlainTextResponse)
async def browse(
    body: BrowseRequest,
    chain: Runnable = Depends(get_browse_chain),
) -> str:
    result = await chain.ainvoke({"query": body.query})
    return result
