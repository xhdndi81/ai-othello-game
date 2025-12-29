package com.othello.ai.service;

import com.othello.ai.dto.GameStateDto;
import com.othello.ai.dto.RoomDto;
import com.othello.ai.entity.GameHistory;
import com.othello.ai.entity.GameRoom;
import com.othello.ai.entity.OthelloGameData;
import com.othello.ai.entity.User;
import com.othello.ai.repository.GameHistoryRepository;
import com.othello.ai.repository.GameRoomRepository;
import com.othello.ai.repository.OthelloGameDataRepository;
import com.othello.ai.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GameRoomService {

    private static final Logger log = LoggerFactory.getLogger(GameRoomService.class);

    private final GameRoomRepository gameRoomRepository;
    private final OthelloGameDataRepository othelloGameDataRepository;
    private final UserRepository userRepository;
    private final GameHistoryRepository gameHistoryRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final OthelloGameEngine othelloEngine;

    public GameRoomService(GameRoomRepository gameRoomRepository, 
                          OthelloGameDataRepository othelloGameDataRepository, 
                          UserRepository userRepository, 
                          GameHistoryRepository gameHistoryRepository, 
                          SimpMessagingTemplate messagingTemplate,
                          OthelloGameEngine othelloEngine) {
        this.gameRoomRepository = gameRoomRepository;
        this.othelloGameDataRepository = othelloGameDataRepository;
        this.userRepository = userRepository;
        this.gameHistoryRepository = gameHistoryRepository;
        this.messagingTemplate = messagingTemplate;
        this.othelloEngine = othelloEngine;
    }

    @Transactional
    public GameRoom createRoom(Long hostId) {
        User host = userRepository.findById(hostId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        GameRoom room = new GameRoom();
        room.setHost(host);
        room.setStatus(GameRoom.RoomStatus.WAITING);
        room.setGameType(GameRoom.GameType.OTHELLO);
        
        GameRoom savedRoom = gameRoomRepository.save(room);
        
        // OthelloGameData 생성
        String initialBoardState = othelloEngine.getInitialBoardState();
        OthelloGameData othelloData = new OthelloGameData(savedRoom, initialBoardState, "B"); // 흑이 먼저 시작
        othelloGameDataRepository.save(othelloData);

        return savedRoom;
    }

    @Transactional
    public void handleUserDisconnect(Long userId) {
        List<GameRoom> allRooms = gameRoomRepository.findAll();
        for (GameRoom room : allRooms) {
            boolean isHost = room.getHost().getId().equals(userId);
            boolean isGuest = room.getGuest() != null && room.getGuest().getId().equals(userId);
            
            if (!isHost && !isGuest) continue;

            if (room.getStatus() == GameRoom.RoomStatus.PLAYING) {
                processDisconnectWin(room, isHost);
            } else if (room.getStatus() == GameRoom.RoomStatus.WAITING) {
                if (isHost) {
                    room.setStatus(GameRoom.RoomStatus.FINISHED);
                    gameRoomRepository.save(room);
                    log.info("Waiting room {} closed because host {} disconnected", room.getId(), userId);
                }
            } else if (room.getStatus() == GameRoom.RoomStatus.FINISHED) {
                if (isGuest) {
                    room.setGuest(null);
                    gameRoomRepository.save(room);
                    log.info("Guest {} left finished room {}", userId, room.getId());
                } else if (isHost) {
                    log.info("Host {} left finished room {}", userId, room.getId());
                    if (room.getGuest() != null) {
                        Map<String, Object> notification = new HashMap<>();
                        notification.put("status", "FINISHED");
                        notification.put("message", "방장이 나갔습니다. 방이 닫힙니다.");
                        messagingTemplate.convertAndSend("/topic/game/" + room.getId(), notification);
                    }
                }
            }
        }
    }

    private void processDisconnectWin(GameRoom room, boolean isHost) {
        String winner = isHost ? "W" : "B"; // 호스트가 나가면 게스트(백) 승리, 게스트가 나가면 호스트(흑) 승리
        User winnerUser = isHost ? room.getGuest() : room.getHost();
        User loserUser = isHost ? room.getHost() : room.getGuest();
        
        String winnerName = winnerUser != null ? winnerUser.getName() : "상대방";
        String loserName = loserUser != null ? loserUser.getName() : "상대방";
        
        room.setStatus(GameRoom.RoomStatus.FINISHED);
        
        // OthelloGameData 업데이트 (없으면 생성)
        OthelloGameData othelloData = othelloGameDataRepository.findByRoom(room)
                .orElseGet(() -> {
                    String initialBoardState = othelloEngine.getInitialBoardState();
                    OthelloGameData newData = new OthelloGameData(room, initialBoardState, "B");
                    return othelloGameDataRepository.save(newData);
                });
        othelloData.setWinner(winner);
        othelloGameDataRepository.save(othelloData);
        
        // 승패 기록 저장
        saveGameHistory(winnerUser, GameHistory.GameResult.WIN, loserName, GameHistory.GameType.OTHELLO);
        saveGameHistory(loserUser, GameHistory.GameResult.LOSS, winnerName, GameHistory.GameType.OTHELLO);
        
        if (!isHost) {
            room.setGuest(null);
        }
        
        gameRoomRepository.save(room);
        
        // 남은 플레이어에게 알림 전송
        GameStateDto gameState = getGameState(room.getId());
        Map<String, Object> notification = new HashMap<>();
        notification.put("boardState", gameState.getBoardState());
        notification.put("turn", gameState.getTurn());
        notification.put("status", "FINISHED");
        notification.put("isGameOver", true);
        notification.put("winner", winner);
        notification.put("hostName", gameState.getHostName());
        notification.put("guestName", gameState.getGuestName());
        notification.put("message", loserName + "님이 나갔습니다. " + winnerName + "님이 승리했습니다!");
        
        messagingTemplate.convertAndSend("/topic/game/" + room.getId(), notification);
        log.info("User in room {} disconnected. Automatic win for {}", room.getId(), winner);
    }

    private void saveGameHistory(User user, GameHistory.GameResult result, String opponentName, GameHistory.GameType gameType) {
        if (user == null) return;
        
        GameHistory history = new GameHistory();
        history.setUser(user);
        history.setResult(result);
        history.setGameType(gameType);
        history.setOpponentName(opponentName);
        history.setMovesCount(0);
        gameHistoryRepository.save(history);
        log.info("Saved game history for user {}: {}", user.getName(), result);
    }

    public List<RoomDto> getWaitingRooms() {
        return gameRoomRepository.findByStatusOrderByCreatedAtDesc(GameRoom.RoomStatus.WAITING)
                .stream()
                .map(room -> new RoomDto(
                        room.getId(),
                        room.getHost().getName(),
                        room.getStatus().name(),
                        room.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public GameRoom joinRoom(Long roomId, Long guestId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        if (room.getStatus() != GameRoom.RoomStatus.WAITING) {
            throw new IllegalStateException("Room is not available");
        }

        if (room.getHost().getId().equals(guestId)) {
            throw new IllegalStateException("Cannot join your own room");
        }

        User guest = userRepository.findById(guestId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        room.setGuest(guest);
        room.setStatus(GameRoom.RoomStatus.PLAYING);
        room.setStartedAt(LocalDateTime.now());

        GameRoom savedRoom = gameRoomRepository.save(room);
        
        GameStateDto gameState = getGameState(roomId);
        Map<String, Object> notification = new HashMap<>();
        notification.put("boardState", gameState.getBoardState());
        notification.put("turn", gameState.getTurn());
        notification.put("status", gameState.getStatus());
        notification.put("isGameOver", gameState.getIsGameOver());
        notification.put("winner", gameState.getWinner());
        notification.put("hostName", gameState.getHostName());
        notification.put("guestName", gameState.getGuestName());
        notification.put("blackCount", gameState.getBlackCount());
        notification.put("whiteCount", gameState.getWhiteCount());
        notification.put("message", guest.getName() + "님이 게임에 참여했습니다! 게임을 시작합니다.");
        
        messagingTemplate.convertAndSend("/topic/game/" + roomId, notification);
        
        return savedRoom;
    }

    @Transactional
    public GameStateDto makeMove(Long roomId, Integer row, Integer col, String boardState, String turn, Long userId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        if (room.getStatus() != GameRoom.RoomStatus.PLAYING) {
            throw new IllegalStateException("Game is not in progress");
        }

        // OthelloGameData 조회
        OthelloGameData othelloData = othelloGameDataRepository.findByRoom(room)
                .orElseThrow(() -> new IllegalStateException("OthelloGameData not found for room " + roomId));

        // 차례 확인
        String currentTurn = othelloData.getTurn();
        char currentPlayer = currentTurn.charAt(0);
        
        // 유효한 수인지 확인
        if (!othelloEngine.isValidMove(othelloData.getBoardState(), row, col, currentPlayer)) {
            throw new IllegalStateException("Invalid move");
        }
        
        // 호스트는 흑(B), 게스트는 백(W)
        boolean isHostTurn = currentTurn.equals("B") && room.getHost().getId().equals(userId);
        boolean isGuestTurn = currentTurn.equals("W") && room.getGuest() != null && room.getGuest().getId().equals(userId);

        if (!isHostTurn && !isGuestTurn) {
            throw new IllegalStateException("Not your turn");
        }

        // 수 실행
        String newBoardState = othelloEngine.makeMove(othelloData.getBoardState(), row, col, currentPlayer);
        
        // 다음 차례 결정 (상대방이 수를 둘 수 없으면 차례 유지)
        char nextPlayer = (currentPlayer == OthelloGameEngine.BLACK) ? OthelloGameEngine.WHITE : OthelloGameEngine.BLACK;
        List<int[]> nextValidMoves = othelloEngine.getValidMoves(newBoardState, nextPlayer);
        
        String nextTurn = nextValidMoves.isEmpty() ? currentTurn : (nextPlayer == OthelloGameEngine.BLACK ? "B" : "W");
        
        // 게임 종료 확인
        boolean isGameOver = othelloEngine.isGameOver(newBoardState);
        String winner = null;
        if (isGameOver) {
            winner = othelloEngine.getWinner(newBoardState);
            room.setStatus(GameRoom.RoomStatus.FINISHED);
            
            // 게임 기록 저장
            String winnerName = winner.equals("B") ? room.getHost().getName() : 
                               (winner.equals("W") ? room.getGuest().getName() : "무승부");
            String loserName = winner.equals("B") ? room.getGuest().getName() : 
                              (winner.equals("W") ? room.getHost().getName() : "무승부");
            
            if (!winner.equals("draw")) {
                saveGameHistory(winner.equals("B") ? room.getHost() : room.getGuest(), 
                              GameHistory.GameResult.WIN, loserName, GameHistory.GameType.OTHELLO);
                saveGameHistory(winner.equals("B") ? room.getGuest() : room.getHost(), 
                              GameHistory.GameResult.LOSS, winnerName, GameHistory.GameType.OTHELLO);
            } else {
                saveGameHistory(room.getHost(), GameHistory.GameResult.DRAW, loserName, GameHistory.GameType.OTHELLO);
                saveGameHistory(room.getGuest(), GameHistory.GameResult.DRAW, winnerName, GameHistory.GameType.OTHELLO);
            }
            
            othelloData.setWinner(winner);
        }
        
        // 보드 상태와 차례 업데이트
        othelloData.setBoardState(newBoardState);
        othelloData.setTurn(nextTurn);
        othelloGameDataRepository.save(othelloData);
        gameRoomRepository.save(room);

        return getGameState(roomId);
    }

    public GameStateDto getGameState(Long roomId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        boolean isGameOver = room.getStatus() == GameRoom.RoomStatus.FINISHED;

        // OthelloGameData 조회 (없으면 생성)
        OthelloGameData othelloData = othelloGameDataRepository.findByRoom(room)
                .orElseGet(() -> {
                    String initialBoardState = othelloEngine.getInitialBoardState();
                    OthelloGameData newData = new OthelloGameData(room, initialBoardState, "B");
                    return othelloGameDataRepository.save(newData);
                });

        int[] counts = othelloEngine.countPieces(othelloData.getBoardState());
        
        GameStateDto gameState = new GameStateDto(
                othelloData.getBoardState(),
                othelloData.getTurn(),
                room.getStatus().name(),
                isGameOver,
                othelloData.getWinner(),
                room.getHost().getName(),
                room.getGuest() != null ? room.getGuest().getName() : null
        );
        gameState.setBlackCount(counts[0]);
        gameState.setWhiteCount(counts[1]);
        
        return gameState;
    }

    @Transactional
    public void updateGameState(Long roomId, String boardState, String turn, boolean isGameOver, String winner, String status) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        // OthelloGameData 조회 또는 생성
        OthelloGameData othelloData = othelloGameDataRepository.findByRoom(room)
                .orElseGet(() -> {
                    String initialBoardState = othelloEngine.getInitialBoardState();
                    OthelloGameData newData = new OthelloGameData(room, initialBoardState, "B");
                    return othelloGameDataRepository.save(newData);
                });

        othelloData.setBoardState(boardState);
        othelloData.setTurn(turn);

        if (isGameOver) {
            room.setStatus(GameRoom.RoomStatus.FINISHED);
            othelloData.setWinner(winner);
        } else {
            if ("WAITING".equals(status)) {
                room.setStatus(GameRoom.RoomStatus.WAITING);
                othelloData.setWinner(null);
                String initialBoardState = othelloEngine.getInitialBoardState();
                othelloData.setBoardState(initialBoardState);
                othelloData.setTurn("B");
                room.setGuest(null);
                room.setStartedAt(null);
                log.info("Room {} manually set to WAITING status", roomId);
            } else if (room.getStatus() == GameRoom.RoomStatus.FINISHED) {
                if (room.getGuest() == null) {
                    room.setStatus(GameRoom.RoomStatus.WAITING);
                    othelloData.setWinner(null);
                    String initialBoardState = othelloEngine.getInitialBoardState();
                    othelloData.setBoardState(initialBoardState);
                    othelloData.setTurn("B");
                    room.setGuest(null);
                    room.setStartedAt(null);
                    log.info("Room {} reset to WAITING status for new game (no guest)", roomId);
                } else {
                    room.setStatus(GameRoom.RoomStatus.PLAYING);
                    othelloData.setWinner(null);
                    String initialBoardState = othelloEngine.getInitialBoardState();
                    othelloData.setBoardState(initialBoardState);
                    othelloData.setTurn("B");
                    log.info("Room {} reset to PLAYING status for new game (with guest)", roomId);
                }
            }
        }

        othelloGameDataRepository.save(othelloData);
        gameRoomRepository.save(room);
    }

    @Transactional
    public GameStateDto sendNudgeMessage(Long roomId, Long fromUserId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        if (room.getStatus() != GameRoom.RoomStatus.PLAYING) {
            log.warn("Cannot send nudge message: Room {} is not in PLAYING status", roomId);
            return getGameState(roomId);
        }

        if (!userRepository.existsById(fromUserId)) {
            log.warn("User {} not found for nudge message", fromUserId);
            return getGameState(roomId);
        }

        User opponentUser = null;
        String opponentName = null;
        
        if (room.getHost().getId().equals(fromUserId)) {
            opponentUser = room.getGuest();
            opponentName = opponentUser != null ? opponentUser.getName() : null;
        } else if (room.getGuest() != null && room.getGuest().getId().equals(fromUserId)) {
            opponentUser = room.getHost();
            opponentName = opponentUser != null ? opponentUser.getName() : null;
        }

        if (opponentName == null) {
            log.warn("Cannot send nudge message: Opponent not found for room {}", roomId);
            return getGameState(roomId);
        }

        String[] nudgeMessages = {
            opponentName + "님, 빨리 두세요~ 😊",
            opponentName + "님, 기다리고 있어요! 💕",
            opponentName + "님, 생각이 오래 걸리네요! ⏰",
            opponentName + "님, 빨리빨리! 🚀"
        };

        String selectedMessage = nudgeMessages[(int) (Math.random() * nudgeMessages.length)];

        GameStateDto gameState = getGameState(roomId);
        
        GameStateDto nudgeState = new GameStateDto(
            gameState.getBoardState(),
            gameState.getTurn(),
            gameState.getStatus(),
            gameState.getIsGameOver(),
            gameState.getWinner(),
            gameState.getHostName(),
            gameState.getGuestName(),
            selectedMessage
        );
        nudgeState.setBlackCount(gameState.getBlackCount());
        nudgeState.setWhiteCount(gameState.getWhiteCount());

        log.info("Nudge message created for room {}: {}", roomId, selectedMessage);
        
        return nudgeState;
    }

    @Transactional
    public GameStateDto sendVoiceMessage(Long roomId, Long fromUserId, String message) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        if (room.getStatus() != GameRoom.RoomStatus.PLAYING) {
            log.warn("Cannot send voice message: Room {} is not in PLAYING status", roomId);
            return getGameState(roomId);
        }

        if (!userRepository.existsById(fromUserId)) {
            log.warn("User {} not found for voice message", fromUserId);
            return getGameState(roomId);
        }

        GameStateDto gameState = getGameState(roomId);
        
        GameStateDto voiceState = new GameStateDto(
            gameState.getBoardState(),
            gameState.getTurn(),
            gameState.getStatus(),
            gameState.getIsGameOver(),
            gameState.getWinner(),
            gameState.getHostName(),
            gameState.getGuestName(),
            message
        );
        voiceState.setBlackCount(gameState.getBlackCount());
        voiceState.setWhiteCount(gameState.getWhiteCount());
        
        log.info("Voice message created for room {}: {}", roomId, message);
        return voiceState;
    }
}

