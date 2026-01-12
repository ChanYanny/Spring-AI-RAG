package io.github.chanyanny.rag.trigger.http;

import io.github.chanyanny.rag.api.IAIService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Flux;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/ollama")
public class OllamaController implements IAIService {

    @Resource
    private OllamaChatModel ollamaChatModel;


    @GetMapping("/ai/generate")
    @Override
    public ChatResponse generate(@RequestParam(value = "model") String model, 
                                 @RequestParam(value = "message") String message) {
        Prompt prompt = new Prompt(message, OllamaChatOptions.builder().model(model).build());
        return ollamaChatModel.call(prompt);
    }

    @GetMapping("/ai/generateStream")
    @Override
    public Flux<ChatResponse> generateStream(@RequestParam(value = "model") String model, 
                                             @RequestParam(value = "message") String message) {
        Prompt prompt = new Prompt(message, OllamaChatOptions.builder().model(model).build());
        return ollamaChatModel.stream(prompt);
    }

}
