"""Recommendation-service launcher.

Run this file from the repository root or from ``recommendation-service``:

    python recommendation-service/app.py
    python app.py

The actual FastAPI application remains in ``src/app.py``.  This launcher adds
that directory to ``sys.path`` so the service's sibling modules (``config``,
``proactive_service`` and others) can be imported reliably.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import uvicorn


SERVICE_DIR = Path(__file__).resolve().parent
SRC_DIR = SERVICE_DIR / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

from app import app  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description="Run recommendation-service")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8090)
    parser.add_argument("--reload", action="store_true")
    args = parser.parse_args()

    uvicorn.run(app, host=args.host, port=args.port, reload=args.reload)


if __name__ == "__main__":
    main()
