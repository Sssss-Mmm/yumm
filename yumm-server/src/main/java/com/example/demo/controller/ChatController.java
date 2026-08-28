package com.example.demo.controller;

import com.example.demo.domain.ChatMessage;
import com.example.demo.dto.chat.ChatMessageRequest;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

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
    private final ChatService chatService;

    @MessageMapping("chatroom.{roomId}")
    public void sendMessage(@DestinationVariable String roomId,
                            ChatMessageRequest request,
                            Principal principal) {

        String content = request.getContent();
        if (content == null || content.isBlank() || content.length() > ChatMessage.MAX_CONTENT_LENGTH) {
            return; // 빈 메시지와 저장할 수 없을 만큼 긴 메시지는 조용히 버린다
        }

        // 저장한 뒤 전달한다. 저장이 실패하면 아무에게도 보이지 않는 편이 낫다.
        messagingTemplate.convertAndSend("/sub/chat/room/" + roomId,
                chatService.save(roomId, userIdOf(principal), content));
    }

    private Long userIdOf(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getId();
        }
        throw new CustomException(ErrorCode.INVALID_TOKEN);
    }
}
