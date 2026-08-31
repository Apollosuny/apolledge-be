package com.apollosuny.apolledgebe.user.repository;

import com.apollosuny.apolledgebe.user.entity.User;
import com.apollosuny.apolledgebe.user.entity.UserProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsernameAndProvider(
            String username,
            UserProvider provider
    );

    boolean existsByUsernameAndProvider(
            String username,
            UserProvider provider
    );
}
