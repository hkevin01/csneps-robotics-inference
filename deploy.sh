#!/bin/bash

# Enhanced CSNePS + Ontology + GPU Stack Deployment Script
# Comprehensive automation for production deployment with ROCm/HIP GPU support

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_COMPOSE_FILE="$PROJECT_ROOT/docker/docker-compose.yml"
ENV_FILE="$PROJECT_ROOT/.env"
LOG_DIR="$PROJECT_ROOT/logs"
BACKUP_DIR="$PROJECT_ROOT/backups"

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_prerequisites() {
    log_info "Checking prerequisites..."

    # Check Docker
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed"
        exit 1
    fi

    # Check Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose is not installed"
        exit 1
    fi

    # Check Java
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed"
        exit 1
    fi

    # Check Maven
    if ! command -v mvn &> /dev/null; then
        log_error "Maven is not installed"
        exit 1
    fi

    # Check Python
    if ! command -v python3 &> /dev/null; then
        log_error "Python 3 is not installed"
        exit 1
    fi

    # Check GPU support
    if command -v rocm-smi &> /dev/null; then
        log_success "ROCm detected"
        export GPU_TYPE="rocm"
    elif command -v nvidia-smi &> /dev/null; then
        log_success "NVIDIA GPU detected"
        export GPU_TYPE="nvidia"
    else
        log_warning "No GPU detected - will run in CPU mode"
        export GPU_TYPE="cpu"
    fi

    log_success "Prerequisites check passed"
}

create_directories() {
    log_info "Creating required directories..."

    mkdir -p "$LOG_DIR"
    mkdir -p "$BACKUP_DIR"
    mkdir -p "$PROJECT_ROOT/data/csneps"
    mkdir -p "$PROJECT_ROOT/data/ontologies"
    mkdir -p "$PROJECT_ROOT/data/models"
    mkdir -p "$PROJECT_ROOT/data/visualizations"

    log_success "Directories created"
}

setup_environment() {
    log_info "Setting up environment..."

    # Create .env file if it doesn't exist
    if [[ ! -f "$ENV_FILE" ]]; then
        cat > "$ENV_FILE" << EOF
# CSNePS + Ontology + GPU Stack Configuration

# CSNePS Configuration
CSNEPS_HOST=localhost
CSNEPS_PORT=3000
CSNEPS_MEMORY=2g

# Database Configuration
POSTGRES_DB=csneps_db
POSTGRES_USER=csneps
POSTGRES_PASSWORD=csneps_secure_password
POSTGRES_HOST=postgres
POSTGRES_PORT=5432

# Knowledge Graph Service Configuration
KG_SERVICE_PORT=8080
KG_SERVICE_MEMORY=1g
SHACL_VALIDATION_ENABLED=true

# Ontology Configuration
OWL_CONVERTER_ENABLED=true
REASONING_ENABLED=true

# GPU Configuration
GPU_TYPE=$GPU_TYPE
PYTORCH_IMAGE_TAG=rocm5.4.2-ubuntu20.04-py3.8-pytorch1.12.1
ROCM_VERSION=5.4.2

# Visualization Configuration
VISUALIZATION_PORT=8081
LAYOUT_CACHE_SIZE=100

# Monitoring Configuration
PROMETHEUS_PORT=9090
GRAFANA_PORT=3001
GRAFANA_ADMIN_PASSWORD=admin_secure_password

# Security Configuration
JWT_SECRET=your_jwt_secret_key_here
API_RATE_LIMIT=1000
CORS_ORIGINS=http://localhost:3000,http://localhost:8080

# Development/Production Mode
ENVIRONMENT=production
LOG_LEVEL=INFO
DEBUG_MODE=false
EOF
        log_success "Environment file created: $ENV_FILE"
    else
        log_info "Environment file already exists: $ENV_FILE"
    fi

    # Source environment
    source "$ENV_FILE"
}

build_java_services() {
    log_info "Building Java services..."

    # Build CSNePS OWL Tools
    log_info "Building CSNePS OWL Tools..."
    cd "$PROJECT_ROOT/java/csri-owl-tools"
    mvn clean compile package -DskipTests
    log_success "CSNePS OWL Tools built"

    # Build Knowledge Graph Service
    log_info "Building Knowledge Graph Service..."
    cd "$PROJECT_ROOT/java/csri-kg-service"
    mvn clean compile package -DskipTests
    log_success "Knowledge Graph Service built"

    cd "$PROJECT_ROOT"
}

setup_python_environment() {
    log_info "Setting up Python environment..."

    # Create virtual environment if it doesn't exist
    if [[ ! -d "$PROJECT_ROOT/venv" ]]; then
        python3 -m venv "$PROJECT_ROOT/venv"
        log_success "Python virtual environment created"
    fi

    # Activate virtual environment
    source "$PROJECT_ROOT/venv/bin/activate"

    # Install requirements
    if [[ -f "$PROJECT_ROOT/requirements.txt" ]]; then
        pip install -r "$PROJECT_ROOT/requirements.txt"
        log_success "Python dependencies installed"
    else
        # Install basic requirements
        pip install torch torchvision torchaudio opencv-python numpy requests aiohttp
        log_success "Basic Python dependencies installed"
    fi
}

prepare_csneps() {
    log_info "Preparing CSNePS..."

    # Ensure CSNePS core is available
    if [[ ! -f "$PROJECT_ROOT/src/csneps-core/src/csri/http_server.clj" ]]; then
        log_error "CSNePS core not found. Please ensure it's properly installed."
        exit 1
    fi

    # Check if CSNePS bridge enhancements are in place
    if grep -q "handle-health" "$PROJECT_ROOT/src/csneps-core/src/csri/http_server.clj"; then
        log_success "CSNePS bridge enhancements detected"
    else
        log_warning "CSNePS bridge enhancements not detected - basic functionality only"
    fi
}

deploy_with_docker() {
    log_info "Deploying with Docker..."

    # Stop any existing containers
    docker-compose -f "$DOCKER_COMPOSE_FILE" down

    # Pull/build images
    if [[ "$GPU_TYPE" == "rocm" ]]; then
        log_info "Building ROCm-enabled containers..."
        docker-compose -f "$DOCKER_COMPOSE_FILE" --profile rocm build
    elif [[ "$GPU_TYPE" == "nvidia" ]]; then
        log_info "Building NVIDIA GPU-enabled containers..."
        docker-compose -f "$DOCKER_COMPOSE_FILE" --profile nvidia build
    else
        log_info "Building CPU-only containers..."
        docker-compose -f "$DOCKER_COMPOSE_FILE" --profile cpu build
    fi

    # Start services
    log_info "Starting services..."
    if [[ "$GPU_TYPE" == "rocm" ]]; then
        docker-compose -f "$DOCKER_COMPOSE_FILE" --profile rocm up -d
    elif [[ "$GPU_TYPE" == "nvidia" ]]; then
        docker-compose -f "$DOCKER_COMPOSE_FILE" --profile nvidia up -d
    else
        docker-compose -f "$DOCKER_COMPOSE_FILE" --profile cpu up -d
    fi

    log_success "Docker deployment completed"
}

wait_for_services() {
    log_info "Waiting for services to start..."

    # Wait for CSNePS
    log_info "Waiting for CSNePS..."
    timeout 60 bash -c 'until curl -s http://localhost:3000/health > /dev/null; do sleep 2; done' || {
        log_error "CSNePS failed to start"
        return 1
    }
    log_success "CSNePS is ready"

    # Wait for Knowledge Graph Service
    log_info "Waiting for Knowledge Graph Service..."
    timeout 60 bash -c 'until curl -s http://localhost:8080/actuator/health > /dev/null; do sleep 2; done' || {
        log_error "Knowledge Graph Service failed to start"
        return 1
    }
    log_success "Knowledge Graph Service is ready"

    # Wait for PostgreSQL
    log_info "Waiting for PostgreSQL..."
    timeout 60 bash -c 'until docker exec $(docker-compose -f '"$DOCKER_COMPOSE_FILE"' ps -q postgres) pg_isready -U '"$POSTGRES_USER"' > /dev/null; do sleep 2; done' || {
        log_error "PostgreSQL failed to start"
        return 1
    }
    log_success "PostgreSQL is ready"

    log_success "All services are ready"
}

run_integration_tests() {
    log_info "Running integration tests..."

    # Test CSNePS health
    if curl -s http://localhost:3000/health | grep -q "ok"; then
        log_success "CSNePS health check passed"
    else
        log_error "CSNePS health check failed"
        return 1
    fi

    # Test Knowledge Graph Service
    if curl -s http://localhost:8080/actuator/health | grep -q "UP"; then
        log_success "Knowledge Graph Service health check passed"
    else
        log_error "Knowledge Graph Service health check failed"
        return 1
    fi

    # Test SHACL validation
    log_info "Testing SHACL validation..."
    response=$(curl -s -X POST http://localhost:8080/api/graph/validate \
        -H "Content-Type: application/json" \
        -d '{"assertion": "TestEntity rdf:type owl:Thing"}')

    if echo "$response" | grep -q "success"; then
        log_success "SHACL validation test passed"
    else
        log_warning "SHACL validation test failed or not configured"
    fi

    # Test subgraph endpoint
    log_info "Testing subgraph endpoint..."
    response=$(curl -s http://localhost:3000/subgraph/TestConcept)
    if echo "$response" | grep -q "nodes\|edges"; then
        log_success "Subgraph endpoint test passed"
    else
        log_warning "Subgraph endpoint test failed or not configured"
    fi

    log_success "Integration tests completed"
}

load_sample_ontology() {
    log_info "Loading sample ontology..."

    # Create a simple test ontology if none exists
    if [[ ! -f "$PROJECT_ROOT/data/ontologies/test-ontology.owl" ]]; then
        cat > "$PROJECT_ROOT/data/ontologies/test-ontology.owl" << 'EOF'
<?xml version="1.0"?>
<rdf:RDF xmlns="http://example.org/ontology#"
     xml:base="http://example.org/ontology"
     xmlns:owl="http://www.w3.org/2002/07/owl#"
     xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
     xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#">

    <owl:Ontology rdf:about="http://example.org/ontology"/>

    <owl:Class rdf:about="#Robot"/>
    <owl:Class rdf:about="#Landmark"/>
    <owl:Class rdf:about="#HighConfidenceLandmark">
        <rdfs:subClassOf rdf:resource="#Landmark"/>
    </owl:Class>

    <owl:ObjectProperty rdf:about="#locatedAt"/>
    <owl:ObjectProperty rdf:about="#observes"/>

</rdf:RDF>
EOF
        log_success "Sample ontology created"
    fi

    # Load ontology via Knowledge Graph Service
    if curl -s -X POST http://localhost:8080/api/ontology/load \
        -H "Content-Type: application/json" \
        -d '{"file": "test-ontology.owl"}' | grep -q "success"; then
        log_success "Sample ontology loaded"
    else
        log_warning "Failed to load sample ontology"
    fi
}

show_service_status() {
    log_info "Service Status Summary:"
    echo

    # CSNePS
    if curl -s http://localhost:3000/health > /dev/null; then
        log_success "CSNePS: Running (http://localhost:3000)"
    else
        log_error "CSNePS: Not responding"
    fi

    # Knowledge Graph Service
    if curl -s http://localhost:8080/actuator/health > /dev/null; then
        log_success "Knowledge Graph Service: Running (http://localhost:8080)"
    else
        log_error "Knowledge Graph Service: Not responding"
    fi

    # GPU Perception (check if process is running)
    if pgrep -f "gpu_perception_service.py" > /dev/null; then
        log_success "GPU Perception Service: Running"
    else
        log_warning "GPU Perception Service: Not running (start manually)"
    fi

    # Docker services
    echo
    log_info "Docker Services:"
    docker-compose -f "$DOCKER_COMPOSE_FILE" ps
}

show_usage() {
    echo "Enhanced CSNePS + Ontology + GPU Stack Deployment"
    echo
    echo "Usage: $0 [COMMAND]"
    echo
    echo "Commands:"
    echo "  deploy     - Full deployment (default)"
    echo "  start      - Start services"
    echo "  stop       - Stop services"
    echo "  restart    - Restart services"
    echo "  status     - Show service status"
    echo "  logs       - Show logs"
    echo "  test       - Run integration tests"
    echo "  clean      - Clean up containers and volumes"
    echo "  backup     - Create backup"
    echo "  restore    - Restore from backup"
    echo "  help       - Show this help"
}

# Main script logic
case "${1:-deploy}" in
    "deploy")
        log_info "Starting full deployment..."
        check_prerequisites
        create_directories
        setup_environment
        build_java_services
        setup_python_environment
        prepare_csneps
        deploy_with_docker
        wait_for_services
        run_integration_tests
        load_sample_ontology
        show_service_status
        log_success "Deployment completed successfully!"
        ;;

    "start")
        if [[ "$GPU_TYPE" == "rocm" ]]; then
            docker-compose -f "$DOCKER_COMPOSE_FILE" --profile rocm up -d
        elif [[ "$GPU_TYPE" == "nvidia" ]]; then
            docker-compose -f "$DOCKER_COMPOSE_FILE" --profile nvidia up -d
        else
            docker-compose -f "$DOCKER_COMPOSE_FILE" --profile cpu up -d
        fi
        ;;

    "stop")
        docker-compose -f "$DOCKER_COMPOSE_FILE" down
        ;;

    "restart")
        docker-compose -f "$DOCKER_COMPOSE_FILE" restart
        ;;

    "status")
        show_service_status
        ;;

    "logs")
        docker-compose -f "$DOCKER_COMPOSE_FILE" logs -f
        ;;

    "test")
        run_integration_tests
        ;;

    "clean")
        docker-compose -f "$DOCKER_COMPOSE_FILE" down -v --remove-orphans
        docker system prune -f
        ;;

    "backup")
        timestamp=$(date +%Y%m%d_%H%M%S)
        backup_file="$BACKUP_DIR/csneps_backup_$timestamp.tar.gz"
        tar -czf "$backup_file" -C "$PROJECT_ROOT" data logs
        log_success "Backup created: $backup_file"
        ;;

    "restore")
        if [[ $# -lt 2 ]]; then
            log_error "Usage: $0 restore <backup_file>"
            exit 1
        fi
        tar -xzf "$2" -C "$PROJECT_ROOT"
        log_success "Restored from: $2"
        ;;

    "help"|*)
        show_usage
        ;;
esac
