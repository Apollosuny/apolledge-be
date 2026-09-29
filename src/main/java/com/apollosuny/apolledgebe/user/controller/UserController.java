package com.apollosuny.apolledgebe.user.controller;

import com.apollosuny.apolledgebe.auth.security.AuthenticatedUser;
import com.apollosuny.apolledgebe.user.dto.UserResponse;
import com.apollosuny.apolledgebe.user.entity.UserProvider;
import com.apollosuny.apolledgebe.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse getCurrentUser(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return userService.getCurrentUser(currentUser.id());
    }

    @GetMapping("/{username}")
    public UserResponse getUser(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable String username,
            @RequestParam UserProvider provider
    ) {
        return userService.getUserByUsername(
                currentUser.id(),
                username,
                provider
        );
    }
}
