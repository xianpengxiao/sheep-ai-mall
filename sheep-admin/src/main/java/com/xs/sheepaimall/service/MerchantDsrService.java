package com.xs.sheepaimall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xs.sheepaimall.entity.MerchantDsr;
import com.xs.sheepaimall.vo.MerchantDsrVO;

public interface MerchantDsrService extends IService<MerchantDsr> {

    /** 查询店铺最新DSR评分 */
    MerchantDsrVO getLatestDsr(Long merchantId);

    /** 查询近30天DSR趋势（商家后台） */
    MerchantDsrVO getTrendDsr(Long merchantId);

    /** 每日滚动计算所有商家DSR */
    void dailyCalculateAll();

    /** 批量更新DSR缓存快照（定时任务用） */
    void recalculateMerchant(Long merchantId);
}
