-- 明星图鉴：人物、作品、人物与作品的关系（docs/design/star-catalog.md）。
-- 关系行带身份语义（role_type / role_name / billing_order），不扁平化成两端 id。
-- 首版照片直接存授权图片 URL；photo_source 为空时 facade 不透出 photo_url（按占位图渲染）。

CREATE TABLE persons (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    original_name VARCHAR(200),
    aliases TEXT[] NOT NULL DEFAULT '{}',
    nationality VARCHAR(100) NOT NULL,
    profession_types TEXT[] NOT NULL,
    bio TEXT,
    photo_url VARCHAR(500),
    photo_source VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT persons_status_ck CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT persons_name_not_blank_ck CHECK (char_length(btrim(name)) > 0)
);

CREATE TABLE works (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    type VARCHAR(50) NOT NULL,
    year INT,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT works_status_ck CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT works_year_ck CHECK (year IS NULL OR (year >= 1900 AND year <= 2100))
);

-- 一人在一部作品中的一种身份一行；主演 + 演唱主题曲 = 两行。
-- role_name NOT NULL DEFAULT ''：同类型多首主题曲靠 role_name 区分，唯一约束才不会因 NULL 放水。
CREATE TABLE person_works (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    person_id BIGINT NOT NULL,
    work_id BIGINT NOT NULL,
    role_type VARCHAR(50) NOT NULL,
    role_name VARCHAR(200) NOT NULL DEFAULT '',
    billing_order INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT person_works_person_fk FOREIGN KEY (person_id) REFERENCES persons (id) ON DELETE CASCADE,
    CONSTRAINT person_works_work_fk FOREIGN KEY (work_id) REFERENCES works (id) ON DELETE CASCADE,
    CONSTRAINT person_works_unique UNIQUE (person_id, work_id, role_type, role_name)
);

CREATE INDEX person_works_work_idx ON person_works (work_id);
CREATE INDEX persons_profession_idx ON persons USING gin (profession_types);

CREATE TRIGGER trg_person_updated_at BEFORE UPDATE ON persons
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_work_updated_at BEFORE UPDATE ON works
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
