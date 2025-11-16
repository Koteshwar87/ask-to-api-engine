package com.asktoapiengine.engine.ai.browse.rag;

import com.asktoapiengine.engine.ai.browse.swagger.ApiOperationDescriptor;
import com.asktoapiengine.engine.ai.browse.swagger.SwaggerApiCatalog;
import com.asktoapiengine.engine.ai.browse.swagger.SwaggerToDocumentMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SwaggerDocumentIndexer is responsible for:
 *
 *  1. Fetching all API operations from SwaggerApiCatalog
 *  2. Converting each into a Spring AI Document
 *  3. Storing them into the VectorStore
 *
 * This class performs the ONE-TIME indexing step required for RAG.
 *
 * After this, SwaggerRetrievalService can perform similarity search
 * against these Document embeddings.
 *
 * NOTE:
 *  - This class runs ONCE during startup.
 *  - If Swagger files change, we need to restart (POC only).
 */
@Component
@RequiredArgsConstructor
public class SwaggerDocumentIndexer {

    private final SwaggerApiCatalog catalog;
    private final SwaggerToDocumentMapper mapper;
    private final VectorStore vectorStore;

    /**
     * Execute indexing right after Spring initializes all beans.
     */
    @PostConstruct
    public void buildIndex() {
        System.out.println("[SwaggerDocumentIndexer] Starting vector store indexing…");

        // 1. Get all operations from SwaggerApiCatalog
        List<ApiOperationDescriptor> operations = catalog.getAllOperations();

        if (operations.isEmpty()) {
            System.out.println("[SwaggerDocumentIndexer] No Swagger operations found. Skipping indexing.");
            return;
        }

        // 2. Convert operations → Documents
        List<Document> documents = mapper.toDocuments(operations);

        // 3. Insert all documents into the vector store
        vectorStore.add(documents);

        System.out.println("[SwaggerDocumentIndexer] Indexed " + documents.size() + " Swagger API operations into VectorStore.");
    }
}
