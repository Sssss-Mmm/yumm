package com.example.demo.service.impl;

import com.example.demo.domain.ChatMessage;
import com.example.demo.dto.chat.ChatMessageResponse;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final UserRepository userRepository;

    /**
     * 메시지 저장. 참여 자격은 STOMP 단(StompAuthChannelInterceptor)에서 이미 끝났으므로 다시 보지 않는다.
     */
    @Override
    @Transactional
    public ChatMessageResponse save(String roomId, Long senderId, String content) {
        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .roomId(roomId)
                .sender(userRepository.findById(senderId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)))
                .content(content)
                .sentAt(LocalDateTime.now())
                .build());

        return toResponse(saved);
    }

    /**
     * 지난 대화 조회. 방 주소를 알아도 그 그룹 구성원이 아니면 볼 수 없다.
     * 구성원 판정은 STOMP 구독 검사와 같은 쿼리를 쓴다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getRoomHistory(String roomId, Long userId) {
        if (!matchRequestRepository.existsByGroupIdAndUser_Id(roomId, userId)) {
            // ponytail: 없는 방과 남의 방을 구분하지 않는다. 구분하면 방 존재 여부가 새어 나간다.
            throw new CustomException(ErrorCode.CHAT_ROOM_FORBIDDEN);
        }

        return chatMessageRepository.findRoomHistory(roomId).stream()
                .map(ChatServiceImpl::toResponse)
                .toList();
    }

    /** 닉네임은 저장해두지 않고 보낸 사람에서 꺼낸다(닉네임이 바뀌면 지난 기록도 함께 바뀐다). */
    private static ChatMessageResponse toResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .roomId(message.getRoomId())
                .senderId(message.getSender().getId())
                .sender(message.getSender().getNickname())
                .content(message.getContent())
                .type("TALK")
                .sentAt(message.getSentAt())
                .build();
    }
}
