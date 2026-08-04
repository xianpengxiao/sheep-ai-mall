package com.xs.sheepaimall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.entity.Merchant;
import com.xs.sheepaimall.entity.MerchantDsr;
import com.xs.sheepaimall.mapper.MerchantDsrMapper;
import com.xs.sheepaimall.mapper.MerchantMapper;
import com.xs.sheepaimall.service.MerchantDsrService;
import com.xs.sheepaimall.vo.MerchantDsrVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Service
public class MerchantDsrServiceImpl extends ServiceImpl<MerchantDsrMapper, MerchantDsr> implements MerchantDsrService {

    @Autowired
    private MerchantDsrMapper merchantDsrMapper;

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public MerchantDsrVO getLatestDsr(Long merchantId) {
        MerchantDsr dsr = merchantDsrMapper.selectOne(
                new LambdaQueryWrapper<MerchantDsr>()
                        .eq(MerchantDsr::getMerchantId, merchantId)
                        .orderByDesc(MerchantDsr::getStatDate)
                        .last("LIMIT 1"));
        if (dsr == null) return emptyDsrVO();
        return toDsrVO(dsr);
    }

    @Override
    public MerchantDsrVO getTrendDsr(Long merchantId) {
        MerchantDsrVO vo = getLatestDsr(merchantId);
        // 近30天趋势
        List<MerchantDsr> list = merchantDsrMapper.selectList(
                new LambdaQueryWrapper<MerchantDsr>()
                        .eq(MerchantDsr::getMerchantId, merchantId)
                        .ge(MerchantDsr::getStatDate, LocalDate.now().minusDays(30))
                        .orderByAsc(MerchantDsr::getStatDate));
        List<MerchantDsrVO.DsrTrendItem> trend = list.stream()
                .map(d -> MerchantDsrVO.DsrTrendItem.builder()
                        .statDate(d.getStatDate().toString())
                        .describeScore(d.getDescribeScore())
                        .serviceScore(d.getServiceScore())
                        .logisticsScore(d.getLogisticsScore())
                        .totalCount(d.getTotalCount())
                        .monthCount(d.getMonthCount())
                        .build())
                .collect(Collectors.toList());
        vo.setTrend(trend);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dailyCalculateAll() {
        // 分页遍历所有商家
        int page = 1;
        int size = 100;
        long total;
        do {
            Page<Merchant> merchantPage = merchantMapper.selectPage(
                    new Page<>(page, size),
                    new LambdaQueryWrapper<Merchant>().eq(Merchant::getStatus, 1));
            total = merchantPage.getTotal();
            for (Merchant merchant : merchantPage.getRecords()) {
                try {
                    recalculateMerchant(merchant.getId());
                } catch (Exception e) {
                    log.error("DSR计算失败 merchantId={}", merchant.getId(), e);
                }
            }
            page++;
        } while (page * size < total);
        log.info("DSR每日计算完成");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalculateMerchant(Long merchantId) {
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(90);

        // 用原生SQL计算DSR，处理刷单限制（同买家同商家每月前3条）
        String sql = """
                SELECT COALESCE(ROUND(AVG(pr.describe_score), 2), 0),
                       COALESCE(ROUND(AVG(pr.service_score), 2), 0),
                       COALESCE(ROUND(AVG(pr.logistics_score), 2), 0),
                       COUNT(*),
                       SUM(CASE WHEN pr.create_time >= DATE_FORMAT(NOW(), '%Y-%m-01') THEN 1 ELSE 0 END),
                       SUM(CASE WHEN pr.describe_score >= 4 THEN 1 ELSE 0 END),
                       SUM(CASE WHEN pr.service_score >= 4 THEN 1 ELSE 0 END),
                       SUM(CASE WHEN pr.logistics_score >= 4 THEN 1 ELSE 0 END)
                FROM (
                    SELECT pr.*,
                           ROW_NUMBER() OVER (
                               PARTITION BY pr.user_id, s.merchant_id, DATE_FORMAT(pr.create_time, '%Y-%m')
                               ORDER BY pr.create_time
                           ) AS rn
                    FROM product_review pr
                    JOIN spu s ON pr.spu_id = s.id
                    WHERE s.merchant_id = ?
                      AND pr.review_status = 1
                      AND pr.status = 1
                      AND pr.deleted = 0
                      AND pr.create_time >= ?
                      AND pr.order_id NOT IN (SELECT id FROM order_info WHERE status = 4)
                ) t
                WHERE t.rn <= 3
                """;

        List<Object[]> rows = jdbcTemplate.query(sql,
                ps -> {
                    ps.setLong(1, merchantId);
                    ps.setString(2, windowStart.toString());
                },
                (rs, rowNum) -> new Object[]{
                        rs.getBigDecimal(1),
                        rs.getBigDecimal(2),
                        rs.getBigDecimal(3),
                        rs.getInt(4),
                        rs.getInt(5),
                        rs.getInt(6),
                        rs.getInt(7),
                        rs.getInt(8)
                });

        if (rows.isEmpty() || rows.get(0)[0] == null) {
            // 无评价数据，删除旧记录或跳过
            return;
        }

        Object[] row = rows.get(0);
        BigDecimal descScore = (BigDecimal) row[0];
        BigDecimal servScore = (BigDecimal) row[1];
        BigDecimal logiScore = (BigDecimal) row[2];
        int totalCount = (int) row[3];
        int monthCount = (int) row[4];
        int descHigh = (int) row[5];
        int servHigh = (int) row[6];
        int logiHigh = (int) row[7];

        // 写入今日快照
        MerchantDsr dsr = merchantDsrMapper.selectOne(
                new LambdaQueryWrapper<MerchantDsr>()
                        .eq(MerchantDsr::getMerchantId, merchantId)
                        .eq(MerchantDsr::getStatDate, today));
        if (dsr == null) {
            dsr = new MerchantDsr();
            dsr.setMerchantId(merchantId);
            dsr.setStatDate(today);
        }
        dsr.setDescribeScore(descScore);
        dsr.setServiceScore(servScore);
        dsr.setLogisticsScore(logiScore);
        dsr.setTotalCount(totalCount);
        dsr.setMonthCount(monthCount);
        dsr.setDescribeHigh(descHigh);
        dsr.setServiceHigh(servHigh);
        dsr.setLogisticsHigh(logiHigh);

        if (dsr.getId() == null) {
            merchantDsrMapper.insert(dsr);
        } else {
            merchantDsrMapper.updateById(dsr);
        }
    }

    // ==================== 内部方法 ====================

    private MerchantDsrVO toDsrVO(MerchantDsr dsr) {
        return MerchantDsrVO.builder()
                .describeScore(dsr.getDescribeScore())
                .serviceScore(dsr.getServiceScore())
                .logisticsScore(dsr.getLogisticsScore())
                .totalCount(dsr.getTotalCount())
                .monthCount(dsr.getMonthCount())
                .describeHighRate(dsr.getTotalCount() > 0
                        ? BigDecimal.valueOf(dsr.getDescribeHigh())
                        .divide(BigDecimal.valueOf(dsr.getTotalCount()), 4, RoundingMode.HALF_UP)
                        .doubleValue() : 0.0)
                .serviceHighRate(dsr.getTotalCount() > 0
                        ? BigDecimal.valueOf(dsr.getServiceHigh())
                        .divide(BigDecimal.valueOf(dsr.getTotalCount()), 4, RoundingMode.HALF_UP)
                        .doubleValue() : 0.0)
                .logisticsHighRate(dsr.getTotalCount() > 0
                        ? BigDecimal.valueOf(dsr.getLogisticsHigh())
                        .divide(BigDecimal.valueOf(dsr.getTotalCount()), 4, RoundingMode.HALF_UP)
                        .doubleValue() : 0.0)
                .build();
    }

    private MerchantDsrVO emptyDsrVO() {
        return MerchantDsrVO.builder()
                .describeScore(BigDecimal.ZERO)
                .serviceScore(BigDecimal.ZERO)
                .logisticsScore(BigDecimal.ZERO)
                .totalCount(0)
                .monthCount(0)
                .describeHighRate(0.0)
                .serviceHighRate(0.0)
                .logisticsHighRate(0.0)
                .build();
    }
}
