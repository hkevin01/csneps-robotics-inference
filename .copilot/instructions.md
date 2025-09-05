# GitHub Copilot Configuration for CSNePS Robotics Inference

## Project Context

This is a hybrid symbolic-probabilistic reasoning system that integrates:
- **CSNePS (Clojure SNePS 3)** for knowledge representation and inference
- **Multi-language bridges** (Python, C++) for sensor data integration
- **Domain expertise** in robotics, computer vision, GNC, and medical imaging
- **Real-time reasoning** with explainable AI capabilities

## Code Patterns and Conventions

### Clojure (CSNePS Core)
- Use kebab-case for function and variable names
- Namespace organization: `csri.domain.component`
- Prefer pure functions and immutable data structures
- Use `defn` with docstrings for public functions
- Follow CSNePS conventions for knowledge representation

### Python (Bridge Adapters)
- Use snake_case for functions and variables
- Class names in PascalCase
- Type hints for all public functions
- Async/await for I/O operations
- Comprehensive error handling

### C++ (High-Performance Bridges)
- Google style guide conventions
- RAII for resource management
- Smart pointers over raw pointers
- Exception safety guarantees
- Modern C++20 features when appropriate

## Domain-Specific Knowledge

### Robotics/SLAM
- Coordinate frames and transformations
- Uncertainty representation (covariance matrices)
- Loop closure detection and validation
- Map consistency checking

### Computer Vision
- Object detection pipelines
- Feature extraction and matching
- Multi-modal sensor fusion
- Confidence score calibration

### Medical Imaging
- DICOM standard compliance
- Anatomical coordinate systems
- Clinical workflow integration
- Privacy and security requirements

### GNC (Guidance, Navigation, Control)
- Mission phase management
- Fault detection and isolation
- Safety-critical decision making
- Real-time constraint satisfaction

## AI Assistant Preferences

### Code Generation
- Prioritize readability and maintainability
- Include comprehensive error handling
- Add informative docstrings and comments
- Consider performance implications
- Follow established patterns in codebase

### Testing
- Generate unit tests for all new functions
- Include integration tests for adapters
- Create mock data for testing scenarios
- Test error conditions and edge cases

### Documentation
- Generate API documentation from code
- Include usage examples
- Explain complex algorithms and reasoning
- Document integration patterns

### Refactoring
- Maintain backward compatibility
- Preserve existing test coverage
- Update related documentation
- Consider performance impact

## Common Tasks

1. **Adding new observation types**: Update protobuf, adapters, and inference rules
2. **Creating inference rules**: Define conditions, actions, and justifications
3. **Building adapters**: Implement protocol translation and error handling
4. **Extending ontology**: Add new entities, relations, and hierarchies
5. **Performance optimization**: Profile bottlenecks and implement improvements

## Debugging Approaches

- Use structured logging throughout the system
- Implement comprehensive health checks
- Provide clear error messages with context
- Include tracing for inference chains
- Support replay of recorded scenarios

## Integration Patterns

- **Sensor → Adapter → CSNePS → Decision**: Standard data flow
- **Query → Reasoning → Justification → Response**: Interactive queries
- **Conflict → Resolution → Update**: Consistency management
- **Event → Rule → Action → Feedback**: Reactive behavior
