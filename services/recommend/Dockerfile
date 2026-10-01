# ==============================================================================
# Dockerfile for AI Recommendation & Chatbot Service (FastAPI + Python 3.11)
# ==============================================================================
FROM python:3.11-slim

WORKDIR /app

# Thiết lập biến môi trường Python
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# Cài đặt dependencies hệ thống tối thiểu nếu cần
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy và cài đặt requirements
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy mã nguồn
COPY app app

EXPOSE 8088

HEALTHCHECK --interval=10s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:8088/actuator/health || exit 1

ENTRYPOINT ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8088"]
