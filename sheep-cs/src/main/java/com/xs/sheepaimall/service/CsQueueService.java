package com.xs.sheepaimall.service;

import com.xs.sheepaimall.vo.CsQueueStatusVO;

/** 排队分配 Service */
public interface CsQueueService {

    /** 进入排队 */
    CsQueueStatusVO enqueue(Long sessionId, Long userId);

    /** 查询排队状态 */
    CsQueueStatusVO getQueueStatus(Long sessionId);

    /** 取消排队 */
    void cancelQueue(Long sessionId, Long userId);

    /** 分配客服（最少负载优先） */
    Long assignAgent(Long sessionId);

    /** 客服上线 */
    void agentOnline(Long agentId);

    /** 客服下线 */
    void agentOffline(Long agentId);
}
