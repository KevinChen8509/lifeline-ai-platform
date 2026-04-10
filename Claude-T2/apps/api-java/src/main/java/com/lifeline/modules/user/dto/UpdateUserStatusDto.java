package com.lifeline.modules.user.dto;

import com.lifeline.modules.user.entity.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserStatusDto {

    @NotNull(message = "状态不能为空")
    private UserStatus status;
}
