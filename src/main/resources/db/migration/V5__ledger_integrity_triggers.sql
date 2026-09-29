-- The ledger is append-only: corrections are made by posting reversal transactions.
-- A trigger (rather than REVOKE) is used so the rule also holds for the role that owns the table.
create function forbid_ledger_entry_mutation() returns trigger
    language plpgsql as
$$
begin
    raise exception 'ledger_entries is append-only: % is not allowed', tg_op;
end
$$;

create trigger ledger_entries_append_only
    before update or delete
    on ledger_entries
    for each row
execute function forbid_ledger_entry_mutation();

create trigger ledger_entries_no_truncate
    before truncate
    on ledger_entries
    for each statement
execute function forbid_ledger_entry_mutation();


-- Every transaction must balance: total debit = total credit.
-- Deferred to commit so entries can be inserted one by one inside the same database transaction.
create function assert_transaction_balanced() returns trigger
    language plpgsql as
$$
declare
    total_debit  bigint;
    total_credit bigint;
begin
    select coalesce(sum(amount_vnd) filter (where direction = 'DEBIT'), 0),
           coalesce(sum(amount_vnd) filter (where direction = 'CREDIT'), 0)
    into total_debit, total_credit
    from ledger_entries
    where transaction_id = new.transaction_id;

    if total_debit <> total_credit then
        raise exception 'transaction % is not balanced: debit %, credit %',
            new.transaction_id, total_debit, total_credit;
    end if;

    return null;
end
$$;

create constraint trigger ledger_entries_balanced
    after insert
    on ledger_entries
    deferrable initially deferred
    for each row
execute function assert_transaction_balanced();
