#!/bin/bash

# Setup script for CSNePS Docker Orchestration
# Makes scripts executable and verifies dependencies

echo "Setting up CSNePS Docker Orchestration..."

# Make scripts executable
chmod +x run.sh
chmod +x test-docker.sh

echo "✓ Scripts made executable"

# Check Docker installation
if ! command -v docker &> /dev/null; then
    echo "⚠ Docker is not installed. Please install Docker first."
    echo "  Visit: https://docs.docker.com/get-docker/"
else
    echo "✓ Docker found"
fi

# Check Docker Compose installation
if ! command -v docker-compose &> /dev/null; then
    echo "⚠ Docker Compose is not installed. Please install Docker Compose first."
    echo "  Visit: https://docs.docker.com/compose/install/"
else
    echo "✓ Docker Compose found"
fi

# Check for ROCm (optional)
if command -v rocm-smi &> /dev/null; then
    echo "✓ ROCm found - GPU acceleration available"
    rocm-smi --showpid > /dev/null 2>&1 && echo "✓ GPU devices detected" || echo "⚠ No GPU devices detected"
else
    echo "ℹ ROCm not found - GPU acceleration will not be available"
    echo "  For AMD GPU support, install ROCm: https://rocmdocs.amd.com/en/latest/Installation_Guide/Installation-Guide.html"
fi

echo
echo "Setup complete! You can now use:"
echo "  ./run.sh          - Start core services"
echo "  ./run.sh gpu       - Start with GPU acceleration"
echo "  ./test-docker.sh   - Test the Docker setup"
echo
echo "For full documentation, see README-docker.md"
