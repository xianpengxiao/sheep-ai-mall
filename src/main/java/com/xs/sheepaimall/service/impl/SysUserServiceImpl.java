package com.xs.sheepaimall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.common.AccountStatusException;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.CacheConstants;
import com.xs.sheepaimall.dto.LoginDTO;
import com.xs.sheepaimall.dto.RegisterDTO;
import com.xs.sheepaimall.entity.SysRole;
import com.xs.sheepaimall.entity.SysUser;
import com.xs.sheepaimall.entity.SysUserRole;
import com.xs.sheepaimall.mapper.SysRoleMapper;
import com.xs.sheepaimall.mapper.SysRolePermissionMapper;
import com.xs.sheepaimall.mapper.SysUserMapper;
import com.xs.sheepaimall.mapper.SysUserRoleMapper;
import com.xs.sheepaimall.security.AuthInterceptor;
import com.xs.sheepaimall.security.JwtUtil;
import com.xs.sheepaimall.service.SysUserService;
import com.xs.sheepaimall.vo.LoginVO;
import com.xs.sheepaimall.util.SmsUtil;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 系统用户 Service 实现
 */
@Slf4j
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    /** 默认新注册用户分配的角色ID（3 = ROLE_VIEWER 只读用户） */
    private static final long DEFAULT_ROLE_ID = 3L;

    @Resource
    private SysUserRoleMapper sysUserRoleMapper;

    @Resource
    private SysRolePermissionMapper sysRolePermissionMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SmsUtil smsUtil;

    @Value("${sheep.jwt.expiration}")
    private long jwtExpiration;

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null) {
            log.warn("登录失败：用户不存在 username={}", dto.getUsername());
            throw new BizException("账号或密码错误");
        }

        if (user.getStatus() == 0) {
            throw new AccountStatusException("账号已被禁用，请联系管理员");
        }
        if (user.getStatus() == 2) {
            throw new AccountStatusException("账号已被锁定，请联系管理员");
        }

        if (!PASSWORD_ENCODER.matches(dto.getPassword(), user.getPassword())) {
            log.warn("登录失败：密码错误 username={}", dto.getUsername());
            throw new BizException("账号或密码错误");
        }

        List<String> roles = sysUserRoleMapper.selectRoleCodesByUserId(user.getId());
        roles = roles != null ? roles : Collections.emptyList();

        List<String> permissions = sysRolePermissionMapper.selectPermCodesByUserId(user.getId());
        permissions = permissions != null ? permissions : Collections.emptyList();

        log.info("用户 {} 登录成功，角色：{}，权限数：{}", dto.getUsername(), roles, permissions.size());

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), permissions, roles);

        user.setLastLogin(LocalDateTime.now());
        this.updateById(user);

        return LoginVO.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName() != null ? user.getRealName() : "")
                .avatar(user.getAvatar() != null ? user.getAvatar() : "")
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    @Override
    public void logout(String token) {
        try {
            Claims claims = jwtUtil.parseToken(token);
            Date expiration = claims.getExpiration();
            long remainingSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            if (remainingSeconds <= 0) {
                return;
            }
            String blacklistKey = AuthInterceptor.buildBlacklistKey(token);
            stringRedisTemplate.opsForValue().set(blacklistKey, "1", Duration.ofSeconds(remainingSeconds));
            log.info("Token 已加入黑名单，剩余有效时间: {} 秒", remainingSeconds);
        } catch (Exception e) {
            log.debug("退出登录时 Token 解析失败（可能已过期）: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser register(RegisterDTO dto) {
        // 校验手机验证码
        String verifiedKey = CacheConstants.SMS_VERIFIED_PREFIX + dto.getPhone();
        String verified = stringRedisTemplate.opsForValue().get(verifiedKey);
        if (!"1".equals(verified)) {
            throw new BizException("手机号未通过验证码验证");
        }

        SysUser existing = this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (existing != null) {
            throw new BizException("账号已存在：" + dto.getUsername());
        }

        // 清除已验证标记
        stringRedisTemplate.delete(verifiedKey);

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(PASSWORD_ENCODER.encode(dto.getPassword()));
        user.setRealName(dto.getRealName() != null ? dto.getRealName() : "");
        user.setPhone(dto.getPhone() != null ? dto.getPhone() : "");
        user.setEmail(dto.getEmail() != null ? dto.getEmail() : "");
        user.setAvatar("https://xxp-itcast.oss-cn-beijing.aliyuncs.com/sheepaimallicon.png");
        user.setStatus(1);
        this.save(user);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(DEFAULT_ROLE_ID);
        sysUserRoleMapper.insert(userRole);

        log.info("新用户注册成功：{}，分配默认角色ID={}", dto.getUsername(), DEFAULT_ROLE_ID);
        user.setPassword(null);
        return user;
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }

        // 校验旧密码
        if (!PASSWORD_ENCODER.matches(oldPassword, user.getPassword())) {
            throw new BizException("旧密码错误");
        }

        // 新旧密码不能相同
        if (PASSWORD_ENCODER.matches(newPassword, user.getPassword())) {
            throw new BizException("新密码不能与旧密码相同");
        }

        user.setPassword(PASSWORD_ENCODER.encode(newPassword));
        this.updateById(user);
        log.info("用户 {} 密码已更新", user.getUsername());
    }

    @Override
    public void updateAvatar(Long userId, String avatarUrl) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        user.setAvatar(avatarUrl);
        this.updateById(user);
        log.info("用户 {} 头像已更新", user.getUsername());
    }

    @Override
    public List<String> getUserRoleCodes(Long userId) {
        return sysUserRoleMapper.selectRoleCodesByUserId(userId);
    }

    @Override
    public Page<SysUser> listUsers(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        // 按关键字模糊搜索（用户名 / 真实姓名 / 手机号）
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getRealName, keyword)
                    .or()
                    .like(SysUser::getPhone, keyword)
            );
        }
        // 不返回密码字段
        wrapper.select(SysUser.class, info -> !"password".equals(info.getColumn()))
                .orderByDesc(SysUser::getCreateTime);

        Page<SysUser> page = this.page(new Page<>(pageNum, pageSize), wrapper);
        // 每个用户填充角色编码
        for (SysUser user : page.getRecords()) {
            user.setPassword(null);
        }
        return page;
    }

    @Override
    public List<Long> getUserRoleIds(Long userId) {
        return sysUserRoleMapper.selectRoleIdsByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }

        // 1. 删除用户所有现有角色
        sysUserRoleMapper.deleteByUserId(userId);

        // 2. 分配新角色（空列表 = 清空所有角色）
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                sysUserRoleMapper.insert(userRole);
            }
        }

        log.info("用户 {} (id={}) 角色已更新为: {}", user.getUsername(), userId, roleIds);
    }

    @Override
    public List<SysRole> listAllRoles() {
        return sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getStatus, 1)
                        .orderByAsc(SysRole::getSortOrder)
        );
    }

    @Override
    public boolean checkPhoneExists(String phone) {
        return this.lambdaQuery().eq(SysUser::getPhone, phone).count() > 0;
    }

    @Override
    public void sendVerifyCode(String phone) {
        // 校验手机号格式
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException("手机号格式不正确");
        }
        // 校验是否已注册
        if (checkPhoneExists(phone)) {
            throw new BizException("该手机号已注册");
        }
        // 60秒内不能重复发送
        String limitKey = CacheConstants.SMS_LIMIT_PREFIX + phone;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(limitKey))) {
            throw new BizException("验证码已发送，请60秒后重试");
        }
        // 生成6位数字验证码
        String code = String.format("%06d", (int) (Math.random() * 1000000));
        // 存入Redis，有效期5分钟
        String codeKey = CacheConstants.SMS_CODE_PREFIX + phone;
        stringRedisTemplate.opsForValue().set(codeKey, code, java.time.Duration.ofSeconds(CacheConstants.SMS_CODE_TTL));
        // 发送限制标记，60秒过期
        stringRedisTemplate.opsForValue().set(limitKey, "1", java.time.Duration.ofSeconds(60));

        smsUtil.sendCode(phone, code);
    }

    @Override
    public boolean verifyCode(String phone, String code) {
        if (phone == null || code == null) return false;
        String codeKey = CacheConstants.SMS_CODE_PREFIX + phone;
        String saved = stringRedisTemplate.opsForValue().get(codeKey);
        if (saved == null) return false;
        boolean valid = saved.equals(code);
        if (valid) {
            // 验证成功后删除验证码，标记该手机号已验证
            stringRedisTemplate.delete(codeKey);
            String verifiedKey = CacheConstants.SMS_VERIFIED_PREFIX + phone;
            stringRedisTemplate.opsForValue().set(verifiedKey, "1", java.time.Duration.ofMinutes(10));
        }
        return valid;
    }

    /** 使用 BCrypt 加密明文密码（供初始化 / 新增用户使用） */
    public static String encodePassword(String rawPassword) {
        return PASSWORD_ENCODER.encode(rawPassword);
    }
}
