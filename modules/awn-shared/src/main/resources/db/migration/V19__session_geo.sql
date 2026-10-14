-- 场次地理位置（docs/design/ui-system.md §6.1：每场次后台配置 city/address/lat/lng）
-- 地理归属场馆（venue 携带城市/地址/坐标），场次可单独指定场馆（如巡回赛每场换城市），
-- 未指定时读路径继承父活动场馆；场次表另留直配列，供运营逐场覆盖。
-- 坐标成对出现：经度或纬度单给一律视为非法，由应用层守卫（Venue 构造 / Session.applyGeo）
-- 拒绝，这里只提供可空列，不在库里造半截坐标。
ALTER TABLE venue ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE venue ADD COLUMN longitude DOUBLE PRECISION;

-- 场次可单独指定场馆（覆盖父活动场馆，如巡回赛每场换城市）
ALTER TABLE session ADD COLUMN venue_id BIGINT REFERENCES venue (id);
CREATE INDEX ix_session_venue ON session (venue_id);

ALTER TABLE session ADD COLUMN city VARCHAR(64);
ALTER TABLE session ADD COLUMN address VARCHAR(256);
ALTER TABLE session ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE session ADD COLUMN longitude DOUBLE PRECISION;

-- 列表按城市降级排序（定位缺失时按城市名），建索引支撑
CREATE INDEX ix_session_city ON session (city);
