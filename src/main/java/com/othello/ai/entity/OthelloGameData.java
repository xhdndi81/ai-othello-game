package com.othello.ai.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "othello_game_data")
public class OthelloGameData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false, unique = true)
    private GameRoom room;

    @Column(length = 64)
    private String boardState; // 현재 게임 상태 (64자 문자열: ' ', 'B', 'W')

    @Column(length = 10)
    private String turn; // 'B' 또는 'W'

    @Column(length = 10)
    private String winner; // 'B', 'W', 'draw' 또는 null

    public OthelloGameData() {}

    public OthelloGameData(GameRoom room, String boardState, String turn) {
        this.room = room;
        this.boardState = boardState;
        this.turn = turn;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public GameRoom getRoom() { return room; }
    public void setRoom(GameRoom room) { this.room = room; }
    public String getBoardState() { return boardState; }
    public void setBoardState(String boardState) { this.boardState = boardState; }
    public String getTurn() { return turn; }
    public void setTurn(String turn) { this.turn = turn; }
    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }
}

