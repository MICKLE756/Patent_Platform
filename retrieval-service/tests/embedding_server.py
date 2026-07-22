import os
import time
import torch
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

MODEL_PATH = "/root/autodl-tmp/models/Qwen3-Embedding-8B"
API_TOKEN = os.getenv("EMBEDDING_API_TOKEN", "change-me")

app = FastAPI(title="Qwen3 Embedding Service")

model = SentenceTransformer(
    MODEL_PATH,
    device="cuda",
    model_kwargs={"torch_dtype": torch.float16}
)

class EmbedRequest(BaseModel):
    texts: list[str]
    is_query: bool = False
    normalize: bool = True

@app.get("/health")
def health():
    return {
        "status": "ok",
        "model": MODEL_PATH,
        "cuda": torch.cuda.is_available(),
        "gpu": torch.cuda.get_device_name(0) if torch.cuda.is_available() else None
    }

@app.post("/embed")
def embed(req: EmbedRequest, authorization: str | None = Header(default=None)):
    if authorization != f"Bearer {API_TOKEN}":
        raise HTTPException(status_code=401, detail="unauthorized")

    if not req.texts:
        raise HTTPException(status_code=400, detail="texts cannot be empty")

    texts = req.texts

    if req.is_query:
        texts = [
            "Instruct: 给定一个企业技术需求，检索最相关的专利文本\nQuery: " + t
            for t in texts
        ]

    start = time.time()
    vectors = model.encode(
        texts,
        normalize_embeddings=req.normalize,
        batch_size=4
    )

    return {
        "dim": int(vectors.shape[1]),
        "count": len(texts),
        "latency_ms": int((time.time() - start) * 1000),
        "vectors": vectors.tolist()
    }
