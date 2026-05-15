CREATE TABLE IF NOT EXISTS stock_basic (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL UNIQUE,
    stock_name VARCHAR(50),
    industry VARCHAR(50),
    is_st SMALLINT DEFAULT 0,
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
    UNIQUE (stock_code, trade_date)
);

CREATE INDEX IF NOT EXISTS idx_stock_daily_code ON stock_daily(stock_code);
CREATE INDEX IF NOT EXISTS idx_stock_daily_trade_date ON stock_daily(trade_date);

CREATE TABLE IF NOT EXISTS market_status (
    id BIGSERIAL PRIMARY KEY,
    trade_date DATE NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    raw_status VARCHAR(20),
    hs300_close DECIMAL(12, 4),
    hs300_ma20 DECIMAL(12, 4),
    hs300_ma60 DECIMAL(12, 4),
    hs300_above_ma20 SMALLINT,
    hs300_above_ma60 SMALLINT,
    up_ratio DECIMAL(8, 6),
    remark TEXT,
    update_time TIMESTAMP DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_market_status_trade_date ON market_status(trade_date);

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
CREATE UNIQUE INDEX IF NOT EXISTS uk_index_valuation_code_date ON index_valuation(index_code, update_date);

CREATE TABLE IF NOT EXISTS strategy_config (
    id BIGSERIAL PRIMARY KEY,
    strategy_name VARCHAR(50) NOT NULL UNIQUE,
    strategy_type VARCHAR(20) DEFAULT 'MA',
    params JSONB NOT NULL,
    cron_expr VARCHAR(50) NOT NULL,
    status SMALLINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT NOW(),
    update_time TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS trade_signal (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(50),
    signal_type VARCHAR(10) NOT NULL,
    suggest_price DECIMAL(10, 2),
    signal_date DATE NOT NULL,
    strategy_id BIGINT NOT NULL,
    is_execute SMALLINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT NOW(),
    UNIQUE (stock_code, signal_type, signal_date, strategy_id)
);

CREATE TABLE IF NOT EXISTS position (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL UNIQUE,
    stock_name VARCHAR(50),
    quantity INT NOT NULL,
    cost_price DECIMAL(10, 2) NOT NULL,
    current_price DECIMAL(10, 2),
    float_profit DECIMAL(10, 4),
    loss_warning SMALLINT DEFAULT 0,
    update_time TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS strategy_run_log (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL,
    run_time TIMESTAMP DEFAULT NOW(),
    annual_return DECIMAL(10, 2),
    max_drawdown DECIMAL(10, 2),
    win_rate DECIMAL(10, 2),
    total_profit DECIMAL(10, 2),
    is_invalid SMALLINT DEFAULT 0,
    remark TEXT
);

CREATE TABLE IF NOT EXISTS strategy_switch_audit (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL,
    strategy_type VARCHAR(20),
    market_status VARCHAR(20),
    decision VARCHAR(10) NOT NULL,
    reason TEXT,
    actor VARCHAR(64),
    trigger_source VARCHAR(64),
    create_time TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_strategy_switch_audit_strategy_time ON strategy_switch_audit(strategy_id, create_time DESC);

CREATE TABLE IF NOT EXISTS signal_execution_feedback (
    id BIGSERIAL PRIMARY KEY,
    signal_id BIGINT NOT NULL UNIQUE,
    signal_date DATE NOT NULL,
    due_date DATE NOT NULL,
    check_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    executed_quantity INT DEFAULT 0,
    last_trade_date DATE,
    overdue_days INT DEFAULT 0,
    remark TEXT,
    update_time TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_signal_execution_feedback_check_date ON signal_execution_feedback(check_date DESC);
CREATE INDEX IF NOT EXISTS idx_signal_execution_feedback_status ON signal_execution_feedback(status);

CREATE TABLE IF NOT EXISTS canary_run_log (
    id BIGSERIAL PRIMARY KEY,
    run_date DATE NOT NULL,
    baseline_strategy_id BIGINT NOT NULL,
    candidate_strategy_id BIGINT NOT NULL,
    months INT NOT NULL,
    comparable_months INT DEFAULT 0,
    candidate_better_annual_months INT DEFAULT 0,
    candidate_lower_drawdown_months INT DEFAULT 0,
    candidate_higher_win_rate_months INT DEFAULT 0,
    candidate_lower_invalid_rate_months INT DEFAULT 0,
    market_status VARCHAR(20),
    recommendation VARCHAR(30) NOT NULL,
    remark TEXT,
    create_time TIMESTAMP DEFAULT NOW(),
    UNIQUE (run_date, baseline_strategy_id, candidate_strategy_id, months)
);
CREATE INDEX IF NOT EXISTS idx_canary_run_log_run_date ON canary_run_log(run_date DESC);

DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM strategy_config WHERE strategy_name = 'MA20_CROSS') THEN
        IF EXISTS (SELECT 1 FROM strategy_config WHERE id = 1) THEN
            INSERT INTO strategy_config (strategy_name, strategy_type, params, cron_expr, status)
            VALUES (
                'MA20_CROSS',
                'MA',
                '{"ma_period": 20, "enabled_regimes": ["bull", "volatile", "bear"], "stop_loss_rate": 0.08, "max_single_position_pct": 0.15, "max_total_position_pct": 0.80, "portfolio_capital": 100000, "allocator_base_weight": 0.6, "regime_budget_weights": {"bull": 1.0, "volatile": 0.8, "bear": 0.5, "panic": 0.0, "default": 0.8}}'::jsonb,
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
                '{"ma_period": 20, "enabled_regimes": ["bull", "volatile", "bear"], "stop_loss_rate": 0.08, "max_single_position_pct": 0.15, "max_total_position_pct": 0.80, "portfolio_capital": 100000, "allocator_base_weight": 0.6, "regime_budget_weights": {"bull": 1.0, "volatile": 0.8, "bear": 0.5, "panic": 0.0, "default": 0.8}}'::jsonb,
                '0 30 15 * * ?',
                1
            )
            ON CONFLICT (strategy_name) DO NOTHING;
        END IF;
    END IF;
END
$$;
