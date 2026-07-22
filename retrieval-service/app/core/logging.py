import logging
import sys

LOG_FORMAT = (
    "%(asctime)s | %(levelname)-7s | %(name)s | "
    "request_id=%(request_id)s session_id=%(session_id)s | %(message)s"
)


class RequestContextFilter(logging.Filter):
    def filter(self, record):
        if not hasattr(record, "request_id"):
            record.request_id = "-"
        if not hasattr(record, "session_id"):
            record.session_id = "-"
        return True


def get_logger(name: str) -> logging.Logger:
    logger = logging.getLogger(name)
    if not logger.handlers:
        handler = logging.StreamHandler(sys.stdout)
        handler.setFormatter(logging.Formatter(LOG_FORMAT))
        handler.addFilter(RequestContextFilter())
        logger.addHandler(handler)
        logger.setLevel(logging.INFO)
    return logger
