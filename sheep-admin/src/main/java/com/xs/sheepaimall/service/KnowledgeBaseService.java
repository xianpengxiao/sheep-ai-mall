package com.xs.sheepaimall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.dto.KnowledgeDocSaveDTO;
import com.xs.sheepaimall.entity.ChatRagDoc;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * 向量知识库服务：文档嵌入入库、语义检索、文档变更向量同步、商家向量表管理。
 * <p>平台公共文档（merchantId=null）存平台向量库 vector_store；
 * 商家文档按 merchantId 路由到专属向量表 vector_store_m_&lt;merchantId&gt;。</p>
 */
public interface KnowledgeBaseService {

    /**
     * 按 merchantId 路由目标向量库：null 返回平台库，非 null 返回（必要时先建表并缓存）商家库。
     * 供后续 ChatServiceImpl 检索及本服务内部使用。
     */
    VectorStore getStore(Long merchantId);

    /**
     * 初始化商家专属向量表（幂等，建表语句带 IF NOT EXISTS），结构对齐 sheep-pgvector.sql。
     * 商家开通智能客服时由 MerchantAiService 调用。
     */
    void createMerchantVectorTable(Long merchantId);

    /** 文档嵌入入库：内容切分后写入归属向量库；返回 null=成功，否则为失败/未同步原因（对操作方可见） */
    String embedDoc(ChatRagDoc doc);

    /**
     * 语义检索，结果格式化为 "【标题】内容" 并包裹定界符，单块内容截断 500 字。
     * PG 不可用时返回空字符串，绝不把异常抛给对话流程；命中情况记录日志（归属/条数）。
     */
    String search(Long merchantId, String query, int topK);

    /** 文档变更同步：先按 docId 删除旧向量，enabled=1 且内容非空则重新嵌入（先删后插，天然幂等）；返回 null=成功，否则失败/未同步原因 */
    String onDocChanged(ChatRagDoc doc);

    /** 仅删除文档向量（按 metadata.docId 过滤，走正确的库路由） */
    void removeDoc(Long docId, Long merchantId);

    /** 分页查询文档；merchantId 非 null 时限定该商家文档，null 查全量（平台管理员） */
    Page<ChatRagDoc> pageDocs(int pageNum, int pageSize, String title, String category, Long merchantId);

    /** 新增文档并同步向量；归属规则：管理员可指定 merchantId（null=平台），商家强制归属当前商家 */
    ChatRagDoc saveDoc(KnowledgeDocSaveDTO dto);

    /**
     * 上传 Markdown 文件新增文档（设计 D6）：校验 .md 扩展名、UTF-8 读取并剥离 Markdown 排版符号，
     * 标题缺省取文件名（去扩展名）；归属与权限完全复用 saveDoc 管线
     */
    ChatRagDoc uploadMdDoc(org.springframework.web.multipart.MultipartFile file, String title,
                           String category, String keywords, Long merchantId);

    /** 修改文档并同步向量（不转移归属，仅管理员与归属商家可操作） */
    ChatRagDoc updateDoc(Long id, KnowledgeDocSaveDTO dto);

    /** 启用/禁用文档并同步向量；返回嵌入状态（null=成功，否则失败/未同步原因） */
    String changeEnabled(Long id, Integer enabled);

    /** 删除文档并清理其向量 */
    void deleteDoc(Long id);
}
