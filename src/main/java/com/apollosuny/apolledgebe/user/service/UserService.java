package com.apollosuny.apolledgebe.user.service;

import com.apollosuny.apolledgebe.user.entity.User;
import com.apollosuny.apolledgebe.user.entity.UserProvider;
import com.apollosuny.apolledgebe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public Optional<User> findByUsernameAndProvider(
            String username,
            UserProvider provider
    ) {
        return userRepository.findByUsernameAndProvider(username, provider);
    }

}
