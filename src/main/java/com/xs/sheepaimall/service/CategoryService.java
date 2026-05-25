package com.xs.sheepaimall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xs.sheepaimall.entity.Category;
import com.xs.sheepaimall.vo.CategoryVO;

import java.util.List;

/**
 * 商品分类 Service
 */
public interface CategoryService extends IService<Category> {

    /** 获取分类树 */
    List<CategoryVO> getTree();

    /** 根据父ID查询子分类列表 */
    List<CategoryVO> listByParentId(Long parentId);

    /** 新增或更新分类，自动处理sort_order */
    boolean saveOrUpdateCategory(Category category);
}
