create table budgets
(
    account_id   uuid   not null references accounts (id),

    -- always the first day of the month
    period_month date   not null,

    limit_vnd    bigint not null,

    primary key (account_id, period_month),

    constraint chk_budgets_period_first_day
        check (extract(day from period_month) = 1),

    constraint chk_budgets_limit
        check (limit_vnd > 0)
);
