package com.othello.ai.controller;

import com.othello.ai.dto.GameStateDto;
import com.othello.ai.dto.MoveDto;
import com.othello.ai.service.GameRoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Controller
public class GameWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketController.class);

    private final GameRoomService gameRoomService;

    public GameWebSocketController(GameRoomService gameRoomService) {
        this.gameRoomService = gameRoomService;
    }

    @MessageMapping("/game/{roomId}/move")
    @SendTo("/topic/game/{roomId}")
    public GameStateDto handleMove(
            @DestinationVariable Long roomId,
            @Payload MoveDto moveDto,
            SimpMessageHeaderAccessor headerAccessor) {
        try {
            String userIdStr = headerAccessor.getFirstNativeHeader("userId");
            if (userIdStr == null) {
                userIdStr = (String) headerAccessor.getSessionAttributes().get("userId");
            }

            Long userId = userIdStr != null ? Long.parseLong(userIdStr) : null;

            if (userId == null) {
                log.warn("UserId not found in headers or session");
                return null;
            }

            GameStateDto state = gameRoomService.makeMove(
                    roomId,
                    moveDto.getRow(),
                    moveDto.getCol(),
                    moveDto.getBoardState(),
                    moveDto.getTurn(),
                    userId);

            return state;
        } catch (Exception e) {
            log.error("Error handling move", e);
            try {
                return gameRoomService.getGameState(roomId);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    @MessageMapping("/game/{roomId}/state")
    @SendTo("/topic/game/{roomId}")
    public GameStateDto handleStateUpdate(
            @DestinationVariable Long roomId,
            @Payload GameStateDto stateDto,
            SimpMessageHeaderAccessor headerAccessor) {
        try {
            String userIdStr = headerAccessor.getFirstNativeHeader("userId");
            if (userIdStr == null) {
                userIdStr = (String) headerAccessor.getSessionAttributes().get("userId");
            }

            Long userId = userIdStr != null ? Long.parseLong(userIdStr) : null;

            if (userId == null) {
                log.warn("UserId not found in headers or session");
                return null;
            }

            gameRoomService.updateGameState(
                    roomId,
                    stateDto.getBoardState(),
                    stateDto.getTurn(),
                    stateDto.getIsGameOver() != null ? stateDto.getIsGameOver() : false,
                    stateDto.getWinner(),
                    stateDto.getStatus());

            GameStateDto updatedState = gameRoomService.getGameState(roomId);
            log.info("Broadcasting game state update for room {}: BoardState={}, Turn={}", roomId, updatedState.getBoardState(),
                    updatedState.getTurn());
            return updatedState;
        } catch (Exception e) {
            log.error("Error handling state update", e);
            return null;
        }
    }

    @MessageMapping("/game/{roomId}/nudge")
    @SendTo("/topic/game/{roomId}")
    public GameStateDto handleNudge(
            @DestinationVariable Long roomId,
            SimpMessageHeaderAccessor headerAccessor) {
        try {
            String userIdStr = headerAccessor.getFirstNativeHeader("userId");
            if (userIdStr == null) {
                userIdStr = (String) headerAccessor.getSessionAttributes().get("userId");
            }

            Long userId = userIdStr != null ? Long.parseLong(userIdStr) : null;

            if (userId == null) {
                log.warn("UserId not found in headers or session");
                return null;
            }

            return gameRoomService.sendNudgeMessage(roomId, userId);
        } catch (Exception e) {
            log.error("Error handling nudge", e);
            return null;
        }
    }

    @MessageMapping("/game/{roomId}/voice-message")
    @SendTo("/topic/game/{roomId}")
    public GameStateDto handleVoiceMessage(
            @DestinationVariable Long roomId,
            @Payload Map<String, String> payload,
            SimpMessageHeaderAccessor headerAccessor) {
        try {
            String userIdStr = headerAccessor.getFirstNativeHeader("userId");
            if (userIdStr == null) {
                userIdStr = (String) headerAccessor.getSessionAttributes().get("userId");
            }

            Long userId = userIdStr != null ? Long.parseLong(userIdStr) : null;

            if (userId == null) {
                log.warn("UserId not found in headers or session");
                return null;
            }

            String message = payload.get("message");
            if (message == null || message.trim().isEmpty()) {
                log.warn("Empty voice message received");
                return gameRoomService.getGameState(roomId);
            }

            return gameRoomService.sendVoiceMessage(roomId, userId, message.trim());
        } catch (Exception e) {
            log.error("Error handling voice message", e);
            return null;
        }
    }
}

