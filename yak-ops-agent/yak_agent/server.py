"""HTTP service entrypoint.

Run:  python -m yak_agent.server             (defaults 127.0.0.1:8090)
"""

from yak_agent.api.server import app, main

__all__ = ["app", "main"]

if __name__ == "__main__":
    main()
