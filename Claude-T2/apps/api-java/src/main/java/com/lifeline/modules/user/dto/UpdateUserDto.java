package com.lifeline.modules.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserDto {

    @Size(max = 50)
    private String name;

    @Size(max = 100)
    private String email;

    @Size(max = 20)
    private String phone;
}
