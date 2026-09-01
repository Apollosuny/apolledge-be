package com.apollosuny.apolledgebe.account.mapper;

import com.apollosuny.apolledgebe.account.dto.AccountResponse;
import com.apollosuny.apolledgebe.account.entity.Account;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getType(),
                account.getName(),
                account.getIcon(),
                account.getParent() != null
                    ? account.getParent().getId() : null,
                account.getCurrency(),
                account.getArchivedAt(),
                account.getCreatedAt()
        );
    }
}
