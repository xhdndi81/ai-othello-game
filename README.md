# ⚫⚪ 꼬마 오셀로 선생님 - AI와 함께하는 오셀로 여행

> **아이들을 위한 친절한 AI 오셀로 게임입니다.** OpenAI GPT를 활용하여 아이들이 오셀로를 배우고 즐길 수 있도록 도와주는 웹 애플리케이션입니다. 이제 친구와 함께 즐기는 멀티플레이어 모드도 지원합니다!

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen)
![WebSocket](https://img.shields.io/badge/WebSocket-STOMP-blue)
![License](https://img.shields.io/badge/License-MIT-blue)

---

## 📖 프로젝트 소개

**꼬마 오셀로 선생님**은 초등학생을 위한 교육용 오셀로 게임입니다. AI가 아이의 이름을 부르며 격려하고 칭찬하는 친절한 멘트로 오셀로를 가르칩니다. 싱글 플레이어 모드에서는 강력한 AI와 대결하고, 멀티플레이어 모드에서는 친구와 실시간으로 실력을 겨룰 수 있습니다.

### ✨ 주요 기능

- 🤖 **AI 오셀로 상대**: OpenAI GPT를 활용한 지능형 오셀로 상대
- 👥 **실시간 멀티플레이어**: WebSocket(STOMP)을 이용한 실시간 방 생성 및 대결 기능
- 🎤 **음성 메시지**: 멀티플레이어 모드에서 상대방과 음성으로 대화 가능
- 👶 **아이 친화적 UI**: 초등학생도 쉽게 사용할 수 있는 직관적이고 따뜻한 인터페이스
- 💬 **음성 격려 멘트**: Web Speech API(TTS)를 활용하여 AI가 아이의 이름을 부르며 친절하게 격려
- 📊 **자동 게임 기록**: 승패 결과를 DB에 자동 저장하여 아이의 성장 과정을 추적
- 🔄 **재경기 및 퇴장 로직**: 게임 종료 후 재경기 지원 및 패배자 자동 퇴장 시스템
- 📱 **반응형 디자인**: 스마트폰과 태블릿에서도 최적화된 레이아웃 지원

---

## 🛠️ 기술 스택

### Backend
- **Java 17 / Spring Boot 3.2.0**
- **Spring Data JPA**: 데이터베이스 ORM
- **Spring WebSocket**: STOMP 프로토콜 기반 실시간 통신
- **MariaDB**: 관계형 데이터베이스
- **Lombok**: 효율적인 Java 코드 작성

### Frontend
- **HTML5 / CSS3 / JavaScript (Vanilla JS + jQuery)**
- **Custom Othello Engine**: 자체 구현한 오셀로 게임 엔진 로직
- **SockJS / Stomp.js**: 웹소켓 클라이언트 라이브러리
- **Web Speech API**: 음성 인식 및 음성 출력

### AI & Speech
- **OpenAI GPT-4o-mini**: 상황별 친절한 오셀로 코멘트 생성
- **Web Speech API**: 시스템 TTS를 이용한 한국어 음성 출력 및 음성 인식

---

## 📁 프로젝트 구조

모듈화된 구조로 유지보수가 용이하도록 설계되었습니다.

```
src/main/
├── java/com/othello/ai/
│   ├── config/             # WebSocket, JPA 등 앱 설정
│   ├── controller/         # API 및 WebSocket 엔드포인트
│   ├── dto/                # 데이터 전송 객체
│   ├── entity/             # DB 테이블 매핑 (User, GameRoom, GameHistory, OthelloGameData)
│   ├── listener/           # WebSocket 연결/해제 이벤트 리스너
│   ├── repository/         # DB 접근 인터페이스
│   └── service/            # 핵심 비즈니스 로직 (AI 분석, 방 관리, 오셀로 게임 엔진)
└── resources/
    ├── static/
    │   ├── js/
    │   │   ├── app.js            # 공통 로직 및 UI 제어
    │   │   ├── othello.js         # 오셀로 게임 엔진 로직
    │   │   ├── single-player.js  # 싱글 모드 (AI 대전) 로직
    │   │   └── multiplayer.js    # 멀티 모드 (WebSocket) 로직
    │   ├── css/
    │   │   └── style.css         # 스타일시트
    │   ├── index.html            # 메인 페이지
    │   ├── waiting-rooms.html    # 대기방 목록 조각 (동적 로드)
    │   └── manifest.json         # PWA 매니페스트
    ├── application.yml           # 설정 파일
    └── application-local.yml.example  # 로컬 설정 예시 파일
```

---

## 🚀 설치 및 실행 방법

### 1. 사전 요구사항
- **JDK 17 이상**, **MariaDB**, **OpenAI API Key**

### 2. 데이터베이스 설정
```sql
CREATE DATABASE games CHARACTER SET utf8mb4;
```
> 참고: 체스 게임과 동일한 데이터베이스를 사용하며, `game_type` 필드로 게임 종류를 구분합니다.

### 3. API 키 설정 (`application-local.yml`)
`src/main/resources/application-local.yml.example` 파일을 참고하여 `application-local.yml` 파일을 생성하고 키를 입력합니다.
```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/your-database-schema
    username: your-database-username
    password: your-database-password

openai:
  api:
    key: your-api-key-here
    url: https://api.openai.com/v1/chat/completions
```

### 4. 실행
```bash
mvn spring-boot:run
```
접속 주소: `http://localhost:8080`

---

## 🎮 게임 모드 설명

### 🌱 혼자하기 (Single Mode)
- **난이도 선택**: 쉬움, 보통, 어려움, 마스터 4단계 조절 가능
- **AI 선생님**: 수를 둘 때마다 GPT가 친절하게 칭찬하거나 조언해줍니다.
- **재촉 기능**: 아이가 고민에 빠지면 AI가 다정하게 말을 건넵니다.

### 🤝 같이하기 (Multiplayer Mode)
- **대기방 목록**: 현재 대기 중인 친구의 방을 확인하고 입장합니다.
- **실시간 대결**: 웹소켓을 통해 지연 없는 실시간 대결이 가능합니다.
- **음성 메시지**: 상대방과 음성으로 대화할 수 있습니다 (선택 사항).
- **재촉하기**: 상대방 차례에 재촉 메시지를 보낼 수 있습니다.
- **중도 이탈 처리**: 상대방이 게임 중 접속을 끊으면 자동으로 남은 사람이 승리 처리됩니다.
- **승자 권한**: 게임 종료 후 승리자에게만 '새 게임' 시작 권한이 주어집니다.

---

## 🎯 오셀로 게임 규칙

- **흑색이 먼저 시작**하며, 중앙 4칸에 초기 배치됩니다.
- **상대방 돌을 둘러싸면** 그 돌들이 자신의 색으로 뒤집힙니다.
- **가로, 세로, 대각선** 모든 방향으로 둘러쌀 수 있습니다.
- **둘 수 있는 곳이 없으면** 패스하며, 양쪽 모두 패스하면 게임이 종료됩니다.
- **게임 종료 시 돌이 많은 쪽이 승리**합니다.

---

## 📝 주요 특징

### 음성 기능
- **TTS (Text-to-Speech)**: AI의 멘트를 음성으로 들을 수 있습니다.
- **음성 인식**: 멀티플레이어 모드에서 상대방과 음성으로 대화할 수 있습니다.
- **마이크 권한**: 브라우저에서 마이크 권한을 허용해야 음성 메시지를 사용할 수 있습니다.

### 반응형 디자인
- **세로 모드**: 스마트폰 세로 방향에 최적화된 레이아웃
- **가로 모드**: 태블릿 가로 방향에 최적화된 레이아웃
- **자동 조정**: 화면 크기에 따라 UI 요소가 자동으로 조정됩니다

---

## 👨‍👩‍👧‍👦 제작자
**소희, 선우 아빠 ❤️**  
아이들이 오셀로를 통해 생각하는 즐거움을 배우길 바라는 마음으로 만들었습니다.

---

**즐거운 오셀로 여행을 시작해보세요! ⚫⚪**

