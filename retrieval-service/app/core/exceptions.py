from fastapi import Request
from fastapi.responses import JSONResponse
from app.core.logging import get_logger

logger = get_logger("exception_handler")


class ServiceError(Exception):
    def __init__(self, error_code: str, message: str, status_code: int = 500):
        self.error_code = error_code
        self.message = message
        self.status_code = status_code


class VectorStoreError(ServiceError):
    def __init__(self, message: str = "向量库异常"):
        super().__init__("VECTOR_STORE_ERROR", message, status_code=503)


class EmbeddingError(ServiceError):
    def __init__(self, message: str = "Embedding 服务异常"):
        super().__init__("EMBEDDING_ERROR", message, status_code=503)


class RerankError(ServiceError):
    """Rerank 异常 — 不中断主流程，仅用于内部标记"""
    def __init__(self, message: str = "Rerank 服务异常"):
        super().__init__("RERANK_ERROR", message, status_code=200)


async def service_error_handler(_req: Request, exc: ServiceError) -> JSONResponse:
    logger.error(
        "service_error code=%s msg=%s",
        exc.error_code, exc.message,
        extra={"request_id": getattr(_req.state, "request_id", "-"),
               "session_id": "-"},
    )
    return JSONResponse(
        status_code=exc.status_code,
        content={"error_code": exc.error_code, "message": exc.message},
    )


async def generic_error_handler(_req: Request, exc: Exception) -> JSONResponse:
    logger.exception(
        "unhandled_error",
        extra={"request_id": getattr(_req.state, "request_id", "-"),
               "session_id": "-"},
    )
    return JSONResponse(
        status_code=500,
        content={"error_code": "INTERNAL_ERROR", "message": "服务内部错误"},
    )
