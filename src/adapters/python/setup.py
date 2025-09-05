from setuptools import setup, find_packages

with open("README.md", "r", encoding="utf-8") as fh:
    long_description = fh.read()

with open("requirements.txt", "r", encoding="utf-8") as fh:
    requirements = [line.strip() for line in fh if line.strip() and not line.startswith("#")]

setup(
    name="csri-bridge",
    version="0.1.0",
    author="CSNePS Robotics Inference Team",
    description="Python bridge for CSNePS Robotics Inference system",
    long_description=long_description,
    long_description_content_type="text/markdown",
    packages=find_packages(),
    classifiers=[
        "Development Status :: 3 - Alpha",
        "Intended Audience :: Developers",
        "Intended Audience :: Science/Research",
        "License :: OSI Approved :: MIT License",
        "Operating System :: OS Independent",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.8",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
        "Programming Language :: Python :: 3.11",
        "Topic :: Scientific/Engineering :: Artificial Intelligence",
        "Topic :: Software Development :: Libraries :: Python Modules",
    ],
    python_requires=">=3.8",
    install_requires=requirements,
    extras_require={
        "dev": [
            "pytest>=7.0",
            "pytest-asyncio>=0.21.0",
            "pytest-cov>=4.0",
            "black>=23.0",
            "isort>=5.12",
            "mypy>=1.0",
            "flake8>=6.0",
            "pre-commit>=3.0",
        ],
        "ros2": [
            "rclpy>=3.0",
            "geometry_msgs",
            "sensor_msgs",
            "std_msgs",
        ],
        "cv": [
            "opencv-python>=4.7",
            "numpy>=1.21",
            "pillow>=9.0",
        ],
        "medical": [
            "pydicom>=2.4",
            "nibabel>=5.1",
            "scikit-image>=0.20",
        ],
    },
    entry_points={
        "console_scripts": [
            "csri-slam-adapter=csri_bridge.examples.slam_adapter:main",
            "csri-cv-adapter=csri_bridge.examples.cv_adapter:main",
            "csri-medical-adapter=csri_bridge.examples.medical_adapter:main",
            "csri-gnc-adapter=csri_bridge.examples.gnc_adapter:main",
        ],
    },
    include_package_data=True,
    package_data={
        "csri_bridge": ["*.proto", "py.typed"],
    },
)
