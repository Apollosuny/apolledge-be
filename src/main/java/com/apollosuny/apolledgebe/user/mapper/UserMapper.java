package com.apollosuny.apolledgebe.user.mapper;

import com.apollosuny.apolledgebe.user.dto.UserResponse;
import com.apollosuny.apolledgebe.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getProvider()
        );
    }
}
