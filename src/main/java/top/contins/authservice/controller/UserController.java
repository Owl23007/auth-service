package top.contins.authservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import top.contins.authservice.model.common.Result;
import top.contins.authservice.model.po.UserPo;
import top.contins.authservice.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/user")
@Validated
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 获取当前用户信息
     *
     * @return 用户信息
     */
    @GetMapping("/profile")
    public Result<UserPo> getCurrentUserProfile() {
        Long userId = userService.getCurrentUserId();
        if (userId == null) {
            return Result.error("用户未登录或不存在");
        }
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
        //TODO 添加更多的验证逻辑
        UserPo updatedUser = userService.updateUser(user);
        return Result.success(updatedUser);
    }

    /**
     * 根据用户ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    @GetMapping("/{userId}")
    public Result<UserPo> getUserById(@PathVariable("userId") Long userId) {
        UserPo user = userService.getUserById(userId);
        return Result.success(user);
    }

    /**
     * 根据 input 查询用户列表
     *
     * @param input 查询参数
     * @return 用户列表
     */
    @GetMapping("/search")
    public Result<UserPo> searchUsers(@RequestParam("input") String input) {
        //TODO 实现用户列表查询功能
        // return userService.searchUsers(input);
        return Result.success(null);
    }


    /**
     * 修改密码（需要旧密码验证）
     *
     * @param oldPassword     旧密码
     * @param newPassword     新密码
     * @param hashedPassword  密码哈希值
     * @return 修改结果
     */
    @PostMapping("/update")
    public Result<String> updatePassword(
            @RequestParam("oldPassword") String oldPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("hashId") String hashId,
            @RequestParam("hashedPassword") String hashedPassword) {
        //TODO 验证 hashId 和 hashedPassword 的有效性
        return userService.changePassword(oldPassword, newPassword, hashedPassword);
    }

    /**
     * 获取所有用户ID列表
     *
     * @return 用户ID列表
     */
    @GetMapping("/all")
    public List<Long> getAllUsers(){
        return userService.getAll();
    }
}
