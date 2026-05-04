#!/bin/sh
set -eu

python -m app.prestart
exec uvicorn app.main:app --host 0.0.0.0 --port 8000
