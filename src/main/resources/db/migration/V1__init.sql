CREATE TABLE products (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku         VARCHAR(64)   NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    description TEXT,
    price       NUMERIC(12,2) NOT NULL,
    stock       INTEGER       NOT NULL,
    category    VARCHAR(64)   NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_sku UNIQUE (sku)
);
ALTER TABLE products SET (autovacuum_enabled = false);
