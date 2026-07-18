
"""
Face Comparison Service - Pure Python Alternative
Uses open-source models that don't require C++ compilation on Windows
"""

import os
import sys
import base64
import json
import logging
from pathlib import Path
from io import BytesIO
import numpy as np
from typing import Dict, List, Tuple

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

try:
    import cv2
    logger.info("✓ OpenCV imported successfully")
except ImportError as e:
    logger.error(f"✗ OpenCV not found: {e}")
    sys.exit(1)

try:
    from PIL import Image
    logger.info("✓ PIL imported successfully")
except ImportError as e:
    logger.error(f"✗ PIL not found: {e}")
    sys.exit(1)

try:
    import onnxruntime as rt
    logger.info("✓ ONNX Runtime imported successfully")
except ImportError as e:
    logger.error(f"✗ ONNX Runtime not found: {e}")
    sys.exit(1)

try:
    import facenet_pytorch
    logger.info("✓ FaceNet PyTorch imported successfully")
    HAS_FACENET = True
except ImportError:
    logger.warning("⚠ FaceNet PyTorch not available - will use fallback method")
    HAS_FACENET = False

class FaceComparisonEngine:
    """
    Face comparison engine that avoids C++ compilation issues on Windows.
    Uses available open-source models.
    """
    
    def __init__(self):
        self.ready = False
        self.use_facenet = HAS_FACENET
        
        if self.use_facenet:
            self._init_facenet()
        else:
            self._init_fallback()
    
    def _init_facenet(self):
        """Initialize FaceNet model"""
        try:
            from facenet_pytorch import MTCNN, InceptionResnetV1
            logger.info("Initializing FaceNet...")
            
            
            self.mtcnn = MTCNN(keep_all=True, device='cpu')
            logger.info("✓ MTCNN detector loaded")
            
            
            self.facenet = InceptionResnetV1(pretrained='vggface2').eval()
            logger.info("✓ InceptionResnetV1 model loaded")
            
            self.ready = True
            logger.info("✓ FaceNet engine ready")
            
        except Exception as e:
            logger.error(f"Error initializing FaceNet: {e}")
            self.use_facenet = False
            self.ready = False
    
    def _init_fallback(self):
        """Initialize fallback method using simpler algorithms"""
        logger.info("Using fallback face comparison (ORB features)")
        self.orb = cv2.ORB_create(nfeatures=500)
        self.bf = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=True)
        self.ready = True
    
    def compare_faces(self, face1_base64: str, face2_base64: str) -> Dict:
        """
        Compare two faces and return similarity score.
        
        Args:
            face1_base64: Base64-encoded first face image
            face2_base64: Base64-encoded second face image
            
        Returns:
            Dict with similarity score and match status
        """
        try:
            
            img1 = self._decode_image(face1_base64)
            img2 = self._decode_image(face2_base64)
            
            if img1 is None or img2 is None:
                return {"success": False, "error": "Could not decode images"}
            
            if self.use_facenet:
                return self._compare_facenet(img1, img2)
            else:
                return self._compare_orb(img1, img2)
                
        except Exception as e:
            logger.error(f"Error comparing faces: {e}")
            return {"success": False, "error": str(e)}
    
    def _compare_facenet(self, img1: np.ndarray, img2: np.ndarray) -> Dict:
        """Compare using FaceNet embeddings"""
        try:
            import torch
            
            
            faces1 = self.mtcnn(img1)
            faces2 = self.mtcnn(img2)
            
            if faces1 is None or faces2 is None:
                return {
                    "success": True,
                    "similarity": 0.0,
                    "matched": False,
                    "confidence": 0.0,
                    "error": "No face detected in one or both images"
                }
            
            
            with torch.no_grad():
                emb1 = self.facenet(faces1[0:1])
                emb2 = self.facenet(faces2[0:1])
            
            
            distance = torch.norm(emb1 - emb2).item()
            similarity = 1 / (1 + distance)  
            similarity = max(0, min(1, similarity))  
            
            threshold = 0.55
            matched = similarity >= threshold
            
            return {
                "success": True,
                "similarity": float(similarity),
                "matched": matched,
                "confidence": similarity if matched else (1 - similarity),
                "method": "FaceNet"
            }
            
        except Exception as e:
            logger.error(f"FaceNet comparison failed: {e}")
            return {"success": False, "error": f"FaceNet error: {e}"}
    
    def _compare_orb(self, img1: np.ndarray, img2: np.ndarray) -> Dict:
        """Compare using ORB features (fallback method)"""
        try:
            
            gray1 = cv2.cvtColor(img1, cv2.COLOR_RGB2GRAY)
            gray2 = cv2.cvtColor(img2, cv2.COLOR_RGB2GRAY)
            
            
            kp1, des1 = self.orb.detectAndCompute(gray1, None)
            kp2, des2 = self.orb.detectAndCompute(gray2, None)
            
            if des1 is None or des2 is None or len(kp1) < 5 or len(kp2) < 5:
                return {
                    "success": True,
                    "similarity": 0.0,
                    "matched": False,
                    "confidence": 0.0,
                    "note": "Insufficient features detected"
                }
            
            
            matches = self.bf.match(des1, des2)
            matches = sorted(matches, key=lambda x: x.distance)
            
            
            good_matches = len([m for m in matches if m.distance < 50])
            total_matches = max(len(kp1), len(kp2))
            similarity = good_matches / total_matches if total_matches > 0 else 0
            similarity = min(1.0, similarity)
            
            threshold = 0.55
            matched = similarity >= threshold
            
            return {
                "success": True,
                "similarity": float(similarity),
                "matched": matched,
                "confidence": similarity,
                "good_matches": good_matches,
                "method": "ORB_Features"
            }
            
        except Exception as e:
            logger.error(f"ORB comparison failed: {e}")
            return {"success": False, "error": f"ORB error: {e}"}
    
    def extract_vector(self, image_base64: str, cccd: str = None) -> Dict:
        """
        Extract face vector (embedding) from image.
        
        Args:
            image_base64: Base64-encoded image
            cccd: Employee CCCD (for reference)
            
        Returns:
            Dict with extracted vector and quality score
        """
        try:
            img = self._decode_image(image_base64)
            
            if img is None:
                return {"success": False, "error": "Could not decode image"}
            
            if self.use_facenet:
                return self._extract_facenet(img, cccd)
            else:
                return self._extract_orb(img, cccd)
                
        except Exception as e:
            logger.error(f"Error extracting vector: {e}")
            return {"success": False, "error": str(e)}
    
    def _extract_facenet(self, img: np.ndarray, cccd: str = None) -> Dict:
        """Extract using FaceNet"""
        try:
            import torch
            
            faces = self.mtcnn(img)
            
            if faces is None:
                return {
                    "success": False,
                    "error": "No face detected",
                    "quality": 0.0
                }
            
            with torch.no_grad():
                embedding = self.facenet(faces[0:1])
            
            
            emb_bytes = embedding.cpu().numpy().tobytes()
            vector_base64 = base64.b64encode(emb_bytes).decode('utf-8')
            
            return {
                "success": True,
                "vector_base64": vector_base64,
                "quality": 0.95,
                "cccd": cccd,
                "method": "FaceNet"
            }
            
        except Exception as e:
            logger.error(f"FaceNet extraction failed: {e}")
            return {"success": False, "error": str(e)}
    
    def _extract_orb(self, img: np.ndarray, cccd: str = None) -> Dict:
        """Extract using ORB features"""
        try:
            gray = cv2.cvtColor(img, cv2.COLOR_RGB2GRAY)
            kp, des = self.orb.detectAndCompute(gray, None)
            
            if des is None:
                return {
                    "success": False,
                    "error": "Could not detect features",
                    "quality": 0.0
                }
            
            
            vector = des.flatten().astype(np.float32)
            vector_bytes = vector.tobytes()
            vector_base64 = base64.b64encode(vector_bytes).decode('utf-8')
            
            quality = min(1.0, len(kp) / 500.0)  
            
            return {
                "success": True,
                "vector_base64": vector_base64,
                "quality": quality,
                "cccd": cccd,
                "features_detected": len(kp),
                "method": "ORB_Features"
            }
            
        except Exception as e:
            logger.error(f"ORB extraction failed: {e}")
            return {"success": False, "error": str(e)}
    
    def _decode_image(self, image_base64: str) -> np.ndarray:
        """Decode base64 image"""
        try:
            
            if image_base64.startswith("data:image"):
                image_base64 = image_base64.split(",")[1]
            
            
            image_bytes = base64.b64decode(image_base64)
            image = Image.open(BytesIO(image_bytes))
            image_array = cv2.cvtColor(np.array(image), cv2.COLOR_BGR2RGB)
            
            return image_array
            
        except Exception as e:
            logger.error(f"Error decoding image: {e}")
            return None
    
    def is_ready(self) -> bool:
        """Check if engine is ready"""
        return self.ready
    
    def get_status(self) -> Dict:
        """Get engine status"""
        return {
            "ready": self.ready,
            "method": "FaceNet" if self.use_facenet else "ORB_Features",
            "has_facenet": self.use_facenet,
            "models": {
                "detection": "MTCNN" if self.use_facenet else "OpenCV",
                "recognition": "InceptionResnetV1" if self.use_facenet else "ORB"
            }
        }

engine = None

def initialize_engine():
    """Initialize face comparison engine"""
    global engine
    engine = FaceComparisonEngine()
    return engine.is_ready()

def get_engine() -> FaceComparisonEngine:
    """Get engine instance"""
    global engine
    if engine is None:
        initialize_engine()
    return engine

if __name__ == "__main__":
    
    logger.info("Initializing Face Comparison Engine...")
    if initialize_engine():
        logger.info("✓ Engine ready")
        status = get_engine().get_status()
        logger.info(f"Status: {json.dumps(status, indent=2)}")
    else:
        logger.error("✗ Failed to initialize engine")
        sys.exit(1)
