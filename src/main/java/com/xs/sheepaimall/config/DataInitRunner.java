package com.xs.sheepaimall.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xs.sheepaimall.entity.SysUser;
import com.xs.sheepaimall.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 应用启动数据初始化：确保管理员账号存在且密码经 BCrypt 加密
 */
@Slf4j
@Component
public class DataInitRunner implements ApplicationRunner {

    /** 默认管理员账号 */
    private static final String ADMIN_USERNAME = "admin";
    /** 默认管理员密码（明文） */
    private static final String ADMIN_RAW_PASSWORD = "123456";

    @Resource
    private SysUserMapper sysUserMapper;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        // 检查管理员是否已存在
        SysUser admin = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, ADMIN_USERNAME));

        if (admin == null) {
            // 创建管理员
            admin = new SysUser();
            admin.setId(1L);
            admin.setUsername(ADMIN_USERNAME);
            admin.setPassword(ENCODER.encode(ADMIN_RAW_PASSWORD));
            admin.setRealName("超级管理员");
            admin.setStatus(1);
            admin.setRemark("系统初始化创建");
            sysUserMapper.insert(admin);
            log.info("管理员账号已创建: admin / {}", ADMIN_RAW_PASSWORD);
        } else {
            // 如果密码仍是明文或旧格式，更新为 BCrypt（兼容旧数据迁移）
            if (admin.getPassword() == null || !admin.getPassword().startsWith("$2a$")) {
                admin.setPassword(ENCODER.encode(ADMIN_RAW_PASSWORD));
                sysUserMapper.updateById(admin);
                log.info("管理员密码已升级为 BCrypt 加密格式");
            }
        }
    }
}
