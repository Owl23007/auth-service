package top.contins.authservice.controller;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import top.contins.authservice.model.common.Result;
import top.contins.authservice.model.po.UserPo;
import top.contins.authservice.model.vo.*;
import top.contins.authservice.service.UserService;
import top.contins.authservice.util.ObjectConvertUtil;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user")
@Validated
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ========== 获取用户信息 ========== //

    /**
     * 获取当前登录用户完整资料（自己看自己）
     */
    @GetMapping("/profile")
    public Result<UserSelfProfileVO> getCurrentUserProfile() {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return Result.error("用户未登录");
        }

        UserPo userPo = userService.getUserById(currentUserId);
        if (userPo == null) {
            return Result.error("用户不存在");
        }

        UserSelfProfileVO vo = ObjectConvertUtil.copyProperties(userPo, UserSelfProfileVO.class);
        return Result.success(vo);
    }

    /**
     * 根据用户ID获取用户公开资料（他人查看）
     */
    @GetMapping("/{userId}")
    public Result<UserPublicProfileVO> getUserPublicProfile(@PathVariable("userId") Long userId) {
        UserPo userPo = userService.getUserById(userId);
        if (userPo == null) {
            return Result.error("用户不存在");
        }

        // 不用判断当前用户是否是本人，因为前端会直接请求 /user/{userId} 是自己的公开资料
        UserPublicProfileVO vo = ObjectConvertUtil.copyProperties(userPo, UserPublicProfileVO.class);
        return Result.success(vo);
    }

    /**
     * 搜索用户（返回公开资料列表）
     */
    @GetMapping("/search")
    public Result<List<UserPublicProfileVO>> searchUsers(@RequestParam("query") String query) {
        if (query == null || query.trim().isEmpty()) {
            return Result.success(List.of());
        }
    }

    // ========== 更新用户信息 ========== //

    /**
     * 更新当前用户基本信息（昵称、签名、头像等）
     */
    @PutMapping("/profile")
    public Result<UserSelfProfileVO> updateUserProfile(@RequestBody UserUpdateRequest request) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return Result.error("用户未登录");
        }

        // 防止越权
        if (request.getUserId() != null && !request.getUserId().equals(currentUserId)) {
            return Result.error("只能更新当前登录用户的信息");
        }

        UserPo updatedUser = userService.updateUserProfile(currentUserId, request);
        UserSelfProfileVO vo = UserVoConverter.toSelfVO(updatedUser);
        return Result.success(vo);
    }

    /**
     * 修改密码（需要验证旧密码）
     */
    @PostMapping("/password")
    public Result<String> updatePassword(@RequestBody UpdatePasswordRequest request) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return Result.error("用户未登录");
        }

        Result<String> result = userService.changePassword(
                currentUserId,
                request.getOldPassword(),
                request.getNewPassword()
        );

        return result;
    }

    // ========== 管理/辅助接口 ========== //

    /**
     * 获取所有用户ID（用于后台或调试）
     * TODO:加上管理员权限校验 @PreAuthorize("hasRole('ADMIN')")
     */
    @GetMapping("/all-ids")
    public Result<?> getAllUserIds() {
        //List<Long> ids = userService.getAllUserIds();
        return Result.success("TODO");
    }

    // ========== 工具方法 ========== //

    public Long getCurrentUserId() {
        // 从Spring Security上下文获取当前登录用户的ID
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
                authentication.getPrincipal() instanceof UserPo user) {
            return user.getUserId();
        }
        return null;
    }

    // ========== 内部请求 DTO ========== //

    @Data
    public static class UserUpdateRequest {
        // getters & setters
        private Long userId; // 用于防越权校验，前端可不传
        private String nickname;
        private String signature;
        private String avatarImage;
        private String backgroundImage;
        private String email; // 如允许修改
        private String phone; // 如允许修改

    }

    @Data
    public static class UpdatePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }
}