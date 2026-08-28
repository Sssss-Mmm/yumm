// console-client/chatClient.js
const { Client } = require('@stomp/stompjs');
const WebSocket = require('websocket').w3cwebsocket;

let stompClient;

// 채팅 시작
function startChat(roomId, accessToken, rl) {
  return new Promise((resolve) => {
    username = 'me'; // 나중에 userId 또는 nickname으로 치환

    // 이전 리스너 정리
    rl.removeAllListeners('line');

    stompClient = new Client({
      brokerURL: 'ws://localhost:8080/ws', // 💡 Spring 서버의 WebSocket 연결 주소
      connectHeaders: {
        Authorization: `Bearer ${accessToken}` // JWT 인증
      },
      webSocketFactory: () => new WebSocket('ws://localhost:8080/ws'), // 실제 연결용
      reconnectDelay: 5000,
      debug: str => console.log('[DEBUG]', str),
      onConnect: () => {
        console.log(`✅ WebSocket 연결됨 → 채팅방 입장 (roomId: ${roomId})`);

        // 채팅방 구독
        stompClient.subscribe(`/sub/chat/room/${roomId}`, message => {
          const msg = JSON.parse(message.body);
          console.log(`\n💬 [${msg.sender}] ${msg.content}`);
          rl.prompt(); // 다시 입력 가능하게
          
        });

        // 채팅 입력 루프
        rl.setPrompt(`[Me] > `);
        rl.prompt();
        rl.on('line', line => {
          if (line.trim() === '/exit') {
            stompClient.deactivate();
            console.log('👋 채팅 종료');
            rl.close();
            resolve();
          } else {
            // 메시지 전송
            stompClient.publish({
              destination: `/pub/chatroom.${roomId}`,
              body: JSON.stringify({
                //sender: username,
                roomId: roomId,
                content: line.trim(),
                type: 'TALK'
              })
            });
            rl.prompt();
          }
        });
      }
    });

    stompClient.activate();
  });
}

module.exports = { startChat };
