package top.contins.authservice.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserSelfProfileVO {
    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String signature;
    private String avatarImage;
    private String backgroundImage;
    private String status;
    private String role;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private String lastLoginIpLocation;
    private String lastLoginDevice;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
