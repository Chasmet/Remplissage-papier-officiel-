create table if not exists public.remplissage_mcp_client_tokens (
  token_hash text primary key check (token_hash ~ '^[0-9a-f]{64}$'),
  session_id uuid not null references public.remplissage_mcp_sessions(id) on delete cascade,
  label text not null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  revoked boolean not null default false,
  last_seen_at timestamptz
);

create index if not exists remplissage_mcp_client_tokens_session_idx
  on public.remplissage_mcp_client_tokens(session_id);

alter table public.remplissage_mcp_client_tokens enable row level security;
revoke all on public.remplissage_mcp_client_tokens from anon, authenticated;
grant select, insert, update, delete on public.remplissage_mcp_client_tokens to service_role;

comment on table public.remplissage_mcp_client_tokens is
  'Private hashed tokens for individual MCP clients; only the service role can resolve them.';
