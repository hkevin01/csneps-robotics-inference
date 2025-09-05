package com.csri.kg.service.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request for batch assertion operations.
 */
public record AssertBatchRequest(
    @NotEmpty(message = "Assertions list cannot be empty")
    @Valid 
    List<AssertionDTO> assertions
) {
    
    /**
     * Create batch request from assertion list.
     */
    public static AssertBatchRequest of(List<AssertionDTO> assertions) {
        return new AssertBatchRequest(assertions);
    }
    
    /**
     * Create batch request from varargs.
     */
    public static AssertBatchRequest of(AssertionDTO... assertions) {
        return new AssertBatchRequest(List.of(assertions));
    }
}
