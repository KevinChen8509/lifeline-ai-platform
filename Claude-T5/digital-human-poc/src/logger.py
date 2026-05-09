"""数字人汇报系统 - 统一日志配置"""

import logging
import sys

# 日志格式
_FMT = "%(asctime)s [%(name)s] %(levelname)s: %(message)s"
_DATE_FMT = "%H:%M:%S"


def get_logger(name: str) -> logging.Logger:
    """获取模块日志器"""
    logger = logging.getLogger(name)
    if not logger.handlers:
        handler = logging.StreamHandler(sys.stdout)
        handler.setFormatter(logging.Formatter(_FMT, datefmt=_DATE_FMT))
        logger.addHandler(handler)
        logger.setLevel(logging.INFO)
    return logger


def set_log_level(level: str):
    """设置全局日志级别 (DEBUG/INFO/WARNING/ERROR)"""
    logging.getLogger("src").setLevel(getattr(logging, level.upper(), logging.INFO))
