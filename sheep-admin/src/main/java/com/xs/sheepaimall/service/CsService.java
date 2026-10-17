package com.xs.sheepaimall.service;

import com.xs.sheepaimall.vo.ChatMessageVO;
import com.xs.sheepaimall.vo.CsSessionVO;

import java.util.List;

/**
 * 人工坐席服务（设计 D4）—— 商家端与平台端控制台共用，数据范围由 scope 隔离。
 * <p>scope 为会话归属商家ID（null=平台会话）。身份推导在 controller 层完成
 * （商家端按 UserContext.userId 查 merchant，平台端校验 ROLE_ADMIN），
 * 本服务只做数据范围校验：session.merchantId 必须等于 scope，不匹配统一抛
 * "会话不存在或无权访问"，避免会话存在性泄露。</p>
 */
public interface CsService {

    /**
     * 坐席会话列表：按最后消息时间倒序
     *
     * @param scope 商家ID（null=平台）
     */
    List<CsSessionVO> listSessions(Long scope);

    /**
     * 会话全部消息（按消息ID正序），范围校验失败抛业务异常
     *
     * @param scope     商家ID（null=平台）
     * @param sessionId 会话ID
     */
    List<ChatMessageVO> getMessages(Long scope, Long sessionId);

    /**
     * 坐席回复：落 role=2（商家）/3（平台）消息、刷新会话时间、写审计 HUMAN_REPLY
     *
     * @param scope      商家ID（null=平台）
     * @param sessionId  会话ID
     * @param content    回复内容
     * @param operatorId 操作者用户ID（审计 actorId）
     */
    void reply(Long scope, Long sessionId, String content, Long operatorId);

    /**
     * 坐席接入：写审计 TAKEOVER（接入效果由首条人工消息自然体现，见 ChatSessionStateService）
     *
     * @param scope      商家ID（null=平台）
     * @param sessionId  会话ID
     * @param operatorId 操作者用户ID（审计 actorId）
     */
    void takeover(Long scope, Long sessionId, Long operatorId);

    /**
     * 坐席转回 AI：清空人工态、写审计 BACK_TO_AI
     *
     * @param scope      商家ID（null=平台）
     * @param sessionId  会话ID
     * @param operatorId 操作者用户ID（审计 actorId）
     */
    void backToAi(Long scope, Long sessionId, Long operatorId);

    /**
     * 坐席手动转人工：AI 态会话经坐席确认后切入人工态，转接后坐席即可人工回复（写审计 TRANSFER_REQUEST）
     *
     * @param scope      商家ID（null=平台）
     * @param sessionId  会话ID
     * @param operatorId 操作者用户ID（审计 actorId）
     */
    void transferToHuman(Long scope, Long sessionId, Long operatorId);
}
