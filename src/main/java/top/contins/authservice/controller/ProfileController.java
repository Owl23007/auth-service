package top.contins.authservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import top.contins.authservice.model.common.Result;
import top.contins.authservice.model.dto.UpdateProfileRequest;
import top.contins.authservice.service.UserService;
import top.contins.authservice.util.JwtUtil;

/**
 * 用户资料控制器
 * 负责用户个人资料的查看和更新功能
 */
@RestController
@RequestMapping("/profile")
@Validated
@Slf4j
public class ProfileController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Autowired
    public ProfileController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 获取当前用户的完整个人资料
     * 需要提供有效的access token
     *
     * @param accessToken 访问令牌
     * @return 用户完整个人资料
     */
    @GetMapping("/me")
    public Result<?> getMyProfile(@RequestHeader("Authorization") String accessToken) {
        String token = extractToken(accessToken);
        Long userId = jwtUtil.getUserIdFromToken(token);
        
        if (userId == null) {
            return Result.error("无效的token");
        }

        return userService.getSelfProfile(userId);
    }

    /**
     * 获取指定用户的公开资料
     * 任何人都可以访问
     *
     * @param userId 用户ID
     * @return 用户公开资料
     */
    @GetMapping("/{userId}")
    public Result<?> getUserProfile(@PathVariable Long userId) {
        return userService.getPublicProfile(userId);
    }

    /**
     * 根据用户名获取用户公开资料
     * 任何人都可以访问
     *
     * @param username 用户名
     * @return 用户公开资料
     */
    @GetMapping("/username/{username}")
    public Result<?> getUserProfileByUsername(@PathVariable String username) {
        return userService.getPublicProfileByUsername(username);
    }

    /**
     * 搜索用户
     * 支持通过关键字搜索用户名或昵称
     *
     * @param keyword 搜索关键字
     * @param pageNum 页码（默认1）
     * @param pageSize 每页大小（默认10，最大100）
     * @return 用户公开资料列表（分页）
     */
    @GetMapping("/search")
    public Result<?> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return userService.searchUsers(keyword, pageNum, pageSize);
    }

    /**
     * 更新当前用户的个人资料
     * 需要提供有效的access token
     *
     * @param accessToken 访问令牌
     * @param request 更新请求
     * @return 更新后的用户资料
     */
    @PutMapping("/me")
    public Result<?> updateMyProfile(
            @RequestHeader("Authorization") String accessToken,
            @RequestBody @Validated UpdateProfileRequest request) {
        String token = extractToken(accessToken);
        Long userId = jwtUtil.getUserIdFromToken(token);
        
        if (userId == null) {
            return Result.error("无效的token");
        }

        return userService.updateProfile(userId, request);
    }

    /**
     * 提取token
     * 兼容Bearer前缀
     *
     * @param token 原始token
     * @return 纯净的token
     */
    private String extractToken(String token) {
        if (token == null) return null;
        return token.trim().startsWith("Bearer ") ? token.substring(7).trim() : token.trim();
    }
}
