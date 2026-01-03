package io.github.chanyanny.rag.trigger.http;

import io.github.chanyanny.rag.api.IAIService;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ollama")
public class OllamaController implements IAIService {

    private final OllamaChatModel chatModel;

    @Autowired
    public OllamaController(OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }


    @GetMapping("/ai/generate")
    @Override
    public ChatResponse generate(@RequestParam(value = "model") String model, 
                                 @RequestParam(value = "message") String message) {
        Prompt prompt = new Prompt(message, OllamaChatOptions.builder().model(model).build());
        return chatModel.call(prompt);
    }

    @GetMapping("/ai/generateStream")
    @Override
    public Flux<ChatResponse> generateStream(@RequestParam(value = "model") String model, 
                                             @RequestParam(value = "message") String message) {
        Prompt prompt = new Prompt(message, OllamaChatOptions.builder().model(model).build());
        return chatModel.stream(prompt);
    }

}
