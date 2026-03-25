package top.contins.authservice.model.vo;

import lombok.Data;

import java.util.Map;

@Data
public class ProfileImageUploadUrlVO {
    private String objectName;
    private String uploadUrl;
    private String publicUrl;
    private String method;
    private String contentType;
    private Integer expireInSeconds;
    private Long expireAt;
    private Map<String, String> headers;
}
