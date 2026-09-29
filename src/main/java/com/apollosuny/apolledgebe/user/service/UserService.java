package com.apollosuny.apolledgebe.user.service;

import com.apollosuny.apolledgebe.common.exception.BusinessException;
import com.apollosuny.apolledgebe.user.dto.UserResponse;
import com.apollosuny.apolledgebe.user.entity.User;
import com.apollosuny.apolledgebe.user.entity.UserProvider;
import com.apollosuny.apolledgebe.user.mapper.UserMapper;
import com.apollosuny.apolledgebe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID currentUserId) {
        return userRepository.findById(currentUserId)
                .map(userMapper::toResponse)
                .orElseThrow(UserService::userNotFound);
    }

    /**
     * Profiles contain the email address, so a user may only look themselves up by username.
     * Any other user is reported as not found, which also avoids revealing which usernames exist.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(
            UUID currentUserId,
            String username,
            UserProvider provider
    ) {
        User user = userRepository
                .findByUsernameAndProvider(username.trim().toLowerCase(), provider)
                .filter(found -> found.getId().equals(currentUserId))
                .orElseThrow(UserService::userNotFound);

        return userMapper.toResponse(user);
    }

    private static BusinessException userNotFound() {
        return new BusinessException(
                "USER_NOT_FOUND",
                "User not found",
                HttpStatus.NOT_FOUND
        );
    }
}
