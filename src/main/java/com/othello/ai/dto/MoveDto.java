package com.othello.ai.dto;

public class MoveDto {
    private Long roomId;
    private Integer row; // 0-7
    private Integer col; // 0-7
    private String boardState; // 이동 후의 보드 상태
    private String turn; // 다음 차례 ('B' 또는 'W')

    public MoveDto() {}

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Integer getRow() { return row; }
    public void setRow(Integer row) { this.row = row; }
    public Integer getCol() { return col; }
    public void setCol(Integer col) { this.col = col; }
    public String getBoardState() { return boardState; }
    public void setBoardState(String boardState) { this.boardState = boardState; }
    public String getTurn() { return turn; }
    public void setTurn(String turn) { this.turn = turn; }
}

