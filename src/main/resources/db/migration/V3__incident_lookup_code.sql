-- Mã tra cứu công khai của ca (8 ký tự, hiển thị dạng XXXX-XXXX): người dân dùng mã + 4 số cuối SĐT
-- để xem lại thông tin cứu hộ mà không lộ incidents.id. Bảng chữ bỏ 0/O, 1/I/L cho dễ đọc.
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS lookup_code varchar(8);

-- Ca cũ: sinh mã ngẫu nhiên từng dòng (where tham chiếu dòng ngoài để Postgres tính lại cho mỗi dòng)
UPDATE incidents i
SET lookup_code = (
    SELECT string_agg(substr('23456789ABCDEFGHJKMNPQRSTUVWXYZ', 1 + floor(random() * 31)::int, 1), '')
    FROM generate_series(1, 8) g
    WHERE i.id IS NOT NULL
)
WHERE lookup_code IS NULL;

ALTER TABLE incidents ALTER COLUMN lookup_code SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_incidents_lookup_code ON incidents (lookup_code);
