-- Mỗi dòng CONSUME chỉ được hoàn đúng một lần (p5 R7). Dòng REFUND ghi ref_type = NULL và
-- ref_id = id dòng CONSUME gốc, nên index (ref_type, ref_id) không chặn được (NULL khác nhau):
-- index chỉ trên ref_id, lọc type = 'REFUND'. Lưới an toàn cuối sau phép kiểm tra trong khoá lô
-- của FefoCreditConsumptionService.refund — chặn hoàn hai lần bằng hai idempotency key khác nhau.
CREATE UNIQUE INDEX uq_credit_ledger_refund_ref
    ON credit_ledger (ref_id)
    WHERE type = 'REFUND' AND ref_id IS NOT NULL;
