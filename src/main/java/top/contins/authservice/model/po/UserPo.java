package top.contins.authservice.model.po;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class UserPo {
    @TableId(value = "user_id", type = IdType.AUTO)
    private Integer userId;
    private String username;
    private String nickname;

    private String email;
    private String phone;

    private String signature;
    private String avatarImage;
    private String backgroundImage;

    @JsonIgnore
    private String password;

    private UserStatus status;

    @JsonIgnore
    private LocalDateTime lastLoginTime;
    @JsonIgnore
    private String lastLoginIp;
    @JsonIgnore
    private String lastLoginIpLocation;
    @JsonIgnore
    private String lastLoginDevice;

    @JsonIgnore
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Getter
    public enum UserStatus {
        UNVERIFIED(0),
        NORMAL(1),
        BANNED(2),
        DEACTIVATED(3);

        @EnumValue
        private final int value;

        UserStatus(int value) {
            this.value = value;
        }

    }
}
