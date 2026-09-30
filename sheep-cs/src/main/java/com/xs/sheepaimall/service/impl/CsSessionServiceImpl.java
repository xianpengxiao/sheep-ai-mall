package com.xs.sheepaimall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.entity.CsAgent;
import com.xs.sheepaimall.entity.CsMessage;
import com.xs.sheepaimall.entity.CsSession;
import com.xs.sheepaimall.mapper.CsAgentMapper;
import com.xs.sheepaimall.mapper.CsMessageMapper;
import com.xs.sheepaimall.mapper.CsSessionMapper;
import com.xs.sheepaimall.service.CsSessionService;
import com.xs.sheepaimall.vo.CsSessionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CsSessionServiceImpl implements CsSessionService {

    @Autowired
    private CsSessionMapper csSessionMapper;

    @Autowired
    private CsMessageMapper csMessageMapper;

    @Autowired
    private CsAgentMapper csAgentMapper;

    @Override
    public Long getOrCreateSession(Long userId, Long sessionId, Integer type) {
        if (sessionId != null) {
            CsSession session = csSessionMapper.selectById(sessionId);
            if (session == null) throw new BizException("会话不存在");
            if (!session.getUserId().equals(userId)) throw new BizException("无权访问");
            return sessionId;
        }
        CsSession session = new CsSession();
        session.setUserId(userId);
        session.setType(type != null ? type : 0);
        session.setStatus(0);
        session.setTitle("新会话");
        session.setLastMessageAt(LocalDateTime.now());
        csSessionMapper.insert(session);
        return session.getId();
    }

    @Override
    public List<CsSessionVO> listUserSessions(Long userId) {
        List<CsSession> sessions = csSessionMapper.selectList(
                new LambdaQueryWrapper<CsSession>()
                        .eq(CsSession::getUserId, userId)
                        .orderByDesc(CsSession::getLastMessageAt)
        );
        return sessions.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<CsSessionVO> listAgentSessions(Long agentId) {
        CsAgent agent = csAgentMapper.selectOne(
                new LambdaQueryWrapper<CsAgent>().eq(CsAgent::getUserId, agentId));
        if (agent == null) return Collections.emptyList();

        List<CsSession> sessions = csSessionMapper.selectList(
                new LambdaQueryWrapper<CsSession>()
                        .eq(CsSession::getAgentId, agent.getId())
                        .eq(CsSession::getStatus, 0)
                        .orderByDesc(CsSession::getLastMessageAt)
        );
        return sessions.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public void closeSession(Long sessionId, Long operatorId) {
        CsSession session = csSessionMapper.selectById(sessionId);
        if (session == null) throw new BizException("会话不存在");
        session.setStatus(1);
        csSessionMapper.updateById(session);
    }

    @Override
    public void transferToHuman(Long sessionId, Long agentId) {
        CsSession session = csSessionMapper.selectById(sessionId);
        if (session == null) throw new BizException("会话不存在");
        session.setType(1);
        session.setAgentId(agentId);
        session.setStatus(0);
        csSessionMapper.updateById(session);
    }

    private CsSessionVO toVO(CsSession session) {
        CsSessionVO vo = new CsSessionVO();
        vo.setId(session.getId());
        vo.setTitle(session.getTitle());
        vo.setType(session.getType());
        vo.setStatus(session.getStatus());
        vo.setLastMessageAt(session.getLastMessageAt());
        if (session.getAgentId() != null) {
            CsAgent agent = csAgentMapper.selectById(session.getAgentId());
            if (agent != null) vo.setAgentName(agent.getName());
        }
        return vo;
    }
}
