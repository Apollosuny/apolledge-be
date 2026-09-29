package com.apollosuny.apolledgebe.user.service;

import com.apollosuny.apolledgebe.common.exception.BusinessException;
import com.apollosuny.apolledgebe.user.dto.UserResponse;
import com.apollosuny.apolledgebe.user.entity.User;
import com.apollosuny.apolledgebe.user.entity.UserProvider;
import com.apollosuny.apolledgebe.user.mapper.UserMapper;
import com.apollosuny.apolledgebe.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Spy
    private UserMapper userMapper = new UserMapper();

    @InjectMocks
    private UserService userService;

    private final User currentUser = User.builder()
            .id(UUID.randomUUID())
            .username("trung")
            .email("trung@example.com")
            .provider(UserProvider.LOCAL)
            .build();

    @Test
    void getCurrentUser_shouldReturnProfile() {
        when(userRepository.findById(currentUser.getId())).thenReturn(Optional.of(currentUser));

        UserResponse response = userService.getCurrentUser(currentUser.getId());

        assertThat(response.id()).isEqualTo(currentUser.getId());
        assertThat(response.email()).isEqualTo("trung@example.com");
    }

    @Test
    void getCurrentUser_shouldThrowNotFound_whenUserWasDeleted() {
        UUID deletedId = UUID.randomUUID();
        when(userRepository.findById(deletedId)).thenReturn(Optional.empty());

        assertNotFound(() -> userService.getCurrentUser(deletedId));
    }

    @Test
    void getUserByUsername_shouldReturnProfile_whenLookingUpYourself() {
        when(userRepository.findByUsernameAndProvider("trung", UserProvider.LOCAL))
                .thenReturn(Optional.of(currentUser));

        UserResponse response = userService.getUserByUsername(
                currentUser.getId(), "  Trung ", UserProvider.LOCAL);

        assertThat(response.username()).isEqualTo("trung");
    }

    @Test
    void getUserByUsername_shouldThrowNotFound_whenLookingUpAnotherUser() {
        User other = User.builder()
                .id(UUID.randomUUID()).username("other").email("other@example.com")
                .provider(UserProvider.LOCAL).build();
        when(userRepository.findByUsernameAndProvider("other", UserProvider.LOCAL))
                .thenReturn(Optional.of(other));

        assertNotFound(() -> userService.getUserByUsername(
                currentUser.getId(), "other", UserProvider.LOCAL));
    }

    @Test
    void getUserByUsername_shouldThrowSameNotFound_whenUsernameDoesNotExist() {
        when(userRepository.findByUsernameAndProvider("ghost", UserProvider.LOCAL))
                .thenReturn(Optional.empty());

        assertNotFound(() -> userService.getUserByUsername(
                currentUser.getId(), "ghost", UserProvider.LOCAL));
    }

    private void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("USER_NOT_FOUND");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }
}
