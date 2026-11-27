-- MySQL 8.0.16+ enforces CHECK constraints.
ALTER TABLE warehouse
    ADD COLUMN default_slot TINYINT GENERATED ALWAYS AS (CASE WHEN is_default = 1 THEN 1 ELSE NULL END) STORED,
    ADD CONSTRAINT uq_warehouse_default UNIQUE (default_slot),
    ADD CONSTRAINT ck_default_warehouse_active CHECK (is_default = 0 OR is_active = 1);
ALTER TABLE warehouse_stock
    ADD COLUMN version INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_warehouse_stock_nonnegative CHECK (current_quantity >= 0);
ALTER TABLE warehouse_transfer
    ADD COLUMN version INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_transfer_quantity_positive CHECK (quantity > 0),
    ADD CONSTRAINT ck_transfer_warehouses_different CHECK (from_warehouse_id <> to_warehouse_id);
ALTER TABLE purchase_order ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE inventory_transaction
    ADD COLUMN version INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_transaction_balances CHECK (quantity_before >= 0 AND quantity_after >= 0);
ALTER TABLE invoice
    ADD COLUMN version INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_invoice_amounts CHECK (total_amount > 0 AND paid_amount >= 0 AND paid_amount <= total_amount);
ALTER TABLE product
    ADD CONSTRAINT ck_product_balances CHECK (current_quantity >= 0 AND min_stock_threshold >= 0);
ALTER TABLE purchase_order_item
    ADD COLUMN version INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_po_line_quantities CHECK (quantity_ordered > 0 AND quantity_received >= 0 AND quantity_received <= quantity_ordered),
    ADD CONSTRAINT ck_po_line_price CHECK (unit_price >= 0),
    ADD CONSTRAINT uq_po_product UNIQUE (purchase_order_id, product_id);
CREATE INDEX ix_transaction_product_created ON inventory_transaction (product_id, created_at);
CREATE INDEX ix_transaction_status ON inventory_transaction (status);
CREATE INDEX ix_po_supplier_status ON purchase_order (supplier_id, status);
