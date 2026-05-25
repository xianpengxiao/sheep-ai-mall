package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.entity.Category;
import com.xs.sheepaimall.mapper.CategoryMapper;
import com.xs.sheepaimall.service.CategoryService;
import com.xs.sheepaimall.vo.CategoryVO;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Override
    public List<CategoryVO> getTree() {
        // 查询所有启用分类，按 sort_order 排序
        List<Category> all = this.list(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getStatus, 1)
                        .orderByAsc(Category::getSortOrder)
        );

        List<CategoryVO> voList = all.stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        // 构建树：parentId=0 的为根节点
        return voList.stream()
                .filter(vo -> vo.getParentId() == 0)
                .peek(root -> root.setChildren(buildChildren(root.getId(), voList)))
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryVO> listByParentId(Long parentId) {
        List<Category> list = this.list(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getParentId, parentId)
                        .eq(Category::getStatus, 1)
                        .orderByAsc(Category::getSortOrder)
        );
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public boolean saveOrUpdateCategory(Category category) {
        // 新增时自动分配 sort_order
        if (category.getId() == null) {
            long count = this.count(new LambdaQueryWrapper<Category>()
                    .eq(Category::getParentId, category.getParentId()));
            category.setSortOrder((int) count + 1);
        }
        return this.saveOrUpdate(category);
    }

    /** 递归构建子分类 */
    private List<CategoryVO> buildChildren(Long parentId, List<CategoryVO> all) {
        return all.stream()
                .filter(vo -> vo.getParentId().equals(parentId))
                .peek(child -> child.setChildren(buildChildren(child.getId(), all)))
                .collect(Collectors.toList());
    }

    /** Entity → VO */
    private CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        BeanUtil.copyProperties(category, vo);
        return vo;
    }
}
