package com.xs.sheepaimall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.common.AccountStatusException;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.CacheConstants;
import com.xs.sheepaimall.dto.*;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.util.DesensitizeUtil;
import com.xs.sheepaimall.util.EmailUtil;
import com.xs.sheepaimall.util.IdCardVerifyUtil;
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
import com.xs.sheepaimall.vo.SecurityProfileVO;
import com.xs.sheepaimall.vo.UserProfileVO;
import com.xs.sheepaimall.util.SensitiveWordUtil;
import com.xs.sheepaimall.util.SmsUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 系统用户 Service 实现
 */
@Slf4j
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    /** 默认新注册用户分配的角色ID（3 = ROLE_VIEWER 只读用户） */
    private static final long DEFAULT_ROLE_ID = 3L;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private SysRolePermissionMapper sysRolePermissionMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SmsUtil smsUtil;

    @Autowired
    private EmailUtil emailUtil;

    @Autowired
    private IdCardVerifyUtil idCardVerifyUtil;

    @Autowired
    private SensitiveWordUtil sensitiveWordUtil;

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
                .realName(DesensitizeUtil.name(user.getRealName()))
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
        // 校验验证码：手机注册走短信验证码，邮箱注册走邮箱验证码
        if (StrUtil.isNotBlank(dto.getPhone())) {
            String verifiedKey = CacheConstants.SMS_VERIFIED_PREFIX + dto.getPhone();
            String verified = stringRedisTemplate.opsForValue().get(verifiedKey);
            if (!"1".equals(verified)) {
                throw new BizException("手机号未通过验证码验证");
            }
            stringRedisTemplate.delete(verifiedKey);
        } else if (StrUtil.isNotBlank(dto.getEmail())) {
            String verifiedKey = CacheConstants.EMAIL_VERIFIED_PREFIX + dto.getEmail();
            String verified = stringRedisTemplate.opsForValue().get(verifiedKey);
            if (!"1".equals(verified)) {
                throw new BizException("邮箱未通过验证码验证");
            }
            stringRedisTemplate.delete(verifiedKey);
        } else {
            throw new BizException("手机号或邮箱至少填一项，且需通过验证码验证");
        }

        SysUser existing = this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (existing != null) {
            throw new BizException("账号已存在：" + dto.getUsername());
        }

        // 校验邮箱唯一性
        if (StrUtil.isNotBlank(dto.getEmail())) {
            SysUser emailUser = this.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmail, dto.getEmail()));
            if (emailUser != null) {
                throw new BizException("该邮箱已被其他账号绑定");
            }
        }

        // 校验手机号唯一性
        if (StrUtil.isNotBlank(dto.getPhone())) {
            SysUser phoneUser = this.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, dto.getPhone()));
            if (phoneUser != null) {
                throw new BizException("该手机号已被其他账号绑定");
            }
        }

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
    public void updateStatus(Long userId, Integer status) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (status != 0 && status != 1) {
            throw new BizException("状态值不正确：0=禁用 1=正常");
        }
        // 不能操作自己
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(userId)) {
            throw new BizException("不能操作自己的账号");
        }
        // 权限层级校验：操作者不能封禁权限高于自己的账号
        Integer currentSort = sysUserRoleMapper.selectMinSortOrderByUserId(currentUserId);
        Integer targetSort = sysUserRoleMapper.selectMinSortOrderByUserId(userId);
        if (currentSort != null && targetSort != null && targetSort < currentSort) {
            throw new BizException("无权操作权限高于自己的账号");
        }
        user.setStatus(status);
        this.updateById(user);
        log.info("管理员操作：用户 {} 状态变更为 {}", userId, status);
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

        // 不能操作自己
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(userId)) {
            throw new BizException("不能修改自己的角色");
        }

        // 权限层级校验：不能分配权限高于自己的角色
        if (roleIds != null && !roleIds.isEmpty()) {
            Integer currentSort = sysUserRoleMapper.selectMinSortOrderByUserId(currentUserId);
            // 查询要分配的角色 sortOrder 列表
            List<Integer> targetSorts = sysRoleMapper.selectSortOrdersByRoleIds(roleIds);
            if (currentSort != null && targetSorts != null) {
                for (Integer ts : targetSorts) {
                    if (ts != null && ts < currentSort) {
                        throw new BizException("不能分配权限高于自己的角色");
                    }
                }
            }
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
    public void sendLoginCode(String phone) {
        // 校验手机号格式
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException("手机号格式不正确");
        }
        // 校验手机号必须已注册
        if (!checkPhoneExists(phone)) {
            throw new BizException("该手机号未注册，请先注册");
        }
        // 60秒内不能重复发送
        String limitKey = CacheConstants.SMS_LIMIT_PREFIX + phone;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(limitKey))) {
            throw new BizException("验证码已发送，请60秒后重试");
        }
        String code = String.format("%06d", (int) (Math.random() * 1000000));
        String codeKey = CacheConstants.SMS_CODE_PREFIX + phone;
        stringRedisTemplate.opsForValue().set(codeKey, code, java.time.Duration.ofSeconds(CacheConstants.SMS_CODE_TTL));
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

    @Override
    public LoginVO smsLogin(String phone, String code) {
        // 校验验证码
        String codeKey = CacheConstants.SMS_CODE_PREFIX + phone;
        String saved = stringRedisTemplate.opsForValue().get(codeKey);
        if (saved == null || !saved.equals(code)) {
            throw new BizException("验证码错误或已过期");
        }
        // 验证通过，删除验证码
        stringRedisTemplate.delete(codeKey);

        // 查找用户
        SysUser user = this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone));
        if (user == null) {
            throw new BizException("该手机号未注册，请先注册");
        }
        if (user.getStatus() == 0) {
            throw new AccountStatusException("账号已被禁用，请联系管理员");
        }
        if (user.getStatus() == 2) {
            throw new AccountStatusException("账号已被锁定，请联系管理员");
        }

        // 获取角色和权限
        List<String> roles = sysUserRoleMapper.selectRoleCodesByUserId(user.getId());
        roles = roles != null ? roles : Collections.emptyList();
        List<String> permissions = sysRolePermissionMapper.selectPermCodesByUserId(user.getId());
        permissions = permissions != null ? permissions : Collections.emptyList();

        log.info("用户 {} 短信验证码登录成功，角色：{}", user.getUsername(), roles);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), permissions, roles);

        user.setLastLogin(LocalDateTime.now());
        this.updateById(user);

        return LoginVO.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .userId(user.getId())
                .username(user.getUsername())
                .realName(DesensitizeUtil.name(user.getRealName()))
                .avatar(user.getAvatar() != null ? user.getAvatar() : "")
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    /** 使用 BCrypt 加密明文密码（供初始化 / 新增用户使用） */
    public static String encodePassword(String rawPassword) {
        return PASSWORD_ENCODER.encode(rawPassword);
    }

    @Override
    public SecurityProfileVO getSecurityProfile(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        boolean hasRealName = user.getIdCard() != null && !user.getIdCard().isBlank();
        boolean hasPhone = user.getPhone() != null && !user.getPhone().isBlank();
        boolean hasEmail = user.getEmail() != null && !user.getEmail().isBlank();

        return SecurityProfileVO.builder()
                .realNameAuth(hasRealName)
                .realName(hasRealName ? DesensitizeUtil.name(user.getRealName()) : null)
                .idCard(hasRealName ? DesensitizeUtil.idCard(user.getIdCard()) : null)
                .phone(hasPhone ? DesensitizeUtil.phone(user.getPhone()) : null)
                .email(hasEmail ? DesensitizeUtil.email(user.getEmail()) : null)
                .phoneBound(hasPhone)
                .emailBound(hasEmail)
                .profileComplete(hasRealName && hasPhone && hasEmail)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitRealName(Long userId, RealNameAuthDTO dto) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (user.getIdCard() != null && !user.getIdCard().isBlank()) {
            throw new BizException("已实名认证，不可重复提交");
        }

        // 调用公安接口校验姓名+身份证是否匹配（API为空时跳过）
        if (!idCardVerifyUtil.verify(dto.getRealName(), dto.getIdCard())) {
            throw new BizException("姓名与身份证号不匹配");
        }

        user.setRealName(dto.getRealName());
        user.setIdCard(dto.getIdCard());

        checkAndUpdatePerfect(user);
        this.updateById(user);
        log.info("用户 {} 实名认证成功", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindPhone(Long userId, BindPhoneDTO dto) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }

        // 已绑定手机号 → 校验原手机验证码（验证本人操作）
        boolean hasOldPhone = user.getPhone() != null && !user.getPhone().isBlank();
        if (hasOldPhone) {
            if (dto.getOldCode() == null || dto.getOldCode().isBlank()) {
                throw new BizException("原手机号验证码不能为空");
            }
            String oldCodeKey = CacheConstants.SMS_CODE_PREFIX + user.getPhone();
            String oldSaved = stringRedisTemplate.opsForValue().get(oldCodeKey);
            if (oldSaved == null || !oldSaved.equals(dto.getOldCode())) {
                throw new BizException("原手机号验证码错误或已过期");
            }
            stringRedisTemplate.delete(oldCodeKey);
        }

        // 校验新手机号验证码
        String codeKey = CacheConstants.SMS_CODE_PREFIX + dto.getPhone();
        String saved = stringRedisTemplate.opsForValue().get(codeKey);
        if (saved == null || !saved.equals(dto.getCode())) {
            throw new BizException("新手机号验证码错误或已过期");
        }
        stringRedisTemplate.delete(codeKey);

        // 新手机号唯一性校验
        SysUser exists = this.lambdaQuery().eq(SysUser::getPhone, dto.getPhone()).one();
        if (exists != null && !exists.getId().equals(userId)) {
            throw new BizException("该手机号已被其他账号绑定");
        }

        user.setPhone(dto.getPhone());
        checkAndUpdatePerfect(user);
        this.updateById(user);
        log.info("用户 {} 绑定手机号成功", userId);
    }

    @Override
    public void sendOldPhoneCode(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null || user.getPhone() == null || user.getPhone().isBlank()) {
            throw new BizException("当前账号未绑定手机号");
        }
        // 复用登录验证码发送逻辑（手机号必须已注册）
        sendLoginCode(user.getPhone());
    }

    @Override
    public void sendOldEmailCode(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BizException("当前账号未绑定邮箱");
        }
        // 向已绑定的邮箱发送验证码
        sendEmailCode(user.getEmail());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindEmail(Long userId, BindEmailDTO dto) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }

        // 已绑定邮箱 → 校验原邮箱验证码（验证本人操作）
        boolean hasOldEmail = user.getEmail() != null && !user.getEmail().isBlank();
        if (hasOldEmail) {
            if (dto.getOldCode() == null || dto.getOldCode().isBlank()) {
                throw new BizException("原邮箱验证码不能为空");
            }
            String oldCodeKey = CacheConstants.EMAIL_CODE_PREFIX + user.getEmail();
            String oldSaved = stringRedisTemplate.opsForValue().get(oldCodeKey);
            if (oldSaved == null || !oldSaved.equals(dto.getOldCode())) {
                throw new BizException("原邮箱验证码错误或已过期");
            }
            stringRedisTemplate.delete(oldCodeKey);
        }

        // 校验新邮箱验证码
        String codeKey = CacheConstants.EMAIL_CODE_PREFIX + dto.getEmail();
        String saved = stringRedisTemplate.opsForValue().get(codeKey);
        if (saved == null || !saved.equals(dto.getCode())) {
            throw new BizException("新邮箱验证码错误或已过期");
        }
        stringRedisTemplate.delete(codeKey);

        // 新邮箱唯一性校验
        SysUser exists = this.lambdaQuery().eq(SysUser::getEmail, dto.getEmail()).one();
        if (exists != null && !exists.getId().equals(userId)) {
            throw new BizException("该邮箱已被其他账号绑定");
        }

        user.setEmail(dto.getEmail());
        checkAndUpdatePerfect(user);
        this.updateById(user);
        log.info("用户 {} 绑定邮箱成功", userId);
    }

    @Override
    public void sendEmailCode(String email) {
        String limitKey = CacheConstants.EMAIL_LIMIT_PREFIX + email;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(limitKey))) {
            throw new BizException("验证码已发送，请60秒后重试");
        }
        String code = String.format("%06d", (int) (Math.random() * 1000000));
        String codeKey = CacheConstants.EMAIL_CODE_PREFIX + email;
        stringRedisTemplate.opsForValue().set(codeKey, code,
                java.time.Duration.ofSeconds(CacheConstants.EMAIL_CODE_TTL));
        stringRedisTemplate.opsForValue().set(limitKey, "1", java.time.Duration.ofSeconds(60));

        // 真实发送邮件
        emailUtil.sendCode(email, code);
    }

    @Override
    public boolean verifyEmailCode(String email, String code) {
        if (email == null || code == null) return false;
        String codeKey = CacheConstants.EMAIL_CODE_PREFIX + email;
        String saved = stringRedisTemplate.opsForValue().get(codeKey);
        if (saved == null) return false;
        boolean valid = saved.equals(code);
        if (valid) {
            stringRedisTemplate.delete(codeKey);
            String verifiedKey = CacheConstants.EMAIL_VERIFIED_PREFIX + email;
            stringRedisTemplate.opsForValue().set(verifiedKey, "1", java.time.Duration.ofMinutes(10));
        }
        return valid;
    }

    @Override
    public boolean checkEmailExists(String email) {
        if (email == null || email.isBlank()) return false;
        return this.lambdaQuery().eq(SysUser::getEmail, email).count() > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String phone, String email, String code, String newPassword) {
        // 校验验证码（手机号或邮箱二选一，验证码须已通过 verify-code / verify-email-code 校验）
        String verifiedKey;
        SysUser user;

        if (phone != null && !phone.isBlank()) {
            verifiedKey = CacheConstants.SMS_VERIFIED_PREFIX + phone;
            user = this.getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone));
        } else if (email != null && !email.isBlank()) {
            verifiedKey = CacheConstants.EMAIL_VERIFIED_PREFIX + email;
            user = this.getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email));
        } else {
            throw new BizException("请提供手机号或邮箱");
        }

        if (user == null) {
            throw new BizException("账号不存在");
        }
        if (user.getStatus() == 0) {
            throw new BizException("账号已被禁用，请联系管理员");
        }

        // 检查验证标记
        String mark = stringRedisTemplate.opsForValue().get(verifiedKey);
        if (mark == null) {
            throw new BizException("验证码未通过校验或已过期，请重新验证");
        }

        // 更新密码
        user.setPassword(encodePassword(newPassword));
        this.updateById(user);
        stringRedisTemplate.delete(verifiedKey);

        log.info("用户 {} 通过 {} 找回密码成功", user.getUsername(), phone != null ? "手机号" : "邮箱");
    }

    /** 检查是否满足完善条件（实名+手机+邮箱），满足则标记 is_perfect=1 */
    private void checkAndUpdatePerfect(SysUser user) {
        boolean hasRealName = user.getIdCard() != null && !user.getIdCard().isBlank();
        boolean hasPhone = user.getPhone() != null && !user.getPhone().isBlank();
        boolean hasEmail = user.getEmail() != null && !user.getEmail().isBlank();
        if (hasRealName && hasPhone && hasEmail) {
            user.setIsPerfect(1);
        }
    }

    @Override
    public UserProfileVO getProfile(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return toProfileVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO updateProfile(Long userId, UserProfileUpdateDTO dto) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }

        // 昵称和性别必须同时上传
        if ((dto.getNickname() == null) != (dto.getGender() == null)) {
            throw new BizException("昵称和性别必须同时上传");
        }

        // 昵称敏感词校验
        if (dto.getNickname() != null) {
            List<String> sensitive = sensitiveWordUtil.checkSensitive(dto.getNickname());
            if (!sensitive.isEmpty()) {
                log.warn("昵称包含敏感词：{}, 词={}", dto.getNickname(), sensitive);
                throw new BizException("昵称包含违规内容，请修改");
            }
            user.setNickname(dto.getNickname());
        }

        if (dto.getGender() != null) {
            if (dto.getGender() < 0 || dto.getGender() > 2) {
                throw new BizException("性别值无效 0未知 1男 2女");
            }
            user.setGender(dto.getGender());
        }

        if (dto.getBirthday() != null) {
            user.setBirthday(dto.getBirthday());
        }

        if (dto.getSignature() != null) {
            user.setSignature(dto.getSignature());
        }

        this.updateById(user);
        log.info("用户 {} 资料已更新", user.getUsername());
        return toProfileVO(user);
    }

    /** 构建资料 VO（排除敏感字段） */
    private UserProfileVO toProfileVO(SysUser user) {
        return UserProfileVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(DesensitizeUtil.name(user.getRealName()))
                .nickname(user.getNickname())
                .gender(user.getGender())
                .birthday(user.getBirthday())
                .signature(user.getSignature())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .email(user.getEmail())
                .isPerfect(user.getIsPerfect())
                .createTime(user.getCreateTime() != null ? user.getCreateTime().toString() : null)
                .build();
    }
}
