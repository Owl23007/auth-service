package top.contins.authservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.contins.authservice.model.common.Result;
import top.contins.authservice.model.dto.ConfirmProfileImageUploadRequest;
import top.contins.authservice.model.dto.CreateProfileImageUploadRequest;
import top.contins.authservice.model.dto.UpdateProfileRequest;
import top.contins.authservice.service.UserService;
import top.contins.authservice.util.JwtUtil;

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

    @GetMapping("/me")
    public Result<?> getMyProfile(@RequestHeader("Authorization") String accessToken) {
        Long userId = jwtUtil.getUserIdFromToken(extractToken(accessToken));
        if (userId == null) {
            return Result.error("无效的token");
        }

        return userService.getSelfProfile(userId);
    }

    @GetMapping("/{userId}")
    public Result<?> getUserProfile(@PathVariable Long userId) {
        return userService.getPublicProfile(userId);
    }

    @GetMapping("/username/{username}")
    public Result<?> getUserProfileByUsername(@PathVariable String username) {
        return userService.getPublicProfileByUsername(username);
    }

    @GetMapping("/search")
    public Result<?> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return userService.searchUsers(keyword, pageNum, pageSize);
    }

    @PutMapping("/me")
    public Result<?> updateMyProfile(
            @RequestHeader("Authorization") String accessToken,
            @RequestBody @Validated UpdateProfileRequest request) {
        Long userId = jwtUtil.getUserIdFromToken(extractToken(accessToken));
        if (userId == null) {
            return Result.error("无效的token");
        }

        return userService.updateProfile(userId, request);
    }

    @PostMapping(value = "/me/avatar/upload-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<?> createAvatarUploadUrl(
            @RequestHeader("Authorization") String accessToken,
            @RequestBody @Validated CreateProfileImageUploadRequest request) {
        Long userId = jwtUtil.getUserIdFromToken(extractToken(accessToken));
        if (userId == null) {
            return Result.error("无效的token");
        }

        return userService.createAvatarUploadUrl(userId, request);
    }

    @PostMapping(value = "/me/avatar/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<?> confirmAvatarUpload(
            @RequestHeader("Authorization") String accessToken,
            @RequestBody @Validated ConfirmProfileImageUploadRequest request) {
        Long userId = jwtUtil.getUserIdFromToken(extractToken(accessToken));
        if (userId == null) {
            return Result.error("无效的token");
        }

        return userService.confirmAvatarUpload(userId, request.getObjectName());
    }

    @PostMapping(value = "/me/background/upload-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<?> createBackgroundUploadUrl(
            @RequestHeader("Authorization") String accessToken,
            @RequestBody @Validated CreateProfileImageUploadRequest request) {
        Long userId = jwtUtil.getUserIdFromToken(extractToken(accessToken));
        if (userId == null) {
            return Result.error("无效的token");
        }

        return userService.createBackgroundUploadUrl(userId, request);
    }

    @PostMapping(value = "/me/background/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<?> confirmBackgroundUpload(
            @RequestHeader("Authorization") String accessToken,
            @RequestBody @Validated ConfirmProfileImageUploadRequest request) {
        Long userId = jwtUtil.getUserIdFromToken(extractToken(accessToken));
        if (userId == null) {
            return Result.error("无效的token");
        }

        return userService.confirmBackgroundUpload(userId, request.getObjectName());
    }

    private String extractToken(String token) {
        if (token == null) {
            return null;
        }
        return token.trim().startsWith("Bearer ") ? token.substring(7).trim() : token.trim();
    }
}
