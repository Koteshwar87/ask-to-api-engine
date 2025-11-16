package com.asktoapiengine.engine.ai.browse.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a simple in-memory VectorStore for development.
 *
 * In production, you can replace this with:
 *  - PGVector
 *  - Redis Vector Store
 *  - Milvus / Qdrant (when Spring AI releases official support)
 */
@Configuration
public class VectorStoreConfig {

    /**
     * Creates an in-memory vector store backed by the EmbeddingModel.
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
