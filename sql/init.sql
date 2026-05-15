CREATE TABLE IF NOT EXISTS stock_basic (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL UNIQUE,
    stock_name VARCHAR(50),
    industry VARCHAR(50),
    is_st SMALLINT DEFAULT 0 CHECK (is_st IN (0, 1)),
    list_date DATE,
    create_time TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS stock_daily (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    open DECIMAL(10, 2),
    close DECIMAL(10, 2),
    high DECIMAL(10, 2),
    low DECIMAL(10, 2),
    volume BIGINT,
    ma20 DECIMAL(10, 2),
    ma60 DECIMAL(10, 2),
    create_time TIMESTAMP DEFAULT NOW(),
    UNIQUE (stock_code, trade_date),
    CONSTRAINT fk_stock_daily_stock_code
        FOREIGN KEY (stock_code) REFERENCES stock_basic(stock_code)
);

CREATE INDEX IF NOT EXISTS idx_stock_daily_code ON stock_daily(stock_code);
CREATE INDEX IF NOT EXISTS idx_stock_daily_trade_date ON stock_daily(trade_date);

CREATE TABLE IF NOT EXISTS market_status (
    id BIGSERIAL PRIMARY KEY,
    trade_date DATE NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('bull', 'volatile', 'bear', 'panic')),
    raw_status VARCHAR(20) CHECK (raw_status IN ('bull', 'volatile', 'bear', 'panic')),
    hs300_close DECIMAL(12, 4),
    hs300_ma20 DECIMAL(12, 4),
    hs300_ma60 DECIMAL(12, 4),
    hs300_above_ma20 SMALLINT CHECK (hs300_above_ma20 IN (0, 1)),
    hs300_above_ma60 SMALLINT CHECK (hs300_above_ma60 IN (0, 1)),
    up_ratio DECIMAL(8, 6),
    remark TEXT,
    update_time TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_market_status_trade_date ON market_status(trade_date DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_market_status_trade_date ON market_status(trade_date);

ALTER TABLE market_status ADD COLUMN IF NOT EXISTS trade_date DATE;
ALTER TABLE market_status ADD COLUMN IF NOT EXISTS status VARCHAR(20);
ALTER TABLE market_status ADD COLUMN IF NOT EXISTS raw_status VARCHAR(20);
ALTER TABLE market_status ADD COLUMN IF NOT EXISTS hs300_close DECIMAL(12, 4);
ALTER TABLE market_status ADD COLUMN IF NOT EXISTS hs300_ma20 DECIMAL(12, 4);
ALTER TABLE market_status ADD COLUMN IF NOT EXISTS hs300_ma60 DECIMAL(12, 4);
ALTER TABLE market_status ADD COLUMN IF NOT EXISTS hs300_above_ma20 SMALLINT;
ALTER TABLE market_status ADD COLUMN IF NOT EXISTS hs300_above_ma60 SMALLINT;
ALTER TABLE market_status ADD COLUMN IF NOT EXISTS up_ratio DECIMAL(8, 6);
ALTER TABLE market_status ADD COLUMN IF NOT EXISTS remark TEXT;
ALTER TABLE market_status ADD COLUMN IF NOT EXISTS update_time TIMESTAMP DEFAULT NOW();

CREATE TABLE IF NOT EXISTS index_valuation (
    id BIGSERIAL PRIMARY KEY,
    index_code VARCHAR(20) NOT NULL,
    index_name VARCHAR(100),
    pe DECIMAL(12, 4),
    pb DECIMAL(12, 4),
    pe_percentile DECIMAL(8, 2),
    pb_percentile DECIMAL(8, 2),
    source VARCHAR(100),
    update_date DATE NOT NULL,
    update_time TIMESTAMP DEFAULT NOW(),
    UNIQUE (index_code, update_date)
);

CREATE INDEX IF NOT EXISTS idx_index_valuation_code_date ON index_valuation(index_code, update_date DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_index_valuation_code_date ON index_valuation(index_code, update_date);

ALTER TABLE index_valuation ADD COLUMN IF NOT EXISTS index_code VARCHAR(20);
ALTER TABLE index_valuation ADD COLUMN IF NOT EXISTS index_name VARCHAR(100);
ALTER TABLE index_valuation ADD COLUMN IF NOT EXISTS pe DECIMAL(12, 4);
ALTER TABLE index_valuation ADD COLUMN IF NOT EXISTS pb DECIMAL(12, 4);
ALTER TABLE index_valuation ADD COLUMN IF NOT EXISTS pe_percentile DECIMAL(8, 2);
ALTER TABLE index_valuation ADD COLUMN IF NOT EXISTS pb_percentile DECIMAL(8, 2);
ALTER TABLE index_valuation ADD COLUMN IF NOT EXISTS source VARCHAR(100);
ALTER TABLE index_valuation ADD COLUMN IF NOT EXISTS update_date DATE;
ALTER TABLE index_valuation ADD COLUMN IF NOT EXISTS update_time TIMESTAMP DEFAULT NOW();

CREATE TABLE IF NOT EXISTS strategy_config (
    id BIGSERIAL PRIMARY KEY,
    strategy_name VARCHAR(50) NOT NULL UNIQUE,
    strategy_type VARCHAR(20) DEFAULT 'MA',
    params JSONB NOT NULL,
    cron_expr VARCHAR(50) NOT NULL,
    status SMALLINT DEFAULT 1 CHECK (status IN (0, 1)),
    create_time TIMESTAMP DEFAULT NOW(),
    update_time TIMESTAMP DEFAULT NOW()
);

ALTER TABLE strategy_config ADD COLUMN IF NOT EXISTS update_time TIMESTAMP DEFAULT NOW();

CREATE TABLE IF NOT EXISTS trade_signal (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(50),
    signal_type VARCHAR(10) NOT NULL CHECK (signal_type IN ('BUY', 'SELL')),
    suggest_price DECIMAL(10, 2) CHECK (suggest_price IS NULL OR suggest_price > 0),
    signal_date DATE NOT NULL,
    strategy_id BIGINT NOT NULL,
    is_execute SMALLINT DEFAULT 0 CHECK (is_execute IN (0, 1)),
    create_time TIMESTAMP DEFAULT NOW(),
    UNIQUE (stock_code, signal_type, signal_date, strategy_id),
    CONSTRAINT fk_trade_signal_strategy_id
        FOREIGN KEY (strategy_id) REFERENCES strategy_config(id),
    CONSTRAINT fk_trade_signal_stock_code
        FOREIGN KEY (stock_code) REFERENCES stock_basic(stock_code)
);

CREATE INDEX IF NOT EXISTS idx_trade_signal_date ON trade_signal(signal_date);
CREATE INDEX IF NOT EXISTS idx_trade_signal_execute ON trade_signal(is_execute);

CREATE TABLE IF NOT EXISTS position (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL UNIQUE,
    stock_name VARCHAR(50),
    quantity INT NOT NULL CHECK (quantity > 0),
    cost_price DECIMAL(10, 4) NOT NULL CHECK (cost_price > 0),
    current_price DECIMAL(10, 4),
    float_profit DECIMAL(10, 4),
    loss_warning SMALLINT DEFAULT 0 CHECK (loss_warning IN (0, 1)),
    update_time TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_position_stock_code
        FOREIGN KEY (stock_code) REFERENCES stock_basic(stock_code)
);

CREATE TABLE IF NOT EXISTS strategy_run_log (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL,
    run_time TIMESTAMP DEFAULT NOW(),
    annual_return DECIMAL(10, 2),
    max_drawdown DECIMAL(10, 2),
    win_rate DECIMAL(10, 2),
    total_profit DECIMAL(10, 2),
    is_invalid SMALLINT DEFAULT 0 CHECK (is_invalid IN (0, 1)),
    remark TEXT,
    CONSTRAINT fk_strategy_run_log_strategy_id
        FOREIGN KEY (strategy_id) REFERENCES strategy_config(id)
);

CREATE INDEX IF NOT EXISTS idx_strategy_run_log_time ON strategy_run_log(run_time DESC);

CREATE TABLE IF NOT EXISTS strategy_switch_audit (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL,
    strategy_type VARCHAR(20),
    market_status VARCHAR(20),
    decision VARCHAR(10) NOT NULL CHECK (decision IN ('ALLOW', 'BLOCK')),
    reason TEXT,
    actor VARCHAR(64),
    trigger_source VARCHAR(64),
    create_time TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_strategy_switch_audit_strategy_id
        FOREIGN KEY (strategy_id) REFERENCES strategy_config(id)
);
CREATE INDEX IF NOT EXISTS idx_strategy_switch_audit_strategy_time ON strategy_switch_audit(strategy_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_strategy_switch_audit_time ON strategy_switch_audit(create_time DESC);

CREATE TABLE IF NOT EXISTS signal_execution_feedback (
    id BIGSERIAL PRIMARY KEY,
    signal_id BIGINT NOT NULL UNIQUE,
    signal_date DATE NOT NULL,
    due_date DATE NOT NULL,
    check_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('EXECUTED', 'MISSED', 'PENDING')),
    executed_quantity INT DEFAULT 0 CHECK (executed_quantity >= 0),
    last_trade_date DATE,
    overdue_days INT DEFAULT 0 CHECK (overdue_days >= 0),
    remark TEXT,
    update_time TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_signal_execution_feedback_signal_id
        FOREIGN KEY (signal_id) REFERENCES trade_signal(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_signal_execution_feedback_check_date ON signal_execution_feedback(check_date DESC);
CREATE INDEX IF NOT EXISTS idx_signal_execution_feedback_status ON signal_execution_feedback(status);

CREATE TABLE IF NOT EXISTS canary_run_log (
    id BIGSERIAL PRIMARY KEY,
    run_date DATE NOT NULL,
    baseline_strategy_id BIGINT NOT NULL,
    candidate_strategy_id BIGINT NOT NULL,
    months INT NOT NULL CHECK (months > 0),
    comparable_months INT DEFAULT 0 CHECK (comparable_months >= 0),
    candidate_better_annual_months INT DEFAULT 0 CHECK (candidate_better_annual_months >= 0),
    candidate_lower_drawdown_months INT DEFAULT 0 CHECK (candidate_lower_drawdown_months >= 0),
    candidate_higher_win_rate_months INT DEFAULT 0 CHECK (candidate_higher_win_rate_months >= 0),
    candidate_lower_invalid_rate_months INT DEFAULT 0 CHECK (candidate_lower_invalid_rate_months >= 0),
    market_status VARCHAR(20),
    recommendation VARCHAR(30) NOT NULL,
    remark TEXT,
    create_time TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_canary_baseline_strategy_id
        FOREIGN KEY (baseline_strategy_id) REFERENCES strategy_config(id),
    CONSTRAINT fk_canary_candidate_strategy_id
        FOREIGN KEY (candidate_strategy_id) REFERENCES strategy_config(id),
    UNIQUE (run_date, baseline_strategy_id, candidate_strategy_id, months)
);
CREATE INDEX IF NOT EXISTS idx_canary_run_log_run_date ON canary_run_log(run_date DESC);

CREATE TABLE IF NOT EXISTS quant_governance_decision (
    id BIGSERIAL PRIMARY KEY,
    baseline_strategy_id BIGINT NOT NULL REFERENCES strategy_config(id),
    candidate_strategy_id BIGINT NOT NULL REFERENCES strategy_config(id),
    months INT NOT NULL DEFAULT 6 CHECK (months > 0),
    system_recommendation VARCHAR(40) NOT NULL,
    governance_action VARCHAR(30) NOT NULL,
    confidence_level VARCHAR(20),
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decision_source VARCHAR(40) NOT NULL DEFAULT 'shadow_compare',
    effective_from DATE,
    actor VARCHAR(80),
    remark TEXT,
    core_evidences JSONB NOT NULL DEFAULT '[]'::jsonb,
    risk_notes JSONB NOT NULL DEFAULT '[]'::jsonb,
    create_time TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_quant_governance_decision_pair_time
    ON quant_governance_decision(baseline_strategy_id, candidate_strategy_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_quant_governance_decision_action_time
    ON quant_governance_decision(governance_action, create_time DESC);

CREATE TABLE IF NOT EXISTS quant_review_conclusion (
    id BIGSERIAL PRIMARY KEY,
    review_level VARCHAR(20) NOT NULL,
    strategy_id BIGINT,
    stock_code VARCHAR(20),
    signal_id BIGINT,
    date_range_start DATE,
    date_range_end DATE,
    review_target_name VARCHAR(120),
    review_conclusion VARCHAR(20) NOT NULL,
    primary_reason VARCHAR(255),
    secondary_reason VARCHAR(255),
    suggested_action VARCHAR(30),
    confidence_level VARCHAR(20),
    actor VARCHAR(80),
    remark TEXT,
    source_page VARCHAR(40),
    source_action VARCHAR(40),
    evidence_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    create_time TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_quant_review_conclusion_scope_time
    ON quant_review_conclusion(review_level, strategy_id, stock_code, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_quant_review_conclusion_signal_time
    ON quant_review_conclusion(signal_id, create_time DESC);

CREATE TABLE IF NOT EXISTS execution_record (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity INT NOT NULL CHECK (quantity > 0),
    price DECIMAL(10, 4) NOT NULL CHECK (price > 0),
    trade_date DATE NOT NULL,
    strategy_id BIGINT NOT NULL,
    signal_id BIGINT,
    commission DECIMAL(12, 4) DEFAULT 0 CHECK (commission >= 0),
    tax DECIMAL(12, 4) DEFAULT 0 CHECK (tax >= 0),
    slippage DECIMAL(12, 4) DEFAULT 0 CHECK (slippage >= 0),
    gross_amount DECIMAL(18, 4) NOT NULL,
    net_amount DECIMAL(18, 4) NOT NULL,
    external_order_id VARCHAR(100),
    create_time TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_execution_record_strategy_id
        FOREIGN KEY (strategy_id) REFERENCES strategy_config(id),
    CONSTRAINT fk_execution_record_signal_id
        FOREIGN KEY (signal_id) REFERENCES trade_signal(id),
    CONSTRAINT fk_execution_record_stock_code
        FOREIGN KEY (stock_code) REFERENCES stock_basic(stock_code)
);

CREATE INDEX IF NOT EXISTS idx_execution_record_date ON execution_record(trade_date DESC);
CREATE INDEX IF NOT EXISTS idx_execution_record_stock ON execution_record(stock_code);

CREATE TABLE IF NOT EXISTS job_run_batch (
    id BIGSERIAL PRIMARY KEY,
    pipeline_name VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    params JSONB,
    batch_key VARCHAR(100) NOT NULL UNIQUE,
    start_time TIMESTAMP NOT NULL DEFAULT NOW(),
    end_time TIMESTAMP,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_job_run_batch_name_time ON job_run_batch(pipeline_name, start_time DESC);

CREATE TABLE IF NOT EXISTS job_run_step (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    step_name VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'RETRYING', 'SUCCESS', 'FAILED')),
    start_time TIMESTAMP NOT NULL DEFAULT NOW(),
    end_time TIMESTAMP,
    retries INT DEFAULT 0 CHECK (retries >= 0),
    error_message TEXT,
    UNIQUE (batch_id, step_name),
    CONSTRAINT fk_job_run_step_batch_id
        FOREIGN KEY (batch_id) REFERENCES job_run_batch(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS quant_async_job (
    id BIGSERIAL PRIMARY KEY,
    job_key VARCHAR(80) NOT NULL UNIQUE,
    job_type VARCHAR(40) NOT NULL,
    requested_mode VARCHAR(20) NOT NULL,
    resolved_mode VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    actor VARCHAR(80),
    request_payload JSONB NOT NULL,
    normalized_payload JSONB NOT NULL,
    cost_estimate JSONB,
    planned_shard_count INT NOT NULL DEFAULT 0,
    completed_shard_count INT NOT NULL DEFAULT 0,
    failed_shard_count INT NOT NULL DEFAULT 0,
    cancel_requested SMALLINT NOT NULL DEFAULT 0,
    error_message TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),
    start_time TIMESTAMP,
    end_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS quant_async_job_shard (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES quant_async_job(id) ON DELETE CASCADE,
    shard_key VARCHAR(120) NOT NULL UNIQUE,
    strategy_id BIGINT,
    shard_index INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    symbol_count INT NOT NULL DEFAULT 0,
    payload JSONB NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(120),
    lease_expires_at TIMESTAMP,
    heartbeat_at TIMESTAMP,
    last_error TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),
    start_time TIMESTAMP,
    end_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS quant_async_job_attempt (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES quant_async_job(id) ON DELETE CASCADE,
    shard_id BIGINT NOT NULL REFERENCES quant_async_job_shard(id) ON DELETE CASCADE,
    attempt_no INT NOT NULL,
    worker_id VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    error_class VARCHAR(200),
    error_message TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    UNIQUE (shard_id, attempt_no)
);

CREATE TABLE IF NOT EXISTS quant_async_job_summary (
    job_id BIGINT PRIMARY KEY REFERENCES quant_async_job(id) ON DELETE CASCADE,
    total_symbols INT NOT NULL DEFAULT 0,
    processed_symbols INT NOT NULL DEFAULT 0,
    skipped_symbols INT NOT NULL DEFAULT 0,
    total_strategies INT NOT NULL DEFAULT 0,
    signal_count INT NOT NULL DEFAULT 0,
    invalid_count INT NOT NULL DEFAULT 0,
    runtime_ms BIGINT NOT NULL DEFAULT 0,
    payload JSONB NOT NULL,
    update_time TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS quant_async_job_result (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES quant_async_job(id) ON DELETE CASCADE,
    strategy_id BIGINT,
    stock_code VARCHAR(20) NOT NULL,
    signal_type VARCHAR(10),
    annual_return DECIMAL(10, 2),
    max_drawdown DECIMAL(10, 2),
    win_rate DECIMAL(10, 2),
    total_profit DECIMAL(10, 2),
    trade_count INT NOT NULL DEFAULT 0,
    total_cost DECIMAL(12, 4) NOT NULL DEFAULT 0,
    is_invalid SMALLINT NOT NULL DEFAULT 0,
    remark TEXT,
    payload JSONB NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (job_id, strategy_id, stock_code)
);

CREATE INDEX IF NOT EXISTS idx_quant_async_job_status_time ON quant_async_job(status, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_quant_async_job_shard_job_status ON quant_async_job_shard(job_id, status, shard_index);
CREATE INDEX IF NOT EXISTS idx_quant_async_job_shard_lease ON quant_async_job_shard(status, lease_expires_at);
CREATE INDEX IF NOT EXISTS idx_quant_async_job_result_job_strategy ON quant_async_job_result(job_id, strategy_id, stock_code);

DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM strategy_config WHERE strategy_name = 'MA20_CROSS') THEN
        IF EXISTS (SELECT 1 FROM strategy_config WHERE id = 1) THEN
            INSERT INTO strategy_config (strategy_name, strategy_type, params, cron_expr, status)
            VALUES (
                'MA20_CROSS',
                'MA',
                '{
                  "ma_period": 20,
                  "enabled_regimes": ["bull", "volatile", "bear"],
                  "stop_loss_rate": 0.08,
                  "max_single_position_pct": 0.15,
                  "max_total_position_pct": 0.80,
                  "portfolio_capital": 100000,
                  "allocator_base_weight": 0.6,
                  "regime_budget_weights": {"bull": 1.0, "volatile": 0.8, "bear": 0.5, "panic": 0.0, "default": 0.8},
                  "commission_rate": 0.0001,
                  "slippage_rate": 0.0005,
                  "stamp_duty_rate": 0.001,
                  "monthly_max_drawdown_limit_pct": 12,
                  "latest_month_win_rate_min_pct": 40,
                  "max_consecutive_losses": 6,
                  "underperform_months": 2,
                  "invalid_trigger_count": 2
                }'::jsonb,
                '0 30 15 * * ?',
                1
            )
            ON CONFLICT (strategy_name) DO NOTHING;
        ELSE
            INSERT INTO strategy_config (id, strategy_name, strategy_type, params, cron_expr, status)
            VALUES (
                1,
                'MA20_CROSS',
                'MA',
                '{
                  "ma_period": 20,
                  "enabled_regimes": ["bull", "volatile", "bear"],
                  "stop_loss_rate": 0.08,
                  "max_single_position_pct": 0.15,
                  "max_total_position_pct": 0.80,
                  "portfolio_capital": 100000,
                  "allocator_base_weight": 0.6,
                  "regime_budget_weights": {"bull": 1.0, "volatile": 0.8, "bear": 0.5, "panic": 0.0, "default": 0.8},
                  "commission_rate": 0.0001,
                  "slippage_rate": 0.0005,
                  "stamp_duty_rate": 0.001,
                  "monthly_max_drawdown_limit_pct": 12,
                  "latest_month_win_rate_min_pct": 40,
                  "max_consecutive_losses": 6,
                  "underperform_months": 2,
                  "invalid_trigger_count": 2
                }'::jsonb,
                '0 30 15 * * ?',
                1
            )
            ON CONFLICT (strategy_name) DO NOTHING;
        END IF;
    END IF;
END
$$;

INSERT INTO strategy_config (strategy_name, strategy_type, params, cron_expr, status)
VALUES (
    'MA_DUAL_CROSS_5_20',
    'MA_DUAL',
    '{
      "short_ma_period": 5,
      "long_ma_period": 20,
      "enabled_regimes": ["bull", "volatile"],
      "stop_loss_rate": 0.08,
      "max_single_position_pct": 0.12,
      "max_total_position_pct": 0.75,
      "portfolio_capital": 100000,
      "allocator_base_weight": 0.4,
      "regime_budget_weights": {"bull": 1.1, "volatile": 0.9, "bear": 0.4, "panic": 0.0, "default": 0.8},
      "commission_rate": 0.0001,
      "slippage_rate": 0.0005,
      "stamp_duty_rate": 0.001,
      "monthly_max_drawdown_limit_pct": 12,
      "latest_month_win_rate_min_pct": 40,
      "max_consecutive_losses": 6,
      "underperform_months": 2,
      "invalid_trigger_count": 2
    }'::jsonb,
    '0 35 15 * * ?',
    1
)
ON CONFLICT (strategy_name) DO NOTHING;
