# InsightFace Face Matching API

Offline, free face comparison service using InsightFace (Buffalo model).

## Installation

```bash
pip install -r requirements.txt
```

## Running

```bash
# Development mode (with auto-reload)
uvicorn main:app --reload --host 0.0.0.0 --port 5000

# Production mode
uvicorn main:app --host 0.0.0.0 --port 5000 --workers 4
```

The API will be available at `http://localhost:5000`.

## API Endpoints

### Health Check
```
GET /health
```

### Compare Faces
```
POST /compare
Content-Type: application/json

{
  "image1_base64": "base64-encoded-image-1",
  "image2_base64": "base64-encoded-image-2"
}

Response:
{
  "similarity": 0.85,
  "match": true,
  "message": "Match=true, Similarity=0.8500"
}
```

## Notes

- **Model**: Buffalo-L (balanced speed/accuracy)
- **Similarity Range**: 0 (no match) to 1 (perfect match)
- **Match Threshold**: 0.6 (configurable in code)
- **CPU Mode**: Default (set ctx_id=0 for GPU if available)
- **Face Detection**: Auto-detects and uses first face in each image

## Docker

Run the engine in Docker (recommended if local Python build fails):

```bash
# from project root
cd Prj3/insightface_api
docker compose build
docker compose up -d

# check logs
docker compose logs -f
```

The service will be available at `http://localhost:5000` after the container finishes startup (the model downloads on first run).
