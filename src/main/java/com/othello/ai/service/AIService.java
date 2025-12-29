package com.othello.ai.service;

import com.othello.ai.dto.AIRequest;
import com.othello.ai.dto.AIResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OthelloGameEngine othelloEngine;
    
    public AIService(RestTemplate restTemplate, ObjectMapper objectMapper, OthelloGameEngine othelloEngine) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.othelloEngine = othelloEngine;
    }

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    /**
     * 오셀로 게임의 다음 수를 계산하고 격려 멘트 생성
     */
    public AIResponse getNextMove(AIRequest request, int difficulty) {
        char player = request.getTurn().charAt(0);
        String boardState = request.getBoardState();
        
        // 미니맥스 알고리즘으로 최적 수 계산
        int[] bestMove = minimax(boardState, player, difficulty, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
        
        if (bestMove == null || bestMove.length < 2) {
            // 유효한 수가 없으면 패스
            return new AIResponse("pass", "아, 이번엔 둘 곳이 없네요. 차례를 넘길게요!");
        }
        
        String moveStr = bestMove[0] + "," + bestMove[1];
        
        // OpenAI GPT로 격려 멘트 생성
        String comment = generateComment(request, bestMove[0], bestMove[1]);
        
        return new AIResponse(moveStr, comment);
    }

    /**
     * 미니맥스 알고리즘 (알파-베타 가지치기)
     */
    private int[] minimax(String boardState, char player, int depth, int alpha, int beta, boolean maximizing) {
        if (depth == 0 || othelloEngine.isGameOver(boardState)) {
            return evaluate(boardState, player);
        }
        
        List<int[]> validMoves = othelloEngine.getValidMoves(boardState, player);
        
        if (validMoves.isEmpty()) {
            // 수를 둘 수 없으면 패스하고 상대방 차례로
            char opponent = (player == OthelloGameEngine.BLACK) ? OthelloGameEngine.WHITE : OthelloGameEngine.BLACK;
            int[] result = minimax(boardState, opponent, depth - 1, alpha, beta, !maximizing);
            return new int[]{-1, -1, result[2]}; // row=-1, col=-1은 패스를 의미
        }
        
        int[] bestMove = null;
        int bestValue = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        
        for (int[] move : validMoves) {
            String newBoardState = othelloEngine.makeMove(boardState, move[0], move[1], player);
            char nextPlayer = (player == OthelloGameEngine.BLACK) ? OthelloGameEngine.WHITE : OthelloGameEngine.BLACK;
            
            // 다음 차례의 유효한 수 확인
            List<int[]> nextValidMoves = othelloEngine.getValidMoves(newBoardState, nextPlayer);
            if (nextValidMoves.isEmpty()) {
                // 상대방이 수를 둘 수 없으면 다시 내 차례
                nextPlayer = player;
            }
            
            int[] result = minimax(newBoardState, nextPlayer, depth - 1, alpha, beta, !maximizing);
            int value = result[2];
            
            if (maximizing) {
                if (value > bestValue) {
                    bestValue = value;
                    bestMove = new int[]{move[0], move[1], value};
                }
                alpha = Math.max(alpha, value);
            } else {
                if (value < bestValue) {
                    bestValue = value;
                    bestMove = new int[]{move[0], move[1], value};
                }
                beta = Math.min(beta, value);
            }
            
            if (beta <= alpha) {
                break; // 알파-베타 가지치기
            }
        }
        
        return bestMove != null ? bestMove : new int[]{-1, -1, bestValue};
    }

    /**
     * 보드 상태 평가 (간단한 휴리스틱)
     */
    private int[] evaluate(String boardState, char player) {
        int[] counts = othelloEngine.countPieces(boardState);
        int myCount = (player == OthelloGameEngine.BLACK) ? counts[0] : counts[1];
        int opponentCount = (player == OthelloGameEngine.BLACK) ? counts[1] : counts[0];
        
        // 게임 종료 시 승리/패배 점수
        if (othelloEngine.isGameOver(boardState)) {
            if (myCount > opponentCount) {
                return new int[]{-1, -1, 1000};
            } else if (myCount < opponentCount) {
                return new int[]{-1, -1, -1000};
            } else {
                return new int[]{-1, -1, 0};
            }
        }
        
        // 돌 개수 차이 + 모서리/가장자리 보너스
        int score = myCount - opponentCount;
        
        // 모서리와 가장자리 가중치
        char[] board = boardState.toCharArray();
        int cornerBonus = 0;
        int edgeBonus = 0;
        
        // 모서리 (0,0), (0,7), (7,0), (7,7)
        int[] corners = {0, 7, 56, 63};
        for (int corner : corners) {
            if (board[corner] == player) {
                cornerBonus += 10;
            } else if (board[corner] != OthelloGameEngine.EMPTY) {
                cornerBonus -= 10;
            }
        }
        
        // 가장자리 (모서리 제외)
        for (int i = 0; i < 8; i++) {
            // 상단 가장자리
            if (board[i] == player && i != 0 && i != 7) edgeBonus += 2;
            // 하단 가장자리
            if (board[56 + i] == player && i != 0 && i != 7) edgeBonus += 2;
            // 좌측 가장자리
            if (board[i * 8] == player && i != 0 && i != 7) edgeBonus += 2;
            // 우측 가장자리
            if (board[i * 8 + 7] == player && i != 0 && i != 7) edgeBonus += 2;
        }
        
        return new int[]{-1, -1, score + cornerBonus + edgeBonus};
    }

    /**
     * OpenAI GPT로 격려 멘트 생성
     */
    private String generateComment(AIRequest request, int row, int col) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        String systemPrompt = "당신은 오셀로 게임을 가르치는 친절한 선생님입니다. " +
                "아이의 이름을 부르며 격려하고 칭찬하는 멘트를 작성하세요. " +
                "예: '" + request.getUserName() + "야, 이 수 정말 좋은데? 나도 집중해야겠어요!' " +
                "응답은 반드시 JSON 형식: {\"comment\": \"멘트\"} 로만 보내세요.";

        String userPrompt = "현재 오셀로 보드 상태에서 " + request.getUserName() + "가 (" + row + ", " + col + ") 위치에 돌을 두었습니다. " +
                "이 수에 대한 친절한 격려 멘트를 작성해주세요. " +
                "**반드시 " + request.getUserName() + "의 이름을 부르면서 칭찬이나 격려의 멘트를 작성하세요.**";

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));

        body.put("messages", messages);
        body.put("response_format", Map.of("type", "json_object"));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            String responseStr = restTemplate.postForObject(apiUrl, entity, String.class);
            JsonNode root = objectMapper.readTree(responseStr);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            JsonNode commentNode = objectMapper.readTree(content);
            return commentNode.path("comment").asText();
        } catch (Exception e) {
            log.error("Error calling OpenAI API for comment", e);
            return request.getUserName() + "야, 좋은 수였어요! 계속 열심히 해봐요!";
        }
    }
}

