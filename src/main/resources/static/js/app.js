// 오셀로 게임 공통 로직
let boardState = null;
let currentTurn = 'B';
let userId = null;
let userName = null;
let movesCount = 0;
let nudgeTimer = null;
let gameMode = 'single';
let roomId = null;
let stompClient = null;
let myColor = 'B';
let isHost = false;
let opponentName = 'AI';
let currentDifficulty = 4;
let validMoves = [];

// 음성 출력 관리 변수
let lastSpokenText = "";
let lastSpokenTime = 0;

// 전체 화면 전환 함수
function requestFullscreen() {
    const elem = document.documentElement;
    if (elem.requestFullscreen) {
        elem.requestFullscreen().catch(err => {
            console.log('Fullscreen request failed:', err);
        });
    } else if (elem.webkitRequestFullscreen) { // Safari
        elem.webkitRequestFullscreen();
    } else if (elem.msRequestFullscreen) { // IE/Edge
        elem.msRequestFullscreen();
    }
}

// 전체 화면 해제 함수
function exitFullscreen() {
    if (document.exitFullscreen) {
        document.exitFullscreen().catch(err => {
            console.log('Exit fullscreen failed:', err);
        });
    } else if (document.webkitExitFullscreen) { // Safari
        document.webkitExitFullscreen();
    } else if (document.msExitFullscreen) { // IE/Edge
        document.msExitFullscreen();
    }
}

// 음성 출력 함수
function speak(text) {
    if (typeof speechSynthesis === 'undefined' || !text) return;
    
    const now = Date.now();
    if (text === lastSpokenText && (now - lastSpokenTime) < 1000) return;
    
    lastSpokenText = text;
    lastSpokenTime = now;

    speechSynthesis.cancel();
    
    setTimeout(() => {
        const utterance = new SpeechSynthesisUtterance(text);
        const voices = speechSynthesis.getVoices();
        
        const preferredVoice = voices.find(v => v.lang === 'ko-KR' && (v.name.includes('Google') || v.name.includes('Natural'))) ||
                               voices.find(v => v.lang === 'ko-KR' && v.name.includes('Heami')) ||
                               voices.find(v => v.lang === 'ko-KR');

        if (preferredVoice) utterance.voice = preferredVoice;
        utterance.lang = 'ko-KR';
        utterance.rate = 0.95;
        utterance.pitch = 1.1;
        speechSynthesis.speak(utterance);
    }, 50);
}

// 오셀로 보드 렌더링
function renderBoard(boardStateStr) {
    const board = $('#othello-board');
    board.empty();
    
    const boardArray = boardStateStr.split('');
    validMoves = OTHELLO.getValidMoves(boardStateStr, currentTurn);
    
    for (let row = 0; row < 8; row++) {
        for (let col = 0; col < 8; col++) {
            const index = row * 8 + col;
            const cell = $('<div>').addClass('othello-cell');
            cell.attr('data-row', row).attr('data-col', col);
            
            // 유효한 수 표시
            const isValidMove = validMoves.some(m => m[0] === row && m[1] === col);
            if (isValidMove && boardArray[index] === ' ') {
                cell.addClass('valid-move');
            }
            
            // 돌 표시
            if (boardArray[index] === 'B') {
                cell.append($('<div>').addClass('othello-piece black'));
            } else if (boardArray[index] === 'W') {
                cell.append($('<div>').addClass('othello-piece white'));
            }
            
            // 클릭 이벤트
            if (isValidMove && boardArray[index] === ' ') {
                cell.on('click', () => handleCellClick(row, col));
            }
            
            board.append(cell);
        }
    }
}

// 셀 클릭 처리
function handleCellClick(row, col) {
    if (!boardState) return;
    
    const player = currentTurn;
    
    // 차례 확인
    if (gameMode === 'multi') {
        if (currentTurn !== myColor) {
            alert('당신의 차례가 아닙니다!');
            return;
        }
    } else {
        if (currentTurn !== 'B') {
            alert('당신의 차례가 아닙니다!');
            return;
        }
    }
    
    // 유효한 수인지 확인
    if (!OTHELLO.isValidMove(boardState, row, col, player)) {
        alert('유효하지 않은 수입니다!');
        return;
    }
    
    // 수 실행
    boardState = OTHELLO.makeMove(boardState, row, col, player);
    movesCount++;
    
    // 다음 차례 결정
    const nextPlayer = (player === 'B') ? 'W' : 'B';
    const nextValidMoves = OTHELLO.getValidMoves(boardState, nextPlayer);
    
    if (nextValidMoves.length === 0) {
        // 상대방이 수를 둘 수 없으면 다시 내 차례
        currentTurn = player;
    } else {
        currentTurn = nextPlayer;
    }
    
    if (gameMode === 'multi') {
        sendMoveToServer(row, col);
    } else {
        stopNudgeTimer();
        updateStatus();
        renderBoard(boardState);
        
        if (!checkGameOver()) {
            setTimeout(() => makeAIMove(), 500);
        }
    }
}

// 상태 업데이트
function updateStatus() {
    if (!boardState) return;
    
    const turnText = currentTurn === 'B' ? '흑색' : '백색';
    const counts = OTHELLO.countPieces(boardState);
    
    $('#game-status').text(turnText + ' 차례');
    $('#piece-count').text(`⚫ ${counts.black} : ${counts.white} ⚪`);
    
    if (gameMode === 'multi') {
        if (currentTurn === myColor) {
            // 음성 인식 중이 아닐 때만 메시지 업데이트
            if (!window.isRecording) {
                $('#ai-message').text('당신의 차례입니다. 멋진 수를 보여주세요! 😊');
            }
            $('#btn-nudge').hide();
            $('#btn-voice-message').hide();
        } else {
            // 음성 인식 중이 아니고, 메시지가 음성 관련이 아닐 때만 업데이트
            const currentMessage = $('#ai-message').text();
            const isVoiceRelatedMessage = currentMessage.includes('메시지를 전송했습니다') || 
                                         currentMessage.includes('🎤') ||
                                         currentMessage.includes('음성이 감지되지 않았습니다');
            
            if (!window.isRecording && !isVoiceRelatedMessage) {
                $('#ai-message').text('상대방이 생각 중입니다... ⏳');
            }
            $('#btn-nudge').show();
            // 음성 사용 허용 체크박스 확인
            const VOICE_PERMISSION_KEY = 'othello_voicePermissionAllowed';
            const voicePermissionAllowed = localStorage.getItem(VOICE_PERMISSION_KEY) === 'true';
            if (typeof isSpeechRecognitionSupported === 'function' && isSpeechRecognitionSupported() && voicePermissionAllowed) {
                $('#btn-voice-message').show();
            } else {
                $('#btn-voice-message').hide();
            }
        }
    } else {
        if (currentTurn === 'B') {
            $('#ai-message').text('어디로 두면 좋을까? 천천히 생각해보렴!');
        }
        $('#btn-nudge').hide();
        $('#btn-voice-message').hide();
    }
    
    if (OTHELLO.isGameOver(boardState)) {
        $('#btn-nudge').hide();
        $('#btn-voice-message').hide();
    }
}

// 게임 종료 확인
function checkGameOver() {
    if (!OTHELLO.isGameOver(boardState)) return false;
    
    const winner = OTHELLO.getWinner(boardState);
    const counts = OTHELLO.countPieces(boardState);
    
    let message = '';
    let result = 'DRAW';
    
    if (winner === 'draw') {
        message = '게임 종료! 무승부입니다.';
    } else {
        const winnerText = winner === 'B' ? '흑색' : '백색';
        if (gameMode === 'multi') {
            if (winner === myColor) {
                message = `게임 종료! ${winnerText} 승리! 🎉`;
                result = 'WIN';
            } else {
                message = `게임 종료! ${winnerText} 승리! 패배했습니다.`;
                result = 'LOSS';
            }
        } else {
            if (winner === 'B') {
                message = '게임 종료! 승리했습니다! 🎉';
                result = 'WIN';
            } else {
                message = '게임 종료! 패배했습니다.';
                result = 'LOSS';
            }
        }
    }
    
    $('#ai-message').text(message);
    speak(message);
    
    if (userId) {
        const currentOpponentName = (gameMode === 'multi' && opponentName && opponentName !== 'AI') ? opponentName : 'AI';
        
        $.ajax({
            url: '/api/history/' + userId,
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({ 
                result: result, 
                movesCount: movesCount, 
                opponentName: currentOpponentName, 
                gameType: 'OTHELLO' 
            }),
            success: function() {
                if (result === 'WIN' || result === 'DRAW') {
                    $('#btn-new-game').show();
                }
            },
            error: function(xhr, status, error) {
                console.error('Failed to save game history:', error);
            }
        });
    }
    
    return true;
}

// 보드 초기화
function initBoard() {
    boardState = OTHELLO.getInitialBoardState();
    currentTurn = 'B';
    movesCount = 0;
    renderBoard(boardState);
    updateStatus();
    $('#btn-new-game').hide();
    $('#btn-nudge').hide();
    $('#btn-voice-message').hide();
}

$(document).ready(function() {
    // 대기방 목록 HTML 로드 및 음성 메시지 체크박스 처리
    $('#waiting-rooms-placeholder').load('/waiting-rooms.html', function() {
        // 음성 사용 허용 체크박스 상태 로드 및 저장
        const VOICE_PERMISSION_KEY = 'othello_voicePermissionAllowed';
        const voicePermissionCheckbox = $('#voice-permission-checkbox');
        
        // localStorage에서 체크박스 상태 로드
        const savedVoicePermission = localStorage.getItem(VOICE_PERMISSION_KEY);
        if (savedVoicePermission === 'true') {
            voicePermissionCheckbox.prop('checked', true);
        }
        
        // 체크박스 변경 시 localStorage에 저장
        voicePermissionCheckbox.on('change', function() {
            const isChecked = $(this).is(':checked');
            localStorage.setItem(VOICE_PERMISSION_KEY, isChecked ? 'true' : 'false');
            
            // 체크된 경우 마이크 권한 요청 (이미 게임 중이면 Speech Recognition 초기화)
            if (isChecked && gameMode === 'multi' && typeof initSpeechRecognition === 'function') {
                initSpeechRecognition();
                // 게임 중이고 상대방 차례면 말하기 버튼 표시
                if (currentTurn !== myColor) {
                    $('#btn-voice-message').show();
                }
            } else if (!isChecked) {
                // 체크 해제된 경우 말하기 버튼 숨김
                $('#btn-voice-message').hide();
            }
        });
    });
    
    $('#btn-new-game').hide();
    
    const savedName = localStorage.getItem('othello_username');
    if (savedName) $('#username').val(savedName);
    
    const savedDiff = localStorage.getItem('othello_difficulty');
    if (savedDiff !== null) {
        $('#difficulty').val(savedDiff);
        currentDifficulty = parseInt(savedDiff);
    }
    
    // 모드 버튼 이벤트
    $('.mode-btn').on('click', function(e) {
        e.preventDefault();
        $('.mode-btn').css('background', '#fff');
        $(this).css('background', '#ffeb99');
        
        if ($(this).attr('id') === 'btn-single-mode') {
            gameMode = 'single';
            $('#single-mode-options').show();
            $('#btn-start').show();
        } else {
            gameMode = 'multi';
            $('#single-mode-options').hide();
            $('#btn-start').hide();
            
            const name = $('#username').val();
            if (!name) {
                alert('이름을 입력해주세요!');
                $('#btn-single-mode').trigger('click');
                return;
            }
            
            $.ajax({
                url: '/api/login',
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify({ name: name }),
                success: function(user) {
                    userId = user.id;
                    userName = user.name;
                    localStorage.setItem('othello_username', name);
                    
                    $('#login-container').hide();
                    $('#waiting-rooms-container').show();
                    loadWaitingRooms();
                    
                    if (window.roomRefreshInterval) clearInterval(window.roomRefreshInterval);
                    window.roomRefreshInterval = setInterval(loadWaitingRooms, 5000);
                },
                error: function() {
                    alert('로그인에 실패했습니다.');
                    $('#btn-single-mode').trigger('click');
                }
            });
        }
    });
    
    // 초기 상태
    gameMode = 'single';
    $('#single-mode-options').show();
    $('#btn-start').show();
    $('#btn-single-mode').css('background', '#ffeb99');
    
    $('#btn-start').on('click', function() {
        const name = $('#username').val();
        if (!name) { alert('이름을 입력해주세요!'); return; }
        
        currentDifficulty = parseInt($('#difficulty').val());
        localStorage.setItem('othello_username', name);
        localStorage.setItem('othello_difficulty', currentDifficulty);
        
        $.ajax({
            url: '/api/login',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({ name: name }),
            success: function(user) {
                userId = user.id;
                userName = user.name;
                $('#login-container').hide();
                $('#game-container').show();
                // 전체 화면으로 전환
                requestFullscreen();
                initBoard();
                
                const welcome = `안녕, ${userName}야! 나는 너의 오셀로 친구야. 우리 재미있게 놀아보자!`;
                $('#ai-message').text(welcome);
                speak(welcome);
                
                startNudgeTimer();
            }
        });
    });
    
    $(document).on('click', '#btn-back-to-login', function() {
        if (window.roomRefreshInterval) {
            clearInterval(window.roomRefreshInterval);
            window.roomRefreshInterval = null;
        }
        $('#waiting-rooms-container').hide();
        $('#login-container').show();
        // 전체 화면 해제
        exitFullscreen();
    });
    
    // 나가기 버튼 클릭 이벤트
    $(document).on('click', '#btn-logout', function() {
        // 게임 종료 처리
        if (gameMode === 'multi' && typeof stompClient !== 'undefined' && stompClient && stompClient.connected) {
            stompClient.disconnect();
        }
        
        // 전체 화면 해제
        exitFullscreen();
        
        // 페이지 새로고침 (게임 상태 초기화)
        location.reload();
    });
    
    $(document).on('click', '#btn-refresh-rooms', function() {
        loadWaitingRooms();
    });
    
    $(document).on('click', '#btn-create-new-room', function() {
        if (!userId) { alert('먼저 이름을 입력하고 같이하기를 선택해주세요.'); return; }
        createRoom();
    });
    
    // 나가기 버튼 핸들러는 위에서 이미 정의됨 (중복 방지)
    
    $('#btn-history').on('click', () => {
        if (!userId) return;
        $.ajax({
            url: '/api/history/' + userId,
            method: 'GET',
            success: function(history) {
                const tbody = $('#history-table tbody').empty();
                history.forEach(h => {
                    const res = h.result === 'WIN' ? '승리 🏆' : h.result === 'LOSS' ? '패배' : '무승부';
                    const opponent = h.opponentName || 'AI';
                    
                    // 날짜 파싱 개선
                    let dateStr = '';
                    if (h.playedAt) {
                        try {
                            // LocalDateTime 배열 형식 [년, 월, 일, 시, 분, 초] 처리
                            if (Array.isArray(h.playedAt)) {
                                const [year, month, day, hour, minute, second] = h.playedAt;
                                const date = new Date(year, month - 1, day, hour || 0, minute || 0, second || 0);
                                dateStr = date.toLocaleDateString('ko-KR');
                            } else if (typeof h.playedAt === 'string') {
                                // ISO 8601 형식 문자열 처리
                                const date = new Date(h.playedAt);
                                if (!isNaN(date.getTime())) {
                                    dateStr = date.toLocaleDateString('ko-KR');
                                } else {
                                    dateStr = h.playedAt;
                                }
                            } else {
                                // 객체 형식 처리
                                const date = new Date(h.playedAt);
                                if (!isNaN(date.getTime())) {
                                    dateStr = date.toLocaleDateString('ko-KR');
                                } else {
                                    dateStr = '날짜 없음';
                                }
                            }
                        } catch (e) {
                            console.error('Date parsing error:', e, h.playedAt);
                            dateStr = '날짜 없음';
                        }
                    } else {
                        dateStr = '날짜 없음';
                    }
                    
                    tbody.append(`<tr><td>${dateStr}</td><td>${res}</td><td>${opponent}</td><td>${h.movesCount}</td></tr>`);
                });
                $('#history-modal').show();
            }
        });
    });
    
    $('#btn-new-game').on('click', () => {
        initBoard();
        if (gameMode === 'multi' && stompClient && stompClient.connected && roomId) {
            const headers = { userId: userId.toString() };
            const initialBoardState = OTHELLO.getInitialBoardState();
            
            const isRematch = opponentName && opponentName !== '상대방' && opponentName !== 'AI';
            const nextStatus = isRematch ? 'PLAYING' : 'WAITING';
            
            stompClient.send('/app/game/' + roomId + '/state', headers, JSON.stringify({
                boardState: initialBoardState,
                turn: 'B',
                status: nextStatus,
                isGameOver: false,
                winner: null
            }));
        }
        speak('새 게임을 시작합니다!');
    });
    
    $('#btn-nudge').on('click', function() {
        if (gameMode === 'multi' && typeof sendNudgeToServer === 'function') {
            sendNudgeToServer();
        }
    });
    
    // 말하기 버튼 이벤트 핸들러 (이벤트 위임 사용)
    $(document).on('mousedown touchstart', '#btn-voice-message', function(e) {
        e.preventDefault();
        e.stopPropagation();
        if (gameMode === 'multi') {
            // recognition 변수가 정의되어 있는지 확인 (multiplayer.js에서 정의됨)
            if (typeof window.recognition !== 'undefined' && window.recognition && !window.isRecording) {
                try {
                    console.log('Starting speech recognition...');
                    window.recognition.start();
                } catch (err) {
                    console.error('Failed to start recognition:', err);
                    // 이미 실행 중이면 무시
                    if (err.message && !err.message.includes('already started')) {
                        $('#ai-message').text('음성 인식을 시작할 수 없습니다. 다시 시도해주세요.');
                    }
                }
            } else {
                console.warn('Speech recognition not initialized');
                $('#ai-message').text('음성 인식이 초기화되지 않았습니다. 페이지를 새로고침해주세요.');
            }
        }
    });
    
    $(document).on('mouseup touchend mouseleave', '#btn-voice-message', function(e) {
        e.preventDefault();
        e.stopPropagation();
        if (typeof window.recognition !== 'undefined' && window.recognition && window.isRecording) {
            try {
                console.log('Stopping speech recognition...');
                window.recognition.stop();
            } catch (err) {
                console.error('Failed to stop recognition:', err);
            }
        }
    });
    
    $('.close').on('click', () => $('#history-modal').hide());
});

// 재촉 타이머
function startNudgeTimer() {
    stopNudgeTimer();
    nudgeTimer = setTimeout(() => {
        if (currentTurn === 'B' && !OTHELLO.isGameOver(boardState)) {
            const nudges = [
                "어디로 둘지 결정했니? 😊",
                `${userName}야, 천천히 생각해도 돼!`,
                "선생님은 기다리고 있어!",
                `${userName}야, 어떤 전략을 세우고 있니?`,
                "선생님은 준비 다 됐어! 천천히 해봐~"
            ];
            const ment = nudges[Math.floor(Math.random() * nudges.length)];
            $('#ai-message').text(ment);
            speak(ment);
            startNudgeTimer();
        }
    }, 30000);
}

function stopNudgeTimer() {
    if (nudgeTimer) clearTimeout(nudgeTimer);
}

