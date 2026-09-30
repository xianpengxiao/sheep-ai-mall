package com.xs.sheepaimall.core;

import com.xs.sheepaimall.vo.CsMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** 管理所有挂起的长轮询请求 */
@Slf4j
@Component
public class CsPollHolder {

    /** key: sessionId, value: 挂起的 DeferredResult */
    private final ConcurrentHashMap<Long, DeferredResult<List<CsMessageVO>>> polls = new ConcurrentHashMap<>();

    /** 注册一个新的长轮询 */
    public DeferredResult<List<CsMessageVO>> register(Long sessionId, long timeoutMs) {
        DeferredResult<List<CsMessageVO>> dr = new DeferredResult<>(timeoutMs);
        polls.put(sessionId, dr);

        dr.onTimeout(() -> {
            polls.remove(sessionId);
            dr.setResult(List.of());
        });
        dr.onCompletion(() -> polls.remove(sessionId));
        return dr;
    }

    /** 新消息到达时唤醒该会话的轮询 */
    public void wakeUp(Long sessionId, List<CsMessageVO> messages) {
        DeferredResult<List<CsMessageVO>> dr = polls.remove(sessionId);
        if (dr != null && !dr.isSetOrExpired()) {
            dr.setResult(messages);
        }
    }

    /** 取消轮询 */
    public void cancel(Long sessionId) {
        DeferredResult<List<CsMessageVO>> dr = polls.remove(sessionId);
        if (dr != null && !dr.isSetOrExpired()) {
            dr.setErrorResult(new RuntimeException("轮询已取消"));
        }
    }

    /** 是否有会话在轮询 */
    public boolean hasPolling(Long sessionId) {
        return polls.containsKey(sessionId);
    }
}
