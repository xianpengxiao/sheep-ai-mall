package com.xs.sheepaimall.feign;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.dto.SpuSaveDTO;
import com.xs.sheepaimall.entity.Category;
import com.xs.sheepaimall.entity.Sku;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.vo.SpuVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品服务内部 Feign 接口
 */
@FeignClient(name = "sheep-product", contextId = "productClient", path = "/internal/product")
public interface ProductFeignClient {

    @GetMapping("/sku/{id}")
    Sku getSkuById(@PathVariable Long id);

    @GetMapping("/sku/list-by-ids")
    List<Sku> listSkuByIds(@RequestParam List<Long> ids);

    @GetMapping("/spu/{id}")
    Spu getSpuById(@PathVariable Long id);

    @GetMapping("/spu/list-by-ids")
    List<Spu> listSpuByIds(@RequestParam List<Long> ids);

    @GetMapping("/spu/list-ids-by-merchant/{merchantId}")
    List<Long> listSpuIdsByMerchant(@PathVariable Long merchantId);

    @GetMapping("/spu/list-ids-by-keyword")
    List<Long> listSpuIdsByKeyword(@RequestParam String keyword);

    @GetMapping("/category/{id}")
    Category getCategoryById(@PathVariable Long id);

    @PostMapping("/stock/release")
    void releaseStock(@RequestBody com.xs.sheepaimall.dto.StockReleaseDTO dto);

    @PostMapping("/spu")
    SpuVO saveSpu(@RequestBody SpuSaveDTO dto);

    @PutMapping("/spu")
    SpuVO updateSpu(@RequestBody SpuSaveDTO dto);

    @GetMapping("/spu/detail/{id}")
    SpuVO getSpuDetail(@PathVariable Long id);

    @PutMapping("/spu/{id}/status")
    void updateSpuStatus(@PathVariable Long id, @RequestParam Integer status);

    @GetMapping("/spu/page-by-merchant")
    Page<Spu> pageSpuByMerchant(@RequestParam Long merchantId,
                                @RequestParam(defaultValue = "1") int pageNum,
                                @RequestParam(defaultValue = "10") int pageSize,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(required = false) Long categoryId,
                                @RequestParam(required = false) Integer status);

    @GetMapping("/sku/list-by-spu-ids")
    List<Sku> listSkuBySpuIds(@RequestParam List<Long> spuIds);

    @PostMapping("/sku/batch-save/{spuId}")
    void batchSaveSku(@PathVariable Long spuId, @RequestBody List<Sku> skuList);

    @GetMapping("/category/list-by-ids")
    List<Category> listCategoryByIds(@RequestParam List<Long> ids);
}
