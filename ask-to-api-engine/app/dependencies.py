from fastapi import Request
from langchain_core.runnables import Runnable


def get_browse_chain(request: Request) -> Runnable:
    return request.app.state.browse_chain
