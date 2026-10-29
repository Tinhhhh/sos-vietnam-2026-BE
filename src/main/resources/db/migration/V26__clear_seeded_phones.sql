-- Số điện thoại cán bộ do seeder sinh ngẫu nhiên (09xxxxxxxx) có thể là số thật: SMS link hiện trường / ca kết thúc
-- gửi nhầm người. Xoá hết, chỉ giữ 2 số thử nghiệm của nhóm. Số của tài khoản admin seed cũng bỏ.
UPDATE unit_members
SET phone = NULL
WHERE phone IS NOT NULL
  AND RIGHT(REGEXP_REPLACE(phone, '\D', '', 'g'), 9) NOT IN ('794801006', '379704006');

UPDATE accounts
SET phone = NULL
WHERE phone IS NOT NULL
  AND RIGHT(REGEXP_REPLACE(phone, '\D', '', 'g'), 9) NOT IN ('794801006', '379704006');
