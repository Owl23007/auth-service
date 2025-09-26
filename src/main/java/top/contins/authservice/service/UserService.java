package top.contins.authservice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.contins.authservice.model.common.Result;
import top.contins.authservice.model.dto.RegisterRequest;
import top.contins.authservice.model.dto.UserLoginRequest;
import top.contins.authservice.model.po.UserPo;

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
    int updateUser(UserPo user);

    /**
     * 更新用户信息
     *
     * @param status 用户状态
     */
    int updateUserStatus(UserPo.UserStatus status, Long userId);

    /**
     * 删除用户
     *
     * @param userId 用户ID
     * @return 是否删除成功
     */
    int deleteUser(Long userId);

    /**
     * 软删除用户
     *
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean softDeleteUser(Long userId);

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 注册结果
     */
    Result<String> register(RegisterRequest request);

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
     * @return 重置结果
     */
    Result<String> resetPassword(String token, String newPassword);

    /**
     * 更新密码
     *
     * @param userId          用户ID
     * @param oldPassword     旧密码
     * @param newPassword     新密码
     * @param confirmPassword 确认密码
     * @return 修改结果
     */
    Result<String> updatePassword(Long userId, String oldPassword, String newPassword, String confirmPassword);

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

    /**
     * 更新用户个人资料
     *
     * @param userId  用户ID
     * @param request 更新请求
     * @return 更新后的用户信息
     */
    UserPo updateUserProfile(Long userId, Object request);
}
