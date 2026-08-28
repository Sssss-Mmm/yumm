package com.example.demo.repository;

import com.example.demo.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 방 하나의 대화를 시간순으로 전부. 닉네임을 붙여야 하므로 보낸 사람을 함께 가져온다.
     *
     * ponytail: 페이징 없이 전부 준다. 그룹당 3~4명이 한 끼 약속을 잡는 대화라 길어야 수백 건이다.
     * 방이 길어지면 그때 Pageable을 받는다.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            JOIN FETCH m.sender
            WHERE m.roomId = :roomId
            ORDER BY m.sentAt ASC
            """)
    List<ChatMessage> findRoomHistory(@Param("roomId") String roomId);
}
