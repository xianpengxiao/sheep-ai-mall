package com.xs.sheepaimall.service;

import com.xs.sheepaimall.vo.CsSessionVO;

import java.util.List;

/** 客服会话 Service */
public interface CsSessionService {

    /** 获取或创建会话 */
    Long getOrCreateSession(Long userId, Long sessionId, Integer type);

    /** 用户会话列表 */
    List<CsSessionVO> listUserSessions(Long userId);

    /** 客服的进行中会话 */
    List<CsSessionVO> listAgentSessions(Long agentId);

    /** 结束会话 */
    void closeSession(Long sessionId, Long operatorId);

    /** 切换会话类型（AI→人工） */
    void transferToHuman(Long sessionId, Long agentId);
}
