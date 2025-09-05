#!/bin/bash

# CSNePS Docker Orchestration Test Script
# Validates the complete Docker setup with health checks

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
PROJECT_NAME="csri"
TEST_TIMEOUT=300
LOG_FILE="docker-test-$(date +%Y%m%d-%H%M%S).log"

echo -e "${BLUE}===========================================${NC}"
echo -e "${BLUE}CSNePS Docker Orchestration Test Suite${NC}"
echo -e "${BLUE}===========================================${NC}"
echo

# Function to log messages
log() {
    echo -e "$1" | tee -a "$LOG_FILE"
}

# Function to check if a service is healthy
check_service_health() {
    local service_name=$1
    local health_url=$2
    local max_attempts=10
    local attempt=1

    log "${YELLOW}Checking health of $service_name...${NC}"

    while [ $attempt -le $max_attempts ]; do
        if curl -f -s "$health_url" > /dev/null 2>&1; then
            log "${GREEN}✓ $service_name is healthy${NC}"
            return 0
        fi

        log "${YELLOW}  Attempt $attempt/$max_attempts - $service_name not ready yet${NC}"
        sleep 10
        ((attempt++))
    done

    log "${RED}✗ $service_name failed health check${NC}"
    return 1
}

# Function to check gRPC service health
check_grpc_health() {
    local service_name=$1
    local grpc_addr=$2
    local max_attempts=10
    local attempt=1

    log "${YELLOW}Checking gRPC health of $service_name...${NC}"

    while [ $attempt -le $max_attempts ]; do
        if grpc_health_probe -addr="$grpc_addr" > /dev/null 2>&1; then
            log "${GREEN}✓ $service_name gRPC is healthy${NC}"
            return 0
        fi

        log "${YELLOW}  Attempt $attempt/$max_attempts - $service_name gRPC not ready yet${NC}"
        sleep 10
        ((attempt++))
    done

    log "${RED}✗ $service_name gRPC failed health check${NC}"
    return 1
}

# Function to test Docker and Docker Compose
test_docker_setup() {
    log "${CYAN}Testing Docker setup...${NC}"

    if ! command -v docker &> /dev/null; then
        log "${RED}✗ Docker is not installed${NC}"
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null; then
        log "${RED}✗ Docker Compose is not installed${NC}"
        exit 1
    fi

    if ! docker info &> /dev/null; then
        log "${RED}✗ Docker daemon is not running${NC}"
        exit 1
    fi

    log "${GREEN}✓ Docker setup is valid${NC}"
}

# Function to test GPU availability
test_gpu_setup() {
    log "${CYAN}Testing GPU setup...${NC}"

    if ! command -v rocm-smi &> /dev/null; then
        log "${YELLOW}⚠ ROCm not installed - GPU tests will be skipped${NC}"
        return 1
    fi

    if ! rocm-smi &> /dev/null; then
        log "${YELLOW}⚠ No AMD GPU detected - GPU tests will be skipped${NC}"
        return 1
    fi

    log "${GREEN}✓ GPU setup is valid${NC}"
    return 0
}

# Function to start services with profile
start_services() {
    local profile=$1

    log "${CYAN}Starting services with profile: $profile${NC}"

    if [ "$profile" = "core" ]; then
        docker-compose up -d
    else
        COMPOSE_PROFILES="$profile" docker-compose up -d
    fi

    # Wait for services to start
    sleep 30
}

# Function to stop services
stop_services() {
    log "${CYAN}Stopping all services...${NC}"
    docker-compose down -v
}

# Function to test core services
test_core_services() {
    log "${PURPLE}Testing core services...${NC}"

    start_services "core"

    # Test CSNePS core
    check_service_health "CSNePS Core" "http://localhost:3000/health"
    check_service_health "CSNePS Assert" "http://localhost:3000/assert"
    check_service_health "CSNePS Query" "http://localhost:3000/query"
    check_service_health "CSNePS Why" "http://localhost:3000/why"
    check_grpc_health "CSNePS gRPC" "localhost:50051"

    # Test Python bridge
    check_service_health "Python Bridge" "http://localhost:8080/health"

    # Test Web UI
    check_service_health "Web UI" "http://localhost:8090/health"

    # Test Java KG Service
    check_grpc_health "Java KG Service" "localhost:50052"

    stop_services
    log "${GREEN}✓ Core services test completed${NC}"
}

# Function to test GPU services
test_gpu_services() {
    if ! test_gpu_setup; then
        log "${YELLOW}Skipping GPU services test${NC}"
        return
    fi

    log "${PURPLE}Testing GPU services...${NC}"

    start_services "gpu"

    # Test all core services
    check_service_health "CSNePS Core" "http://localhost:3000/health"
    check_grpc_health "CSNePS gRPC" "localhost:50051"
    check_service_health "Python Bridge" "http://localhost:8080/health"
    check_service_health "Web UI" "http://localhost:8090/health"
    check_grpc_health "Java KG Service" "localhost:50052"

    # Test GPU perception service
    check_service_health "GPU Perception" "http://localhost:8081/health"

    # Additional GPU verification
    if docker exec perception-gpu python3 -c "import torch; print('ROCm available:', torch.cuda.is_available())" 2>/dev/null; then
        log "${GREEN}✓ GPU perception PyTorch/ROCm integration working${NC}"
    else
        log "${RED}✗ GPU perception PyTorch/ROCm integration failed${NC}"
    fi

    stop_services
    log "${GREEN}✓ GPU services test completed${NC}"
}

# Function to test monitoring services
test_monitoring_services() {
    log "${PURPLE}Testing monitoring services...${NC}"

    start_services "monitoring"

    # Test core services
    check_service_health "CSNePS Core" "http://localhost:3000/health"

    # Test monitoring services
    check_service_health "Prometheus" "http://localhost:9090/-/healthy"
    check_service_health "Grafana" "http://localhost:3001/api/health"

    stop_services
    log "${GREEN}✓ Monitoring services test completed${NC}"
}

# Function to test development services
test_dev_services() {
    log "${PURPLE}Testing development services...${NC}"

    start_services "dev"

    # Test core services
    check_service_health "CSNePS Core" "http://localhost:3000/health"

    # Test Jupyter
    check_service_health "Jupyter" "http://localhost:8888/api"

    stop_services
    log "${GREEN}✓ Development services test completed${NC}"
}

# Function to run comprehensive test
run_comprehensive_test() {
    log "${PURPLE}Running comprehensive test suite...${NC}"

    test_docker_setup
    test_core_services
    test_gpu_services
    test_monitoring_services
    test_dev_services

    log "${GREEN}===========================================${NC}"
    log "${GREEN}All tests completed successfully!${NC}"
    log "${GREEN}===========================================${NC}"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [test_type]"
    echo
    echo "Test types:"
    echo "  core       - Test core services only"
    echo "  gpu        - Test GPU services (requires ROCm)"
    echo "  monitoring - Test monitoring stack"
    echo "  dev        - Test development services"
    echo "  all        - Run all tests (default)"
    echo "  setup      - Test Docker/GPU setup only"
    echo
}

# Main execution
main() {
    local test_type=${1:-all}

    # Create log file
    touch "$LOG_FILE"
    log "${BLUE}Test log: $LOG_FILE${NC}"

    case $test_type in
        "core")
            test_docker_setup
            test_core_services
            ;;
        "gpu")
            test_docker_setup
            test_gpu_services
            ;;
        "monitoring")
            test_docker_setup
            test_monitoring_services
            ;;
        "dev")
            test_docker_setup
            test_dev_services
            ;;
        "setup")
            test_docker_setup
            test_gpu_setup
            ;;
        "all")
            run_comprehensive_test
            ;;
        "help"|"-h"|"--help")
            show_usage
            exit 0
            ;;
        *)
            log "${RED}Unknown test type: $test_type${NC}"
            show_usage
            exit 1
            ;;
    esac

    log "${GREEN}Test completed. Log saved to: $LOG_FILE${NC}"
}

# Trap for cleanup
trap 'stop_services' EXIT

# Run main function
main "$@"
