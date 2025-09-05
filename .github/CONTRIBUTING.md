# Contributing to CSNePS Robotics Inference

Thank you for your interest in contributing to this project! This document provides guidelines and information for contributors.

## Code of Conduct

This project adheres to a code of conduct. By participating, you are expected to uphold this standard.

## How to Contribute

### Reporting Bugs

1. Check existing issues to avoid duplicates
2. Use the bug report template
3. Provide detailed reproduction steps
4. Include environment information

### Suggesting Features

1. Check existing feature requests
2. Use the feature request template
3. Explain the use case and benefits
4. Consider implementation complexity

### Submitting Code

1. Fork the repository
2. Create a feature branch from `develop`
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Follow coding standards
7. Submit a pull request

## Development Setup

### Prerequisites

- Java 11+ (for Clojure/CSNePS)
- Python 3.9+
- C++ compiler with C++20 support
- Docker and Docker Compose
- Leiningen (for Clojure)

### Local Development

```bash
# Clone the repository
git clone <repository-url>
cd csneps-robotics-inference

# Set up Python environment
cd src/adapters/python
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt

# Set up Clojure environment
cd src/csneps-core
lein deps

# Build C++ components
cd src/adapters/cpp
mkdir build && cd build
cmake ..
make
```

## Coding Standards

### Python

- Follow PEP 8
- Use Black for formatting
- Add type hints
- Use snake_case for functions and variables
- Use PascalCase for classes

### C++

- Follow Google C++ Style Guide
- Use clang-format for formatting
- Use snake_case for functions and variables
- Use PascalCase for classes
- Use UPPER_CASE for constants

### Clojure

- Follow standard Clojure conventions
- Use kebab-case for functions and variables
- Use PascalCase for protocols and records
- Document public functions

### Commit Messages

- Use conventional commit format
- Start with type: feat, fix, docs, style, refactor, test, chore
- Include scope in parentheses when applicable
- Keep first line under 50 characters
- Use imperative mood

Example:
```
feat(python): add gRPC adapter for SLAM observations

- Implement ObservationServicer with async support
- Add protobuf message validation
- Include comprehensive error handling
```

## Testing

### Running Tests

```bash
# Python tests
cd src/adapters/python
pytest

# C++ tests
cd src/adapters/cpp/build
ctest

# Clojure tests
cd src/csneps-core
lein test

# Integration tests
docker-compose -f docker/compose.yaml up --build
```

### Test Requirements

- All new features must include tests
- Maintain or improve test coverage
- Integration tests for multi-component features
- Performance tests for critical paths

## Documentation

- Update README.md for significant changes
- Add docstrings for public APIs
- Update docs/ for architectural changes
- Include examples for new features

## Pull Request Process

1. Ensure your branch is up to date with `develop`
2. Run all tests locally
3. Update documentation as needed
4. Fill out the PR template completely
5. Request review from appropriate maintainers
6. Address review feedback promptly

## Release Process

1. Features are merged to `develop`
2. Release candidates are created from `develop`
3. After testing, releases are merged to `main`
4. Tags are created for releases

## Questions?

- Open a discussion for general questions
- Join our community channels (if available)
- Contact maintainers for specific issues

Thank you for contributing!
