package com.othello.ai.dto;

public class AIRequest {
    private String boardState; // 오셀로 보드 상태 (64자 문자열)
    private String turn; // 'B' 또는 'W'
    private String userName;

    public AIRequest() {}

    public String getBoardState() { return boardState; }
    public void setBoardState(String boardState) { this.boardState = boardState; }
    public String getTurn() { return turn; }
    public void setTurn(String turn) { this.turn = turn; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
}

