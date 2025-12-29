package com.othello.ai.repository;

import com.othello.ai.entity.GameRoom;
import com.othello.ai.entity.OthelloGameData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OthelloGameDataRepository extends JpaRepository<OthelloGameData, Long> {
    Optional<OthelloGameData> findByRoom(GameRoom room);
    Optional<OthelloGameData> findByRoomId(Long roomId);
}

