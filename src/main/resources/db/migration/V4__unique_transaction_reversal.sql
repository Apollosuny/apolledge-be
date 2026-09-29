-- A transaction can be reversed at most once; this closes the race between concurrent reversal requests.
create unique index uq_transactions_reverses_id
    on transactions (reverses_id)
    where reverses_id is not null;
