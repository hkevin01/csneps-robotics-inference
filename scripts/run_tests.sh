#!/bin/bash

# CSNePS Robotics Inference - Comprehensive Test Runner
# This script runs all tests across the project components

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

log_test() {
    echo -e "${BLUE}[TEST]${NC} $1"
}

# Project root directory
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST_RESULTS=()

# Test tracking
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_SKIPPED=0

record_test_result() {
    local test_name="$1"
    local result="$2"
    local details="$3"
    
    TEST_RESULTS+=("$test_name:$result:$details")
    
    case "$result" in
        "PASS") TESTS_PASSED=$((TESTS_PASSED + 1)) ;;
        "FAIL") TESTS_FAILED=$((TESTS_FAILED + 1)) ;;
        "SKIP") TESTS_SKIPPED=$((TESTS_SKIPPED + 1)) ;;
    esac
}

# Test 1: Project Structure Validation
test_project_structure() {
    log_test "Testing project structure..."
    
    local required_dirs=(
        "src/csneps-core"
        "src/adapters/python"
        "src/adapters/cpp"
        "src/webui"
        "proto"
        "docker"
        "docs"
        "tests"
        "scripts"
        "data"
        "assets"
        ".github"
        ".vscode"
        ".copilot"
    )
    
    local missing_dirs=()
    
    for dir in "${required_dirs[@]}"; do
        if [ ! -d "$PROJECT_ROOT/$dir" ]; then
            missing_dirs+=("$dir")
        fi
    done
    
    if [ ${#missing_dirs[@]} -eq 0 ]; then
        record_test_result "Project Structure" "PASS" "All required directories present"
        log_info "✓ Project structure validation passed"
    else
        record_test_result "Project Structure" "FAIL" "Missing directories: ${missing_dirs[*]}"
        log_error "✗ Missing directories: ${missing_dirs[*]}"
    fi
}

# Test 2: Configuration Files
test_configuration_files() {
    log_test "Testing configuration files..."
    
    local config_files=(
        ".gitignore"
        ".editorconfig"
        "README.md"
        "CHANGELOG.md"
        "docker/compose.yaml"
        "src/csneps-core/project.clj"
        "src/adapters/python/setup.py"
        "src/adapters/python/requirements.txt"
        ".vscode/settings.json"
        ".vscode/extensions.json"
        ".copilot/instructions.md"
        "docs/project-plan.md"
        "proto/observations.proto"
        "proto/beliefs.proto"
    )
    
    local missing_files=()
    
    for file in "${config_files[@]}"; do
        if [ ! -f "$PROJECT_ROOT/$file" ]; then
            missing_files+=("$file")
        fi
    done
    
    if [ ${#missing_files[@]} -eq 0 ]; then
        record_test_result "Configuration Files" "PASS" "All configuration files present"
        log_info "✓ Configuration files validation passed"
    else
        record_test_result "Configuration Files" "FAIL" "Missing files: ${missing_files[*]}"
        log_error "✗ Missing configuration files: ${missing_files[*]}"
    fi
}

# Test 3: Clojure Setup
test_clojure_setup() {
    log_test "Testing Clojure setup..."
    
    if ! command -v lein &> /dev/null; then
        record_test_result "Clojure Setup" "SKIP" "Leiningen not installed"
        log_warn "⚠ Leiningen not found, skipping Clojure tests"
        return
    fi
    
    cd "$PROJECT_ROOT/src/csneps-core"
    
    # Check if project.clj is valid
    if lein check 2>/dev/null; then
        record_test_result "Clojure Setup" "PASS" "project.clj is valid"
        log_info "✓ Clojure project configuration is valid"
    else
        record_test_result "Clojure Setup" "FAIL" "project.clj validation failed"
        log_error "✗ Clojure project configuration failed validation"
    fi
}

# Test 4: Python Setup
test_python_setup() {
    log_test "Testing Python setup..."
    
    if ! command -v python3 &> /dev/null; then
        record_test_result "Python Setup" "SKIP" "Python3 not installed"
        log_warn "⚠ Python3 not found, skipping Python tests"
        return
    fi
    
    cd "$PROJECT_ROOT/src/adapters/python"
    
    # Check if setup.py is valid
    if python3 setup.py check 2>/dev/null; then
        record_test_result "Python Setup" "PASS" "setup.py is valid"
        log_info "✓ Python package configuration is valid"
    else
        record_test_result "Python Setup" "FAIL" "setup.py validation failed"
        log_error "✗ Python package configuration failed validation"
    fi
}

# Test 5: Docker Configuration
test_docker_setup() {
    log_test "Testing Docker configuration..."
    
    if ! command -v docker &> /dev/null; then
        record_test_result "Docker Setup" "SKIP" "Docker not installed"
        log_warn "⚠ Docker not found, skipping Docker tests"
        return
    fi
    
    cd "$PROJECT_ROOT"
    
    # Validate docker-compose.yml
    if docker-compose config >/dev/null 2>&1; then
        record_test_result "Docker Setup" "PASS" "docker-compose.yml is valid"
        log_info "✓ Docker Compose configuration is valid"
    else
        record_test_result "Docker Setup" "FAIL" "docker-compose.yml validation failed"
        log_error "✗ Docker Compose configuration failed validation"
    fi
}

# Test 6: Protobuf Definitions
test_protobuf_setup() {
    log_test "Testing protobuf definitions..."
    
    if ! command -v protoc &> /dev/null; then
        record_test_result "Protobuf Setup" "SKIP" "protoc not installed"
        log_warn "⚠ Protocol buffer compiler not found, skipping protobuf tests"
        return
    fi
    
    cd "$PROJECT_ROOT/proto"
    
    local proto_files=(*.proto)
    local validation_passed=true
    
    for proto_file in "${proto_files[@]}"; do
        if [ -f "$proto_file" ]; then
            if ! protoc --proto_path=. --descriptor_set_out=/dev/null "$proto_file" 2>/dev/null; then
                log_error "✗ Protobuf validation failed for $proto_file"
                validation_passed=false
            fi
        fi
    done
    
    if $validation_passed; then
        record_test_result "Protobuf Setup" "PASS" "All protobuf files are valid"
        log_info "✓ Protobuf definitions validation passed"
    else
        record_test_result "Protobuf Setup" "FAIL" "Protobuf validation failed"
    fi
}

# Test 7: VSCode Configuration
test_vscode_setup() {
    log_test "Testing VSCode configuration..."
    
    local vscode_files=(
        ".vscode/settings.json"
        ".vscode/extensions.json"
    )
    
    local valid_config=true
    
    for file in "${vscode_files[@]}"; do
        if [ -f "$PROJECT_ROOT/$file" ]; then
            # Check if JSON is valid
            if ! python3 -m json.tool "$PROJECT_ROOT/$file" >/dev/null 2>&1; then
                log_error "✗ Invalid JSON in $file"
                valid_config=false
            fi
        else
            log_error "✗ Missing VSCode configuration file: $file"
            valid_config=false
        fi
    done
    
    if $valid_config; then
        record_test_result "VSCode Setup" "PASS" "VSCode configuration is valid"
        log_info "✓ VSCode configuration validation passed"
    else
        record_test_result "VSCode Setup" "FAIL" "VSCode configuration validation failed"
    fi
}

# Test 8: Documentation Structure
test_documentation() {
    log_test "Testing documentation structure..."
    
    local doc_files=(
        "README.md"
        "CHANGELOG.md"
        "docs/project-plan.md"
    )
    
    local missing_docs=()
    
    for doc in "${doc_files[@]}"; do
        if [ ! -f "$PROJECT_ROOT/$doc" ]; then
            missing_docs+=("$doc")
        elif [ ! -s "$PROJECT_ROOT/$doc" ]; then
            missing_docs+=("$doc (empty)")
        fi
    done
    
    if [ ${#missing_docs[@]} -eq 0 ]; then
        record_test_result "Documentation" "PASS" "All documentation files present and non-empty"
        log_info "✓ Documentation structure validation passed"
    else
        record_test_result "Documentation" "FAIL" "Missing/empty docs: ${missing_docs[*]}"
        log_error "✗ Missing or empty documentation: ${missing_docs[*]}"
    fi
}

# Generate test report
generate_report() {
    log_info "Generating test report..."
    
    echo ""
    echo "========================================"
    echo "       TEST EXECUTION SUMMARY"
    echo "========================================"
    echo ""
    
    printf "%-20s %-8s %-50s\n" "TEST NAME" "RESULT" "DETAILS"
    echo "------------------------------------------------------------------------"
    
    for result in "${TEST_RESULTS[@]}"; do
        IFS=':' read -r test_name test_result test_details <<< "$result"
        
        case "$test_result" in
            "PASS") color="$GREEN" ;;
            "FAIL") color="$RED" ;;
            "SKIP") color="$YELLOW" ;;
            *) color="$NC" ;;
        esac
        
        printf "%-20s ${color}%-8s${NC} %-50s\n" "$test_name" "$test_result" "$test_details"
    done
    
    echo "------------------------------------------------------------------------"
    echo ""
    echo "Summary:"
    echo "  ✓ Passed:  $TESTS_PASSED"
    echo "  ✗ Failed:  $TESTS_FAILED"
    echo "  ⚠ Skipped: $TESTS_SKIPPED"
    echo "  Total:    $((TESTS_PASSED + TESTS_FAILED + TESTS_SKIPPED))"
    echo ""
    
    if [ $TESTS_FAILED -eq 0 ]; then
        log_info "🎉 All tests passed! Project setup is complete."
        echo ""
        echo "Next steps:"
        echo "1. Run ./scripts/setup-dev.sh to set up the development environment"
        echo "2. Start CSNePS core: ./scripts/run_csneps.sh cli"
        echo "3. Start developing your inference rules and adapters!"
        return 0
    else
        log_error "❌ Some tests failed. Please review the errors above."
        return 1
    fi
}

# Main execution
main() {
    log_info "CSNePS Robotics Inference - Running Comprehensive Tests"
    log_info "======================================================="
    log_info "Project root: $PROJECT_ROOT"
    echo ""
    
    # Run all tests
    test_project_structure
    test_configuration_files
    test_clojure_setup
    test_python_setup
    test_docker_setup
    test_protobuf_setup
    test_vscode_setup
    test_documentation
    
    # Generate and display report
    generate_report
}

# Execute main function
main "$@"
