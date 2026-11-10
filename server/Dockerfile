FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && useradd -u 10001 -m appuser && mkdir /data && chown appuser /data
COPY app.py store.py catalog.json ./
ENV BOSS_SOCIAL_DB=/data/social.sqlite
USER appuser
EXPOSE 8765
CMD ["python", "-m", "uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8765", "--lifespan", "off", "--ws", "none", "--no-access-log", "--limit-concurrency", "64"]
