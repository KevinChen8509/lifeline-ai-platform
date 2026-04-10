package com.lifeline.common.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JwtUserDetails {

    private String userId;
    private String username;
    private String role;
}
