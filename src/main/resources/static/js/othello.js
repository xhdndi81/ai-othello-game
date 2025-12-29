// 오셀로 게임 엔진 (JavaScript)

const OTHELLO = {
    BOARD_SIZE: 8,
    EMPTY: ' ',
    BLACK: 'B',
    WHITE: 'W',
    
    // 8방향: 상, 하, 좌, 우, 좌상, 좌하, 우상, 우하
    DX: [-1, 1, 0, 0, -1, 1, -1, 1],
    DY: [0, 0, -1, 1, -1, -1, 1, 1],
    
    /**
     * 초기 보드 상태 생성
     */
    getInitialBoardState: function() {
        const board = new Array(64).fill(' ');
        // 중앙 4칸: (3,3), (3,4), (4,3), (4,4)
        board[3 * 8 + 3] = 'W';
        board[3 * 8 + 4] = 'B';
        board[4 * 8 + 3] = 'B';
        board[4 * 8 + 4] = 'W';
        return board.join('');
    },
    
    /**
     * 유효한 수인지 확인
     */
    isValidMove: function(boardState, row, col, player) {
        if (row < 0 || row >= 8 || col < 0 || col >= 8) {
            return false;
        }
        
        const board = boardState.split('');
        const index = row * 8 + col;
        
        if (board[index] !== ' ') {
            return false;
        }
        
        // 8방향 중 하나라도 뒤집을 수 있는 돌이 있으면 유효한 수
        for (let dir = 0; dir < 8; dir++) {
            if (this.canFlipInDirection(board, row, col, player, dir)) {
                return true;
            }
        }
        
        return false;
    },
    
    /**
     * 특정 방향으로 뒤집을 수 있는지 확인
     */
    canFlipInDirection: function(board, row, col, player, direction) {
        const opponent = (player === 'B') ? 'W' : 'B';
        const dx = this.DX[direction];
        const dy = this.DY[direction];
        
        let x = row + dx;
        let y = col + dy;
        let foundOpponent = false;
        
        while (x >= 0 && x < 8 && y >= 0 && y < 8) {
            const index = x * 8 + y;
            const cell = board[index];
            
            if (cell === opponent) {
                foundOpponent = true;
            } else if (cell === player && foundOpponent) {
                return true;
            } else {
                return false;
            }
            
            x += dx;
            y += dy;
        }
        
        return false;
    },
    
    /**
     * 수를 두고 보드 상태 업데이트
     */
    makeMove: function(boardState, row, col, player) {
        if (!this.isValidMove(boardState, row, col, player)) {
            throw new Error('Invalid move');
        }
        
        const board = boardState.split('');
        const index = row * 8 + col;
        board[index] = player;
        
        // 8방향으로 뒤집기
        for (let dir = 0; dir < 8; dir++) {
            this.flipInDirection(board, row, col, player, dir);
        }
        
        return board.join('');
    },
    
    /**
     * 특정 방향으로 돌 뒤집기
     */
    flipInDirection: function(board, row, col, player, direction) {
        if (!this.canFlipInDirection(board, row, col, player, direction)) {
            return;
        }
        
        const opponent = (player === 'B') ? 'W' : 'B';
        const dx = this.DX[direction];
        const dy = this.DY[direction];
        
        let x = row + dx;
        let y = col + dy;
        
        while (x >= 0 && x < 8 && y >= 0 && y < 8) {
            const index = x * 8 + y;
            if (board[index] === opponent) {
                board[index] = player;
            } else if (board[index] === player) {
                break;
            }
            x += dx;
            y += dy;
        }
    },
    
    /**
     * 유효한 수 목록 반환
     */
    getValidMoves: function(boardState, player) {
        const validMoves = [];
        for (let row = 0; row < 8; row++) {
            for (let col = 0; col < 8; col++) {
                if (this.isValidMove(boardState, row, col, player)) {
                    validMoves.push([row, col]);
                }
            }
        }
        return validMoves;
    },
    
    /**
     * 게임 종료 여부 확인
     */
    isGameOver: function(boardState) {
        const blackMoves = this.getValidMoves(boardState, 'B');
        const whiteMoves = this.getValidMoves(boardState, 'W');
        return blackMoves.length === 0 && whiteMoves.length === 0;
    },
    
    /**
     * 돌 개수 계산
     */
    countPieces: function(boardState) {
        let blackCount = 0;
        let whiteCount = 0;
        const board = boardState.split('');
        
        for (let i = 0; i < board.length; i++) {
            if (board[i] === 'B') {
                blackCount++;
            } else if (board[i] === 'W') {
                whiteCount++;
            }
        }
        
        return { black: blackCount, white: whiteCount };
    },
    
    /**
     * 승자 판정
     */
    getWinner: function(boardState) {
        const counts = this.countPieces(boardState);
        if (counts.black > counts.white) {
            return 'B';
        } else if (counts.white > counts.black) {
            return 'W';
        } else {
            return 'draw';
        }
    }
};

