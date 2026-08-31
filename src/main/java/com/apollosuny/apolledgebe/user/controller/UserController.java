package com.apollosuny.apolledgebe.user.controller;

import com.apollosuny.apolledgebe.user.dto.UserResponse;
import com.apollosuny.apolledgebe.user.entity.UserProvider;
import com.apollosuny.apolledgebe.user.mapper.UserMapper;
import com.apollosuny.apolledgebe.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping("/{username}")
    public ResponseEntity<UserResponse> getUser(
            @PathVariable String username,
            @RequestParam UserProvider provider
    ) {
        return userService.findByUsernameAndProvider(username, provider)
                .map(userMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
