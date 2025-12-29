package com.othello.ai.dto;

public class AIResponse {
    private String move; // "row,col" 형식 (예: "3,4")
    private String comment; // 친절한 코멘트

    public AIResponse() {}

    public AIResponse(String move, String comment) {
        this.move = move;
        this.comment = comment;
    }

    public String getMove() { return move; }
    public void setMove(String move) { this.move = move; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

