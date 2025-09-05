package com.csri.kg.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CSRI Knowledge Graph Service - Java microservice for CSNePS integration.
 * 
 * Provides REST and gRPC APIs for:
 * - Knowledge graph assertions
 * - Queries and inference
 * - Justification/explanation
 * - Ontology-driven validation
 */
@SpringBootApplication
public class CsriKgServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(CsriKgServiceApplication.class, args);
    }
}
