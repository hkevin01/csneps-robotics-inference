#!/bin/bash

# CSNePS Robotics Inference - CSNePS Startup Script
# Usage: ./run_csneps.sh [cli|gui] [kb-file]

set -e

# Configuration
CSNEPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../src/csneps-core" && pwd)"
DEFAULT_KB_FILE="resources/sample-kb.edn"
DEFAULT_MODE="cli"

# Parse arguments
MODE="${1:-$DEFAULT_MODE}"
KB_FILE="${2:-$DEFAULT_KB_FILE}"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
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

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    if ! command -v java &> /dev/null; then
        log_error "Java is not installed. Please install Java 11 or higher."
        exit 1
    fi

    if ! command -v lein &> /dev/null; then
        log_error "Leiningen is not installed. Please install Leiningen."
        exit 1
    fi

    # Check Java version
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    log_info "Java version: $JAVA_VERSION"

    # Check if CSNePS directory exists
    if [ ! -d "$CSNEPS_DIR" ]; then
        log_error "CSNePS directory not found: $CSNEPS_DIR"
        exit 1
    fi

    # Check if knowledge base file exists
    if [ ! -f "$CSNEPS_DIR/$KB_FILE" ]; then
        log_warn "Knowledge base file not found: $KB_FILE"
        log_warn "Will attempt to create default knowledge base"
    fi

    log_info "Prerequisites check completed"
}

# Setup environment
setup_environment() {
    log_info "Setting up environment..."

    # Set JVM options for better performance
    export JVM_OPTS="-Xmx2g -XX:+UseG1GC -XX:+UseStringDeduplication"

    # Set CSNePS specific environment variables
    export CSNEPS_PORT="${CSNEPS_PORT:-3000}"
    export GRPC_PORT="${GRPC_PORT:-50051}"
    export LOG_LEVEL="${LOG_LEVEL:-INFO}"

    log_info "Environment setup completed"
}

# Create default knowledge base if it doesn't exist
create_default_kb() {
    local kb_path="$CSNEPS_DIR/$KB_FILE"

    if [ ! -f "$kb_path" ]; then
        log_info "Creating default knowledge base: $kb_path"

        mkdir -p "$(dirname "$kb_path")"

        cat > "$kb_path" << 'EOF'
{:entities #{:entity :place :landmark :sensor :observation :hypothesis :fault :mode :patient :finding}
 :relations #{:subject :object :time :source :confidence :supports :contradicts :suggests :causes :located-in :subtype-of}
 :facts []
 :rules [{:name :example-rule
          :description "Example rule for testing"
          :domain :general
          :priority 5
          :if [:and [:Observation ?obs :confidence ?conf] [:> ?conf 0.8]]
          :then [:HighConfidenceObservation ?obs]
          :justification "High confidence observations are promoted"}]}
EOF
        log_info "Default knowledge base created"
    fi
}

# Start CSNePS
start_csneps() {
    log_info "Starting CSNePS in $MODE mode..."
    log_info "Knowledge base: $KB_FILE"
    log_info "Working directory: $CSNEPS_DIR"

    cd "$CSNEPS_DIR"

    case "$MODE" in
        "cli")
            log_info "Starting CSNePS CLI mode"
            log_info "gRPC server will be available on port $GRPC_PORT"
            lein run -c --kb "$KB_FILE"
            ;;
        "gui")
            log_info "Starting CSNePS GUI mode"
            log_info "GUI will be available on port $CSNEPS_PORT"
            lein run --kb "$KB_FILE"
            ;;
        "repl")
            log_info "Starting CSNePS REPL mode"
            lein repl
            ;;
        *)
            log_error "Unknown mode: $MODE"
            log_error "Valid modes: cli, gui, repl"
            exit 1
            ;;
    esac
}

# Cleanup function
cleanup() {
    log_info "Cleaning up..."
    # Add any cleanup logic here
}

# Trap cleanup on exit
trap cleanup EXIT

# Main execution
main() {
    log_info "CSNePS Robotics Inference - Starting CSNePS Core"
    log_info "======================================================="

    check_prerequisites
    setup_environment
    create_default_kb
    start_csneps
}

# Show usage if help requested
if [[ "$1" == "-h" || "$1" == "--help" ]]; then
    echo "Usage: $0 [MODE] [KB_FILE]"
    echo ""
    echo "Modes:"
    echo "  cli    - Start CSNePS in CLI mode (default)"
    echo "  gui    - Start CSNePS in GUI mode"
    echo "  repl   - Start CSNePS REPL for development"
    echo ""
    echo "Examples:"
    echo "  $0 cli"
    echo "  $0 gui resources/medical-kb.edn"
    echo "  $0 repl"
    echo ""
    echo "Environment variables:"
    echo "  CSNEPS_PORT - Port for CSNePS GUI (default: 3000)"
    echo "  GRPC_PORT   - Port for gRPC server (default: 50051)"
    echo "  LOG_LEVEL   - Logging level (default: INFO)"
    exit 0
fi

# Execute main function
main "$@"
