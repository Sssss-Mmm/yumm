package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.chat.ChatMessageResponse;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 채팅 기록 조회 REST API. 실시간 송수신은 STOMP(ChatController)가 담당한다.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatService chatService;

    /**
     * 지난 대화 조회 API.
     * 매칭 그룹(=채팅방)의 메시지를 시간순으로 모두 반환합니다.
     * 그룹 구성원이 아니면 403으로 거부합니다.
     *
     * @param userDetails 현재 인증된 사용자
     * @param groupId 매칭 그룹 ID (채팅방 ID와 같습니다)
     */
    @GetMapping("/rooms/{groupId}/messages")
    @Operation(summary = "지난 대화 조회",
            description = "매칭 그룹 채팅방의 메시지를 시간순으로 조회합니다. 그룹 구성원만 조회할 수 있습니다.")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getRoomHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String groupId) {

        return ApiResponse.ok("대화 내역 조회 성공", chatService.getRoomHistory(groupId, userDetails.getId()));
    }
}
