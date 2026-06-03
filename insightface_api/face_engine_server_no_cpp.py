
"""
Face Recognition HTTP Server - Windows Compatible Version
Works with or without InsightFace/FaceNet - Auto-detects available models
"""

import os
import sys
import json
import base64
import logging
from datetime import datetime
from pathlib import Path

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s'
)
logger = logging.getLogger(__name__)

try:
    from flask import Flask, request, jsonify
    from flask_cors import CORS
except ImportError:
    logger.error("Flask not installed. Install with: pip install flask flask-cors")
    sys.exit(1)

app = Flask(__name__)
CORS(app)  

try:
    import numpy as np
    from flask.json.provider import DefaultJSONProvider

    class NumpyJSONProvider(DefaultJSONProvider):
        def default(self, obj):
            if isinstance(obj, np.bool_):
                return bool(obj)
            if isinstance(obj, np.integer):
                return int(obj)
            if isinstance(obj, np.floating):
                return float(obj)
            if isinstance(obj, np.ndarray):
                return obj.tolist()
            return super().default(obj)

    app.json_provider_class = NumpyJSONProvider
    app.json = NumpyJSONProvider(app)
    logger.info("NumpyJSONProvider registered (Flask 2.3+ compatible)")
except Exception as _json_err:
    logger.warning(f"Could not register NumpyJSONProvider: {_json_err}")

g_engine = None
g_engine_type = None

def detect_and_load_engine():
    """Detect available face recognition libraries and load appropriate engine"""
    global g_engine, g_engine_type
    
    logger.info("=" * 60)
    logger.info("Detecting available face recognition engines...")
    logger.info("=" * 60)
    
    
    try:
        import insightface
        logger.info("✓ InsightFace detected")
        from insightface.app import FaceAnalysis
        
        engine_obj = {
            'type': 'insightface',
            'instance': FaceAnalysis(name='buffalo_sc', providers=['CPUExecutionProvider']),
            'name': 'InsightFace (Buffalo_SC)'
        }
        engine_obj['instance'].prepare(ctx_id=0, det_thresh=0.5)
        
        g_engine = engine_obj
        g_engine_type = 'insightface'
        logger.info("✓ InsightFace engine loaded successfully")
        return True
        
    except Exception as e:
        logger.warning(f"✗ InsightFace not available: {type(e).__name__}: {str(e)[:100]}")
    
    
    try:
        from facenet_pytorch import MTCNN, InceptionResnetV1
        import torch
        logger.info("✓ FaceNet-PyTorch detected")
        
        device = 'cuda' if torch.cuda.is_available() else 'cpu'
        logger.info(f"  Using device: {device}")
        
        engine_obj = {
            'type': 'facenet',
            'mtcnn': MTCNN(device=device, keep_all=True),
            'model': InceptionResnetV1(pretrained='vggface2').eval().to(device),
            'device': device,
            'torch': torch,
            'name': 'FaceNet-PyTorch (InceptionResnetV1)'
        }
        
        g_engine = engine_obj
        g_engine_type = 'facenet'
        logger.info("✓ FaceNet-PyTorch engine loaded successfully")
        return True
        
    except Exception as e:
        logger.warning(f"✗ FaceNet-PyTorch not available: {type(e).__name__}: {str(e)[:100]}")
    
    
    try:
        import cv2
        import numpy as np
        from scipy.spatial.distance import cosine
        
        logger.info("✓ OpenCV detected - using feature matching fallback")
        
        engine_obj = {
            'type': 'opencv',
            'orb': cv2.ORB_create(nfeatures=500),
            'cv2': cv2,
            'np': np,
            'cosine': cosine,
            'name': 'OpenCV ORB + Feature Matching'
        }
        
        g_engine = engine_obj
        g_engine_type = 'opencv'
        logger.warning("⚠ Using feature matching fallback (lower accuracy)")
        return True
        
    except Exception as e:
        logger.error(f"✗ OpenCV fallback failed: {e}")
        return False

def compare_with_insightface(face1_b64, face2_b64):
    """Compare faces using InsightFace"""
    try:
        import cv2
        import numpy as np
        from scipy.spatial.distance import cosine
        
        
        img1 = base64_to_cv2(face1_b64)
        img2 = base64_to_cv2(face2_b64)
        
        if img1 is None or img2 is None:
            return {"success": False, "error": "Invalid image"}
        
        
        faces1 = g_engine['instance'].get(img1)
        faces2 = g_engine['instance'].get(img2)
        
        if len(faces1) == 0 or len(faces2) == 0:
            logger.warning(f"InsightFace: no face detected (faces1={len(faces1)}, faces2={len(faces2)}) – fallback to L1")
            return {
                "success": False,
                "error": "No face detected in one or both images – likely ID card crop",
                "face_detected": False,
                "method": "InsightFace_buffalo_sc"
            }
        
        
        emb1 = faces1[0].embedding
        emb2 = faces2[0].embedding
        
        
        
        
        
        distance = cosine(emb1, emb2)
        similarity = 1.0 - distance
        similarity = max(0.0, min(1.0, similarity))
        
        
        
        threshold = 0.60
        matched = bool(similarity >= threshold)  
        
        return {
            "success": True,
            "similarity": float(similarity),
            "matched": matched,
            "confidence": float(similarity),
            "method": "InsightFace_buffalo_sc",
            "threshold_used": threshold
        }
        
    except Exception as e:
        logger.error(f"InsightFace comparison error: {e}")
        return {"success": False, "error": str(e)}

def compare_with_facenet(face1_b64, face2_b64):
    """Compare faces using FaceNet"""
    try:
        import torch
        
        img1 = base64_to_cv2(face1_b64)
        img2 = base64_to_cv2(face2_b64)
        
        if img1 is None or img2 is None:
            return {"success": False, "error": "Invalid image"}
        
        
        faces1 = g_engine['mtcnn'](img1)
        faces2 = g_engine['mtcnn'](img2)
        
        if faces1 is None or faces2 is None or len(faces1) == 0 or len(faces2) == 0:
            logger.warning("FaceNet: no face detected in one or both images – fallback to L1")
            return {
                "success": False,
                "error": "No face detected in one or both images – likely ID card crop",
                "face_detected": False,
                "method": "FaceNet"
            }
        
        
        with torch.no_grad():
            emb1 = g_engine['model'](faces1[0:1].to(g_engine['device']))
            emb2 = g_engine['model'](faces2[0:1].to(g_engine['device']))
        
        
        from torch.nn.functional import cosine_similarity as cos_sim
        similarity = cos_sim(emb1, emb2).item()
        similarity = max(0.0, min(1.0, (similarity + 1) / 2))  
        
        threshold = 0.60
        matched = similarity >= threshold
        
        return {
            "success": True,
            "similarity": float(similarity),
            "matched": matched,
            "confidence": float(similarity),
            "method": "FaceNet"
        }
        
    except Exception as e:
        logger.error(f"FaceNet comparison error: {e}")
        return {"success": False, "error": str(e)}

def compare_with_opencv(face1_b64, face2_b64):
    """OpenCV ORB features - NOT a face recognition algorithm.
    Returns failure so the caller falls back to Layer 1 result instead of
    returning a meaningless similarity score."""
    logger.warning("⚠ OpenCV ORB fallback requested – not suitable for face recognition. "
                   "Install InsightFace or FaceNet-PyTorch for proper comparison.")
    return {
        "success": False,
        "error": "No proper face recognition engine available. "
                 "InsightFace and FaceNet-PyTorch are not loaded. "
                 "ORB feature matching cannot verify faces.",
        "method": "OpenCV_ORB",
        "fallback_needed": True
    }

def base64_to_cv2(image_base64):
    """Decode base64 string to OpenCV image"""
    try:
        import cv2
        import numpy as np
        from io import BytesIO
        from PIL import Image
        
        
        if image_base64.startswith("data:image"):
            image_base64 = image_base64.split(",")[1]
        
        
        image_bytes = base64.b64decode(image_base64)
        image = Image.open(BytesIO(image_bytes))
        image_array = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)
        
        return image_array
        
    except Exception as e:
        logger.error(f"Error decoding image: {e}")
        return None

@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint"""
    return jsonify({
        "status": "ready",
        "engine": g_engine_type,
        "engine_name": g_engine.get('name', 'Unknown') if g_engine else None,
        "timestamp": datetime.now().isoformat()
    })

@app.route('/api/v1/face/compare', methods=['POST'])
def face_compare():
    """Compare two faces"""
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({"success": False, "error": "No JSON data"}), 400
        
        face1_b64 = data.get('face1_base64')
        face2_b64 = data.get('face2_base64')
        
        if not face1_b64 or not face2_b64:
            return jsonify({"success": False, "error": "Missing face1_base64 or face2_base64"}), 400
        
        logger.info(f"Face comparison request - Engine: {g_engine_type}")
        
        
        if g_engine_type == 'insightface':
            result = compare_with_insightface(face1_b64, face2_b64)
        elif g_engine_type == 'facenet':
            result = compare_with_facenet(face1_b64, face2_b64)
        else:
            result = compare_with_opencv(face1_b64, face2_b64)
        
        return jsonify(result)
        
    except Exception as e:
        logger.error(f"Comparison error: {e}")
        return jsonify({"success": False, "error": str(e)}), 500

def extract_face_embedding(image_b64):
    """Trich xuat embedding vector tu anh - dung cho /extract-vector"""
    try:
        if g_engine_type == 'insightface':
            img = base64_to_cv2(image_b64)
            if img is None:
                return None
            faces = g_engine['instance'].get(img)
            if not faces:
                return None
            return faces[0].embedding.tolist()
        elif g_engine_type == 'facenet':
            import torch
            img = base64_to_cv2(image_b64)
            if img is None:
                return None
            faces = g_engine['mtcnn'](img)
            if faces is None or len(faces) == 0:
                return None
            with torch.no_grad():
                emb = g_engine['model'](faces[0:1].to(g_engine['device']))
            return emb[0].cpu().numpy().tolist()
        else:
            
            return None
    except Exception as e:
        logger.error(f"Embedding extraction error: {e}")
        return None

@app.route('/compare', methods=['POST'])
def compare_legacy():
    """Alias endpoint - Java FaceAIService goi POST /compare voi image1/image2 hoac embedding/image"""
    return _do_compare()

@app.route('/compare-vector', methods=['POST'])
def compare_vector():
    """
    Endpoint cho mobile app: so sanh vector (embedding) voi anh moi,
    hoac so sanh 2 anh.
    Chap nhan cac field:
      - embedding + image  (vector tu DB vs anh selfie)
      - face1_base64 + face2_base64
      - image1 + image2
    """
    return _do_compare()

def _do_compare():
    """Logic chung cho /compare va /compare-vector"""
    try:
        data = request.get_json(force=True, silent=True)
        if not data:
            return jsonify({"success": False, "error": "No JSON data"}), 400

        
        safe_keys = {k: (v if not isinstance(v, str) or len(v) < 100 else f"<base64 len={len(v)}>")
                     for k, v in data.items()}
        logger.info(f"compare received fields: {list(data.keys())} | non-image values: { {k:v for k,v in safe_keys.items() if not isinstance(safe_keys[k], str) or '<base64' not in str(safe_keys[k])} }")

        threshold = float(data.get('threshold', 0.55))

        
        cccd = data.get('cccd') or data.get('employeeCccd') or data.get('employee_cccd')
        selfie_only = (data.get('image') or data.get('selfie') or data.get('selfie_image') or
                       data.get('selfieImage') or data.get('imageLive') or data.get('image_live') or
                       data.get('liveImage') or data.get('live_image') or data.get('faceImageLive'))
        if cccd and selfie_only and not (
            data.get('embedding') or data.get('stored_vector') or data.get('vector') or
            data.get('face_vector') or data.get('face1_base64') or data.get('image1') or
            data.get('chip_image') or data.get('chipImage') or data.get('face1')
        ):
            try:
                import requests as _req
                resp = _req.get(f"http://localhost:8080/api/face/employee/{cccd}", timeout=5)
                if resp.status_code == 200:
                    emp_data = resp.json()
                    if emp_data.get('found') and emp_data.get('backupPhoto'):
                        logger.info(f"compare-vector: CCCD={cccd}, using server backup photo")
                        data['chipImage'] = emp_data['backupPhoto']
                        
                    else:
                        logger.warning(f"compare-vector: CCCD={cccd} found but no backupPhoto")
                        return jsonify({"success": False, "matched": False, "similarity": 0.0,
                                        "message": f"No backup photo for CCCD {cccd}"}), 400
                else:
                    logger.warning(f"compare-vector: Spring Boot returned {resp.status_code} for CCCD={cccd}")
                    return jsonify({"success": False, "matched": False, "similarity": 0.0,
                                    "message": f"Employee not found: CCCD {cccd}"}), 400
            except Exception as e:
                logger.error(f"compare-vector: failed to fetch employee backup: {e}")
                return jsonify({"success": False, "matched": False, "similarity": 0.0,
                                "error": f"Cannot fetch backup photo: {e}"}), 500

        embedding_raw = (data.get('embedding') or data.get('stored_vector') or
                         data.get('vector') or data.get('face_vector'))
        image_b64 = (data.get('image') or data.get('selfie') or data.get('face_image') or
                     data.get('live_image') or data.get('selfie_image') or data.get('imageLive') or
                     data.get('image_live') or data.get('liveImage'))

        if embedding_raw is not None and image_b64 is not None:
            logger.info(f"compare-vector: embedding vs image mode, engine={g_engine_type}")
            try:
                import numpy as np
                if isinstance(embedding_raw, str):
                    import json as _json
                    embedding_raw = _json.loads(base64.b64decode(embedding_raw).decode())
                embedding = np.array(embedding_raw, dtype=np.float32)
                face_img = base64_to_cv2(image_b64)
                if face_img is None:
                    return jsonify({"success": False, "error": "Cannot decode image"}), 400

                live_emb = None
                if g_engine_type == 'facenet':
                    import torch
                    faces = g_engine['mtcnn'](face_img)
                    if faces is not None and len(faces) > 0:
                        with torch.no_grad():
                            e = g_engine['model'](faces[0:1].to(g_engine['device']))
                        live_emb = e[0].cpu().numpy()
                elif g_engine_type == 'insightface':
                    detected = g_engine['instance'].get(face_img)
                    if detected:
                        live_emb = detected[0].embedding

                if live_emb is None:
                    return jsonify({"success": True, "similarity": 0.0, "matched": False,
                                    "confidence": 0.0, "message": "No face detected in live image"})

                from scipy.spatial.distance import cosine
                dist = cosine(embedding, live_emb)
                similarity = float(1.0 - dist)
                matched = similarity >= threshold
                return jsonify({
                    "success": True,
                    "similarity": round(similarity, 4),
                    "matched": matched,
                    "confidence": round(similarity, 4),
                    "engine": g_engine_type,
                    "message": "Match" if matched else "No match"
                })
            except Exception as e:
                logger.error(f"compare-vector embedding mode error: {e}")
                return jsonify({"success": False, "error": str(e)}), 500

        
        face1 = (data.get('face1_base64') or data.get('image1') or
                 data.get('chip_image') or data.get('chipImage') or
                 data.get('face1') or data.get('image_chip') or data.get('imageChip'))
        face2 = (data.get('face2_base64') or data.get('image2') or
                 data.get('selfie_image') or data.get('selfieImage') or
                 data.get('face2') or data.get('imageLive') or data.get('image_live') or
                 data.get('selfie') or data.get('live_image') or data.get('liveImage'))

        if not face1 or not face2:
            logger.warning(f"compare-vector 400: received keys={list(data.keys())}")
            return jsonify({"success": False,
                            "error": f"Missing image fields. Got keys: {list(data.keys())}. Need: (embedding+image) or (chipImage+selfieImage) or (image1+image2)"}), 400

        logger.info(f"compare-vector: image vs image mode, engine={g_engine_type}")
        if g_engine_type == 'insightface':
            result = compare_with_insightface(face1, face2)
        elif g_engine_type == 'facenet':
            result = compare_with_facenet(face1, face2)
        else:
            result = compare_with_opencv(face1, face2)
        return jsonify(result)

    except Exception as e:
        logger.error(f"compare error: {e}")
        return jsonify({"success": False, "error": str(e)}), 500

@app.route('/extract-vector', methods=['POST'])
def extract_vector_legacy():
    """Legacy endpoint - Java FaceAIService calls POST /extract-vector"""
    return _do_extract_vector()

@app.route('/api/v1/face/compare-vector', methods=['POST'])
def face_compare_vector():
    """
    Java FaceComparisonService.compareFaceWithVector() calls this endpoint.
    Body: { "selfie_base64": "...", "face_vector_json": "[...]" }
    So sánh ảnh selfie với face vector JSON đã lưu trong DB.
    """
    try:
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "error": "No JSON data"}), 400

        selfie_b64 = data.get('selfie_base64')
        vector_json = data.get('face_vector_json')

        if not selfie_b64 or not vector_json:
            return jsonify({"success": False, "error": "Missing selfie_base64 or face_vector_json"}), 400

        threshold = float(data.get('threshold', 0.55))

        
        selfie_embedding = extract_face_embedding(selfie_b64)
        if selfie_embedding is None:
            return jsonify({"success": False, "error": "Cannot detect face in selfie", "matched": False, "similarity": 0.0}), 400

        
        stored_vector = json.loads(vector_json) if isinstance(vector_json, str) else vector_json
        if not stored_vector:
            return jsonify({"success": False, "error": "Invalid face_vector_json", "matched": False, "similarity": 0.0}), 400

        
        import numpy as np
        v1 = np.array(selfie_embedding, dtype=np.float32)
        v2 = np.array(stored_vector,   dtype=np.float32)

        norm1 = np.linalg.norm(v1)
        norm2 = np.linalg.norm(v2)
        if norm1 == 0 or norm2 == 0:
            return jsonify({"success": True, "matched": False, "similarity": 0.0, "confidence": 0.0})

        similarity = float(np.dot(v1, v2) / (norm1 * norm2))
        
        similarity = max(0.0, min(1.0, (similarity + 1) / 2))

        matched = similarity >= threshold
        logger.info(f"compare-vector result: similarity={similarity:.4f}, matched={matched}, threshold={threshold}")

        return jsonify({
            "success": True,
            "matched": matched,
            "similarity": similarity,
            "confidence": similarity,
            "threshold": threshold
        })

    except Exception as e:
        logger.error(f"compare-vector error: {e}")
        return jsonify({"success": False, "error": str(e), "matched": False, "similarity": 0.0}), 500

@app.route('/api/v1/face/extract', methods=['POST'])
def face_extract():
    """Extract face vector"""
    return _do_extract_vector()

def _do_extract_vector():
    """Shared logic: decode image, extract embedding, return JSON"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "error": "No JSON data"}), 400

        
        image_b64 = data.get('image') or data.get('image_base64')
        cccd = data.get('cccd', 'unknown')

        if not image_b64:
            return jsonify({"success": False, "error": "Missing field: image or image_base64"}), 400

        logger.info(f"Extract vector request - CCCD: {cccd}, engine: {g_engine_type}")

        embedding = extract_face_embedding(image_b64)

        if embedding is None:
            return jsonify({
                "success": False,
                "error": "No face detected or engine not supported",
                "engine": g_engine_type
            }), 422

        return jsonify({
            "success": True,
            "cccd": cccd,
            "embedding": embedding,
            "vector_base64": base64.b64encode(json.dumps(embedding).encode()).decode(),
            "dimension": len(embedding),
            "quality": 0.9,
            "engine": g_engine_type
        })
    except Exception as e:
        logger.error(f"Extract vector error: {e}")
        return jsonify({"success": False, "error": str(e)}), 500

@app.route('/api/v1/status', methods=['GET'])
def status():
    """Get service status"""
    return jsonify({
        "success": True,
        "status": "operational",
        "engine": g_engine_type,
        "engine_name": g_engine.get('name', 'Unknown') if g_engine else None,
        "endpoints": {
            "/health": "GET - Health check",
            "/api/v1/face/compare": "POST - Compare two faces",
            "/api/v1/status": "GET - Service status"
        }
    })

@app.errorhandler(404)
def not_found(e):
    return jsonify({"success": False, "error": "Endpoint not found"}), 404

@app.errorhandler(500)
def server_error(e):
    return jsonify({"success": False, "error": "Internal server error"}), 500

if __name__ == '__main__':
    logger.info("=" * 60)
    logger.info("Starting Face Recognition HTTP Server")
    logger.info("=" * 60)
    
    
    if not detect_and_load_engine():
        logger.error("Failed to load any face recognition engine")
        logger.error("Please install: pip install facenet-pytorch torch pillow opencv-python")
        sys.exit(1)
    
    logger.info("=" * 60)
    logger.info(f"✓ Engine: {g_engine_type.upper()}")
    logger.info(f"✓ Engine Name: {g_engine.get('name', 'Unknown')}")
    logger.info("✓ Starting Flask server...")
    logger.info("=" * 60)
    logger.info("Listenning on http://0.0.0.0:5000")
    logger.info("Health check: curl http://localhost:5000/health")
    logger.info("=" * 60)
    
    
    app.run(
        host='0.0.0.0',
        port=5000,
        debug=False,
        use_reloader=False,
        threaded=True
    )
