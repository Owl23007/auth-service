package top.contins.authservice.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {
    @Pattern(regexp = "^[a-zA-Z0-9]{5,16}$")
    @NotNull
    public String username;

    @NotNull
    @Email
    public String email;

    @NotNull
    public String password;

    @NotNull
    public String captchaId;

    @NotNull
    public String captchaCode;
}