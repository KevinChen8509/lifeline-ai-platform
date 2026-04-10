package com.lifeline.modules.auth.dto;

import com.lifeline.modules.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class AuthResponseDto {

    private String accessToken;
    private String refreshToken;
    private User user;
    private List<Map<String, String>> permissions;
}
