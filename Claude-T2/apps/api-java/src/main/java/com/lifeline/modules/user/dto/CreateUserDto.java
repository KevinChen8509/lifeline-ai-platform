package com.lifeline.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserDto {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100)
    private String password;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50)
    private String name;

    @Size(max = 100)
    private String email;

    @Size(max = 20)
    private String phone;

    private String roleId;
}
