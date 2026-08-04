package com.xs.sheepaimall.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xs.sheepaimall.entity.SysUser;
import com.xs.sheepaimall.entity.SysUserRole;
import com.xs.sheepaimall.mapper.SysUserMapper;
import com.xs.sheepaimall.mapper.SysUserRoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 应用启动数据初始化：确保管理员和测试账号存在
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

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        createAdminIfNotExist();
        createUserIfNotExist(2L, MERCHANT1_USERNAME, "张三", "13912345678");
        createUserIfNotExist(3L, MERCHANT2_USERNAME, "李四", "13611112222");
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

    /**
     * 创建商家用户（仅用户+角色，商家记录由 sheep-merchant 的 DataInitRunner 创建）
     */
    private void createUserIfNotExist(Long userId, String username, String realName, String phone) {
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
    }
}
