import sys
import os
import json
import cv2
import numpy as np
import base64
import io
import argparse
from flask import Flask, request, jsonify
from flask_cors import CORS
from insightface.app import FaceAnalysis
from sklearn.metrics.pairwise import cosine_similarity
from PIL import Image, ImageOps

MODEL_ROOT = os.path.join(os.path.dirname(__file__), 'models')
app_analysis = FaceAnalysis(name='buffalo_sc', root=MODEL_ROOT)
app_analysis.prepare(ctx_id=-1, det_size=(640, 640)) 

def decode_image(image_input):
    """
    Giải mã ảnh từ nhiều nguồn: đường dẫn file, bytes, hoặc base64 thô.
    """
    try:
        if isinstance(image_input, str) and os.path.isfile(image_input):
            
            return cv2.imread(image_input)
            
        elif isinstance(image_input, str):
            
            
            if "," in image_input:
                image_input = image_input.split(",")[1]
            image_data = base64.b64decode(image_input)
            
            
            pil_img = Image.open(io.BytesIO(image_data))
            pil_img = ImageOps.exif_transpose(pil_img) 
            
            
            return cv2.cvtColor(np.array(pil_img.convert("RGB")), cv2.COLOR_RGB2BGR)
            
        elif isinstance(image_input, bytes):
            
            nparr = np.frombuffer(image_input, np.uint8)
            return cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            
        return None
    except Exception as e:
        print(f"Lỗi giải mã ảnh: {e}", file=sys.stderr)
        return None

def get_face_embedding(img):
    """
    Trích xuất Vector 512 số đặc trưng của khuôn mặt rõ nhất trong ảnh.
    """
    if img is None:
        return None
        
    try:
        faces = app_analysis.get(img)
        if not faces:
            return None
            
        
        face = max(faces, key=lambda f: (f.bbox[2]-f.bbox[0])*(f.bbox[3]-f.bbox[1]))
        return face.embedding
    except Exception as e:
        print(f"Lỗi trích xuất vector: {e}", file=sys.stderr)
        return None

def main_extract_mode(image_input, is_path=False):
    """
    Chế độ này được Java gọi để lấy 1 Vector duy nhất.
    Kết quả được in ra Standard Output (console) để Java đọc.
    """
    img = None
    if is_path:
        img = decode_image(image_input) 
    else:
        img = decode_image(image_input) 

    embedding = get_face_embedding(img)
    
    if embedding is not None:
        
        print(json.dumps(embedding.tolist()))
    else:
        
        print("null")

app_api = Flask(__name__)
CORS(app_api) 

@app_api.route("/compare-vector", methods=["POST"])
def compare_vector_endpoint():
    """
    Cứu viện: So sánh 1 Vector mẫu (từ DB) với 1 ảnh Selfie.
    """
    try:
        
        data = request.json
        if not data:
            return jsonify({"error": "Missing JSON data"}), 400
            
        vector_db_str = data.get("vector_db") 
        selfie_base64 = data.get("selfie")     
        threshold = float(data.get("threshold", 0.55)) 

        if not vector_db_str or not selfie_base64:
            return jsonify({"error": "Missing vector_db or selfie"}), 400

        
        try:
            vector_db_arr = np.array(json.loads(vector_db_str)).reshape(1, -1)
        except Exception:
            return jsonify({"error": "Invalid vector_db format"}), 400

        
        img_selfie = decode_image(selfie_base64)
        emb_selfie = get_face_embedding(img_selfie)

        if emb_selfie is None:
            return jsonify({"matched": False, "score": 0.0, "message": "No face in selfie"}), 200

        
        emb_selfie_arr = emb_selfie.reshape(1, -1)
        score = float(cosine_similarity(vector_db_arr, emb_selfie_arr)[0][0])
        matched = score >= threshold

        return jsonify({
            "matched": matched,
            "score": score,
            "threshold": threshold,
            "message": "Xác thực thành công" if matched else "Khuôn mặt không khớp"
        })

    except Exception as e:
        print(f"Lỗi Server API: {e}", file=sys.stderr)
        return jsonify({"error": str(e)}), 500

@app_api.route("/extract-vector", methods=["POST"])
def extract_vector_endpoint():
    """
    Trích xuất Vector 512D từ ảnh Base64 (dùng cho enrollment/import).
    Request: { "image": "base64_string" }
    Response: { "embedding": [...512 values...], "success": true }
    """
    try:
        data = request.json
        if not data or "image" not in data:
            return jsonify({
                "success": False,
                "error": "Missing 'image' in request body",
                "embedding": None
            }), 400

        image_base64 = data.get("image")
        img = decode_image(image_base64)

        if img is None:
            return jsonify({
                "success": False,
                "error": "Failed to decode image",
                "embedding": None
            }), 400

        embedding = get_face_embedding(img)

        if embedding is None:
            return jsonify({
                "success": False,
                "error": "No face detected in image",
                "embedding": None
            }), 200

        return jsonify({
            "success": True,
            "embedding": embedding.tolist(),
            "error": None
        })

    except Exception as e:
        print(f"Lỗi extract-vector: {e}", file=sys.stderr)
        return jsonify({
            "success": False,
            "error": str(e),
            "embedding": None
        }), 500

@app_api.route("/compare", methods=["POST"])
def compare_endpoint():
    """
    So sánh công nếp: 2 ảnh Base64 hoặc Vector + ảnh.
    
    Mode 1 (2 ảnh): So sánh trực tiếp 2 ảnh
      { "image1": "base64", "image2": "base64", "threshold": 0.55 }
    
    Mode 2 (Vector + ảnh): So sánh Vector từ DB với ảnh Selfie
      { "embedding": [0.1, 0.2, ...], "image": "base64", "threshold": 0.55 }
    
    Response: { "matched": true/false, "score": 0.65, "message": "..." }
    """
    try:
        data = request.json
        if not data:
            return jsonify({"error": "Missing JSON data"}), 400

        threshold = float(data.get("threshold", 0.55))

        
        if "image1" in data and "image2" in data:
            img1 = decode_image(data["image1"])
            img2 = decode_image(data["image2"])

            if img1 is None or img2 is None:
                return jsonify({
                    "success": False,
                    "matched": False, 
                    "score": 0.0, 
                    "error": "Failed to decode images"
                }), 400

            emb1 = get_face_embedding(img1)
            # Retry for img2 (often chip image)
            emb2 = get_face_embedding(img2)
            if emb2 is None and img2 is not None:
                # Retry with upscale and CLAHE
                scaled = cv2.resize(img2, (0,0), fx=2.0, fy=2.0)
                emb2 = get_face_embedding(scaled)
                if emb2 is None:
                    lab = cv2.cvtColor(scaled, cv2.COLOR_BGR2LAB)
                    l, a, b = cv2.split(lab)
                    clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8,8))
                    cl = clahe.apply(l)
                    limg = cv2.merge((cl,a,b))
                    enhanced = cv2.cvtColor(limg, cv2.COLOR_LAB2BGR)
                    emb2 = get_face_embedding(enhanced)

            if emb1 is None or emb2 is None:
                return jsonify({
                    "success": False,
                    "matched": False, 
                    "score": 0.0, 
                    "error": "No face detected in one or both images",
                    "face1_detected": emb1 is not None,
                    "face2_detected": emb2 is not None
                }), 200

            score = float(cosine_similarity([emb1], [emb2])[0][0])
            matched = score >= threshold

            return jsonify({
                "success": True,
                "matched": matched,
                "score": round(score, 4),
                "threshold": threshold,
                "message": "Khuôn mặt khớp" if matched else "Khuôn mặt không khớp"
            })

        
        elif "embedding" in data and "image" in data:
            try:
                vector_db = np.array(data["embedding"]).reshape(1, -1)
            except Exception:
                return jsonify({"error": "Invalid embedding format"}), 400

            img = decode_image(data["image"])
            if img is None:
                return jsonify({"matched": False, "score": 0.0, "message": "Failed to decode image"}), 400

            emb = get_face_embedding(img)
            if emb is None:
                return jsonify({"matched": False, "score": 0.0, "message": "No face detected"}), 200

            score = float(cosine_similarity(vector_db, [emb])[0][0])
            matched = score >= threshold

            return jsonify({
                "matched": matched,
                "score": round(score, 4),
                "threshold": threshold,
                "message": "Khuôn mặt khớp" if matched else "Khuôn mặt không khớp"
            })

        else:
            return jsonify({"error": "Missing required fields: (image1 + image2) OR (embedding + image)"}), 400

    except Exception as e:
        print(f"Lỗi compare endpoint: {e}", file=sys.stderr)
        return jsonify({"error": str(e)}), 500

@app_api.route("/health", methods=["GET"])
def health_check():
    """Health check endpoint for monitoring."""
    return jsonify({
        "status": "healthy",
        "service": "Face Recognition Engine",
        "model": "buffalo_sc"
    })

if __name__ == "__main__":
    
    if len(sys.argv) > 1:
        parser = argparse.ArgumentParser()
        parser.add_argument("--image_data", help="Ảnh Base64 thô hoặc đường dẫn file")
        parser.add_argument("--is_path", action="store_true", help="image_data là đường dẫn file")
        args = parser.parse_args()
        
        
        if args.image_data:
            main_extract_mode(args.image_data, args.is_path)
            sys.exit(0)
    
    
    print(">>> Face Engine (buffalo_sc) đang chạy API trên cổng 5000...")
    print("Endpoints:")
    print("  POST /extract-vector - Trích xuất vector từ ảnh")
    print("  POST /compare - So sánh 2 ảnh hoặc Vector + ảnh")
    print("  POST /compare-vector - So sánh Vector từ DB với Selfie (legacy)")
    print("  GET  /health - Health check")
    app_api.run(host="0.0.0.0", port=5000, debug=False)