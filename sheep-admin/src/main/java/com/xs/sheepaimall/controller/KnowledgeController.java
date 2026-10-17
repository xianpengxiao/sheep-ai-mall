package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.KnowledgeDocSaveDTO;
import com.xs.sheepaimall.entity.ChatRagDoc;
import com.xs.sheepaimall.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 知识库管理接口 — 平台管理员可管理全量文档，商家只能管理归属自己的文档。
 * <p>归属与权限判断在 KnowledgeBaseService 内完成（UserContext 角色 + Merchant.userId 关联）。</p>
 */
@Tag(name = "知识库管理", description = "RAG 知识库文档：分页查询、新增、修改、启停、删除（写操作自动同步向量）")
@Validated
@RestController
@RequestMapping("/api/ai/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Operation(summary = "文档分页列表", description = "管理员可查全量（merchantId 可选过滤），商家仅能看到自己的文档")
    @GetMapping("/page")
    public R<Page<ChatRagDoc>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "标题模糊查询") @RequestParam(required = false) String title,
            @Parameter(description = "分类模糊查询") @RequestParam(required = false) String category,
            @Parameter(description = "归属商家ID过滤，仅管理员生效；缺省查全量") @RequestParam(required = false) Long merchantId) {
        return R.ok(knowledgeBaseService.pageDocs(pageNum, pageSize, title, category, merchantId));
    }

    @Operation(summary = "新增文档", description = "管理员可指定归属商家（缺省为平台公共文档），商家自动归属当前店铺；新增后自动嵌入向量")
    @PostMapping
    public R<ChatRagDoc> add(@Valid @RequestBody KnowledgeDocSaveDTO dto) {
        return R.ok(knowledgeBaseService.saveDoc(dto));
    }

    @Operation(summary = "上传文档（md）", description = "上传 .md 文件新增知识文档：服务端读取文本并剥离 Markdown 排版符号后入库，标题缺省取文件名；归属规则同新增文档")
    @PostMapping("/upload")
    public R<ChatRagDoc> upload(
            @Parameter(description = "Markdown 文件") @RequestParam MultipartFile file,
            @Parameter(description = "标题（缺省=文件名去扩展名）") @RequestParam(required = false) String title,
            @Parameter(description = "分类：faq/product/policy") @RequestParam(required = false) String category,
            @Parameter(description = "关键词，逗号分隔") @RequestParam(required = false) String keywords,
            @Parameter(description = "归属商家ID，仅管理员生效") @RequestParam(required = false) Long merchantId) {
        return R.ok(knowledgeBaseService.uploadMdDoc(file, title, category, keywords, merchantId));
    }

    @Operation(summary = "修改文档", description = "修改后自动同步向量；不转移文档归属")
    @PutMapping("/{id}")
    public R<ChatRagDoc> update(
            @Parameter(description = "文档ID") @PathVariable Long id,
            @Valid @RequestBody KnowledgeDocSaveDTO dto) {
        return R.ok(knowledgeBaseService.updateDoc(id, dto));
    }

    @Operation(summary = "启用/禁用文档", description = "禁用即删除其向量，启用则重新嵌入；返回嵌入状态（null=成功）")
    @PutMapping("/{id}/enabled")
    public R<String> changeEnabled(
            @Parameter(description = "文档ID") @PathVariable Long id,
            @Parameter(description = "启用状态：0=禁用 1=启用") @RequestParam Integer enabled) {
        return R.ok(knowledgeBaseService.changeEnabled(id, enabled));
    }

    @Operation(summary = "删除文档", description = "删除文档并清理其向量")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "文档ID") @PathVariable Long id) {
        knowledgeBaseService.deleteDoc(id);
        return R.ok();
    }
}
