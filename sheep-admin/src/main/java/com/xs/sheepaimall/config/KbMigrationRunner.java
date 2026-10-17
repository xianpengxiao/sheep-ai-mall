package com.xs.sheepaimall.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xs.sheepaimall.entity.ChatRagDoc;
import com.xs.sheepaimall.mapper.ChatRagDocMapper;
import com.xs.sheepaimall.service.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 知识库向量一次性迁移：仅当配置 sheep.ai.chat.kb-sync=true 时执行。
 * <p>遍历 chat_rag_doc 中 enabled=1 的文档调用 onDocChanged 重建向量
 * （先删后插，天然幂等，可重复执行）。默认关闭，避免误触发全量重建。</p>
 */
@Slf4j
@Component
public class KbMigrationRunner implements ApplicationRunner {

    @Autowired
    private ChatProperties chatProperties;

    @Autowired
    private ChatRagDocMapper chatRagDocMapper;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Override
    public void run(ApplicationArguments args) {
        if (!chatProperties.isKbSync()) {
            log.info("sheep.ai.chat.kb-sync 未开启，跳过知识库向量迁移");
            return;
        }
        log.info("开始知识库向量迁移（kb-sync=true）...");
        try {
            List<ChatRagDoc> docs = chatRagDocMapper.selectList(
                    new LambdaQueryWrapper<ChatRagDoc>().eq(ChatRagDoc::getEnabled, 1));
            for (ChatRagDoc doc : docs) {
                knowledgeBaseService.onDocChanged(doc);
            }
            log.info("知识库向量迁移完成：共处理 {} 篇启用文档", docs.size());
        } catch (Exception e) {
            log.error("知识库向量迁移执行失败", e);
        }
    }
}
