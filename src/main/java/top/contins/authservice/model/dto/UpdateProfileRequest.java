package top.contins.authservice.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;

    @Size(max = 200, message = "个性签名长度不能超过200个字符")
    private String signature;

    @Size(max = 500, message = "头像URL长度不能超过500个字符")
    private String avatarImage;

    @Size(max = 500, message = "背景图URL长度不能超过500个字符")
    private String backgroundImage;

    @Size(max = 20, message = "手机号长度不能超过20个字符")
    private String phone;
}
