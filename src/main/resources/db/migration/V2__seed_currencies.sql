-- Default currencies on a brand-new database (spec requirement 14b).
-- ON CONFLICT keeps the migration safe if a code already exists.
INSERT INTO currency (code, name, created_at, updated_at) VALUES
    ('USD', '美元',   now(), now()),
    ('EUR', '歐元',   now(), now()),
    ('GBP', '英鎊',   now(), now()),
    ('TWD', '新台幣', now(), now()),
    ('JPY', '日圓',   now(), now())
ON CONFLICT (code) DO NOTHING;
