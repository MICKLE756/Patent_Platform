"""FastAPI 应用入口"""
import uuid
from fastapi import FastAPI, Request
from app.api.routes import router
from app.core.exceptions import ServiceError, service_error_handler, generic_error_handler

app = FastAPI(title="检索召回与重排序服务", version="0.1.0")

# 注册异常处理
app.add_exception_handler(ServiceError, service_error_handler)
app.add_exception_handler(Exception, generic_error_handler)


@app.middleware("http")
async def request_id_middleware(request: Request, call_next):
    rid = request.headers.get("X-Request-ID", str(uuid.uuid4()))
    request.state.request_id = rid
    response = await call_next(request)
    response.headers["X-Request-ID"] = rid
    return response


app.include_router(router)
