package com.asktoapiengine.engine.ai.browse.swagger;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SwaggerApiCatalog is a simple in-memory catalog of all API operations
 * discovered from the Swagger / OpenAPI JSON files.
 *
 * Responsibilities:
 *  - Call SwaggerLoader once at startup to load all ApiOperationDescriptor objects.
 *  - Expose helper methods so other components can:
 *      * get the full list of operations
 *      * look up a specific operation by ID
 *      * (optionally) search by tag, path, etc.
 *
 * This class is intentionally read-only after initialization:
 *  - It loads everything on startup.
 *  - The collections are safe for concurrent reads.
 */
@Component
public class SwaggerApiCatalog {

    private final SwaggerLoader swaggerLoader;

    /**
     * Map of operationId -> ApiOperationDescriptor for fast lookup.
     */
    private final Map<String, ApiOperationDescriptor> operationsById = new ConcurrentHashMap<>();

    /**
     * Immutable snapshot list of all operations.
     * Exposed via getter for read-only iteration.
     */
    @Getter
    private List<ApiOperationDescriptor> allOperations = List.of();

    public SwaggerApiCatalog(SwaggerLoader swaggerLoader) {
        this.swaggerLoader = swaggerLoader;
    }

    /**
     * Initialize the catalog after Spring has created the bean.
     *
     * This method:
     *  - Calls SwaggerLoader.loadAllOperations()
     *  - Populates allOperations and operationsById
     */
    @PostConstruct
    public void init() {
        List<ApiOperationDescriptor> loaded = swaggerLoader.loadAllOperations();

        if (loaded == null || loaded.isEmpty()) {
            System.err.println("[SwaggerApiCatalog] No operations loaded from Swagger.");
            this.allOperations = List.of();
            return;
        }

        // Create an immutable snapshot list for external iteration
        this.allOperations = Collections.unmodifiableList(new ArrayList<>(loaded));

        // Build the ID lookup map
        for (ApiOperationDescriptor op : loaded) {
            if (op.getId() == null || op.getId().isBlank()) {
                // If somehow an operation has no ID, skip putting it into the map
                continue;
            }
            operationsById.put(op.getId(), op);
        }

        System.out.println("[SwaggerApiCatalog] Loaded " + allOperations.size() + " operations from Swagger.");
    }

    /**
     * Returns an Optional with the operation for the given operationId,
     * or Optional.empty() if no such ID exists.
     *
     * This will be useful later when:
     *  - Vector store documents store "operationId" in metadata
     *  - We want to get the full descriptor from the catalog by that ID
     */
    public Optional<ApiOperationDescriptor> findByOperationId(String operationId) {
        if (operationId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(operationsById.get(operationId));
    }

    /**
     * Convenience method to search operations by a tag (case-sensitive by default).
     * This is optional but might help for debugging or future features.
     */
    public List<ApiOperationDescriptor> findByTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return List.of();
        }

        List<ApiOperationDescriptor> result = new ArrayList<>();
        for (ApiOperationDescriptor op : allOperations) {
            if (op.getTags() != null && op.getTags().contains(tag)) {
                result.add(op);
            }
        }
        return result;
    }

    /**
     * Convenience method to search operations whose path contains the given fragment.
     * Example: fragment = "/indices" will match "/indices/{indexId}/levels"
     */
    public List<ApiOperationDescriptor> findByPathContains(String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return List.of();
        }

        String lower = fragment.toLowerCase();
        List<ApiOperationDescriptor> result = new ArrayList<>();
        for (ApiOperationDescriptor op : allOperations) {
            if (op.getPath() != null && op.getPath().toLowerCase().contains(lower)) {
                result.add(op);
            }
        }
        return result;
    }
}
