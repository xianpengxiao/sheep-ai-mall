package com.xs.sheepaimall.common.base;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 通用分页响应 VO，统一前端分页结构
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> {

    @Schema(description = "当前页码")
    private long page;

    @Schema(description = "每页条数")
    private long size;

    @Schema(description = "总记录数")
    private long total;

    @Schema(description = "数据列表")
    private List<T> records;

    /** 空分页 */
    public static <T> PageVO<T> empty() {
        return new PageVO<>(1, 0, 0, Collections.emptyList());
    }

    /** 从 MyBatis-Plus Page 构建 */
    public static <T> PageVO<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> mpPage) {
        return new PageVO<>(mpPage.getCurrent(), mpPage.getSize(), mpPage.getTotal(), mpPage.getRecords());
    }
}
