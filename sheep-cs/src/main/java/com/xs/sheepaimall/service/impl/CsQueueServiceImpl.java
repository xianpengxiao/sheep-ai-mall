package com.xs.sheepaimall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.entity.CsAgent;
import com.xs.sheepaimall.entity.CsSession;
import com.xs.sheepaimall.mapper.CsAgentMapper;
import com.xs.sheepaimall.mapper.CsSessionMapper;
import com.xs.sheepaimall.service.CsQueueService;
import com.xs.sheepaimall.vo.CsQueueStatusVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

/** 排队分配 — Redis ZSET + 最少负载优先 */
@Slf4j
@Service
public class CsQueueServiceImpl implements CsQueueService {

    private static final String AGENTS_ONLINE_KEY = "cs:agents:online";
    private static final String QUEUE_WAITING_KEY = "cs:queue:waiting";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CsAgentMapper csAgentMapper;

    @Autowired
    private CsSessionMapper csSessionMapper;

    @Override
    public CsQueueStatusVO enqueue(Long sessionId, Long userId) {
        CsSession session = csSessionMapper.selectById(sessionId);
        if (session == null) throw new BizException("会话不存在");
        session.setStatus(2); // 排队中
        csSessionMapper.updateById(session);

        long score = Instant.now().toEpochMilli();
        safeRedisOp(() -> stringRedisTemplate.opsForZSet().add(QUEUE_WAITING_KEY, sessionId.toString(), score));

        return getQueueStatus(sessionId);
    }

    @Override
    public CsQueueStatusVO getQueueStatus(Long sessionId) {
        CsQueueStatusVO vo = new CsQueueStatusVO();
        Long rank = safeRedisGet(() -> stringRedisTemplate.opsForZSet().rank(QUEUE_WAITING_KEY, sessionId.toString()));
        if (rank == null) {
            vo.setQueuing(false);
            return vo;
        }
        vo.setQueuing(true);
        vo.setPosition(rank.intValue() + 1);
        vo.setAheadCount(rank.intValue());
        vo.setEstimatedWaitSeconds(rank.intValue() * 30); // 每人大约30秒
        return vo;
    }

    @Override
    public void cancelQueue(Long sessionId, Long userId) {
        CsSession session = csSessionMapper.selectById(sessionId);
        if (session != null && session.getUserId().equals(userId)) {
            session.setStatus(0);
            csSessionMapper.updateById(session);
        }
        safeRedisOp(() -> stringRedisTemplate.opsForZSet().remove(QUEUE_WAITING_KEY, sessionId.toString()));
    }

    @Override
    public Long assignAgent(Long sessionId) {
        // 1. 从在线客服中选负载最少的（ZSET score = current_count）
        Set<ZSetOperations.TypedTuple<String>> agents = safeRedisGet(() ->
                stringRedisTemplate.opsForZSet().rangeWithScores(AGENTS_ONLINE_KEY, 0, 0));
        if (agents == null || agents.isEmpty()) return null;

        String agentIdStr = agents.iterator().next().getValue();
        Long agentId = Long.valueOf(agentIdStr);

        // 2. 检查是否超负载
        CsAgent agent = csAgentMapper.selectById(agentId);
        if (agent == null || agent.getCurrentCount() >= agent.getMaxConcurrent()) return null;

        // 3. 分配
        agent.setCurrentCount(agent.getCurrentCount() + 1);
        csAgentMapper.updateById(agent);

        // 4. 更新 Redis score + 从排队移除
        safeRedisOp(() -> {
            stringRedisTemplate.opsForZSet().incrementScore(AGENTS_ONLINE_KEY, agentIdStr, 1);
            stringRedisTemplate.opsForZSet().remove(QUEUE_WAITING_KEY, sessionId.toString());
        });

        // 5. 更新会话
        CsSession session = csSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setAgentId(agentId);
            session.setStatus(0);
            csSessionMapper.updateById(session);
        }
        return agentId;
    }

    @Override
    public void agentOnline(Long agentId) {
        safeRedisOp(() -> stringRedisTemplate.opsForZSet().add(AGENTS_ONLINE_KEY, agentId.toString(), 0));
        CsAgent agent = csAgentMapper.selectById(agentId);
        if (agent != null) {
            agent.setStatus(1);
            csAgentMapper.updateById(agent);
        }
    }

    @Override
    public void agentOffline(Long agentId) {
        safeRedisOp(() -> stringRedisTemplate.opsForZSet().remove(AGENTS_ONLINE_KEY, agentId.toString()));
        CsAgent agent = csAgentMapper.selectById(agentId);
        if (agent != null) {
            agent.setStatus(0);
            csAgentMapper.updateById(agent);
        }
    }

    private void safeRedisOp(Runnable op) {
        try { op.run(); } catch (RedisConnectionFailureException e) { log.warn("Redis不可用"); } catch (Exception ignored) {}
    }

    private <T> T safeRedisGet(java.util.function.Supplier<T> op) {
        try { return op.get(); } catch (RedisConnectionFailureException e) { return null; } catch (Exception e) { return null; }
    }
}
