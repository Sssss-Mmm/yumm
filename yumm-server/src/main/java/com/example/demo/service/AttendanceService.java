package com.example.demo.service;

import com.example.demo.dto.match.AttendanceCheckRequest;
import com.example.demo.dto.match.MatchStatusResponse;

import java.util.List;

/** 만남 뒤 참석 상호 체크(FR-C-06). 노쇼 집계의 원천 데이터를 모은다. */
public interface AttendanceService {

    /** 지금 체크할 수 있는데 아직 안 한 그룹들. 그룹원에는 나도 포함된다(화면이 거른다). */
    List<MatchStatusResponse> getPending(Long userId);

    /** 한 그룹에 대한 체크를 제출한다. 그룹당 한 번이며 나를 뺀 그룹원 전원을 빠짐없이 담아야 한다. */
    void submit(Long userId, String groupId, AttendanceCheckRequest request);
}
