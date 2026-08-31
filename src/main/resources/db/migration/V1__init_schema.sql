create type user_provider as enum (
    'LOCAL',
    'GOOGLE',
    'APPLE'
);

create table users (
    id uuid primary key default gen_random_uuid(),
    username text not null,
    password text,
    email text not null,
    provider user_provider not null,

    jwt_valid_from timestamptz not null default now(),

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint uq_users_username_provider
        unique (username, provider)
);