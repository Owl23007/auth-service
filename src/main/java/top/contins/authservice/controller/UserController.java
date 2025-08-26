package top.contins.authservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import top.contins.authservice.model.common.Result;
import top.contins.authservice.model.po.UserPo;
import top.contins.authservice.service.UserService;

@RestController
@RequestMapping("/auth/user")
@Validated
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 获取当前用户信息
     *
     * @return 用户信息
     */
    @GetMapping("/profile")
    public Result<UserPo> getCurrentUserProfile() {
        // 获取当前登录用户的ID
        Integer userId = userService.getCurrentUserId();
        if (userId == null) {
            return Result.error("用户未登录或不存在");
        }
        // 根据用户ID获取用户信息
        UserPo user = userService.getUserById(userId);
        return Result.success(user);
    }

    /**
     * 更新用户基本信息
     *
     * @param user 用户信息
     * @return 更新结果
     */
    @PutMapping("/profile")
    public Result<UserPo> updateUserProfile(@RequestBody UserPo user) {
        UserPo updatedUser = userService.updateUser(user);
        return Result.success(updatedUser);
    }

    /**
     * 根据用户ID获取用户信息（管理员功能）
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    @GetMapping("/{userId}")
    public Result<UserPo> getUserById(@PathVariable("userId") Integer userId) {
        UserPo user = userService.getUserById(userId);
        return Result.success(user);
    }

    /**
     * 删除用户账户
     *
     * @param userId 用户ID
     * @return 删除结果
     */
    @DeleteMapping("/{userId}")
    public Result<String> deleteUser(@PathVariable("userId") Integer userId) {
        boolean result = userService.deleteUser(userId);
        if (result) {
            return Result.success("用户删除成功");
        } else {
            return Result.error("用户删除失败");
        }
    }

    /**
     * 禁用用户账户
     *
     * @param userId 用户ID
     * @return 操作结果
     */
    @PostMapping("/{userId}/disable")
    public Result<String> disableUser(@PathVariable("userId") Integer userId) {
        UserPo user = userService.getUserById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        if (user.getStatus() == UserPo.UserStatus.BANNED) {
            return Result.error("用户已被禁用");
        }

        user.setStatus(UserPo.UserStatus.BANNED);
        userService.updateUser(user);

        return Result.success("用户已禁用");
    }

    /**
     * 启用用户账户
     *
     * @param userId 用户ID
     * @return 操作结果
     */
    @PostMapping("/{userId}/enable")
    public Result<String> enableUser(@PathVariable("userId") Integer userId) {
        UserPo user = userService.getUserById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        if (user.getStatus() == UserPo.UserStatus.NORMAL) {
            return Result.error("用户已启用");
        }

        user.setStatus(UserPo.UserStatus.NORMAL);
        userService.updateUser(user);

        return Result.success("用户已启用");
    }
}