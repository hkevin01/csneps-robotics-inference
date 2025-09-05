# CSNePS Robotics Inference - Docker Orchestration

This repository provides a comprehensive Docker orchestration setup for the CSNePS Robotics Inference system, featuring AMD GPU acceleration through ROCm/HIP, enterprise-grade health checks, and production-ready deployment scripts.

## Quick Start

Use the provided `run.sh` script for easy orchestration:

```bash
# Start core services only
./run.sh

# Start with GPU acceleration (requires AMD GPU with ROCm)
./run.sh gpu

# Start with development tools
./run.sh dev

# Start with monitoring stack
./run.sh monitoring

# Start everything
./run.sh all
```

## What the `run.sh` Script Does

The `run.sh` script is a comprehensive Docker orchestration tool that:

### 🚀 **Service Management**
- **Profile Support**: Automatically configures Docker Compose profiles (core, gpu, dev, monitoring, persistence)
- **Dependency Orchestration**: Ensures services start in the correct order with health check dependencies
- **Graceful Shutdown**: Implements trap handling for clean container shutdown on SIGINT/SIGTERM

### 🔍 **Health Verification**
- **Comprehensive Checks**: Validates all service endpoints including CSNePS `/assert`, `/query`, `/why` endpoints
- **gRPC Health Probes**: Verifies gRPC service health for knowledge graph operations
- **GPU Verification**: Tests ROCm/PyTorch availability and GPU device access when using GPU profile
- **Timeout Handling**: Implements configurable timeouts with retry logic

### 🎯 **GPU Acceleration Support**
- **ROCm Integration**: Full AMD GPU support with ROCm 5.7.1+ and PyTorch
- **Device Mapping**: Automatic `/dev/kfd` and `/dev/dri` device access
- **Environment Setup**: Configures HIP_VISIBLE_DEVICES, HSA_OVERRIDE_GFX_VERSION
- **Compatibility Check**: Validates GPU architecture and driver availability

### 📊 **Monitoring & Logging**
- **Real-time Status**: Colored output with progress indicators and service status
- **Health Dashboards**: Optional Prometheus + Grafana monitoring stack
- **Log Aggregation**: Centralized logging with volume mounts
- **Performance Tracking**: Service startup time measurement

### 🔧 **Development Features**
- **Hot Reload**: Development profile with volume mounts for live code changes
- **Jupyter Integration**: Embedded Jupyter Lab for interactive development
- **Debug Support**: Environment variables for debug logging and profiling

## Architecture Overview

### Core Services

| Service | Port | Description | Health Check |
|---------|------|-------------|--------------|
| **csneps-core** | 3000, 50051 | Main CSNePS knowledge system with gRPC | HTTP + gRPC health probes |
| **python-bridge** | 8080 | Python SLAM/robotics adapters | HTTP health endpoint |
| **web-ui** | 8090 | React-based web interface | HTTP health endpoint |
| **csri-kg-service** | 50052 | Java gRPC knowledge graph service | gRPC health probe |

### GPU-Accelerated Services

| Service | Port | Description | Requirements |
|---------|------|-------------|--------------|
| **perception-gpu** | 8081 | ROCm-accelerated perception | AMD GPU + ROCm 5.7.1+ |

### Optional Services (Profiles)

| Profile | Services | Use Case |
|---------|----------|----------|
| `dev` | jupyter | Interactive development |
| `monitoring` | prometheus, grafana | Performance monitoring |
| `persistence` | redis | Data persistence |
| `gpu` | perception-gpu | GPU acceleration |

## GPU Requirements

### Hardware
- AMD GPU with ROCm 5.7.1+ support (Navi 21/22/23, RDNA2/3 architecture)
- Minimum 8GB GPU memory recommended for ML workloads

### Software Prerequisites
```bash
# Install ROCm (Ubuntu/Debian)
wget https://repo.radeon.com/amdgpu-install/latest/ubuntu/jammy/amdgpu-install_5.7.50701-1_all.deb
sudo dpkg -i amdgpu-install_*.deb
sudo amdgpu-install --usecase=rocm

# Add user to GPU groups
sudo usermod -a -G render,video $USER
newgrp render
newgrp video

# Verify installation
rocm-smi
```

## Health Check Endpoints

The system provides comprehensive health monitoring:

### CSNePS Core Endpoints
- `GET /health` - Basic service health
- `GET /assert` - Knowledge assertion capability
- `GET /query` - Knowledge query capability
- `GET /why` - Reasoning explanation capability
- `grpc://localhost:50051/health` - gRPC service health

### Service-Specific Health Checks
- **Python Bridge**: `GET localhost:8080/health`
- **GPU Perception**: `GET localhost:8081/health` (includes ROCm status)
- **Web UI**: `GET localhost:8090/health`
- **Java KG Service**: `grpc://localhost:50052/health`

## Environment Configuration

### Core Environment Variables
```bash
# CSNePS Configuration
CSNEPS_PORT=3000
GRPC_PORT=50051
LOG_LEVEL=INFO
JVM_XMX=2g
JVM_XMS=512m

# GPU Configuration (when using gpu profile)
ROCM_VERSION=5.7.1
HIP_VISIBLE_DEVICES=0
HSA_OVERRIDE_GFX_VERSION=11.0.0
PYTORCH_ROCM_ARCH=gfx1030;gfx1031;gfx1032;gfx1100;gfx1101;gfx1102
```

## Advanced Usage

### Custom Profile Combinations
```bash
# Development with GPU acceleration
COMPOSE_PROFILES=dev,gpu docker-compose up

# Full monitoring with persistence
COMPOSE_PROFILES=monitoring,persistence docker-compose up

# Production deployment with all features
COMPOSE_PROFILES=gpu,monitoring,persistence docker-compose up
```

### Manual Docker Compose Usage
```bash
# Basic services
docker-compose up

# With specific profiles
docker-compose --profile gpu --profile monitoring up

# Background deployment
docker-compose --profile gpu up -d

# View logs
docker-compose logs -f csneps-core
```

### Scaling Services
```bash
# Scale Python adapters
docker-compose --profile gpu up --scale python-bridge=3

# Scale with resource limits
docker-compose up --scale perception-gpu=2 --compatibility
```

## Troubleshooting

### GPU Issues
```bash
# Check ROCm installation
rocm-smi --showpid

# Verify Docker GPU access
docker run --rm -it --device=/dev/kfd --device=/dev/dri rocm/pytorch:latest rocm-smi

# Check container GPU access
docker exec perception-gpu rocm-smi
```

### Service Health Issues
```bash
# Check service status
docker-compose ps

# View specific service logs
docker-compose logs perception-gpu

# Manual health check
curl http://localhost:3000/health
grpc_health_probe -addr=localhost:50051
```

### Performance Monitoring
```bash
# Access Grafana (monitoring profile)
open http://localhost:3001
# Login: admin/admin

# Access Prometheus
open http://localhost:9090

# View service metrics
docker stats
```

## Development Workflow

### Code Changes
1. Make code changes in `src/` directories
2. Services with volume mounts will auto-reload
3. For container rebuilds: `docker-compose build <service>`
4. Restart specific services: `docker-compose restart <service>`

### Adding New Services
1. Create Dockerfile in `docker/` directory
2. Add service definition to `docker/compose.yaml`
3. Update health checks and dependencies
4. Test with `./run.sh dev`

### GPU Development
1. Use `./run.sh gpu` for GPU-enabled development
2. Access Jupyter with GPU: `http://localhost:8888`
3. Test GPU code in perception service
4. Monitor GPU usage with `rocm-smi`

## Production Deployment

### Security Considerations
- Change default passwords in monitoring services
- Use Docker secrets for sensitive data
- Implement proper network segmentation
- Enable container security scanning

### Performance Tuning
- Adjust JVM heap sizes based on available memory
- Configure GPU memory allocation
- Tune Docker resource limits
- Monitor service metrics via Grafana

### Backup Strategy
- Volume data: `docker-compose exec redis redis-cli BGSAVE`
- Container state: `docker commit <container> <image>:backup`
- Configuration: Backup `docker/` directory

## Support

For issues and questions:
1. Check service health endpoints
2. Review Docker Compose logs
3. Verify GPU driver installation (for GPU profiles)
4. Consult CSNePS documentation
5. File issues in the project repository

---

**Note**: This orchestration setup is designed for both development and production use. The `run.sh` script provides the easiest way to get started, while Docker Compose offers fine-grained control for advanced users.
