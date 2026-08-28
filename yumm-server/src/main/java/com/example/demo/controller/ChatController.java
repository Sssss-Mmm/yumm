package com.example.demo.controller;

import com.example.demo.dto.chat.ChatMessageRequest;
import com.example.demo.dto.chat.ChatMessageResponse;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

/**
 * 채팅방 ID는 매칭 그룹의 groupId를 그대로 쓴다. 방을 따로 만들 필요가 없고,
 * 그룹 구성원 = 채팅방 참여자가 자동으로 성립한다.
 *
 * 참여 자격 검사는 StompAuthChannelInterceptor에서 이미 끝났으므로 여기서는 다시 하지 않는다.
 */
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @MessageMapping("chatroom.{roomId}")
    public void sendMessage(@DestinationVariable String roomId,
                            ChatMessageRequest request,
                            Principal principal) {

        if (request.getContent() == null || request.getContent().isBlank()) {
            return; // 빈 메시지는 조용히 버린다
        }

        Long senderId = userIdOf(principal);
        String nickname = userRepository.findById(senderId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND))
                .getNickname();

        // ponytail: 저장하지 않고 접속 중인 사람에게 전달만 한다. 새로고침하면 대화가 사라진다.
        // 지난 대화를 봐야 할 필요가 생기면 ChatMessage 엔티티와 조회 API를 추가한다.
        messagingTemplate.convertAndSend("/sub/chat/room/" + roomId,
                ChatMessageResponse.builder()
                        .roomId(roomId)
                        .senderId(senderId)
                        .sender(nickname)
                        .content(request.getContent())
                        .type("TALK")
                        .sentAt(LocalDateTime.now())
                        .build());
    }

    private Long userIdOf(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getId();
        }
        throw new CustomException(ErrorCode.INVALID_TOKEN);
    }
}
