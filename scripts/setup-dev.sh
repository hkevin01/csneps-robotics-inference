#!/bin/bash

# CSNePS Robotics Inference - Development Setup Script
# This script sets up the development environment

set -e

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# Project root directory
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

log_info "CSNePS Robotics Inference - Development Setup"
log_info "Project root: $PROJECT_ROOT"
log_info "==============================================="

# Step 1: Check and install system dependencies
log_step "1. Checking system dependencies"

check_command() {
    if command -v "$1" &> /dev/null; then
        log_info "$1 is installed"
        return 0
    else
        log_warn "$1 is not installed"
        return 1
    fi
}

# Check Java
if ! check_command java; then
    log_error "Please install Java 11 or higher"
    log_info "Ubuntu/Debian: sudo apt install openjdk-11-jdk"
    log_info "macOS: brew install openjdk@11"
    exit 1
fi

# Check Leiningen
if ! check_command lein; then
    log_error "Please install Leiningen"
    log_info "Visit: https://leiningen.org/"
    exit 1
fi

# Check Python
if ! check_command python3; then
    log_error "Please install Python 3.8 or higher"
    exit 1
fi

# Check pip
if ! check_command pip3; then
    log_error "Please install pip3"
    exit 1
fi

# Check CMake (for C++ components)
if ! check_command cmake; then
    log_warn "CMake not found - C++ components will not be built"
    log_info "Ubuntu/Debian: sudo apt install cmake"
    log_info "macOS: brew install cmake"
fi

# Check Docker (optional)
if check_command docker; then
    if check_command docker-compose; then
        log_info "Docker and Docker Compose are available"
    else
        log_warn "Docker Compose not found"
    fi
else
    log_warn "Docker not found - containerized deployment will not be available"
fi

# Step 2: Set up Python virtual environment
log_step "2. Setting up Python environment"

PYTHON_DIR="$PROJECT_ROOT/src/adapters/python"
VENV_DIR="$PYTHON_DIR/venv"

cd "$PYTHON_DIR"

if [ ! -d "$VENV_DIR" ]; then
    log_info "Creating Python virtual environment"
    python3 -m venv "$VENV_DIR"
else
    log_info "Python virtual environment already exists"
fi

# Activate virtual environment
source "$VENV_DIR/bin/activate"

# Upgrade pip and install dependencies
log_info "Installing Python dependencies"
pip install --upgrade pip setuptools wheel
pip install -r requirements.txt

# Install development dependencies
if [ -f requirements-dev.txt ]; then
    log_info "Installing development dependencies"
    pip install -r requirements-dev.txt
fi

# Step 3: Generate protobuf bindings
log_step "3. Generating protobuf bindings"

PROTO_DIR="$PROJECT_ROOT/proto"
GENERATED_DIR="$PYTHON_DIR/csri_bridge/generated"

mkdir -p "$GENERATED_DIR"

log_info "Generating Python protobuf bindings"
python -m grpc_tools.protoc \
    --proto_path="$PROTO_DIR" \
    --python_out="$GENERATED_DIR" \
    --grpc_python_out="$GENERATED_DIR" \
    "$PROTO_DIR"/*.proto

# Create __init__.py for generated package
touch "$GENERATED_DIR/__init__.py"

# Step 4: Set up Clojure dependencies
log_step "4. Setting up Clojure dependencies"

CLOJURE_DIR="$PROJECT_ROOT/src/csneps-core"
cd "$CLOJURE_DIR"

log_info "Downloading Clojure dependencies"
lein deps

# Step 5: Build C++ components (if available)
if command -v cmake &> /dev/null; then
    log_step "5. Building C++ components"

    CPP_DIR="$PROJECT_ROOT/src/adapters/cpp"
    BUILD_DIR="$CPP_DIR/build"

    mkdir -p "$BUILD_DIR"
    cd "$BUILD_DIR"

    log_info "Configuring C++ build"
    cmake ..

    log_info "Building C++ components"
    make -j$(nproc)
else
    log_step "5. Skipping C++ components (CMake not available)"
fi

# Step 6: Create sample data and configuration
log_step "6. Creating sample data and configuration"

DATA_DIR="$PROJECT_ROOT/data"
mkdir -p "$DATA_DIR/examples"
mkdir -p "$DATA_DIR/datasets"
mkdir -p "$DATA_DIR/models"

# Create example configuration
cat > "$DATA_DIR/examples/sample-config.json" << 'EOF'
{
    "csneps": {
        "port": 3000,
        "grpc_port": 50051,
        "log_level": "INFO"
    },
    "adapters": {
        "slam": {
            "enabled": true,
            "confidence_threshold": 0.7
        },
        "computer_vision": {
            "enabled": true,
            "model_path": "models/cv_model.onnx"
        },
        "medical": {
            "enabled": false,
            "dicom_support": true
        },
        "gnc": {
            "enabled": true,
            "real_time": true
        }
    }
}
EOF

# Step 7: Set up development tools
log_step "7. Setting up development tools"

# Create .env file for development
cd "$PROJECT_ROOT"
if [ ! -f .env ]; then
    cat > .env << 'EOF'
# CSNePS Robotics Inference - Development Environment

# CSNePS Configuration
CSNEPS_PORT=3000
CSNEPS_HOST=localhost
GRPC_PORT=50051
LOG_LEVEL=DEBUG

# Python Bridge Configuration
PYTHON_LOG_LEVEL=DEBUG
ADAPTER_TIMEOUT=30

# Development Settings
DEVELOPMENT_MODE=true
AUTO_RELOAD=true
DEBUG_MODE=true

# Database (if using persistence)
REDIS_URL=redis://localhost:6379

# Monitoring
PROMETHEUS_PORT=9090
GRAFANA_PORT=3001
EOF
    log_info "Created .env file for development"
else
    log_info ".env file already exists"
fi

# Install pre-commit hooks if available
if command -v pre-commit &> /dev/null; then
    log_info "Installing pre-commit hooks"
    pre-commit install
fi

# Step 8: Run basic tests
log_step "8. Running basic validation tests"

# Test Clojure setup
cd "$CLOJURE_DIR"
log_info "Testing Clojure setup"
if lein test; then
    log_info "Clojure tests passed"
else
    log_warn "Some Clojure tests failed"
fi

# Test Python setup
cd "$PYTHON_DIR"
source "$VENV_DIR/bin/activate"
log_info "Testing Python setup"
if python -c "import csri_bridge; print('Python package imported successfully')"; then
    log_info "Python package test passed"
else
    log_warn "Python package test failed"
fi

# Step 9: Generate documentation
log_step "9. Generating documentation"

DOCS_DIR="$PROJECT_ROOT/docs"

# Create API documentation structure
mkdir -p "$DOCS_DIR/api"
mkdir -p "$DOCS_DIR/examples"
mkdir -p "$DOCS_DIR/tutorials"

# Generate basic API documentation
cd "$CLOJURE_DIR"
if command -v codox &> /dev/null; then
    log_info "Generating Clojure API documentation"
    lein codox
fi

# Summary
log_step "Setup Complete!"

cat << EOF

${GREEN}CSNePS Robotics Inference development environment is ready!${NC}

${YELLOW}Next steps:${NC}
1. Start CSNePS core:    ./scripts/run_csneps.sh cli
2. Start Python bridge:  cd src/adapters/python && source venv/bin/activate && python -m csri_bridge.examples.slam_adapter
3. Open web interface:   http://localhost:8080
4. View documentation:   open docs/index.html

${YELLOW}Development commands:${NC}
- Run all tests:         ./scripts/run_tests.sh
- Build containers:      docker-compose build
- Start full stack:      docker-compose up
- Clean environment:     ./scripts/clean.sh

${YELLOW}Project structure:${NC}
- Clojure core:         src/csneps-core/
- Python adapters:      src/adapters/python/
- C++ adapters:         src/adapters/cpp/
- Web UI:               src/webui/
- Documentation:        docs/
- Examples:             data/examples/

${GREEN}Happy coding!${NC}
EOF
