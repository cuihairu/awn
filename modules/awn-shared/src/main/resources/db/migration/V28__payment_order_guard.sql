-- At most one payment that is not explicitly FAILED may exist per order.
-- Application-level advisory locking gives a readable conflict; this index is
-- the database backstop against concurrent writers and alternate code paths.
CREATE UNIQUE INDEX ux_payments_one_active_per_order
    ON payments (order_no)
    WHERE status <> 'FAILED';
