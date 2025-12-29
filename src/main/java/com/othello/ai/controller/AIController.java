package com.othello.ai.controller;

import com.othello.ai.dto.AIRequest;
import com.othello.ai.dto.AIResponse;
import com.othello.ai.service.AIService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AIController {

    private final AIService aiService;

    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/move")
    public AIResponse getMove(@RequestBody Map<String, Object> request) {
        AIRequest aiRequest = new AIRequest();
        aiRequest.setBoardState((String) request.get("boardState"));
        aiRequest.setTurn((String) request.get("turn"));
        aiRequest.setUserName((String) request.get("userName"));
        
        int difficulty = request.containsKey("difficulty") ? 
                        ((Number) request.get("difficulty")).intValue() : 4;
        
        return aiService.getNextMove(aiRequest, difficulty);
    }
}

