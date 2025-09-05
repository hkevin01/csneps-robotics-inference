# Security Policy

## Supported Versions

We currently support the following versions with security updates:

| Version | Supported          |
| ------- | ------------------ |
| 0.1.x   | :white_check_mark: |

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security vulnerability, please report it by emailing the project maintainers.

**Please do not report security vulnerabilities through public GitHub issues.**

When reporting a vulnerability, please include:

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if available)

## Response Timeline

- **Initial Response**: Within 48 hours
- **Status Update**: Within 7 days
- **Fix Timeline**: Varies based on severity and complexity

## Security Best Practices

When using this project:

1. **Keep dependencies updated**: Regularly update all dependencies
2. **Secure configuration**: Follow security guidelines in documentation
3. **Network security**: Use secure communication protocols (gRPC with TLS)
4. **Container security**: Keep Docker images updated and scan for vulnerabilities
5. **Access control**: Implement proper authentication and authorization

## Supported Communication Protocols

- gRPC with TLS encryption
- ZeroMQ with encryption where applicable
- Secure WebSocket connections for UI

## Known Security Considerations

- CSNePS core runs inference rules that could potentially consume significant resources
- Python and C++ adapters handle external sensor data that should be validated
- Docker containers should be run with appropriate security contexts
