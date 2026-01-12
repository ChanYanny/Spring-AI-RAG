package io.github.chanyanny.rag.config;

import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pinecone.PineconeVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pinecone 向量存储配置类
 * 通过 @Value 注解读取 application.yml 中的配置
 */
@Configuration
public class PineconeEmbeddingStoreConfig {

    /**
     * Pinecone API Key，从 yaml 读取
     */
    @Value("${spring.ai.vectorstore.pinecone.api-key}")
    private String apiKey;

    /**
     * Pinecone 索引名称，从 yaml 读取
     */
    @Value("${spring.ai.vectorstore.pinecone.index-name}")
    private String indexName;

    /**
     * Pinecone 命名空间，从 yaml 读取
     */
    @Value("${spring.ai.vectorstore.pinecone.namespace}")
    private String nameSpace;

    /**
     * 创建 PineconeVectorStore Bean
     * 依赖注入 OllamaEmbeddingModel
     */
    @Bean
    public VectorStore pineconeVectorStore(OllamaEmbeddingModel ollamaEmbeddingModel) {
        return PineconeVectorStore.builder(ollamaEmbeddingModel)
                .apiKey(apiKey)        // 注入 yaml 中的 API Key
                .indexName(indexName)  // 注入 yaml 中的索引名称
                .namespace(nameSpace) // 注入 yaml 中的命名空间
                .build();
    }

}
