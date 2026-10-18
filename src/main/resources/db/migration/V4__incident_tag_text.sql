-- Người dân chỉ chọn 1 gợi ý sự cố mỗi lần: incident_tags (mảng jsonb, hay gặp [] / [""]) -> incident_tag (text).
-- Ca cũ có nhiều nhãn thì nối bằng ", " để không mất dữ liệu; không có nhãn -> NULL.
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS incident_tag varchar(200);

UPDATE incidents i
SET incident_tag = left(NULLIF((
    SELECT string_agg(trim(t.tag), ', ')
    FROM jsonb_array_elements_text(
        CASE WHEN jsonb_typeof(i.incident_tags) = 'array' THEN i.incident_tags ELSE '[]'::jsonb END) AS t(tag)
    WHERE trim(t.tag) <> ''
), ''), 200);

ALTER TABLE incidents DROP COLUMN IF EXISTS incident_tags;
