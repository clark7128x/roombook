CREATE TABLE account_invitation (
    id BIGSERIAL CONSTRAINT pk_account_invitation PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    username_key VARCHAR(64) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL CONSTRAINT fk_invitation_creator REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uq_invitation_token_hash UNIQUE(token_hash),
    CONSTRAINT ck_invitation_lifetime CHECK(expires_at > created_at),
    CONSTRAINT ck_invitation_state CHECK(consumed_at IS NULL OR revoked_at IS NULL)
);
CREATE INDEX ix_invitation_created ON account_invitation(created_at DESC, id DESC);
