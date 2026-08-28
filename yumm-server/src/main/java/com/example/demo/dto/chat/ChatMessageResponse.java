package com.example.demo.dto.chat;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

/** /sub/chat/room/{roomId} 구독자에게 브로드캐스트되는 메시지 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@Jacksonized
public class ChatMessageResponse implements java.io.Serializable {

    private final String roomId;
    private final Long senderId;
    private final String sender;      // 닉네임. 콘솔 클라이언트가 msg.sender로 출력한다
    private final String content;
    private final String type;        // 현재는 TALK만. 입퇴장 알림이 생기면 값이 늘어난다
    private final LocalDateTime sentAt;
}
