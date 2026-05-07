"""python -m src.pipeline 入口"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.pipeline import main

if __name__ == "__main__":
    main()
