# Dockerfile for InsightFace Flask engine
FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# Install minimal build deps for some Python packages
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       build-essential \
       libglib2.0-0 \
       libsm6 \
       libxrender1 \
       libxext6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy requirements and install
COPY requirements_flask.txt /app/requirements_flask.txt
RUN pip install --no-cache-dir -r /app/requirements_flask.txt

# Copy app files
COPY . /app

EXPOSE 5000

CMD ["python", "face_engine_flask.py"]
