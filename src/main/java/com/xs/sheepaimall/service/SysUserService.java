package com.xs.sheepaimall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xs.sheepaimall.dto.LoginDTO;
import com.xs.sheepaimall.dto.RegisterDTO;
import com.xs.sheepaimall.entity.SysRole;
import com.xs.sheepaimall.entity.SysUser;
import com.xs.sheepaimall.vo.LoginVO;

import java.util.List;

/**
 * 系统用户 Service
 */
public interface SysUserService extends IService<SysUser> {

    /** 账号密码登录 */
    LoginVO login(LoginDTO dto);

    /** 退出登录：将当前 Token 加入 Redis 黑名单 */
    void logout(String token);

    /** 注册新账号（创建用户并分配默认角色） */
    SysUser register(RegisterDTO dto);

    /** 修改密码：校验旧密码后更新为新密码 */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /** 修改头像 */
    void updateAvatar(Long userId, String avatarUrl);

    /** [管理员] 分页查询所有用户 */
    Page<SysUser> listUsers(int pageNum, int pageSize, String keyword);

    /** 查询用户的角色编码列表 */
    List<String> getUserRoleCodes(Long userId);

    /** [管理员] 查询用户的角色ID列表 */
    List<Long> getUserRoleIds(Long userId);

    /** [管理员] 为用户分配角色（替换模式：先删后增） */
    void assignRoles(Long userId, List<Long> roleIds);

    /** [管理员] 查询所有可用角色 */
    List<SysRole> listAllRoles();

    /** 检查手机号是否已注册 */
    boolean checkPhoneExists(String phone);

    /** 发送短信验证码（注册用，手机号不能已注册） */
    void sendVerifyCode(String phone);

    /** 发送短信验证码（登录用，手机号必须已注册） */
    void sendLoginCode(String phone);

    /** 校验短信验证码 */
    boolean verifyCode(String phone, String code);

    /** 短信验证码登录（手机号需先通过验证码验证） */
    LoginVO smsLogin(String phone, String code);
}
