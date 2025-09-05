#!/usr/bin/env python3
"""
Enhanced GPU Perception Service with ROCm/HIP integration for CSNePS.
Performs GPU-accelerated object detection and posts results to CSNePS.
"""

import torch
import cv2
import numpy as np
import requests
import json
import logging
import time
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Tuple
import asyncio
import aiohttp
from dataclasses import dataclass

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

@dataclass
class DetectionResult:
    """Object detection result with confidence and location."""
    object_class: str
    confidence: float
    bbox: Tuple[int, int, int, int]  # x, y, width, height
    center: Tuple[float, float]
    timestamp: float
    frame_id: int

@dataclass
class PerceptionConfig:
    """Configuration for GPU perception service."""
    csneps_base_url: str = "http://localhost:3000"
    model_name: str = "yolov5s"
    confidence_threshold: float = 0.5
    device: str = "cuda"  # Will be auto-detected for ROCm
    batch_size: int = 1
    frame_rate: int = 30
    input_size: Tuple[int, int] = (640, 640)

class GPUPerceptionService:
    """Enhanced GPU perception service with ROCm/HIP support."""

    def __init__(self, config: PerceptionConfig):
        self.config = config
        self.model = None
        self.device = None
        self.is_rocm = False
        self.frame_count = 0
        self.total_detections = 0
        self.start_time = time.time()

        # Initialize GPU and model
        self._initialize_gpu()
        self._load_model()

    def _initialize_gpu(self):
        """Initialize GPU device with ROCm/HIP or CUDA support."""
        logger.info("Initializing GPU device...")

        # Check for ROCm availability
        if torch.cuda.is_available() and hasattr(torch.version, 'hip') and torch.version.hip is not None:
            self.is_rocm = True
            self.device = torch.device('cuda')
            logger.info(f"ROCm/HIP detected - using device: {self.device}")
            logger.info(f"ROCm version: {torch.version.hip}")
            logger.info(f"GPU device: {torch.cuda.get_device_name()}")
        elif torch.cuda.is_available():
            self.device = torch.device('cuda')
            logger.info(f"CUDA detected - using device: {self.device}")
            logger.info(f"CUDA version: {torch.version.cuda}")
            logger.info(f"GPU device: {torch.cuda.get_device_name()}")
        else:
            self.device = torch.device('cpu')
            logger.warning("No GPU detected - falling back to CPU")

        # Verify GPU memory
        if self.device.type == 'cuda':
            gpu_memory = torch.cuda.get_device_properties(0).total_memory / 1e9
            logger.info(f"GPU memory available: {gpu_memory:.1f} GB")

    def _load_model(self):
        """Load YOLOv5 model optimized for GPU."""
        try:
            logger.info(f"Loading {self.config.model_name} model...")

            # Load YOLOv5 model from torch hub
            self.model = torch.hub.load('ultralytics/yolov5', self.config.model_name, pretrained=True)
            self.model.to(self.device)
            self.model.eval()

            # Configure model settings
            self.model.conf = self.config.confidence_threshold
            self.model.iou = 0.45  # NMS IoU threshold
            self.model.agnostic = False  # NMS class-agnostic
            self.model.multi_label = False  # NMS multiple labels per box
            self.model.max_det = 1000  # Maximum detections per image

            # Optimize for inference
            if self.device.type == 'cuda':
                self.model.half()  # Use FP16 for better GPU performance
                logger.info("Model converted to FP16 for GPU optimization")

            logger.info(f"Model loaded successfully on {self.device}")

            # Run warmup inference
            self._warmup_model()

        except Exception as e:
            logger.error(f"Error loading model: {e}")
            raise

    def _warmup_model(self):
        """Warmup model with dummy input for optimal GPU performance."""
        logger.info("Warming up model...")
        dummy_input = torch.randn(1, 3, *self.config.input_size)
        dummy_input = dummy_input.to(self.device)

        if self.device.type == 'cuda':
            dummy_input = dummy_input.half()

        with torch.no_grad():
            _ = self.model(dummy_input)

        logger.info("Model warmup complete")

    def process_frame(self, frame: np.ndarray) -> List[DetectionResult]:
        """Process single frame and return detections."""
        self.frame_count += 1

        try:
            # Prepare input
            input_tensor = self._prepare_input(frame)

            # Run inference
            with torch.no_grad():
                start_time = time.time()
                results = self.model(input_tensor)
                inference_time = time.time() - start_time

            # Parse results
            detections = self._parse_results(results, inference_time)
            self.total_detections += len(detections)

            if detections:
                logger.debug(f"Frame {self.frame_count}: {len(detections)} detections in {inference_time*1000:.1f}ms")

            return detections

        except Exception as e:
            logger.error(f"Error processing frame {self.frame_count}: {e}")
            return []

    def _prepare_input(self, frame: np.ndarray) -> torch.Tensor:
        """Prepare frame for model input."""
        # Resize frame
        resized = cv2.resize(frame, self.config.input_size)

        # Convert BGR to RGB
        rgb_frame = cv2.cvtColor(resized, cv2.COLOR_BGR2RGB)

        # Convert to tensor and normalize
        tensor = torch.from_numpy(rgb_frame).float()
        tensor = tensor.permute(2, 0, 1).unsqueeze(0)  # HWC to BCHW
        tensor /= 255.0  # Normalize to [0, 1]

        # Move to device
        tensor = tensor.to(self.device)

        if self.device.type == 'cuda':
            tensor = tensor.half()

        return tensor

    def _parse_results(self, results, inference_time: float) -> List[DetectionResult]:
        """Parse YOLO results into DetectionResult objects."""
        detections = []
        current_time = time.time()

        # Extract detections from results
        pred = results.pred[0].cpu().numpy()  # Get predictions for first image

        for detection in pred:
            x1, y1, x2, y2, conf, cls_id = detection

            if conf >= self.config.confidence_threshold:
                # Get class name
                class_name = self.model.names[int(cls_id)]

                # Calculate bbox and center
                bbox = (int(x1), int(y1), int(x2 - x1), int(y2 - y1))
                center = ((x1 + x2) / 2, (y1 + y2) / 2)

                detection_result = DetectionResult(
                    object_class=class_name,
                    confidence=float(conf),
                    bbox=bbox,
                    center=center,
                    timestamp=current_time,
                    frame_id=self.frame_count
                )

                detections.append(detection_result)

        return detections

    async def post_to_csneps(self, detection: DetectionResult) -> bool:
        """Post detection result to CSNePS asynchronously."""
        try:
            # Create CSNePS assertion
            assertion = self._create_csneps_assertion(detection)

            # Post to CSNePS via HTTP
            async with aiohttp.ClientSession() as session:
                url = f"{self.config.csneps_base_url}/assert"
                async with session.post(url, json={"assertion": assertion}) as response:
                    if response.status == 200:
                        logger.debug(f"Posted detection to CSNePS: {assertion}")
                        return True
                    else:
                        logger.warning(f"Failed to post to CSNePS: {response.status}")
                        return False

        except Exception as e:
            logger.error(f"Error posting to CSNePS: {e}")
            return False

    def _create_csneps_assertion(self, detection: DetectionResult) -> str:
        """Create CSNePS assertion from detection result."""
        # Generate unique object ID
        object_id = f"object_{detection.frame_id}_{hash(str(detection.bbox))}"

        # Create structured assertion
        assertion_parts = [
            f"(object {object_id})",
            f"({object_id} isa {detection.object_class})",
            f"({object_id} confidence {detection.confidence:.3f})",
            f"({object_id} location ({detection.center[0]:.1f} {detection.center[1]:.1f}))",
            f"({object_id} bbox ({detection.bbox[0]} {detection.bbox[1]} {detection.bbox[2]} {detection.bbox[3]}))",
            f"({object_id} timestamp {detection.timestamp})",
            f"({object_id} frame {detection.frame_id})"
        ]

        return " ".join(assertion_parts)

    def process_video_stream(self, video_source: str = 0):
        """Process video stream with GPU acceleration."""
        logger.info(f"Starting video stream processing from source: {video_source}")

        # Open video capture
        cap = cv2.VideoCapture(video_source)
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
        cap.set(cv2.CAP_PROP_FPS, self.config.frame_rate)

        if not cap.isOpened():
            raise RuntimeError(f"Failed to open video source: {video_source}")

        logger.info("Video stream opened successfully")

        try:
            while True:
                ret, frame = cap.read()
                if not ret:
                    logger.warning("Failed to read frame")
                    break

                # Process frame
                detections = self.process_frame(frame)

                # Post detections to CSNePS asynchronously
                if detections:
                    asyncio.create_task(self._post_detections_batch(detections))

                # Display performance metrics
                if self.frame_count % 100 == 0:
                    self._log_performance_metrics()

                # Optional: Display frame with detections
                if hasattr(self, 'display_enabled') and self.display_enabled:
                    annotated_frame = self._annotate_frame(frame, detections)
                    cv2.imshow('GPU Perception', annotated_frame)

                    if cv2.waitKey(1) & 0xFF == ord('q'):
                        break

        except KeyboardInterrupt:
            logger.info("Processing interrupted by user")

        finally:
            cap.release()
            cv2.destroyAllWindows()
            self._log_final_metrics()

    async def _post_detections_batch(self, detections: List[DetectionResult]):
        """Post batch of detections to CSNePS."""
        tasks = [self.post_to_csneps(detection) for detection in detections]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        success_count = sum(1 for result in results if result is True)
        logger.debug(f"Posted {success_count}/{len(detections)} detections to CSNePS")

    def _annotate_frame(self, frame: np.ndarray, detections: List[DetectionResult]) -> np.ndarray:
        """Annotate frame with detection results."""
        annotated = frame.copy()

        for detection in detections:
            x, y, w, h = detection.bbox

            # Draw bounding box
            cv2.rectangle(annotated, (x, y), (x + w, y + h), (0, 255, 0), 2)

            # Draw label
            label = f"{detection.object_class}: {detection.confidence:.2f}"
            cv2.putText(annotated, label, (x, y - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 2)

        # Add performance info
        fps = self.frame_count / (time.time() - self.start_time)
        info_text = f"FPS: {fps:.1f} | Frame: {self.frame_count} | GPU: {'ROCm' if self.is_rocm else 'CUDA'}"
        cv2.putText(annotated, info_text, (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)

        return annotated

    def _log_performance_metrics(self):
        """Log performance metrics."""
        elapsed_time = time.time() - self.start_time
        fps = self.frame_count / elapsed_time
        detections_per_second = self.total_detections / elapsed_time

        logger.info(f"Performance: {fps:.1f} FPS, {detections_per_second:.1f} detections/sec, "
                   f"{self.total_detections} total detections")

    def _log_final_metrics(self):
        """Log final performance metrics."""
        elapsed_time = time.time() - self.start_time
        fps = self.frame_count / elapsed_time

        logger.info("=== Final Performance Metrics ===")
        logger.info(f"Total runtime: {elapsed_time:.1f} seconds")
        logger.info(f"Frames processed: {self.frame_count}")
        logger.info(f"Average FPS: {fps:.1f}")
        logger.info(f"Total detections: {self.total_detections}")
        logger.info(f"Detections per frame: {self.total_detections / max(1, self.frame_count):.2f}")
        logger.info(f"GPU type: {'ROCm/HIP' if self.is_rocm else 'CUDA' if self.device.type == 'cuda' else 'CPU'}")

def main():
    """Main entry point for GPU perception service."""
    # Configuration
    config = PerceptionConfig(
        csneps_base_url="http://localhost:3000",
        model_name="yolov5s",
        confidence_threshold=0.5,
        frame_rate=30
    )

    # Create and run service
    service = GPUPerceptionService(config)

    logger.info("Starting GPU Perception Service for CSNePS")
    logger.info(f"Configuration: {config}")

    try:
        # Process video stream (0 for webcam, or path to video file)
        service.process_video_stream(0)
    except Exception as e:
        logger.error(f"Error in main: {e}")
        raise

if __name__ == "__main__":
    main()
