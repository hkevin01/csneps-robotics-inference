package com.csri.kg.service.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * Data Transfer Object for knowledge graph assertions.
 * Represents a single triple with confidence and provenance.
 */
public record AssertionDTO(
    @NotBlank(message = "Subject cannot be empty")
    String subject,
    
    @NotBlank(message = "Predicate cannot be empty") 
    String predicate,
    
    @NotBlank(message = "Object cannot be empty")
    String object,
    
    @NotNull(message = "Confidence is required")
    @DecimalMin(value = "0.0", message = "Confidence must be >= 0.0")
    @DecimalMax(value = "1.0", message = "Confidence must be <= 1.0")
    Double confidence,
    
    ProvenanceDTO provenance
) {
    
    /**
     * Create assertion with default confidence.
     */
    public static AssertionDTO of(String subject, String predicate, String object) {
        return new AssertionDTO(subject, predicate, object, 1.0, null);
    }
    
    /**
     * Create assertion with confidence.
     */
    public static AssertionDTO of(String subject, String predicate, String object, double confidence) {
        return new AssertionDTO(subject, predicate, object, confidence, null);
    }
    
    /**
     * Create assertion with full provenance.
     */
    public static AssertionDTO of(String subject, String predicate, String object, 
                                double confidence, ProvenanceDTO provenance) {
        return new AssertionDTO(subject, predicate, object, confidence, provenance);
    }
}
