package io.github.chanyanny.rag.config;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ollama 手动配置类
 * 通过 @Value 注解读取 application.yml 中的配置
 */
@Configuration
public class OllamaConfig {

    /**
     * Ollama API 地址，从 yaml 读取
     */
    @Value("${spring.ai.ollama.base-url}")
    private String baseUrl;

    /**
     * 聊天模型名称，从 yaml 读取
     */
    @Value("${spring.ai.ollama.chat.options.model}")
    private String chatModel;

    /**
     * 嵌入模型名称，从 yaml 读取
     */
    @Value("${spring.ai.ollama.embedding.options.model}")
    private String embeddingModel;

    /**
     * 创建 OllamaApi Bean
     * 注入 yaml 中配置的 base-url
     */
    @Bean
    public OllamaApi ollamaApi() {
        return OllamaApi.builder()
                .baseUrl(baseUrl)  // 注入 yaml 中的 base-url
                .build();
    }

    /**
     * 创建 OllamaChatModel Bean
     * 依赖注入 OllamaApi
     */
    @Bean
    public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi) {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(
                        OllamaChatOptions.builder()
                                .model(chatModel)  // 注入 yaml 中的聊天模型配置
                                .build())
                .build();
    }

    /**
     * 创建 OllamaEmbeddingModel Bean
     * 依赖注入 OllamaApi
     */
    @Bean
    public OllamaEmbeddingModel ollamaEmbeddingModel(OllamaApi ollamaApi) {
        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(
                        OllamaEmbeddingOptions.builder()
                                .model(embeddingModel)  // 注入 yaml 中的嵌入模型配置
                                .build())
                .build();
    }

}

