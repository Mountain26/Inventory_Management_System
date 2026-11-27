-- Initial schema for an empty MOUNTAIN database. Later migrations extend it.
CREATE TABLE `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6), updated_at DATETIME(6)
);
CREATE TABLE category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE, description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6), updated_at DATETIME(6)
);
CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(50) NOT NULL UNIQUE, barcode VARCHAR(50) UNIQUE,
    name VARCHAR(150) NOT NULL, category_id BIGINT NOT NULL,
    unit VARCHAR(20) NOT NULL, min_stock_threshold INT NOT NULL DEFAULT 0,
    current_quantity INT NOT NULL DEFAULT 0, image_path VARCHAR(255),
    version INT NOT NULL DEFAULT 0, is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6), updated_at DATETIME(6),
    FOREIGN KEY (category_id) REFERENCES category(id)
);
CREATE TABLE supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL, contact_person VARCHAR(100),
    phone VARCHAR(20), email VARCHAR(150), address VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6), updated_at DATETIME(6)
);
CREATE TABLE purchase_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, supplier_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    order_date DATETIME(6) NOT NULL, received_date DATETIME(6),
    created_by BIGINT NOT NULL, created_at DATETIME(6), updated_at DATETIME(6),
    FOREIGN KEY (supplier_id) REFERENCES supplier(id),
    FOREIGN KEY (created_by) REFERENCES `user`(id)
);
CREATE TABLE purchase_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
    quantity_ordered INT NOT NULL, unit_price DECIMAL(10,2),
    created_at DATETIME(6), updated_at DATETIME(6),
    FOREIGN KEY (purchase_order_id) REFERENCES purchase_order(id),
    FOREIGN KEY (product_id) REFERENCES product(id)
);
CREATE TABLE inventory_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL, quantity INT NOT NULL,
    quantity_before INT NOT NULL, quantity_after INT NOT NULL,
    reference_purchase_order_id BIGINT, linked_transaction_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    performed_by BIGINT NOT NULL, approved_by BIGINT, reason VARCHAR(255), created_at DATETIME(6),
    FOREIGN KEY (product_id) REFERENCES product(id),
    FOREIGN KEY (reference_purchase_order_id) REFERENCES purchase_order(id),
    FOREIGN KEY (linked_transaction_id) REFERENCES inventory_transaction(id),
    FOREIGN KEY (performed_by) REFERENCES `user`(id),
    FOREIGN KEY (approved_by) REFERENCES `user`(id)
);
CREATE TABLE user_activity_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL, entity_type VARCHAR(50), entity_id BIGINT,
    details VARCHAR(500), created_at DATETIME(6),
    FOREIGN KEY (user_id) REFERENCES `user`(id)
);
