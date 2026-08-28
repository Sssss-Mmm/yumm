package com.example.demo.service;

import com.example.demo.dto.chat.ChatMessageResponse;

import java.util.List;

public interface ChatService {

    /** 메시지를 저장하고 브로드캐스트할 형태로 돌려준다. */
    ChatMessageResponse save(String roomId, Long senderId, String content);

    /** 방 하나의 지난 대화를 시간순으로. 구성원이 아니면 거부한다. */
    List<ChatMessageResponse> getRoomHistory(String roomId, Long userId);
}
