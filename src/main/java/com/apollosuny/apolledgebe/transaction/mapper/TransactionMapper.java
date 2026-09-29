package com.apollosuny.apolledgebe.transaction.mapper;

import com.apollosuny.apolledgebe.transaction.dto.LedgerEntryResponse;
import com.apollosuny.apolledgebe.transaction.dto.TransactionResponse;
import com.apollosuny.apolledgebe.transaction.entity.LedgerEntry;
import com.apollosuny.apolledgebe.transaction.entity.Transaction;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(
            Transaction transaction,
            List<LedgerEntry> entries
    ) {
        List<LedgerEntryResponse> entryResponses = entries.stream()
                .map(this::toEntryResponse)
                .toList();

        return new TransactionResponse(
                transaction.getId(),
                transaction.getOccurredAt(),
                transaction.getPostedAt(),
                transaction.getNote(),
                transaction.getReceiptUrl(),
                transaction.getSource(),
                transaction.getReverses() != null ? transaction.getReverses().getId() : null,
                entryResponses
        );
    }

    private LedgerEntryResponse toEntryResponse(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getAccount().getId(),
                entry.getDirection(),
                entry.getAmountVnd()
        );
    }
}
