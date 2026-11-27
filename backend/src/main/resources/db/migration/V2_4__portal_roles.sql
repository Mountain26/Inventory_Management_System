-- Add new user columns for portal support
ALTER TABLE `user`
    MODIFY COLUMN role VARCHAR(20) NOT NULL,
    ADD COLUMN linked_supplier_id BIGINT;
