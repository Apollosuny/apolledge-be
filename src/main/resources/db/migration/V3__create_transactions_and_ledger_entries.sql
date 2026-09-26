create table transactions
(
    id                uuid primary key     default gen_random_uuid(),

    user_id           uuid        not null references users (id),

    occurred_at       timestamptz not null,
    posted_at         timestamptz not null default now(),

    note              text,
    receipt_url       text,

    source            varchar(30) not null default 'MANUAL',

    standing_order_id uuid,

    reverses_id       uuid references transactions (id),

    idempotency_key   text,

    constraint uq_transactions_user_idempotency
        unique (user_id, idempotency_key),

    constraint chk_transactions_source
        check (source in (
                          'MANUAL',
                          'STANDING_ORDER',
                          'IMPORT'
            ))
);


create table ledger_entries
(
    id             uuid primary key     default gen_random_uuid(),

    transaction_id uuid        not null references transactions (id),

    account_id     uuid        not null references accounts (id),

    direction      varchar(10) not null,

    amount_vnd     bigint      not null,

    occurred_at    timestamptz not null,

    created_at     timestamptz not null default now(),

    constraint chk_ledger_entries_direction
        check (direction in (
                             'DEBIT',
                             'CREDIT'
            )),

    constraint chk_ledger_entries_amount
        check (amount_vnd > 0)
);


create index idx_transactions_user_occurred_at
    on transactions (user_id, occurred_at desc);

create index idx_ledger_entries_account_occurred_at
    on ledger_entries (account_id, occurred_at desc);

create index idx_ledger_entries_transaction_id
    on ledger_entries (transaction_id);