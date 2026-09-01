create table accounts (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id),

    type varchar(20) not null,
    name text not null,
    icon text,
    parent_id uuid references accounts(id),
    currency varchar(3) not null default 'VND',

    archived_at timestamptz,
    created_at timestamptz not null default now(),

    constraint chk_accounts_type
                      check (type in ('ASSET','LIABILITY','INCOME','EXPENSE','EQUITY'))
);

create index idx_accounts_user_id
on accounts(user_id);

create index idx_accounts_parent_id
on accounts(parent_id);