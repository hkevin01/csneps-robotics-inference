package com.csri.kg.service.grpc;

/**
 * Simple gRPC server mock implementation without complex dependencies
 * This is a placeholder for the full gRPC implementation
 */
public class GrpcServerSimple {

    private int port = 9090;
    private boolean running = false;

    public void start() {
        System.out.println("Mock gRPC server starting on port " + port);
        running = true;
        System.out.println("Mock gRPC server started successfully");
    }

    public void stop() {
        System.out.println("Mock gRPC server stopping");
        running = false;
        System.out.println("Mock gRPC server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
