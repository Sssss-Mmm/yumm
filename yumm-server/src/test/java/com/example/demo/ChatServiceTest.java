package com.example.demo;

import com.example.demo.domain.ChatMessage;
import com.example.demo.domain.User;
import com.example.demo.dto.chat.ChatMessageResponse;
import com.example.demo.exception.CustomException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.MatchRequestRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.impl.ChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 지난 대화 조회 권한. 방 주소를 알아도 그룹 구성원이 아니면 볼 수 없어야 한다.
 */
class ChatServiceTest {

    private static final String ROOM_ID = "8f0b1c2d-0000-0000-0000-000000000001";

    private ChatMessageRepository chatMessageRepository;
    private MatchRequestRepository matchRequestRepository;
    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        chatMessageRepository = mock(ChatMessageRepository.class);
        matchRequestRepository = mock(MatchRequestRepository.class);
        chatService = new ChatServiceImpl(chatMessageRepository, matchRequestRepository, mock(UserRepository.class));

        // 방에는 1번 사용자만 있다
        when(matchRequestRepository.existsByGroupIdAndUser_Id(ROOM_ID, 1L)).thenReturn(true);
        when(matchRequestRepository.existsByGroupIdAndUser_Id(ROOM_ID, 99L)).thenReturn(false);
    }

    @Test
    @DisplayName("그룹 구성원은 지난 대화를 시간순으로 조회한다")
    void memberCanReadHistory() {
        User sender = User.builder().id(1L).nickname("밥친구").build();
        when(chatMessageRepository.findRoomHistory(ROOM_ID)).thenReturn(List.of(
                ChatMessage.builder().roomId(ROOM_ID).sender(sender).content("어디서 볼까요")
                        .sentAt(LocalDateTime.now()).build()));

        List<ChatMessageResponse> history = chatService.getRoomHistory(ROOM_ID, 1L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getSender()).isEqualTo("밥친구");
        assertThat(history.get(0).getContent()).isEqualTo("어디서 볼까요");
    }

    @Test
    @DisplayName("그룹 구성원이 아니면 방 주소를 알아도 지난 대화를 볼 수 없다")
    void nonMemberCannotReadHistory() {
        assertThatThrownBy(() -> chatService.getRoomHistory(ROOM_ID, 99L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_ROOM_FORBIDDEN);

        verify(chatMessageRepository, never()).findRoomHistory(ROOM_ID);
    }
}
