-- V8: nhãn sự cố lưu chữ thuần, bỏ emoji của ô gợi ý trên form ("🚨 Bị cướp giật / Móc túi" -> "Bị cướp giật / Móc túi").
-- Giữ chữ, số, khoảng trắng và / , . ( ) & + -; gộp khoảng trắng thừa.
UPDATE incidents
SET incident_tag = btrim(regexp_replace(regexp_replace(incident_tag, '[^[:alnum:][:space:]/,.()&+-]', '', 'g'), '\s+', ' ', 'g'))
WHERE incident_tag IS NOT NULL
  AND incident_tag <> btrim(regexp_replace(regexp_replace(incident_tag, '[^[:alnum:][:space:]/,.()&+-]', '', 'g'), '\s+', ' ', 'g'));
