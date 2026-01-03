package io.github.chanyanny.rag.api;

import org.springframework.ai.chat.model.ChatResponse;

import reactor.core.publisher.Flux;


public interface IAIService {

    ChatResponse generate(String model, String message);

    Flux<ChatResponse> generateStream(String model, String message);

}

