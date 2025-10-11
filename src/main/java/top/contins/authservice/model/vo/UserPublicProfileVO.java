package top.contins.authservice.model.vo;

import lombok.Data;

@Data
public class UserPublicProfileVO {
    private Long userId;
    private String username;
    private String nickname;
    private String signature;
    private String avatarImage;
    private String backgroundImage;
}

