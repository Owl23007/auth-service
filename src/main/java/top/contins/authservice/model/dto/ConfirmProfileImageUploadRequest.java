package top.contins.authservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmProfileImageUploadRequest {
    @NotBlank(message = "对象名不能为空")
    private String objectName;
}
