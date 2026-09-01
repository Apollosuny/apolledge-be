package com.apollosuny.apolledgebe.account.service;

import com.apollosuny.apolledgebe.account.dto.AccountResponse;
import com.apollosuny.apolledgebe.account.dto.CreateAccountRequest;
import com.apollosuny.apolledgebe.account.entity.Account;
import com.apollosuny.apolledgebe.account.mapper.AccountMapper;
import com.apollosuny.apolledgebe.account.repository.AccountRepository;
import com.apollosuny.apolledgebe.user.entity.User;
import com.apollosuny.apolledgebe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountMapper accountMapper;

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccounts(UUID userId) {
        return accountRepository.findAllByUser_Id(userId)
                .stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Transactional
    public AccountResponse createAccount(
            UUID userId,
            CreateAccountRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(("User not found")));

        Account parent = null;

        if (request.parentId() != null) {
            parent = accountRepository.findById(request.parentId())
                    .orElseThrow(() -> new IllegalArgumentException(("Parent not found")));
            if (!parent.getUser().getId().equals(userId)) {
                throw new IllegalArgumentException("Parent account does not belong to current user");
            }
        }

        Account.AccountBuilder builder = Account.builder()
                .user(user)
                .type(request.type())
                .name(request.name().trim())
                .icon(request.icon())
                .parent(parent);

        if (request.currency() != null) {
            builder.currency(request.currency());
        }

        Account account = builder.build();

        Account savedAccount = accountRepository.save(account);

        return accountMapper.toResponse(savedAccount);
    }
}
