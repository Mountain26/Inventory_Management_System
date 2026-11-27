-- PO workflow enhancements
ALTER TABLE purchase_order
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN approved_by BIGINT,
    ADD COLUMN approved_at DATETIME,
    ADD COLUMN rejection_reason VARCHAR(500),
    ADD COLUMN notes VARCHAR(500);

ALTER TABLE purchase_order
    ADD CONSTRAINT fk_po_approved_by FOREIGN KEY (approved_by) REFERENCES `user`(id);

ALTER TABLE purchase_order_item
    ADD COLUMN quantity_received INT NOT NULL DEFAULT 0;
