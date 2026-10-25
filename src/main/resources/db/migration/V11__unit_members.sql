-- V11: cán bộ của đơn vị (unit_members) + lịch sử trực ban (unit_duty_assignments).
--   accounts = cổng vào của đơn vị (email + mật khẩu đơn vị); unit_members = người (họ tên, cấp bậc, PIN).
--   Mọi cột "ai làm" (người giữ ca, điều động, ký, tải ảnh...) trỏ về unit_members thay cho accounts.
--   Tài khoản cũ: mỗi tài khoản cán bộ / admin sinh 1 cán bộ có member_id = account_id, nên các giá trị cũ
--   giữ nguyên, chỉ đổi khoá ngoại. Admin có đúng 1 cán bộ ẩn, đăng nhập không qua PIN.

-- ---------------------------------------------------------------- 1. unit_members
CREATE TABLE unit_members (
    member_id             uuid PRIMARY KEY,
    account_id            uuid         NOT NULL REFERENCES accounts (account_id),
    full_name             varchar(150) NOT NULL,
    badge_number          varchar(30),
    rank                  varchar(30),            -- PoliceRank
    position              varchar(150),           -- chức vụ, chữ tự do
    unit_role             varchar(20)  NOT NULL,  -- COMMANDER / MEMBER
    phone                 varchar(10),
    pin_hash              varchar(255),           -- bcrypt; null = chưa kích hoạt
    activation_code_hash  varchar(255),
    activation_expires_at timestamp(6),
    failed_pin_count      integer      NOT NULL DEFAULT 0,
    locked_at             timestamp(6),
    active                boolean      NOT NULL DEFAULT true,
    created_at            timestamp(6) NOT NULL,
    created_by            uuid REFERENCES unit_members (member_id)
);
CREATE INDEX ix_unit_members_account ON unit_members (account_id) WHERE active;
CREATE UNIQUE INDEX ux_unit_members_badge ON unit_members (account_id, badge_number)
    WHERE active AND badge_number IS NOT NULL;

-- ---------------------------------------------------------------- 2. unit_duty_assignments
-- ended_at chỉ ghi khi có người mới được giao trực ban; mỗi đơn vị 1 dòng đang mở
CREATE TABLE unit_duty_assignments (
    id          bigserial PRIMARY KEY,
    account_id  uuid         NOT NULL REFERENCES accounts (account_id),
    member_id   uuid         NOT NULL REFERENCES unit_members (member_id),
    assigned_by uuid REFERENCES unit_members (member_id),
    started_at  timestamp(6) NOT NULL,
    ended_at    timestamp(6)
);
CREATE UNIQUE INDEX ux_unit_duty_current ON unit_duty_assignments (account_id) WHERE ended_at IS NULL;
CREATE INDEX ix_unit_duty_account ON unit_duty_assignments (account_id, started_at DESC);

-- ---------------------------------------------------------------- 3. accounts
-- unit_account = tài khoản đơn vị theo định dạng mới (seeder tạo); tài khoản cán bộ cũ = false, seeder gộp vào đơn vị
ALTER TABLE accounts ADD COLUMN unit_account boolean NOT NULL DEFAULT false;

-- ---------------------------------------------------------------- 4. tài khoản cũ -> cán bộ (member_id = account_id)
INSERT INTO unit_members (member_id, account_id, full_name, phone, unit_role, created_at)
SELECT a.account_id, a.account_id,
       COALESCE(NULLIF(TRIM(CONCAT_WS(' ', a.last_name, a.first_name)), ''), a.email),
       a.phone, 'MEMBER', COALESCE(a.created_date, now())
FROM accounts a
JOIN roles r ON r.role_id = a.role_id
WHERE r.role_name IN ('OFFICER', 'ADMIN');

-- ---------------------------------------------------------------- 5. đổi khoá ngoại accounts -> unit_members
DO $$
DECLARE c record;
BEGIN
    FOR c IN
        SELECT con.conname, cls.relname
        FROM pg_constraint con
        JOIN pg_class cls ON cls.oid = con.conrelid
        WHERE con.contype = 'f'
          AND con.confrelid = 'accounts'::regclass
          AND cls.relname IN ('incidents', 'incident_claims', 'incident_dispatches', 'incident_media',
                              'incident_routes', 'incident_route_positions', 'incident_reports', 'incident_documents')
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', c.relname, c.conname);
    END LOOP;
END $$;

ALTER TABLE incident_claims RENAME COLUMN account_id TO member_id;
ALTER INDEX IF EXISTS ix_incident_claims_account RENAME TO ix_incident_claims_member;
ALTER TABLE incident_media RENAME COLUMN uploaded_by_account_id TO uploaded_by_member_id;
ALTER TABLE incident_route_positions RENAME COLUMN account_id TO member_id;

ALTER TABLE incidents
    ADD CONSTRAINT fk_incidents_handler FOREIGN KEY (handler_id) REFERENCES unit_members (member_id),
    ADD CONSTRAINT fk_incidents_dest_updated_by FOREIGN KEY (dest_updated_by) REFERENCES unit_members (member_id),
    ADD CONSTRAINT fk_incidents_officer_confirmed_by FOREIGN KEY (officer_confirmed_by) REFERENCES unit_members (member_id),
    ADD CONSTRAINT fk_incidents_escalated_by FOREIGN KEY (escalated_by) REFERENCES unit_members (member_id),
    ADD CONSTRAINT fk_incidents_transferred_by FOREIGN KEY (transferred_by) REFERENCES unit_members (member_id);
ALTER TABLE incident_claims
    ADD CONSTRAINT fk_incident_claims_member FOREIGN KEY (member_id) REFERENCES unit_members (member_id),
    ADD CONSTRAINT fk_incident_claims_handed_to FOREIGN KEY (handed_to_id) REFERENCES unit_members (member_id);
ALTER TABLE incident_dispatches
    ADD CONSTRAINT fk_incident_dispatches_by FOREIGN KEY (dispatched_by) REFERENCES unit_members (member_id),
    ADD CONSTRAINT fk_incident_dispatches_assigned_to FOREIGN KEY (assigned_to) REFERENCES unit_members (member_id);
ALTER TABLE incident_media
    ADD CONSTRAINT fk_incident_media_member FOREIGN KEY (uploaded_by_member_id) REFERENCES unit_members (member_id);
ALTER TABLE incident_routes
    ADD CONSTRAINT fk_incident_routes_computed_by FOREIGN KEY (computed_by) REFERENCES unit_members (member_id);
ALTER TABLE incident_route_positions
    ADD CONSTRAINT fk_incident_route_positions_member FOREIGN KEY (member_id) REFERENCES unit_members (member_id);
ALTER TABLE incident_reports
    ADD CONSTRAINT fk_incident_reports_created_by FOREIGN KEY (created_by) REFERENCES unit_members (member_id);
ALTER TABLE incident_documents
    ADD CONSTRAINT fk_incident_documents_officer_signed_by FOREIGN KEY (officer_signed_by) REFERENCES unit_members (member_id),
    ADD CONSTRAINT fk_incident_documents_proxy_by FOREIGN KEY (citizen_proxy_by) REFERENCES unit_members (member_id),
    ADD CONSTRAINT fk_incident_documents_created_by FOREIGN KEY (created_by) REFERENCES unit_members (member_id),
    ADD CONSTRAINT fk_incident_documents_updated_by FOREIGN KEY (updated_by) REFERENCES unit_members (member_id);
