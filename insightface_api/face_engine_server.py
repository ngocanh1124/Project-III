"""
Face Recognition AI HTTP Endpoint
Flask application for face comparison using InsightFace
Designed for integration with Face Attendance System
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import base64
import cv2
import numpy as np
import logging
from datetime import datetime
import traceback

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

try:
    import insightface
    app.face_engine = insightface.app.FaceAnalysis(providers=['CUDAExecutionProvider', 'CPUExecutionProvider'])
    app.face_engine.prepare(ctx_id=0, det_size=(640, 480))
    logger.info("✓ Face Engine initialized successfully")
except Exception as e:
    logger.warning(f"⚠ InsightFace not available: {e}")
    app.face_engine = None

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        'status': 'healthy',
        'service': 'Face Recognition AI',
        'version': '1.0.0',
        'timestamp': datetime.now().isoformat(),
        'face_engine': 'ready' if app.face_engine else 'not_initialized'
    }), 200

@app.route('/api/v1/face/compare', methods=['POST'])
def compare_faces():
    """
    Compare two face images
    
    Request Body:
    {
        "face1_base64": "base64_encoded_image",
        "face2_base64": "base64_encoded_image"
    }
    
    Response:
    {
        "success": true,
        "similarity": 0.85,
        "matched": true,
        "confidence": 0.92,
        "timestamp": "2024-04-02T10:30:45"
    }
    """
    try:
        if not app.face_engine:
            return jsonify({
                'success': False,
                'error': 'Face engine not initialized',
                'timestamp': datetime.now().isoformat()
            }), 503

        data = request.get_json()
        if not data or 'face1_base64' not in data or 'face2_base64' not in data:
            return jsonify({
                'success': False,
                'error': 'Missing required fields: face1_base64, face2_base64',
                'timestamp': datetime.now().isoformat()
            }), 400

        
        try:
            face1_data = base64.b64decode(data['face1_base64'])
            face2_data = base64.b64decode(data['face2_base64'])
            
            face1_array = np.frombuffer(face1_data, dtype=np.uint8)
            face2_array = np.frombuffer(face2_data, dtype=np.uint8)
            
            face1_image = cv2.imdecode(face1_array, cv2.IMREAD_COLOR)
            face2_image = cv2.imdecode(face2_array, cv2.IMREAD_COLOR)
            
            if face1_image is None or face2_image is None:
                return jsonify({
                    'success': False,
                    'error': 'Invalid image data',
                    'timestamp': datetime.now().isoformat()
                }), 400
        except Exception as e:
            logger.error(f"Image decode error: {e}")
            return jsonify({
                'success': False,
                'error': f'Image decode error: {str(e)}',
                'timestamp': datetime.now().isoformat()
            }), 400

        
        try:
            faces1 = app.face_engine.get(face1_image)
            faces2 = app.face_engine.get(face2_image)
            
            if not faces1 or not faces2:
                return jsonify({
                    'success': False,
                    'error': 'No face detected in one or both images',
                    'face1_detected': len(faces1) > 0,
                    'face2_detected': len(faces2) > 0,
                    'timestamp': datetime.now().isoformat()
                }), 400
            
            
            embedding1 = faces1[0].embedding
            embedding2 = faces2[0].embedding
            
        except Exception as e:
            logger.error(f"Face extraction error: {e}")
            return jsonify({
                'success': False,
                'error': f'Face extraction error: {str(e)}',
                'timestamp': datetime.now().isoformat()
            }), 500

        
        try:
            
            from scipy.spatial.distance import cosine
            distance = cosine(embedding1, embedding2)
            similarity = 1 - distance
            
            
            similarity = max(0, min(1, similarity))
            
            
            confidence = similarity if similarity > 0.5 else (1 - similarity)
            
            
            threshold = 0.72
            matched = similarity >= threshold
            
            result = {
                'success': True,
                'similarity': float(similarity),
                'matched': matched,
                'confidence': float(confidence),
                'threshold': threshold,
                'face1_quality': float(faces1[0].det_score) if hasattr(faces1[0], 'det_score') else None,
                'face2_quality': float(faces2[0].det_score) if hasattr(faces2[0], 'det_score') else None,
                'timestamp': datetime.now().isoformat()
            }
            
            logger.info(f"Face comparison: similarity={similarity:.4f}, matched={matched}")
            return jsonify(result), 200
            
        except Exception as e:
            logger.error(f"Similarity calculation error: {e}")
            return jsonify({
                'success': False,
                'error': f'Similarity calculation error: {str(e)}',
                'timestamp': datetime.now().isoformat()
            }), 500

    except Exception as e:
        logger.error(f"Unexpected error: {e}\n{traceback.format_exc()}")
        return jsonify({
            'success': False,
            'error': f'Unexpected error: {str(e)}',
            'timestamp': datetime.now().isoformat()
        }), 500

@app.route('/api/v1/face/compare-many', methods=['POST'])
def compare_many_faces():
    """
    Compare one face with multiple stored face vectors (for Layer 2 validation)
    
    Request Body:
    {
        "selfie_base64": "base64_encoded_image",
        "employee_vectors": [
            {"cccd": "071012345678", "vector": "base64_encoded_vector"},
            ...
        ]
    }
    
    Response:
    {
        "success": true,
        "matches": [
            {
                "cccd": "071012345678",
                "similarity": 0.87,
                "matched": true
            }
        ],
        "best_match": {
            "cccd": "071012345678",
            "similarity": 0.87
        },
        "timestamp": "2024-04-02T10:30:45"
    }
    """
    try:
        if not app.face_engine:
            return jsonify({
                'success': False,
                'error': 'Face engine not initialized',
                'timestamp': datetime.now().isoformat()
            }), 503

        data = request.get_json()
        if not data or 'selfie_base64' not in data or 'employee_vectors' not in data:
            return jsonify({
                'success': False,
                'error': 'Missing required fields',
                'timestamp': datetime.now().isoformat()
            }), 400

        
        try:
            selfie_data = base64.b64decode(data['selfie_base64'])
            selfie_array = np.frombuffer(selfie_data, dtype=np.uint8)
            selfie_image = cv2.imdecode(selfie_array, cv2.IMREAD_COLOR)
            
            if selfie_image is None:
                return jsonify({
                    'success': False,
                    'error': 'Invalid selfie image data',
                    'timestamp': datetime.now().isoformat()
                }), 400
        except Exception as e:
            return jsonify({
                'success': False,
                'error': f'Selfie decode error: {str(e)}',
                'timestamp': datetime.now().isoformat()
            }), 400

        
        try:
            faces = app.face_engine.get(selfie_image)
            if not faces:
                return jsonify({
                    'success': False,
                    'error': 'No face detected in selfie',
                    'timestamp': datetime.now().isoformat()
                }), 400
            
            selfie_embedding = faces[0].embedding
        except Exception as e:
            return jsonify({
                'success': False,
                'error': f'Face extraction error: {str(e)}',
                'timestamp': datetime.now().isoformat()
            }), 500

        
        from scipy.spatial.distance import cosine
        matches = []
        best_match = None
        best_similarity = 0

        for employee in data['employee_vectors']:
            try:
                cccd = employee.get('cccd')
                vector_base64 = employee.get('vector')
                
                if not cccd or not vector_base64:
                    continue
                
                
                vector_data = base64.b64decode(vector_base64)
                employee_embedding = np.frombuffer(vector_data, dtype=np.float32)
                
                
                distance = cosine(selfie_embedding, employee_embedding)
                similarity = 1 - distance
                similarity = max(0, min(1, similarity))
                
                threshold = 0.55
                matched = similarity >= threshold
                
                matches.append({
                    'cccd': cccd,
                    'similarity': float(similarity),
                    'matched': matched
                })
                
                if similarity > best_similarity:
                    best_similarity = similarity
                    best_match = {
                        'cccd': cccd,
                        'similarity': float(similarity)
                    }
                    
            except Exception as e:
                logger.warning(f"Error comparing with {cccd}: {e}")
                continue

        result = {
            'success': True,
            'matches': matches,
            'best_match': best_match,
            'best_similarity': float(best_similarity),
            'timestamp': datetime.now().isoformat()
        }
        
        logger.info(f"Face comparison against {len(matches)} employees")
        return jsonify(result), 200

    except Exception as e:
        logger.error(f"Unexpected error: {e}\n{traceback.format_exc()}")
        return jsonify({
            'success': False,
            'error': f'Unexpected error: {str(e)}',
            'timestamp': datetime.now().isoformat()
        }), 500

@app.route('/api/v1/face/extract', methods=['POST'])
def extract_face_vector():
    """
    Extract face vector from image for storage
    
    Request Body:
    {
        "image_base64": "base64_encoded_image",
        "cccd": "071012345678"  # optional
    }
    
    Response:
    {
        "success": true,
        "vector_base64": "base64_encoded_vector",
        "quality": 0.92,
        "timestamp": "2024-04-02T10:30:45"
    }
    """
    try:
        if not app.face_engine:
            return jsonify({
                'success': False,
                'error': 'Face engine not initialized',
                'timestamp': datetime.now().isoformat()
            }), 503

        data = request.get_json()
        if not data or 'image_base64' not in data:
            return jsonify({
                'success': False,
                'error': 'Missing required field: image_base64',
                'timestamp': datetime.now().isoformat()
            }), 400

        
        try:
            image_data = base64.b64decode(data['image_base64'])
            image_array = np.frombuffer(image_data, dtype=np.uint8)
            image = cv2.imdecode(image_array, cv2.IMREAD_COLOR)
            
            if image is None:
                return jsonify({
                    'success': False,
                    'error': 'Invalid image data',
                    'timestamp': datetime.now().isoformat()
                }), 400
        except Exception as e:
            return jsonify({
                'success': False,
                'error': f'Image decode error: {str(e)}',
                'timestamp': datetime.now().isoformat()
            }), 400

        
        try:
            faces = app.face_engine.get(image)
            if not faces:
                return jsonify({
                    'success': False,
                    'error': 'No face detected',
                    'timestamp': datetime.now().isoformat()
                }), 400
            
            face = faces[0]
            embedding = face.embedding
            quality = float(face.det_score) if hasattr(face, 'det_score') else 0.0
            
            
            vector_bytes = embedding.astype(np.float32).tobytes()
            vector_base64 = base64.b64encode(vector_bytes).decode('utf-8')
            
            result = {
                'success': True,
                'vector_base64': vector_base64,
                'quality': quality,
                'cccd': data.get('cccd'),
                'timestamp': datetime.now().isoformat()
            }
            
            logger.info(f"Face vector extracted (quality={quality:.2f})")
            return jsonify(result), 200
            
        except Exception as e:
            logger.error(f"Face extraction error: {e}")
            return jsonify({
                'success': False,
                'error': f'Face extraction error: {str(e)}',
                'timestamp': datetime.now().isoformat()
            }), 500

    except Exception as e:
        logger.error(f"Unexpected error: {e}\n{traceback.format_exc()}")
        return jsonify({
            'success': False,
            'error': f'Unexpected error: {str(e)}',
            'timestamp': datetime.now().isoformat()
        }), 500

@app.route('/api/v1/status', methods=['GET'])
def get_status():
    """Get service status and statistics"""
    return jsonify({
        'service': 'Face Recognition AI',
        'status': 'running',
        'version': '1.0.0',
        'endpoints': {
            'health': 'GET /health',
            'compare_faces': 'POST /api/v1/face/compare',
            'compare_many': 'POST /api/v1/face/compare-many',
            'extract_vector': 'POST /api/v1/face/extract',
            'status': 'GET /api/v1/status'
        },
        'face_engine': 'ready' if app.face_engine else 'not_initialized',
        'timestamp': datetime.now().isoformat()
    }), 200

if __name__ == '__main__':
    logger.info("Starting Face Recognition AI HTTP Server...")
    logger.info("Available endpoints:")
    logger.info("  GET  /health                    - Health check")
    logger.info("  POST /api/v1/face/compare       - Compare two faces")
    logger.info("  POST /api/v1/face/compare-many  - Compare face with multiple vectors")
    logger.info("  POST /api/v1/face/extract       - Extract face vector from image")
    logger.info("  GET  /api/v1/status             - Service status")
    logger.info("")
    logger.info("Listening on 0.0.0.0:5000")
    
    app.run(host='0.0.0.0', port=5000, debug=True, threaded=True)
