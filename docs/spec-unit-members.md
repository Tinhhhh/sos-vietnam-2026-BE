# Spec: Cán bộ đơn vị + trực ban (cách 2: bảng riêng) — NHÁP chờ duyệt 24/10/2026

## 1. Mô hình
- `accounts` = **cổng vào của đơn vị** (vd `ca.ninhkieu@sos.vn`), giữ tỉnh / xã / lực lượng / cấp như cũ. Không còn là "người".
- `unit_members` (bảng mới) = **người**. Mọi chỗ ghi "ai làm" chuyển sang `member_id`.
- 1 đơn vị đăng nhập được trên nhiều máy cùng lúc; mỗi máy sau khi nhập PIN là phiên của 1 cán bộ. Mỗi cán bộ giữ tối đa 1 ca.
- ADMIN (cấp quốc gia) giữ đăng nhập trực tiếp như cũ, không qua PIN (chốt lại nếu muốn ADMIN cũng có danh sách cán bộ).

## 2. DB (Flyway V11)
### `unit_members`
| cột | kiểu | ghi chú |
|---|---|---|
| member_id | uuid PK | |
| account_id | uuid FK accounts | đơn vị |
| full_name | text | |
| badge_number | text | số hiệu, unique trong đơn vị |
| rank | text (enum `PoliceRank`) | Binh nhì → Đại tướng, kiểu HIV có mô tả |
| position | text | chức vụ tự do ("Trưởng Công an xã") |
| unit_role | text (enum `UnitRole`) | COMMANDER / MEMBER (trực ban tính theo bảng duty, không lưu ở đây) |
| phone | text | để sau gửi SMS |
| pin_hash | text null | bcrypt; null = chưa kích hoạt |
| activation_code_hash / activation_expires_at | text / timestamp | mã dùng 1 lần, hạn 72h |
| failed_pin_count / locked_at | int / timestamp | sai 5 lần -> khoá |
| pin_changed_at | timestamp | mốc đá văng phiên của riêng người này |
| active | bool | xoá = active false (giữ lịch sử) |
| created_at / created_by_member | | |

### `unit_duty_assignments` (lịch sử trực ban)
| cột | ghi chú |
|---|---|
| id, account_id (đơn vị), member_id (trực ban) | |
| assigned_by_member | người giao (chỉ huy hoặc trực ban cũ) |
| started_at | lúc nhận |
| ended_at | null tới khi có người mới được giao -> lúc đó mới ghi |
- Unique 1 dòng `ended_at IS NULL` mỗi đơn vị (1 trực ban tại 1 thời điểm). Giao = 1 transaction: đóng dòng cũ + mở dòng mới cùng mốc giờ.

### Thêm cột `*_member_id` (FK unit_members), giữ cột account cũ cho dữ liệu cũ
- `incidents`: handler, escalated_by, transferred_by, dest_updated_by, officer_confirmed_by
- `incident_claims`: account -> member, handed_to -> member
- `incident_dispatches`: dispatched_by, assigned_to
- `incident_routes.computed_by`, `incident_route_positions`, `incident_reports.created_by`
- `incident_documents`: officer_signed_by, citizen_proxy_by, created_by, updated_by
- `incident_media.uploaded_by`
- Index "mỗi người 1 ca" `ux_incidents_active_handler` chuyển sang `handler_member_id`.

### Chuyển dữ liệu cũ
- Mỗi tài khoản OFFICER hiện có -> 1 `unit_members` (họ tên của tài khoản, rank null) + điền `*_member_id` cho ca cũ, để lịch sử vẫn hiện tên. Tài khoản test cùng xã (`congan.ninhkieu`, `congan.ninhkieu2`) gộp về 1 tài khoản đơn vị (chốt lại tên email đơn vị).

## 3. Token
- Đăng nhập email + mật khẩu đơn vị -> **token tạm** `type=unit` (5 phút, `id` = account). Chỉ gọi được: `GET /auth/unit/members`, `POST /auth/unit/pin`, `POST /auth/unit/activate`. API khác -> 401.
- Nhập PIN đúng -> access + refresh **của cán bộ**: claim thêm `mid` (member_id), `sub` = `email#member_id`. Refresh token lưu kèm member_id, chỉ đổi ra token của đúng người đó.
- Thu hồi: đổi mật khẩu đơn vị -> đá mọi cán bộ của đơn vị; đổi PIN / khoá / xoá cán bộ -> chỉ đá người đó (Redis + đóng STOMP 1008, dùng lại `TokenRevocationStore`, `StompSessionRegistry`).
- `OfficerPrincipal` thêm `memberId`, tên, cấp bậc; tên STOMP principal đổi từ email sang `member:{id}` (hàng đợi, gọi video, trực tuyến/bận, uỷ quyền gửi đúng người).

## 4. API mới
| API | ai | việc |
|---|---|---|
| `GET /auth/unit/members` | token tạm | danh sách để chọn (tên, số hiệu, cấp bậc, đã kích hoạt chưa) |
| `POST /auth/unit/pin` {member_id, pin} | token tạm | sai -> 401 + tăng đếm; khoá -> 423 |
| `POST /auth/unit/activate` {member_id, activation_code, pin} | token tạm | đặt PIN lần đầu / sau khi cấp lại |
| `POST /auth/pin` {old_pin, new_pin} | cán bộ | đổi PIN |
| `GET/POST/PUT/DELETE /api/v1/unit/members` | chỉ huy, trực ban | quản lý danh sách (không trả PIN); thêm -> trả mã kích hoạt 1 lần |
| `POST /unit/members/{id}/reset-pin` | chỉ huy, trực ban | sinh mã kích hoạt mới, mở khoá |
| `GET /unit/duty` | cán bộ đơn vị | trực ban hiện tại + lịch sử |
| `POST /unit/duty` {member_id} | chỉ huy, trực ban hiện tại | giao trực ban |
- `GET /auth/me` trả thêm `member` {id, full_name, rank(_label), position, unit_role, is_duty_officer}.
- Ứng viên giao ca / uỷ quyền / `officers/status` trả theo cán bộ (online = cán bộ có phiên STOMP).
- Bản đồ / thông tin trạm: chỉ huy của đơn vị (chức vụ + cấp bậc + tên).

### Hợp đồng FE đã gọi sẵn (24/10/2026)
- `POST /auth/login`: ADMIN -> `{ access_token, refresh_token }`; tài khoản đơn vị -> `{ unit_token }`.
- `GET /auth/unit/members` -> `[{ member_id, full_name, badge_number, rank, rank_label, position, unit_role, activated, locked }]`.
- `POST /auth/unit/pin` / `/auth/unit/activate` -> `{ access_token, refresh_token }`; sai 401, khoá 423.
- `GET /auth/me` thêm `member { member_id, full_name, rank, rank_label, position, unit_role, is_duty_officer }`.
- `GET /unit/members` thêm `phone`, `is_duty_officer`; `POST /unit/members` -> `{ member, activation_code, activation_expires_at }`; `reset-pin` -> `{ activation_code, activation_expires_at }`.
- `GET /unit/duty` -> `{ current, history }`, mỗi dòng `{ id, member_id, full_name, rank_label, assigned_by_name, started_at, ended_at }`.
- Mã `PoliceRank`: BINH_NHI, BINH_NHAT, HA_SI, TRUNG_SI, THUONG_SI, THIEU_UY, TRUNG_UY, THUONG_UY, DAI_UY, THIEU_TA, TRUNG_TA, THUONG_TA, DAI_TA, THIEU_TUONG, TRUNG_TUONG, THUONG_TUONG, DAI_TUONG.
- Đổi mật khẩu đơn vị: chỉ chỉ huy (BE chặn 403 với cán bộ thường).

## 5. FE
- Đăng nhập -> màn **Chọn cán bộ + PIN** (có "Kích hoạt / đặt PIN" bằng mã).
- **Cài đặt** (menu bánh răng): tab "Cán bộ đơn vị" (thêm / sửa / khoá / cấp lại PIN, hiện mã kích hoạt 1 lần) + tab "Trực ban" (hiện tại + lịch sử, nút giao).
- Chỗ hiện tên cán bộ thêm cấp bậc: thẻ ca, chi tiết ca, điều động, văn bản, tra cứu.

## 6. Đã chốt (24/10/2026)
1. ADMIN không qua PIN, giữ nguyên như hiện giờ.
2. Email đơn vị dạng `congan.<xã|tỉnh>@sos.vn` (không dấu, viết liền).
3. Chỉ huy độc lập với trực ban, luôn có quyền như trực ban.

## 8. Sau khi xong: dọn + tạo tài khoản hàng loạt
- Xoá các tài khoản OFFICER cũ (giữ ADMIN).
- Mỗi xã và mỗi tỉnh: 1 tài khoản đơn vị + 3 cán bộ (chỉ huy, trực ban, cán bộ). Trực ban có dòng `unit_duty_assignments` mở.
- Lực lượng: công an, CSGT, PCCC, mỗi lực lượng theo cấp mà `AgencyType.supports(level)` cho phép.
- Email: tỉnh `<lực lượng>.<tỉnh>@sos.vn`, xã `<lực lượng>.<xã>.<tỉnh>@sos.vn` (tiền tố `congan` / `csgt` / `pccc`, không dấu, viết liền).
- Mật khẩu đơn vị = `SEED_TEST_ACCOUNT_PASSWORD`; PIN cán bộ seed = env mới `SEED_MEMBER_PIN` (không ghi vào code).
- Tên cán bộ giả cho đẹp (demo): họ + đệm + tên ghép ngẫu nhiên, cố gắng không trùng; cấp bậc + chức vụ hợp vai (chỉ huy: Trưởng ..., Thượng tá/Trung tá; trực ban: Đại uý; cán bộ: Thượng uý/Trung uý).
- Thêm tài khoản mới trước; sau đó đổi các tài khoản OFFICER cũ cho cùng định dạng (gộp vào đơn vị mới, người cũ thành cán bộ của đơn vị) để ca cũ vẫn trỏ đúng. Chạy thẳng lên DB VPS (dự án đang demo), backup trước khi chạy.

## 7. Thứ tự làm
FE (màn PIN + Cài đặt, gọi API giả) -> user duyệt -> BE V11 + token + API -> chuyển code sang member_id -> test (unit + e2e 8081/5199).
