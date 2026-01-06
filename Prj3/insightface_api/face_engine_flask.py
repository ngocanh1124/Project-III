"""
Flask-based InsightFace engine (UPDATED: Buffalo_SC)
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import numpy as np
import cv2
import logging
import base64
import io
import sys
from PIL import Image, ImageOps
from sklearn.metrics.pairwise import cosine_similarity

# -------- InsightFace --------
from insightface.app import FaceAnalysis

try:
    import wsq
    WSQ_AVAILABLE = True
except Exception:
    WSQ_AVAILABLE = False

app = Flask(__name__)
CORS(app)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("face-engine")

MODEL_NAME = "buffalo_sc" 
MODEL_ROOT = "./models"

print(f" Đang tải mô hình AI ({MODEL_NAME})...")

try:
    face_engine = FaceAnalysis(name=MODEL_NAME, root=MODEL_ROOT)
    face_engine.prepare(ctx_id=-1, det_size=(640, 640))
    print(f"Đã tải xong mô hình InsightFace: {MODEL_NAME}")
except Exception as e:
    print(f"LỖI KHỞI TẠO MODEL: {e}")
def decode_image_bytes(image_bytes: bytes, name="Image"):
    if not image_bytes: return None
    jp2_sig = b'\x00\x00\x00\x0C\x6A\x50\x20\x20'
    jpg_sig = b'\xFF\xD8\xFF'
    idx_jp2 = image_bytes.find(jp2_sig)
    idx_jpg = image_bytes.find(jpg_sig)
    if idx_jp2 > 0: image_bytes = image_bytes[idx_jp2:]
    elif idx_jpg > 0: image_bytes = image_bytes[idx_jpg:]
    try:
        if image_bytes.startswith(b'/9j/') or image_bytes.startswith(b'iVBORw'): 
            image_bytes = base64.b64decode(image_bytes)
    except: pass 
    try:
        pil_img = Image.open(io.BytesIO(image_bytes))
        try: pil_img = ImageOps.exif_transpose(pil_img)
        except: pass
        return cv2.cvtColor(np.array(pil_img.convert("RGB")), cv2.COLOR_RGB2BGR)
    except: pass
    try:
        nparr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is not None: return img
    except: pass
    if WSQ_AVAILABLE:
        try:
            wsq_img = wsq.decode(image_bytes)
            return cv2.cvtColor(wsq_img.astype(np.uint8), cv2.COLOR_GRAY2BGR)
        except: pass
    return None

def get_embedding(image_bytes: bytes, name="Unknown"):
    img = decode_image_bytes(image_bytes, name)
    if img is None: return None, "Cannot decode image"
    try:
        faces = face_engine.get(img)
    except Exception as e: return None, str(e)
    if not faces: return None, "No face detected"
    face = max(faces, key=lambda f: (f.bbox[2]-f.bbox[0])*(f.bbox[3]-f.bbox[1]))
    return face.embedding, None
@app.route("/compare", methods=["POST"])
def compare_faces():
    try:
        print("\n" + "="*40)
        f1 = request.files.get("chip") or request.files.get("file1")
        f2 = request.files.get("selfie") or request.files.get("file2")
        if not f1 or not f2:
            return jsonify({"error": "Missing files"}), 400
        b1, b2 = f1.read(), f2.read()
        if not b1 or not b2: return jsonify({"error": "Empty file"}), 400
        print(f" So sánh: {len(b1)} bytes vs {len(b2)} bytes")
        emb1, err1 = get_embedding(b1, "CHIP")
        emb2, err2 = get_embedding(b2, "SELFIE")
        if emb1 is None or emb2 is None:
            print(f"Lỗi: Chip={err1}, Selfie={err2}")
            return jsonify({"match": False, "score": 0.0, "message": f"{err1} | {err2}"})
        score = float(cosine_similarity([emb1], [emb2])[0][0])
        match = score >= float(request.form.get("threshold", 0.6))
        print(f"Score: {score:.4f} -> {'MATCH' if match else 'NO MATCH'}")
        return jsonify({"match": match, "score": score})
    except Exception as e:
        logger.exception("Error")
        return jsonify({"error": str(e)}), 500
if __name__ == "__main__":
    print("Face Engine (buffalo_sc) running on port 5000...")
    app.run(host="0.0.0.0", port=5000, debug=False)