# SOS Vietnam 2026 - Backend v2
Spring Boot 3 + PostgreSQL/PostGIS

## Chạy lần đầu

Khoá bí mật nằm trong file `.env` (cùng thư mục `pom.xml`, đã git-ignore). Copy `.env.example` thành `.env` rồi điền; **không ghi khoá thật vào `.env.example`** vì file đó được commit.

- `DB_PASSWORD` (bắt buộc): mật khẩu của user PostgreSQL trong `spring.datasource.username`. Đổi DB/user khác thì thêm `DB_URL`, `DB_USERNAME`.
- `JWT_SECRET` (bắt buộc): khoá ký token đăng nhập, Base64 ngẫu nhiên ≥ 32 byte. Tạo bằng `openssl rand -base64 48`. Thiếu, quá ngắn hoặc dùng khoá mẫu trên mạng thì app không khởi động. Đổi khoá thì mọi người phải đăng nhập lại.
- `SEED_ADMIN_PASSWORD`, `SEED_DISPATCHER_PASSWORD` (tuỳ chọn): lần chạy đầu sẽ tạo `admin@sos.vn` / `dispatcher@sos.vn` với mật khẩu này. Để trống thì không tạo. Tài khoản đã có trong DB thì giá trị này không đổi gì.

## Lưu ảnh / video sự cố

Chọn kho bằng `STORAGE_PROVIDER` trong file `.env` (cùng thư mục `pom.xml`, đã git-ignore; mẫu ở `.env.example`):

| Giá trị | Lưu ở | Khi nào dùng |
| --- | --- | --- |
| `local` (mặc định) | thư mục `uploads/` | dev trên máy |
| `r2` | bucket Cloudflare R2 (riêng tư) | khi cần chạy online |

Cấu hình R2:

1. Cloudflare dashboard → **R2 Object Storage** → tạo bucket `sos-vietnam-media` (để Private).
2. Trang **Overview** của R2 → cột phải **Account Details** → **API Tokens: Manage** → **Create Account API token**: quyền *Object Read & Write*, chỉ áp dụng cho bucket trên.
3. Trong `.env`: điền `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET`, đặt `STORAGE_PROVIDER=r2`.
4. `mvn spring-boot:run` → log phải có `Cloudflare R2 bucket 'sos-vietnam-media' is reachable`.

Ảnh trên R2 không public: `GET /api/incidents/{id}/media/{file}` trả 302 sang link có chữ ký, hết hạn sau `app.storage.r2.presign-ttl` (10 phút).
