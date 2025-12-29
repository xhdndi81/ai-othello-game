// 싱글 플레이어 모드 (AI 대전)

function makeAIMove() {
    if (!boardState || currentTurn !== 'W') return;
    
    stopNudgeTimer();
    $('#ai-message').text('음... 어디로 두면 좋을까? 🤔');
    
    $.ajax({
        url: '/api/ai/move',
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            boardState: boardState,
            turn: 'W',
            userName: userName,
            difficulty: currentDifficulty
        }),
        success: function(response) {
            if (response.move === 'pass') {
                // 패스
                currentTurn = 'B';
                updateStatus();
                $('#ai-message').text(response.comment);
                speak(response.comment);
                return;
            }
            
            const [row, col] = response.move.split(',').map(Number);
            
            // AI 수 실행
            boardState = OTHELLO.makeMove(boardState, row, col, 'W');
            movesCount++;
            
            // 다음 차례 결정
            const nextValidMoves = OTHELLO.getValidMoves(boardState, 'B');
            if (nextValidMoves.length === 0) {
                currentTurn = 'W';
            } else {
                currentTurn = 'B';
            }
            
            renderBoard(boardState);
            updateStatus();
            
            // AI 코멘트 표시
            if (response.comment) {
                $('#ai-message').text(response.comment);
                speak(response.comment);
            }
            
            checkGameOver();
            if (!OTHELLO.isGameOver(boardState) && currentTurn === 'B') {
                startNudgeTimer();
            }
        },
        error: function() {
            $('#ai-message').text('미안해요, 잠시 생각 중 오류가 발생했어요.');
            // 랜덤 수로 대체
            const validMoves = OTHELLO.getValidMoves(boardState, 'W');
            if (validMoves.length > 0) {
                const randomMove = validMoves[Math.floor(Math.random() * validMoves.length)];
                boardState = OTHELLO.makeMove(boardState, randomMove[0], randomMove[1], 'W');
                movesCount++;
                
                const nextValidMoves = OTHELLO.getValidMoves(boardState, 'B');
                if (nextValidMoves.length === 0) {
                    currentTurn = 'W';
                } else {
                    currentTurn = 'B';
                }
                
                renderBoard(boardState);
                updateStatus();
                checkGameOver();
                if (!OTHELLO.isGameOver(boardState) && currentTurn === 'B') {
                    startNudgeTimer();
                }
            }
        }
    });
}

