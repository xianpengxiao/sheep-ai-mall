package com.xs.sheepaimall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xs.sheepaimall.entity.ChatSession;
import com.xs.sheepaimall.entity.ChatUserProfile;
import com.xs.sheepaimall.mapper.ChatSessionMapper;
import com.xs.sheepaimall.mapper.ChatUserProfileMapper;
import com.xs.sheepaimall.service.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户画像服务实现 —— 进程内移植自 sheep-ai 的 ChatProfileConsumer（去 MQ 化）。
 * <p>原逻辑依赖 MQ 消息幂等（messageId 去重）；改为 sendMessage 的 doOnComplete 中
 * 每完成一轮对话精确调用一次，天然不重复，无需幂等检查。</p>
 */
@Slf4j
@Service
public class UserProfileServiceImpl implements UserProfileService {

    @Autowired
    private ChatUserProfileMapper chatUserProfileMapper;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Override
    public void updateAfterMessage(Long userId, Long sessionId) {
        try {
            LocalDateTime now = LocalDateTime.now();

            // 查现有画像
            ChatUserProfile profile = chatUserProfileMapper.selectOne(
                    new LambdaQueryWrapper<ChatUserProfile>()
                            .eq(ChatUserProfile::getUserId, userId)
            );

            if (profile == null) {
                // 新建画像
                profile = new ChatUserProfile();
                profile.setUserId(userId);
                profile.setTotalMessages(1);
                profile.setTotalSessions(countUserSessions(userId));
                profile.setLastActiveAt(now);
                profile.setSummary("");
                chatUserProfileMapper.insert(profile);
            } else {
                // 更新画像：消息数 +1，刷新最近活跃时间与会话数
                LambdaUpdateWrapper<ChatUserProfile> wrapper = new LambdaUpdateWrapper<ChatUserProfile>()
                        .eq(ChatUserProfile::getUserId, userId)
                        .set(ChatUserProfile::getLastActiveAt, now)
                        .set(ChatUserProfile::getTotalSessions, countUserSessions(userId))
                        .setSql("total_messages = total_messages + 1");
                chatUserProfileMapper.update(null, wrapper);
            }

            // 定期更新画像摘要（每10条消息更新一次）
            if (profile.getTotalMessages() != null && profile.getTotalMessages() % 10 == 0) {
                updateProfileSummary(userId, profile);
            }
        } catch (Exception e) {
            // 画像更新失败不影响对话主流程
            log.error("画像更新失败 userId={}", userId, e);
        }
    }

    /** 统计用户会话数 */
    private int countUserSessions(Long userId) {
        return chatSessionMapper.selectCount(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
        ).intValue();
    }

    /** 生成用户画像摘要（V1：基于统计数据的简单摘要，后续可接入 AI 生成） */
    private void updateProfileSummary(Long userId, ChatUserProfile profile) {
        try {
            StringBuilder summary = new StringBuilder();
            summary.append("该用户累计发送").append(profile.getTotalMessages()).append("条消息");
            summary.append("，参与").append(profile.getTotalSessions()).append("个会话。");

            if (profile.getLastActiveAt() != null) {
                summary.append("最近活跃时间：").append(profile.getLastActiveAt().toLocalDate());
            }

            chatUserProfileMapper.update(null,
                    new LambdaUpdateWrapper<ChatUserProfile>()
                            .eq(ChatUserProfile::getUserId, userId)
                            .set(ChatUserProfile::getSummary, summary.toString())
            );
        } catch (Exception e) {
            log.error("更新画像摘要失败 userId={}", userId, e);
        }
    }
}
