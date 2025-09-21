package top.contins.authservice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.contins.authservice.model.common.Result;
import top.contins.authservice.model.dto.RegisterRequest;
import top.contins.authservice.model.dto.UserLoginRequest;
import top.contins.authservice.model.po.UserPo;

import java.util.List;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 获取用户列表
     *
     * @param pageSize 每页大小
     * @param pageNum  页码
     * @param keyword  搜索关键字
     * @param status   用户状态 （可选：ACTIVE, INACTIVE, BANNED,DEACTIVATED,ALL）
     * @param sortBy     排序字段  （可选：username, email, status, created_at, updated_at）
     * @param sortOrder 排序方式  （可选：asc, desc）
     * @return List<UserPo> 用户ID列表
     */
    Page<UserPo> getUserList(int pageSize, int pageNum, String status, String keyword, String sortBy, String sortOrder);

    /**
     * 根据用户ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    UserPo getUserById(Long userId);

    /**
     * 根据用户名获取用户信息
     *
     * @param username 用户名
     * @return 用户信息
     */
    UserPo getUserByUsername(String username);

    /**
     * 根据邮箱获取用户信息
     *
     * @param email 邮箱
     * @return 用户信息
     */
    UserPo getUserByEmail(String email);

    /**
     * 更新用户信息
     *
     * @param user 用户信息
     */
    void updateUser(UserPo user);

    /**
     * 删除用户
     *
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deleteUser(Long userId);

    /**
     * 软删除用户
     *
     * @return 是否删除成功
     */
    boolean softDeleteUser();

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 注册结果
     */
    Result<String> registerUser(RegisterRequest request);

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录结果
     */
    Result<?> login(UserLoginRequest request);

    /**
     * 刷新token
     *
     * @param refreshToken 刷新token
     * @return 新的token信息
     */
    Result<?> refreshToken(String refreshToken);

    /**
     * 重置密码
     *
     * @param token           重置token
     * @param newPassword     新密码
     * @param confirmPassword 确认密码
     * @return 重置结果
     */
    Result<String> resetPassword(String token, String newPassword, String confirmPassword);

    /**
     * 修改密码（需要验证旧密码）
     *
     * @param oldPassword     旧密码
     * @param newPassword     新密码
     * @param confirmPassword 确认密码
     * @return 修改结果
     */
    Result<String> changePassword(String oldPassword, String newPassword, String confirmPassword);

    /**
     * 发送密码重置邮件
     *
     * @param email 用户邮箱
     * @return 操作结果
     */
    Result<String> sendResetPasswordEmail(String email);

    /**
     * 激活账户
     *
     * @param token 激活令牌
     * @return 操作结果
     */
    Result<String> activateAccount(String token);

    /**
     * 用户登出
     *
     * @param token 用户token
     * @return 登出结果
     */
    Result<String> logout(String token);

    /*
     * 获取当前用户ID
     *
     * @return 当前用户ID
     */
    Long getCurrentUserId();
}
