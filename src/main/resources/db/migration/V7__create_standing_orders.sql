create table standing_orders
(
    id                uuid primary key default gen_random_uuid(),

    user_id           uuid        not null references users (id),

    name              text        not null,
    amount_vnd        bigint      not null,

    debit_account_id  uuid        not null references accounts (id),
    credit_account_id uuid        not null references accounts (id),

    day_of_month      smallint    not null,
    next_run_on       date        not null,

    auto_post         boolean     not null default true,
    paused_at         timestamptz,

    created_at        timestamptz not null default now(),

    constraint chk_standing_orders_amount
        check (amount_vnd > 0),

    constraint chk_standing_orders_day_of_month
        check (day_of_month between 1 and 31),

    constraint chk_standing_orders_accounts_differ
        check (debit_account_id <> credit_account_id)
);

create index idx_standing_orders_user_id
    on standing_orders (user_id);

create index idx_standing_orders_due
    on standing_orders (next_run_on)
    where auto_post and paused_at is null;

-- transactions.standing_order_id already exists (V3) without a foreign key
alter table transactions
    add constraint fk_transactions_standing_order
        foreign key (standing_order_id) references standing_orders (id);

create index idx_transactions_standing_order_id
    on transactions (standing_order_id)
    where standing_order_id is not null;
