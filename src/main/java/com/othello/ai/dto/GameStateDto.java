package com.othello.ai.dto;

public class GameStateDto {
    private String boardState; // 64자 문자열
    private String turn; // 'B' 또는 'W'
    private String status; // WAITING, PLAYING, FINISHED
    private Boolean isGameOver;
    private String winner; // 'B', 'W', 'draw', 또는 null
    private String hostName;
    private String guestName;
    private String message; // 선택적 메시지 전달용
    private Integer blackCount; // 흑 돌 개수
    private Integer whiteCount; // 백 돌 개수

    public GameStateDto() {}

    public GameStateDto(String boardState, String turn, String status, Boolean isGameOver, String winner, String hostName, String guestName) {
        this.boardState = boardState;
        this.turn = turn;
        this.status = status;
        this.isGameOver = isGameOver;
        this.winner = winner;
        this.hostName = hostName;
        this.guestName = guestName;
        this.message = null;
    }

    public GameStateDto(String boardState, String turn, String status, Boolean isGameOver, String winner, String hostName, String guestName, String message) {
        this.boardState = boardState;
        this.turn = turn;
        this.status = status;
        this.isGameOver = isGameOver;
        this.winner = winner;
        this.hostName = hostName;
        this.guestName = guestName;
        this.message = message;
    }

    public String getBoardState() { return boardState; }
    public void setBoardState(String boardState) { this.boardState = boardState; }
    public String getTurn() { return turn; }
    public void setTurn(String turn) { this.turn = turn; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getIsGameOver() { return isGameOver; }
    public void setIsGameOver(Boolean isGameOver) { this.isGameOver = isGameOver; }
    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }
    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }
    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getBlackCount() { return blackCount; }
    public void setBlackCount(Integer blackCount) { this.blackCount = blackCount; }
    public Integer getWhiteCount() { return whiteCount; }
    public void setWhiteCount(Integer whiteCount) { this.whiteCount = whiteCount; }
}

