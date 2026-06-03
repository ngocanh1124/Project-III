import argparse
import json
import logging
import os
import sys
import io

from insightface.app import FaceAnalysis
from sklearn.metrics.pairwise import cosine_similarity

MODEL_NAME = "buffalo_sc"
MODEL_ROOT = os.path.join(os.path.dirname(__file__), "models")

def decode_image_bytes(image_bytes: bytes):
    import base64
    import cv2
    import numpy as np
    from PIL import Image, ImageOps

    if not image_bytes:
        return None

    jp2_sig = b"\x00\x00\x00\x0C\x6A\x50\x20\x20"
    jpg_sig = b"\xFF\xD8\xFF"
    idx_jp2 = image_bytes.find(jp2_sig)
    idx_jpg = image_bytes.find(jpg_sig)
    if idx_jp2 > 0:
        image_bytes = image_bytes[idx_jp2:]
    elif idx_jpg > 0:
        image_bytes = image_bytes[idx_jpg:]

    try:
        if image_bytes.startswith(b"/9j/") or image_bytes.startswith(b"iVBORw"):
            image_bytes = base64.b64decode(image_bytes)
    except Exception:
        pass

    try:
        pil_img = Image.open(io.BytesIO(image_bytes))
        try:
            pil_img = ImageOps.exif_transpose(pil_img)
        except Exception:
            pass
        return cv2.cvtColor(np.array(pil_img.convert("RGB")), cv2.COLOR_RGB2BGR)
    except Exception:
        pass

    try:
        nparr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is not None:
            return img
    except Exception:
        pass

    return None

def get_embedding(face_engine: FaceAnalysis, image_path: str, name: str = "Unknown"):
    if not os.path.isfile(image_path):
        return None, f"File not found: {image_path}"

    with open(image_path, "rb") as f:
        b = f.read()

    img = decode_image_bytes(b)
    if img is None:
        return None, f"Cannot decode image: {name}"

    try:
        faces = face_engine.get(img)
    except Exception as e:
        return None, f"Detection error: {e}"

    if not faces:
        return None, "No face detected"

    face = max(faces, key=lambda f: (f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1]))
    return face.embedding, None

def main(argv=None):
    parser = argparse.ArgumentParser(description="Compare two images using InsightFace (buffalo_sc).")
    parser.add_argument("--chip", required=True, help="Path to the chip / ID image")
    parser.add_argument("--selfie", required=True, help="Path to the selfie image")
    parser.add_argument("--threshold", type=float, default=0.55, help="Matching threshold")
    args = parser.parse_args(argv)

    try:
        face_engine = FaceAnalysis(name=MODEL_NAME, root=MODEL_ROOT)
        face_engine.prepare(ctx_id=-1, det_size=(640, 640))
    except Exception as e:
        print(json.dumps({"error": f"Failed to init model: {e}"}))
        sys.exit(1)

    emb1, err1 = get_embedding(face_engine, args.chip, "chip")
    emb2, err2 = get_embedding(face_engine, args.selfie, "selfie")

    if emb1 is None or emb2 is None:
        msg = " | ".join([e for e in [err1, err2] if e])
        print(json.dumps({"match": False, "score": 0.0, "message": msg}))
        return

    score = float(cosine_similarity([emb1], [emb2])[0][0])
    match = score >= args.threshold
    print(json.dumps({"match": match, "score": score, "message": ""}))

if __name__ == "__main__":
    main()
