-- SUA LOI H4: uq_color_chart_active_per_batch (V9) dua ca cot status vao khoa, nen thuc te rang
-- buoc "moi (product_line, production_batch) chi mot dong cho MOI status" thay vi "chi mot bang
-- ACTIVE" nhu y do ghi trong V9 (p4 D5): publish lan hai va tao DRAFT thu hai cho cung goi/lo deu
-- dung index. Thay bang partial unique index chi tren dong ACTIVE; DRAFT/ARCHIVED khong gioi han.
DROP INDEX IF EXISTS uq_color_chart_active_per_batch;

CREATE UNIQUE INDEX uq_color_chart_active_per_batch
    ON color_chart (product_line, coalesce(production_batch, ''))
    WHERE status = 'ACTIVE';
