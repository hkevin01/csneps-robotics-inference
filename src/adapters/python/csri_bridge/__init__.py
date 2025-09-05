"""
CSNePS Robotics Inference Bridge
Python package for interfacing with CSNePS reasoning system.
"""

__version__ = "0.1.0"
__author__ = "CSNePS Robotics Inference Team"

from .grpc_client import CSRIClient
from .ros2_node import CSRIRosNode
from .adapters import SLAMAdapter, ComputerVisionAdapter, MedicalAdapter, GNCAdapter

__all__ = [
    "CSRIClient",
    "CSRIRosNode", 
    "SLAMAdapter",
    "ComputerVisionAdapter",
    "MedicalAdapter",
    "GNCAdapter"
]
