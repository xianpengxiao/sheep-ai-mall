package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.CsReplyDTO;
import com.xs.sheepaimall.entity.Merchant;
import com.xs.sheepaimall.mapper.MerchantMapper;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.CsService;
import com.xs.sheepaimall.service.MerchantAiService;
import com.xs.sheepaimall.vo.ChatMessageVO;
import com.xs.sheepaimall.vo.CsSessionVO;
import com.xs.sheepaimall.vo.MerchantCsQuotaVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家人工坐席控制台（设计 D4）—— 商家端登录态，scope 由 MerchantMapper 按当前用户推导，
 * 只能看到/操作归属本商家的会话；回复写 role=2 消息。
 */
@Tag(name = "商家-人工客服", description = "商家坐席控制台：会话列表、消息历史、人工回复、接入、转回AI、额度视图")
@Validated
@RestController
@RequestMapping("/api/merchant/cs")
public class MerchantCsController {

    @Autowired
    private CsService csService;

    @Autowired
    private MerchantAiService merchantAiService;

    @Autowired
    private MerchantMapper merchantMapper;

    @Operation(summary = "额度视图", description = "坐席页内嵌的额度消耗卡片：套餐、月度总额/已用/剩余、今日请求数与Token、日限配置")
    @GetMapping("/quota")
    public R<MerchantCsQuotaVO> quota() {
        return R.ok(merchantAiService.getQuota());
    }

    @Operation(summary = "坐席会话列表", description = "本商家的全部客服会话（含AI会话与转人工会话），按最后消息时间倒序，含未读数")
    @GetMapping("/sessions")
    public R<List<CsSessionVO>> listSessions() {
        return R.ok(csService.listSessions(getScope()));
    }

    @Operation(summary = "会话消息历史", description = "指定会话的全部消息，按时间正序；非本商家会话返回统一文案")
    @GetMapping("/sessions/{sessionId}/messages")
    public R<List<ChatMessageVO>> getMessages(
            @Parameter(description = "会话ID") @PathVariable Long sessionId) {
        return R.ok(csService.getMessages(getScope(), sessionId));
    }

    @Operation(summary = "人工回复", description = "坐席回复用户：落 role=2 消息、刷新会话时间、写审计 HUMAN_REPLY")
    @PostMapping("/sessions/{sessionId}/reply")
    public R<Void> reply(
            @Parameter(description = "会话ID") @PathVariable Long sessionId,
            @Valid @RequestBody CsReplyDTO dto) {
        csService.reply(getScope(), sessionId, dto.getContent(), UserContext.getUserId());
        return R.ok();
    }

    @Operation(summary = "坐席接入", description = "显式标记接入会话（写审计 TAKEOVER），接入效果由首条人工消息自然体现")
    @PutMapping("/sessions/{sessionId}/takeover")
    public R<Void> takeover(
            @Parameter(description = "会话ID") @PathVariable Long sessionId) {
        csService.takeover(getScope(), sessionId, UserContext.getUserId());
        return R.ok();
    }

    @Operation(summary = "转回AI客服", description = "清空人工态回到 AI 服务（写审计 BACK_TO_AI）")
    @PutMapping("/sessions/{sessionId}/to-ai")
    public R<Void> backToAi(
            @Parameter(description = "会话ID") @PathVariable Long sessionId) {
        csService.backToAi(getScope(), sessionId, UserContext.getUserId());
        return R.ok();
    }

    @Operation(summary = "转人工客服", description = "坐席将 AI 态会话切入人工态（写审计 TRANSFER_REQUEST），转接后可人工回复")
    @PutMapping("/sessions/{sessionId}/to-human")
    public R<Void> transferToHuman(
            @Parameter(description = "会话ID") @PathVariable Long sessionId) {
        csService.transferToHuman(getScope(), sessionId, UserContext.getUserId());
        return R.ok();
    }

    /** 当前登录用户对应的商家ID（数据范围 scope），不是商家则抛业务异常（对齐 MerchantAiServiceImpl.getCurrentMerchant） */
    private Long getScope() {
        Merchant merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, UserContext.getUserId()));
        if (merchant == null) {
            throw new BizException("当前账号不是商家");
        }
        return merchant.getId();
    }
}
