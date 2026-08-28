package com.example.demo.dto.chat;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 클라이언트가 /pub/chatroom.{roomId} 로 보내는 본문.
 *
 * 클라이언트는 roomId와 type도 함께 보내지만 서버는 쓰지 않는다.
 * roomId는 목적지 경로에서, 보낸 사람은 인증 정보에서 가져온다 (본문 값은 위조 가능).
 */
@Getter
@NoArgsConstructor
public class ChatMessageRequest {

    private String content;
}
