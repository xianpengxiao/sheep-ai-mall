package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.common.CacheConstants;
import com.xs.sheepaimall.common.CacheHelper;
import com.xs.sheepaimall.entity.Category;
import com.xs.sheepaimall.mapper.CategoryMapper;
import com.xs.sheepaimall.service.CategoryService;
import com.xs.sheepaimall.vo.CategoryVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Resource
    private CacheHelper cacheHelper;

    @Override
    public List<CategoryVO> getTree() {
        String cachedJson = cacheHelper.getOrFetch(CacheConstants.CATEGORY_TREE,
                () -> {
                    List<CategoryVO> tree = buildTreeFromDb();
                    // 空树也缓存（防穿透由 CacheHelper 内部处理）
                    return tree.isEmpty() ? null : JSONUtil.toJsonStr(tree);
                },
                CacheConstants.CATEGORY_TREE_TTL);

        if (cachedJson == null) return List.of();
        return JSONUtil.toList(cachedJson, CategoryVO.class);
    }

    /** 从数据库构建分类树 */
    private List<CategoryVO> buildTreeFromDb() {
        List<Category> all = this.list(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getStatus, 1)
                        .orderByAsc(Category::getSortOrder));

        List<CategoryVO> voList = all.stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return voList.stream()
                .filter(vo -> vo.getParentId() == 0)
                .peek(root -> root.setChildren(buildChildren(root.getId(), voList)))
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryVO> listByParentId(Long parentId) {
        // 从缓存的完整分类树中查找，避免重复查库
        List<CategoryVO> tree = getTree();
        if (parentId == 0) {
            return tree;
        }
        // 递归在树中查找匹配的父节点
        return findChildrenInTree(tree, parentId);
    }

    /** 在分类树中递归查找指定父节点下的子节点列表 */
    private List<CategoryVO> findChildrenInTree(List<CategoryVO> nodes, Long targetParentId) {
        for (CategoryVO node : nodes) {
            if (node.getId().equals(targetParentId)) {
                return node.getChildren() != null ? node.getChildren() : List.of();
            }
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                List<CategoryVO> found = findChildrenInTree(node.getChildren(), targetParentId);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return List.of();
    }

    @Override
    public boolean saveOrUpdateCategory(Category category) {
        if (category.getId() == null) {
            long count = this.count(new LambdaQueryWrapper<Category>()
                    .eq(Category::getParentId, category.getParentId()));
            category.setSortOrder((int) count + 1);
        }
        boolean ok = this.saveOrUpdate(category);
        cacheHelper.evictCategoryTree();
        return ok;
    }

    /** 重写更新，清除分类树缓存 */
    @Override
    public boolean updateById(Category category) {
        boolean ok = super.updateById(category);
        if (ok) {
            cacheHelper.evictCategoryTree();
        }
        return ok;
    }

    /** 重写逻辑删除，清除分类树缓存 */
    @Override
    public boolean removeById(Long id) {
        boolean ok = super.removeById(id);
        if (ok) {
            cacheHelper.evictCategoryTree();
        }
        return ok;
    }

    /** 递归构建子分类 */
    private List<CategoryVO> buildChildren(Long parentId, List<CategoryVO> all) {
        return all.stream()
                .filter(vo -> vo.getParentId().equals(parentId))
                .peek(child -> child.setChildren(buildChildren(child.getId(), all)))
                .collect(Collectors.toList());
    }

    private CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        BeanUtil.copyProperties(category, vo);
        return vo;
    }
}
