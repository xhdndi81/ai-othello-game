package com.othello.ai.repository;

import com.othello.ai.entity.GameHistory;
import com.othello.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GameHistoryRepository extends JpaRepository<GameHistory, Long> {
    List<GameHistory> findByUserOrderByPlayedAtDesc(User user);
}

