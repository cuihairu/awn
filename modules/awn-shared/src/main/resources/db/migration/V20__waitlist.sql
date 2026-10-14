-- 排队 / 候补（售罄场次的等待队列，docs/api/contracts.md「排队 / 候补」）
-- 候补条目只登记排队意愿，不占库存、不预生成订单；
-- 库存释放后由 Worker 递补任务为队首原子预留并生成待支付订单（同一事务：
-- 抢占条目 + 建单 + 回填订单号），预留失败整条回滚，条目保持 WAITING。
--
-- 位次按同池 WAITING 条目的加入顺序（created_at, id）计算，不落库排名字段：
-- 排队人数是派生值，落库会在并发递补与退出排队时失真。
CREATE TABLE waitlist_entries (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    waitlist_no     VARCHAR(64) NOT NULL UNIQUE,
    user_id         BIGINT NOT NULL,
    session_id      VARCHAR(64) NOT NULL,
    pool_code       VARCHAR(64) NOT NULL REFERENCES inventory_pool (pool_code),
    product_id      VARCHAR(64) NOT NULL,
    product_name    VARCHAR(128) NOT NULL,
    quantity        INT NOT NULL CHECK (quantity > 0),
    unit_price      BIGINT NOT NULL CHECK (unit_price >= 0),
    currency        VARCHAR(8) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'WAITING'
                    CHECK (status IN ('WAITING', 'PROMOTED', 'CLAIMED', 'EXPIRED', 'CANCELLED')),
    order_no        VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- FIFO 递补：按池取等待中的条目，顺序即加入顺序
CREATE INDEX ix_waitlist_pool_status ON waitlist_entries (pool_code, status, created_at, id);
-- 同用户同池占用位次判重（排队中与已递补都算占位）
CREATE INDEX ix_waitlist_user_pool ON waitlist_entries (user_id, pool_code, status);
-- 我的候补列表
CREATE INDEX ix_waitlist_user ON waitlist_entries (user_id, created_at);

-- 候补条目随状态迁移更新时间戳
CREATE TRIGGER trg_waitlist_updated_at BEFORE UPDATE ON waitlist_entries
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();