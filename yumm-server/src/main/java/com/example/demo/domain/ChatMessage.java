package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 채팅 메시지 1건. roomId는 매칭 그룹의 groupId를 그대로 쓴다(MatchRequest.groupId).
 *
 * 보낸 사람은 User 참조로만 들고 닉네임은 저장하지 않는다.
 * 닉네임을 복사해두면 사용자가 닉네임을 바꿨을 때 과거 기록이 어긋난다.
 */
@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_messages_room", columnList = "room_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatMessage {

    public static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, length = 36)
    private String roomId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    // ponytail: 메시지 종류 컬럼은 두지 않는다. 지금은 TALK 한 종류뿐이고,
    // 입퇴장 같은 시스템 메시지가 실제로 생기면 그때 추가한다.
    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(nullable = false)
    private LocalDateTime sentAt;
}
