// 멀티플레이어 모드 (WebSocket)

// 음성 인식 관련 변수 (전역으로 선언하여 app.js에서도 접근 가능하도록)
window.recognition = null;
window.isRecording = false;
window.finalTranscript = '';

// WebSocket 연결
function connectWebSocket(roomIdParam) {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    
    const headers = {
        userId: userId.toString()
    };
    
    stompClient.connect(headers, function(frame) {
        console.log('WebSocket Connected: ' + frame);
        
        stompClient.subscribe('/topic/game/' + roomIdParam, function(message) {
            console.log('Received message:', message.body);
            const gameState = JSON.parse(message.body);
            handleGameStateUpdate(gameState);
        });
    }, function(error) {
        console.error('WebSocket connection error:', error);
    });
}

// 서버로 수 전송
function sendMoveToServer(row, col) {
    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket not connected');
        alert('서버와 연결이 끊어졌습니다.');
        return;
    }
    
    const headers = {
        userId: userId.toString()
    };
    
    stompClient.send('/app/game/' + roomId + '/move', headers, JSON.stringify({
        roomId: roomId,
        row: row,
        col: col,
        boardState: boardState,
        turn: currentTurn
    }));
}

// 게임 상태 업데이트 처리
function handleGameStateUpdate(gameState) {
    if (!gameState) return;
    
    console.log('handleGameStateUpdate received:', gameState);
    
    if (gameState.boardState) {
        boardState = gameState.boardState;
    }
    if (gameState.turn) {
        currentTurn = gameState.turn;
    }
    if (gameState.blackCount !== undefined) {
        $('#piece-count').text(`⚫ ${gameState.blackCount} : ${gameState.whiteCount} ⚪`);
    }
    
    // 메시지 처리
    let hasMessage = false;
    if (gameState.message) {
        console.log('Game Message:', gameState.message);
        hasMessage = true;
        
        // 재촉 메시지인지 확인
        const isNudgeMessage = gameState.message.includes('님,') && 
                               (gameState.message.includes('빨리') || 
                                gameState.message.includes('기다리고') || 
                                gameState.message.includes('생각이') ||
                                gameState.message.includes('빨리빨리'));
        
        // 음성 메시지인지 확인 (상대방이 보낸 메시지)
        const isVoiceMessage = !isNudgeMessage && 
                               !gameState.message.includes('참여') && 
                               !gameState.message.includes('시작') &&
                               !gameState.message.includes('나갔습니다');
        
        // 재촉 메시지나 음성 메시지는 TTS로 재생
        if (isNudgeMessage || isVoiceMessage) {
            const displayMessage = gameState.message;
            speak(gameState.message);
            $('#ai-message').text(displayMessage);
        } else if (gameState.message.includes('승리') || gameState.message.includes('패배') || gameState.message.includes('무승부')) {
            // 게임 종료 메시지
            speak(gameState.message);
            $('#ai-message').text(gameState.message);
        } else if (gameState.message.includes('참여') || gameState.message.includes('시작')) {
            // 게임 시작 메시지
            speak(gameState.message);
            $('#ai-message').text(gameState.message);
        } else {
            // 기타 메시지
            $('#ai-message').text(gameState.message);
        }
    }
    
    renderBoard(boardState);
    
    // 메시지가 없을 때만 상태 업데이트 (메시지가 있으면 덮어쓰지 않음)
    if (!hasMessage) {
        // 음성 인식 중이 아니고, 음성 관련 메시지가 아닐 때만 상태 업데이트
        const currentMessage = $('#ai-message').text();
        const isVoiceRelatedMessage = currentMessage.includes('메시지를 전송했습니다') || 
                                     currentMessage.includes('🎤') ||
                                     currentMessage.includes('음성이 감지되지 않았습니다');
        
        if (!window.isRecording && !isVoiceRelatedMessage) {
            updateStatus();
        } else {
            // 상태 정보만 업데이트 (차례, 점수 등)
            const turnText = currentTurn === 'B' ? '흑색' : '백색';
            $('#game-status').text(turnText + ' 차례');
            
            // 버튼 표시/숨김만 업데이트
            if (gameMode === 'multi') {
                if (currentTurn === myColor) {
                    $('#btn-nudge').hide();
                    $('#btn-voice-message').hide();
                } else {
                    $('#btn-nudge').show();
                    const VOICE_PERMISSION_KEY = 'othello_voicePermissionAllowed';
                    const voicePermissionAllowed = localStorage.getItem(VOICE_PERMISSION_KEY) === 'true';
                    if (typeof isSpeechRecognitionSupported === 'function' && isSpeechRecognitionSupported() && voicePermissionAllowed) {
                        $('#btn-voice-message').show();
                    } else {
                        $('#btn-voice-message').hide();
                    }
                }
            }
        }
    } else {
        // 메시지가 있어도 상태 정보는 업데이트 (차례, 점수 등)
        const turnText = currentTurn === 'B' ? '흑색' : '백색';
        $('#game-status').text(turnText + ' 차례');
        
        // 버튼 표시/숨김만 업데이트
        if (gameMode === 'multi') {
            if (currentTurn === myColor) {
                $('#btn-nudge').hide();
                $('#btn-voice-message').hide();
            } else {
                $('#btn-nudge').show();
                const VOICE_PERMISSION_KEY = 'othello_voicePermissionAllowed';
                const voicePermissionAllowed = localStorage.getItem(VOICE_PERMISSION_KEY) === 'true';
                if (typeof isSpeechRecognitionSupported === 'function' && isSpeechRecognitionSupported() && voicePermissionAllowed) {
                    $('#btn-voice-message').show();
                } else {
                    $('#btn-voice-message').hide();
                }
            }
        }
    }
    
    if (gameState.isGameOver) {
        checkGameOver();
    }
}

// 재촉 메시지 전송 (쿨다운 적용)
let nudgeCooldownTimer = null;
const NUDGE_COOLDOWN_MS = 5000; // 5초 쿨다운

function sendNudgeToServer() {
    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket not connected');
        return;
    }
    
    // 쿨다운 중이면 무시
    if (nudgeCooldownTimer !== null) {
        console.log('Nudge is on cooldown');
        return;
    }
    
    const headers = {
        userId: userId.toString()
    };
    
    // 재촉 메시지 전송
    stompClient.send('/app/game/' + roomId + '/nudge', headers, JSON.stringify({}));
    
    // 쿨다운 시작
    const btnNudge = $('#btn-nudge');
    btnNudge.prop('disabled', true);
    
    let remainingSeconds = NUDGE_COOLDOWN_MS / 1000;
    const originalText = btnNudge.text();
    btnNudge.text(`⚡ ${remainingSeconds}초`);
    
    nudgeCooldownTimer = setInterval(() => {
        remainingSeconds--;
        if (remainingSeconds > 0) {
            btnNudge.text(`⚡ ${remainingSeconds}초`);
        } else {
            clearInterval(nudgeCooldownTimer);
            nudgeCooldownTimer = null;
            btnNudge.prop('disabled', false);
            btnNudge.text(originalText);
        }
    }, 1000);
}

// Web Speech API 지원 여부 확인
function isSpeechRecognitionSupported() {
    return 'webkitSpeechRecognition' in window || 'SpeechRecognition' in window;
}

// 마이크 권한 확인 및 요청
async function checkMicrophonePermission() {
    try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        // 권한이 허용되었으면 스트림 종료
        stream.getTracks().forEach(track => track.stop());
        return true;
    } catch (error) {
        console.log('Microphone permission:', error.name);
        return false;
    }
}

// SpeechRecognition 초기화
function initSpeechRecognition() {
    if (!isSpeechRecognitionSupported()) {
        console.warn('Speech Recognition is not supported in this browser');
        $('#btn-voice-message').hide();
        return;
    }

    // HTTPS 체크 (localhost는 예외)
    const isSecureContext = window.isSecureContext || window.location.protocol === 'https:' || window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
    if (!isSecureContext) {
        console.warn('Speech Recognition requires HTTPS. Current protocol:', window.location.protocol);
        $('#btn-voice-message').hide();
        // 사용자에게 경고 메시지 표시
        if (gameMode === 'multi') {
            $('#ai-message').text('⚠️ 음성 메시지 기능은 HTTPS에서만 사용할 수 있습니다. 서버에 SSL 인증서를 설정해주세요.');
        }
        return;
    }

    // localStorage에서 음성 사용 허용 여부 확인
    const VOICE_PERMISSION_KEY = 'othello_voicePermissionAllowed';
    const voicePermissionAllowed = localStorage.getItem(VOICE_PERMISSION_KEY) === 'true';
    
    if (!voicePermissionAllowed) {
        console.log('Voice permission not allowed by user');
        $('#btn-voice-message').hide();
        return;
    }

    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    window.recognition = new SpeechRecognition();
    
    window.recognition.lang = 'ko-KR';
    window.recognition.continuous = false; // 버튼을 떼면 중지
    window.recognition.interimResults = true; // 중간 결과 표시
    
    window.recognition.onstart = function() {
        window.isRecording = true;
        window.finalTranscript = '';
        $('#btn-voice-message').addClass('recording');
        $('#btn-voice-message').text('🎤 녹음 중...');
        console.log('Speech recognition started');
    };
    
    window.recognition.onresult = function(event) {
        let interimTranscript = '';
        
        for (let i = event.resultIndex; i < event.results.length; i++) {
            const transcript = event.results[i][0].transcript;
            if (event.results[i].isFinal) {
                window.finalTranscript += transcript;
            } else {
                interimTranscript += transcript;
            }
        }
        
        // 중간 결과를 AI 메시지 영역에 표시
        if (interimTranscript) {
            $('#ai-message').text('🎤 ' + interimTranscript);
        }
    };
    
    window.recognition.onerror = function(event) {
        console.error('Speech recognition error:', event.error);
        window.isRecording = false;
        $('#btn-voice-message').removeClass('recording');
        $('#btn-voice-message').text('🎤 말하기');
        
        let errorMsg = '음성 인식 오류가 발생했습니다.';
        if (event.error === 'no-speech') {
            errorMsg = '음성이 감지되지 않았습니다.';
        } else if (event.error === 'not-allowed') {
            errorMsg = '마이크 권한이 필요합니다. 브라우저 설정에서 권한을 허용해주세요.';
            $('#ai-message').text(errorMsg);
        } else if (event.error === 'aborted') {
            // 사용자가 중지한 경우는 에러 메시지 표시하지 않음
            console.log('Speech recognition aborted by user');
            return;
        } else {
            $('#ai-message').text(errorMsg);
        }
    };
    
    window.recognition.onend = function() {
        window.isRecording = false;
        $('#btn-voice-message').removeClass('recording');
        $('#btn-voice-message').text('🎤 말하기');
        
        // 최종 텍스트가 있으면 전송
        if (window.finalTranscript && window.finalTranscript.trim()) {
            sendVoiceMessageToServer(window.finalTranscript.trim());
            $('#ai-message').text('메시지를 전송했습니다: ' + window.finalTranscript.trim());
            window.finalTranscript = '';
        } else {
            // 음성이 감지되지 않았을 때는 메시지를 표시하지 않음
            // (사용자가 버튼을 빨리 눌렀다 떼거나, 실제로 음성이 없을 때는 조용히 처리)
            // 대신 상태 업데이트로 자연스럽게 전환
            console.log('No speech detected, updating status');
            // 짧은 딜레이 후 상태 업데이트 (메시지가 덮어쓰이지 않도록)
            setTimeout(function() {
                if (gameMode === 'multi' && currentTurn !== myColor) {
                    $('#ai-message').text('상대방이 생각 중입니다... ⏳');
                }
            }, 500);
        }
        console.log('Speech recognition ended');
    };
    
    // 체크박스가 체크되어 있을 때만 마이크 권한 요청
    // 권한이 이미 허용된 경우 팝업이 뜨지 않음
    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia && voicePermissionAllowed) {
        navigator.mediaDevices.getUserMedia({ audio: true })
            .then(function(stream) {
                // 권한이 허용되었으면 스트림 종료 (실제로는 사용하지 않음)
                stream.getTracks().forEach(track => track.stop());
                console.log('Microphone permission granted');
            })
            .catch(function(error) {
                console.log('Microphone permission denied or not available:', error);
                // 권한이 거부되었거나 사용할 수 없는 경우 버튼 숨김
                $('#btn-voice-message').hide();
            });
    }
}

// 음성 메시지 전송
function sendVoiceMessageToServer(text) {
    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket not connected');
        alert('서버와 연결이 끊어졌습니다. 페이지를 새로고침해주세요.');
        return;
    }
    
    if (!text || text.trim() === '') {
        console.warn('Empty voice message, not sending');
        return;
    }
    
    console.log('Sending voice message:', text.trim());
    
    const headers = {
        userId: userId.toString()
    };
    
    stompClient.send('/app/game/' + roomId + '/voice-message', headers, JSON.stringify({
        message: text.trim()
    }));
}

// 대기방 목록 로드
function loadWaitingRooms() {
    $.ajax({
        url: '/api/rooms/waiting',
        method: 'GET',
        success: function(rooms) {
            const roomsList = $('#rooms-list');
            roomsList.empty();
            
            if (rooms.length === 0) {
                roomsList.append('<p style="text-align: center; color: #999; padding: 20px;">대기 중인 방이 없습니다.</p>');
                return;
            }
            
            rooms.forEach(room => {
                const roomDiv = $('<div>').css({
                    padding: '15px',
                    margin: '10px 0',
                    border: '2px solid #ffcc00',
                    borderRadius: '10px',
                    backgroundColor: '#fff',
                    cursor: 'pointer'
                });
                
                roomDiv.append($('<p>').css({ margin: '0 0 5px 0', fontWeight: 'bold' }).text('방장: ' + room.hostName));
                roomDiv.append($('<p>').css({ margin: '0', fontSize: '0.9rem', color: '#666' })
                    .text('생성 시간: ' + new Date(room.createdAt).toLocaleString()));
                
                roomDiv.on('click', function() {
                    joinRoom(room.id);
                });
                
                roomsList.append(roomDiv);
            });
        },
        error: function() {
            console.error('Failed to load waiting rooms');
        }
    });
}

// 방 생성
function createRoom() {
    $.ajax({
        url: '/api/rooms',
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({ hostId: userId }),
        success: function(room) {
            roomId = room.id;
            isHost = true;
            myColor = 'B';
            opponentName = '상대방';
            
            $('#waiting-rooms-container').hide();
            $('#game-container').show();
            
            // 전체 화면으로 전환
            if (typeof requestFullscreen === 'function') {
                requestFullscreen();
            }
            
            connectWebSocket(roomId);
            initBoard();
            
            // 음성 메시지 초기화 (체크박스가 체크되어 있으면)
            const VOICE_PERMISSION_KEY = 'othello_voicePermissionAllowed';
            const voicePermissionAllowed = localStorage.getItem(VOICE_PERMISSION_KEY) === 'true';
            if (voicePermissionAllowed && typeof initSpeechRecognition === 'function') {
                initSpeechRecognition();
            }
            
            $('#ai-message').text('방을 만들었어요! 친구가 들어올 때까지 기다려주세요...');
        },
        error: function() {
            alert('방 생성에 실패했습니다.');
        }
    });
}

// 방 입장
function joinRoom(roomIdParam) {
    $.ajax({
        url: '/api/rooms/' + roomIdParam + '/join',
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({ guestId: userId }),
        success: function(gameState) {
            roomId = roomIdParam;
            isHost = false;
            myColor = 'W';
            
            // 호스트 이름 가져오기
            $.ajax({
                url: '/api/rooms/' + roomIdParam + '/state',
                method: 'GET',
                success: function(state) {
                    opponentName = state.hostName;
                }
            });
            
            $('#waiting-rooms-container').hide();
            $('#game-container').show();
            
            // 전체 화면으로 전환
            if (typeof requestFullscreen === 'function') {
                requestFullscreen();
            }
            
            connectWebSocket(roomId);
            
            boardState = gameState.boardState || OTHELLO.getInitialBoardState();
            currentTurn = gameState.turn || 'B';
            
            renderBoard(boardState);
            updateStatus();
            
            // 음성 메시지 초기화 (체크박스가 체크되어 있으면)
            const VOICE_PERMISSION_KEY = 'othello_voicePermissionAllowed';
            const voicePermissionAllowed = localStorage.getItem(VOICE_PERMISSION_KEY) === 'true';
            if (voicePermissionAllowed && typeof initSpeechRecognition === 'function') {
                initSpeechRecognition();
            }
            
            $('#ai-message').text('게임에 참여했습니다! 즐거운 게임 되세요!');
        },
        error: function() {
            alert('방 입장에 실패했습니다.');
            loadWaitingRooms();
        }
    });
}

