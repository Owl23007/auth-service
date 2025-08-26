package top.contins.authservice.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 激活令牌实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("token")
public class TokenPo {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    /**
     * 邮箱
     */
    @TableField("email")
    private String email;
    
    /**
     * 激活令牌
     */
    @TableField("token")
    private String token;
    
    /**
     * 过期时间
     */
    @TableField("expires_at")
    private LocalDateTime expiresAt;
    
    /**
     * 是否已使用：0-未使用，1-已使用
     */
    @TableField("is_used")
    private Boolean isUsed;
    
    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
