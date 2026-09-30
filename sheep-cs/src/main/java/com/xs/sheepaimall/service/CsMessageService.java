package com.xs.sheepaimall.service;

import com.xs.sheepaimall.dto.CsSendDTO;
import com.xs.sheepaimall.vo.CsMessageVO;

import java.util.List;

/** 客服消息 Service */
public interface CsMessageService {

    /** 发送消息（同步写Redis + 异步MQ落库+推送） */
    CsMessageVO sendMessage(CsSendDTO dto);

    /** 获取会话历史消息 */
    List<CsMessageVO> getMessages(Long sessionId);

    /** 拉取新消息（长轮询专用，读Redis） */
    List<CsMessageVO> pollNewMessages(Long sessionId, Long lastMsgId);

    /** 客服回复消息 */
    CsMessageVO sendAgentMessage(Long agentId, CsSendDTO dto);
}
