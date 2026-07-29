CREATE TABLE IF NOT EXISTS facebook_pages (
    id            BIGSERIAL PRIMARY KEY,
    page_id       VARCHAR(64)  NOT NULL UNIQUE,
    page_name     VARCHAR(255) NOT NULL,
    access_token  VARCHAR(512) NOT NULL,
    ad_account_id VARCHAR(64),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP
);

CREATE TABLE IF NOT EXISTS campaigns (
    id              BIGSERIAL    PRIMARY KEY,
    campaign_id     VARCHAR(64)  NOT NULL UNIQUE,
    campaign_name   VARCHAR(255) NOT NULL,
    page_id         VARCHAR(64)  NOT NULL REFERENCES facebook_pages(page_id),
    status          VARCHAR(32)  NOT NULL,
    objective       VARCHAR(64),
    daily_budget    NUMERIC(12,2),
    lifetime_budget NUMERIC(12,2),
    spend           NUMERIC(12,2) DEFAULT 0,
    impressions     BIGINT        DEFAULT 0,
    clicks          BIGINT        DEFAULT 0,
    message_count   BIGINT,
    cost_per_message DECIMAL(19,2),
    start_date      DATE,
    end_date        DATE,
    last_synced_at  TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_campaigns_dates ON campaigns(start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_campaigns_page   ON campaigns(page_id);
