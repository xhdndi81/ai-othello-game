package com.othello.ai.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class OthelloGameEngine {

    public static final int BOARD_SIZE = 8;
    public static final char EMPTY = ' ';
    public static final char BLACK = 'B';
    public static final char WHITE = 'W';
    
    // 8방향: 상, 하, 좌, 우, 좌상, 좌하, 우상, 우하
    private static final int[] DX = {-1, 1, 0, 0, -1, 1, -1, 1};
    private static final int[] DY = {0, 0, -1, 1, -1, -1, 1, 1};

    /**
     * 초기 보드 상태 생성 (중앙 4칸에 흑/백 배치)
     */
    public String getInitialBoardState() {
        char[] board = new char[BOARD_SIZE * BOARD_SIZE];
        for (int i = 0; i < board.length; i++) {
            board[i] = EMPTY;
        }
        // 중앙 4칸: (3,3), (3,4), (4,3), (4,4)
        board[3 * BOARD_SIZE + 3] = WHITE;
        board[3 * BOARD_SIZE + 4] = BLACK;
        board[4 * BOARD_SIZE + 3] = BLACK;
        board[4 * BOARD_SIZE + 4] = WHITE;
        return new String(board);
    }

    /**
     * 유효한 수인지 확인
     */
    public boolean isValidMove(String boardState, int row, int col, char player) {
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) {
            return false;
        }
        
        char[] board = boardState.toCharArray();
        int index = row * BOARD_SIZE + col;
        
        // 이미 돌이 있는 칸은 불가능
        if (board[index] != EMPTY) {
            return false;
        }
        
        // 8방향 중 하나라도 뒤집을 수 있는 돌이 있으면 유효한 수
        for (int dir = 0; dir < 8; dir++) {
            if (canFlipInDirection(board, row, col, player, dir)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 특정 방향으로 뒤집을 수 있는지 확인
     */
    private boolean canFlipInDirection(char[] board, int row, int col, char player, int direction) {
        char opponent = (player == BLACK) ? WHITE : BLACK;
        int dx = DX[direction];
        int dy = DY[direction];
        
        int x = row + dx;
        int y = col + dy;
        boolean foundOpponent = false;
        
        // 인접한 칸부터 확인
        while (x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE) {
            int index = x * BOARD_SIZE + y;
            char cell = board[index];
            
            if (cell == opponent) {
                foundOpponent = true;
            } else if (cell == player && foundOpponent) {
                return true; // 상대 돌을 만나고 그 다음 내 돌을 만나면 뒤집기 가능
            } else {
                return false; // 빈 칸이거나 내 돌이 바로 있으면 불가능
            }
            
            x += dx;
            y += dy;
        }
        
        return false;
    }

    /**
     * 수를 두고 보드 상태 업데이트
     */
    public String makeMove(String boardState, int row, int col, char player) {
        if (!isValidMove(boardState, row, col, player)) {
            throw new IllegalArgumentException("Invalid move");
        }
        
        char[] board = boardState.toCharArray();
        int index = row * BOARD_SIZE + col;
        board[index] = player;
        
        // 8방향으로 뒤집기
        for (int dir = 0; dir < 8; dir++) {
            flipInDirection(board, row, col, player, dir);
        }
        
        return new String(board);
    }

    /**
     * 특정 방향으로 돌 뒤집기
     */
    private void flipInDirection(char[] board, int row, int col, char player, int direction) {
        if (!canFlipInDirection(board, row, col, player, direction)) {
            return;
        }
        
        char opponent = (player == BLACK) ? WHITE : BLACK;
        int dx = DX[direction];
        int dy = DY[direction];
        
        int x = row + dx;
        int y = col + dy;
        
        while (x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE) {
            int index = x * BOARD_SIZE + y;
            if (board[index] == opponent) {
                board[index] = player;
            } else if (board[index] == player) {
                break;
            }
            x += dx;
            y += dy;
        }
    }

    /**
     * 유효한 수 목록 반환
     */
    public List<int[]> getValidMoves(String boardState, char player) {
        List<int[]> validMoves = new ArrayList<>();
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (isValidMove(boardState, row, col, player)) {
                    validMoves.add(new int[]{row, col});
                }
            }
        }
        return validMoves;
    }

    /**
     * 게임 종료 여부 확인
     */
    public boolean isGameOver(String boardState) {
        List<int[]> blackMoves = getValidMoves(boardState, BLACK);
        List<int[]> whiteMoves = getValidMoves(boardState, WHITE);
        return blackMoves.isEmpty() && whiteMoves.isEmpty();
    }

    /**
     * 돌 개수 계산
     */
    public int[] countPieces(String boardState) {
        int blackCount = 0;
        int whiteCount = 0;
        char[] board = boardState.toCharArray();
        
        for (char cell : board) {
            if (cell == BLACK) {
                blackCount++;
            } else if (cell == WHITE) {
                whiteCount++;
            }
        }
        
        return new int[]{blackCount, whiteCount};
    }

    /**
     * 승자 판정
     */
    public String getWinner(String boardState) {
        int[] counts = countPieces(boardState);
        int blackCount = counts[0];
        int whiteCount = counts[1];
        
        if (blackCount > whiteCount) {
            return "B";
        } else if (whiteCount > blackCount) {
            return "W";
        } else {
            return "draw";
        }
    }

    /**
     * 보드 상태를 2D 배열로 변환 (디버깅용)
     */
    public char[][] boardTo2D(String boardState) {
        char[][] board2D = new char[BOARD_SIZE][BOARD_SIZE];
        char[] board = boardState.toCharArray();
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                board2D[row][col] = board[row * BOARD_SIZE + col];
            }
        }
        return board2D;
    }

    /**
     * 2D 배열을 보드 상태 문자열로 변환
     */
    public String boardFrom2D(char[][] board2D) {
        char[] board = new char[BOARD_SIZE * BOARD_SIZE];
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                board[row * BOARD_SIZE + col] = board2D[row][col];
            }
        }
        return new String(board);
    }
}

