package com.xs.sheepaimall.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xs.sheepaimall.entity.Merchant;
import com.xs.sheepaimall.entity.MerchantApply;
import com.xs.sheepaimall.entity.SysUser;
import com.xs.sheepaimall.entity.SysUserRole;
import com.xs.sheepaimall.mapper.MerchantApplyMapper;
import com.xs.sheepaimall.mapper.MerchantMapper;
import com.xs.sheepaimall.mapper.SysUserMapper;
import com.xs.sheepaimall.mapper.SysUserRoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 应用启动数据初始化：确保管理员和商家测试账号存在
 */
@Slf4j
@Component
public class DataInitRunner implements ApplicationRunner {

    private static final String ADMIN_USERNAME = "admin";
    private static final String MERCHANT1_USERNAME = "zhangsan";
    private static final String MERCHANT2_USERNAME = "lisi";
    private static final String RAW_PASSWORD = "123456";

    /** 角色ID：ROLE_VIEWER=3, ROLE_MERCHANT=4（来自 schema-rbac.sql） */
    private static final long ROLE_VIEWER_ID = 3L;
    private static final long ROLE_MERCHANT_ID = 4L;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private MerchantApplyMapper merchantApplyMapper;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        createAdminIfNotExist();
        createMerchantIfNotExist(MERCHANT1_USERNAME, 2L, "张三",
                "张三数码店", "手机数码、电脑办公、家用电器", "13912345678");
        createMerchantIfNotExist(MERCHANT2_USERNAME, 3L, "李四",
                "李四优选", "服装鞋帽、食品饮料", "13611112222");
    }

    private void createAdminIfNotExist() {
        SysUser admin = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, ADMIN_USERNAME));
        if (admin == null) {
            admin = new SysUser();
            admin.setId(1L);
            admin.setUsername(ADMIN_USERNAME);
            admin.setPassword(ENCODER.encode(RAW_PASSWORD));
            admin.setRealName("超级管理员");
            admin.setAvatar("https://xxp-itcast.oss-cn-beijing.aliyuncs.com/sheepaimallicon.png");
            admin.setStatus(1);
            admin.setRemark("系统初始化创建");
            sysUserMapper.insert(admin);
            log.info("管理员账号已创建: admin / {}", RAW_PASSWORD);
        } else if (admin.getPassword() == null || !admin.getPassword().startsWith("$2a$")) {
            admin.setPassword(ENCODER.encode(RAW_PASSWORD));
            sysUserMapper.updateById(admin);
            log.info("管理员密码已升级为 BCrypt 加密格式");
        }
    }

    private void createMerchantIfNotExist(String username, Long userId, String realName,
                                          String shopName, String businessScope, String phone) {
        // 1. 创建用户
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            user = new SysUser();
            user.setId(userId);
            user.setUsername(username);
            user.setPassword(ENCODER.encode(RAW_PASSWORD));
            user.setRealName(realName);
            user.setPhone(phone);
            user.setAvatar("https://xxp-itcast.oss-cn-beijing.aliyuncs.com/sheepaimallicon.png");
            user.setStatus(1);
            sysUserMapper.insert(user);
            log.info("商家账号已创建: {} / {}", username, RAW_PASSWORD);
        } else if (user.getPassword() == null || !user.getPassword().startsWith("$2a$")) {
            user.setPassword(ENCODER.encode(RAW_PASSWORD));
            sysUserMapper.updateById(user);
        }

        // 2. 分配角色（ROLE_VIEWER + ROLE_MERCHANT）
        Long viewerExist = sysUserRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(SysUserRole::getRoleId, ROLE_VIEWER_ID));
        if (viewerExist == null || viewerExist == 0) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(ROLE_VIEWER_ID);
            sysUserRoleMapper.insert(ur);
        }

        Long merchantRoleExist = sysUserRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(SysUserRole::getRoleId, ROLE_MERCHANT_ID));
        if (merchantRoleExist == null || merchantRoleExist == 0) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(ROLE_MERCHANT_ID);
            sysUserRoleMapper.insert(ur);
        }

        // 3. 创建商家记录（使用固定ID，与 mock 数据一致）
        Merchant merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, userId));
        if (merchant == null) {
            long merchantId = userId == 2L ? 1001L : 1002L;
            merchant = new Merchant();
            merchant.setId(merchantId);
            merchant.setUserId(userId);
            merchant.setShopName(shopName);
            merchant.setBusinessScope(businessScope);
            merchant.setContactName(realName);
            merchant.setContactPhone(phone);
            merchant.setStatus(1);
            merchant.setAuditTime(LocalDateTime.now());
            merchantMapper.insert(merchant);
            log.info("商家记录已创建: {}, shop={}, merchantId={}", username, shopName, merchantId);
        }

        // 4. 创建入驻申请记录（已审核通过）
        MerchantApply apply = merchantApplyMapper.selectOne(
                new LambdaQueryWrapper<MerchantApply>()
                        .eq(MerchantApply::getUserId, userId));
        if (apply == null) {
            apply = new MerchantApply();
            apply.setUserId(userId);
            apply.setShopName(shopName);
            apply.setBusinessScope(businessScope);
            apply.setContactName(realName);
            apply.setContactPhone(phone);
            apply.setStatus(1);
            apply.setAuditUserId(1L);
            apply.setAuditTime(LocalDateTime.now());
            merchantApplyMapper.insert(apply);
        }
    }
}
