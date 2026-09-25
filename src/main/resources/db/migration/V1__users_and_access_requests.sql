create table users (
    id          bigint generated always as identity primary key,
    email       varchar(320) not null unique,
    name        varchar(200),
    avatar_url  varchar(2048),
    status      varchar(20) not null default 'NONE'
                check (status in ('NONE', 'PENDING', 'MEMBER', 'DECLINED')),
    created_at  timestamptz not null default now()
);

create table access_requests (
    id          bigint generated always as identity primary key,
    user_id     bigint not null references users (id) on delete cascade,
    message     varchar(500),
    status      varchar(20) not null default 'PENDING'
                check (status in ('PENDING', 'APPROVED', 'DECLINED')),
    created_at  timestamptz not null default now(),
    decided_at  timestamptz,
    decided_by  bigint references users (id)
);

-- the database itself guarantees one pending request per user
create unique index one_pending_request_per_user
    on access_requests (user_id) where status = 'PENDING';