package com.csri.kg.service.web.dto;

import jakarta.validation.constraints.Min;

/**
 * Data Transfer Object for provenance information.
 * Tracks source document and extraction details for assertions.
 */
public record ProvenanceDTO(
    String docId,
    
    @Min(value = 0, message = "Start offset must be >= 0")
    Integer startOffset,
    
    @Min(value = 0, message = "End offset must be >= 0") 
    Integer endOffset,
    
    String extractor,
    String modelVersion,
    String timestamp
) {
    
    /**
     * Create minimal provenance with just document ID.
     */
    public static ProvenanceDTO ofDoc(String docId) {
        return new ProvenanceDTO(docId, null, null, null, null, null);
    }
    
    /**
     * Create provenance with document and text span.
     */
    public static ProvenanceDTO ofSpan(String docId, int startOffset, int endOffset) {
        return new ProvenanceDTO(docId, startOffset, endOffset, null, null, null);
    }
    
    /**
     * Create full provenance with extractor info.
     */
    public static ProvenanceDTO of(String docId, int startOffset, int endOffset, 
                                 String extractor, String modelVersion) {
        return new ProvenanceDTO(docId, startOffset, endOffset, extractor, modelVersion, 
                               java.time.Instant.now().toString());
    }
}
