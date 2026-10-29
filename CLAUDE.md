# SOS Việt Nam 2026 — Backend (handoff cho Claude)

> Giao tiếp: tiếng Việt, xưng tao/mày. User code Java Spring Boot (thích `application.properties`, không dùng yml).
> Bối cảnh chung + FE: `C:\Users\TINH KHUNG\IdeaProjects\sos-vietnam-fe-v2\CLAUDE.md`. Chi tiết từng module (lịch sử, API cũ, số test): `docs/claude-history.md` — chỉ đọc phần cần. Spec: `docs/spec-*.md`. Obsidian `Data for life\`.
> File này giữ NGẮN: chỉ quy ước + trạng thái hiện hành. Làm xong module thì ghi 1–3 dòng vào mục liên quan ở đây, chi tiết đưa vào `docs/claude-history.md`. Chỉ ghi thiết kế hệ thống, luồng, nghiệp vụ (không ghi commit, số unit test / e2e, ngày làm).

## Cách làm việc
- Khi user bảo sửa: việc UI thì hỏi có sửa cả bản mobile không; việc logic thì hỏi có sửa cả BE không (trước khi bắt tay làm).
- BE chỉ làm khi user đã duyệt danh sách việc (FE làm trước, báo BE cần gì). Đổi API / quy ước thì báo user + ghi lại.
- Style theo `C:\Users\TINH KHUNG\IdeaProjects\HIV-TMSS`: controller → service (interface) → `service/implement/*ServiceImpl` → repository; DTO `model/payload/request|response`, enum `model/payload/enums` (có field mô tả).
- Làm xong tự commit đúng file của mình (`type(scope): ...`), dán commit message. Không push nếu không được bảo. User tự merge `develop` → `main`, deploy từ `main`.
- Nhiều chat song song: không 2 chat sửa cùng file; báo nhau bằng SendMessage; số Flyway giữ trước.
- Không đưa mật khẩu / secret vào code, test, commit, chat.

## Build / test / chạy (quan trọng)
- **Không chạy `mvn` trong repo** (user chạy BE 8080 bằng `mvn spring-boot:run` + devtools đọc `target/`). Chép `src` + `pom.xml` + `.env` sang scratchpad (`be-copy`), build / test / chạy ở đó.
- Test: `mvn test "-DargLine=-Djdk.net.unixdomain.tmpdir=C:/Users/Public/jdk-uds"`. Chạy: jar bằng `$JAVA_HOME/bin/java` (java trên PATH hỏng), `SERVER_PORT=8081`, thêm `-Djdk.net.unixdomain.tmpdir=C:/Users/Public/jdk-uds`. Không kill 8080 / 5173 của user; tắt bản test theo cổng.
- FE test: Vite 5199 với cacheDir riêng (chạy Vite thứ 2 cùng cache làm hỏng 5173).
- `Unresolved compilation problems` = IDE (ecj) làm hỏng `target/` → `mvn clean` trong bản sao.
- DB local: Docker `sos-db-local` (postgis 18-3.6, `127.0.0.1:5434`, DB `sos_vietnam_dev`, user `sos_dev`), bản sao DB VPS; cần Docker Desktop; `docker exec` cần `MSYS_NO_PATHCONV=1`. psql: `C:\Program Files\PostgreSQL\18\bin\psql.exe`.
- `.env` local: `REDIS_URL` để comment (Redis Cloud dùng chung VPS, bật ở local đá văng tài khoản thật). `SEED_TEST_ACCOUNT_PASSWORD`, `SEED_MEMBER_PIN`, `SMS_GATEWAY_*` có trong `.env`.

## Stack / hạ tầng
- Spring Boot 3.4, Java 21, PostgreSQL + PostGIS, Flyway (`ddl-auto=validate`), JWT, Redis (tuỳ chọn), R2 (S3 SDK), POI, springdoc.
- **Flyway**: `src/main/resources/db/migration`, mới nhất **V26**. Đổi entity = viết file V mới (Hibernate không tự sửa bảng). Seeder chỉ ghi dữ liệu. V11–V26 mới chạy DB local, tự chạy trên VPS khi deploy BE mới.
- VPS `103.179.185.217` (`sosvie.site`, `/opt/sos`, xem `deploy/README.md`). Traccar trên VPS đã bật forward. Render (`main` cũ) giữ tắt.
- Một số trạng thái chỉ ở RAM, chỉ đúng khi 1 instance BE: online STOMP, điểm GPS, giới hạn tra cứu / SMS, công tắc demo, thu hồi token (có Redis thì bền).
- Múi giờ: `main()` đặt `Asia/Ho_Chi_Minh`. CORS: `app.cors.allowed-origins` (`CORS_ALLOWED_ORIGINS`).

## Quy ước API
- Mọi API `/api/v1/...`. Response `{ http_status, time_stamp, message, data }`; lỗi `{ httpStatus, timestamp, message, error?, data? }` (`SosException` có `data` Map).
- JSON snake_case, luôn trả DTO, field lạ → 400. Body rỗng FE phải gửi `{}`.
- Danh sách phân trang kiểu HIV: `pageNo/pageSize/sortBy/sortDir` → `ListResponse { content, page_no, page_size, total_elements, total_pages, last }`.
- Địa bàn: `province_id` (= `provinces.code`) + `province`, `ward_id` + `ward`.
- Công khai (service tự kiểm token): `/geo/resolve`, tạo ca, media, tracking, cancel, escalate, lookup, văn bản dân, dispatch-plan GET/confirm/arrive (dân), ice-servers, `/field/**` (header `X-SOS-Field-Token`), `/internal/traccar/positions` (header `X-Traccar-Secret`). Còn lại cán bộ; `/admin/**` chỉ ADMIN. `/auth/register` tắt. Swagger: `SWAGGER_ENABLED`.
- Dân dùng header `X-SOS-Access-Token` (citizen token, sha256 trong DB, `util/CitizenTokens`). STOMP `/ws-raw`: cán bộ JWT, dân `X-SOS-Incident-Id` + token, người đi hiện trường field token.

## Tài khoản / phân quyền
- `accounts` = tài khoản ĐƠN VỊ (lực lượng `AgencyType` POLICE/CSGT/FIRE/HOSPITAL + `UnitLevel` NATIONAL/PROVINCE/WARD); người = `unit_members` (số hiệu = mã thiết bị GPS, cấp bậc, `UnitRole` COMMANDER/MEMBER, PIN). Đăng nhập đơn vị → `unit_token` → chọn cán bộ + PIN → token cán bộ (claim `mid`). ADMIN vào thẳng. `OfficerPrincipal` = cán bộ; `security/CurrentOfficer`.
- Đổi mật khẩu / cấp lại PIN → đá văng mọi token cũ (`TokenRevocationStore`, kể cả refresh + STOMP 1008).
- Seed demo: `UnitAccountSeeder` mọi xã + tỉnh, email `<congan|csgt|pccc>.<xã>.<tỉnh>@sos.vn` / `<lực lượng>.<tỉnh>@sos.vn`, 3 cán bộ / đơn vị, không có SĐT (V26 xoá mọi SĐT cán bộ / tài khoản, chỉ giữ 2 số thử của nhóm, để SMS không gửi nhầm người thật). Test hay dùng: `congan.cantho`, `congan.tanan.cantho`, `congan.caikhe.cantho`, `csgt.cantho`, `pccc.cantho` (@sos.vn).
- Quyền xem / nhận ca: `util/IncidentAccess`.

## Luồng ca (hiện hành)
- Status: `PENDING` → nhận ca → `VERIFYING` → `request-dispatch` → `RESOLVING` → `RESOLVED`; `CANCELLED`. `IncidentStatus.ACTIVE/HELD`.
- **Tạo ca** `POST /incidents`: phân luồng theo lực lượng; Công an vào cấp xã chứa GPS nếu xã có cán bộ trực tuyến, không thì tỉnh; CSGT / PCCC thẳng tỉnh; y tế 400. Mã ca `lookup_code` (XXXX-XXXX) trả ngay. SĐT có 1 ca mở (24h) → 409 `OPEN_CASE_EXISTS` (gửi lại `new_case: true` tạo ca 2; FE hiện đang tắt nút này), 2 ca → 409 `OPEN_CASE_LIMIT`. SĐT bị khoá báo khống → 403. `report_type` SELF (PROXY chưa hỗ trợ).
- **Hàng đợi / nhận ca**: `GET /incidents/queue`, `POST /{id}/claim` (409 chậm chân, mỗi người 1 ca), `/release` = giao ca (`to_member_id` + lý do; thiếu `to_member_id` → 400, ca đã nhận không trả về hàng đợi chung — chỉ tự nhả khi mất kết nối), `/escalate` (lý do; tracking của dân có `escalation_reason` OFFICER_REQUEST / NO_RESPONSE_TIMEOUT / CITIZEN_REQUEST, không có ghi chú nội bộ; màn dân báo 1 lần khi xã chuyển / quá hạn), `/transfer` (đổi lực lượng), `/officer-cancel` (`false_alarm` = báo khống), dân `/cancel`; huỷ ca (cả tự huỷ) đóng các lượt điều động chưa xong (REJECTED "Ca đã bị huỷ", link người đi hết hiệu lực). Leo thang tự động 15'; tự huỷ ca chờ 60' (NO_RESPONDER); tự nhả ca khi người giữ mất kết nối 3'. Queue / chi tiết có `other_open_cases`, `ip_false_alarm_count`.
- **Điều động** `/incidents/{id}/dispatch-plan` (`DispatchController`): PUT kế hoạch (trạm, điểm đến, tuyến, `delegate`); uỷ quyền xã `/delegate` → xã `/accept` | `/reject`, `/withdraw`; chọn người đi hiện trường (`/field-candidates`, `/field-member`, cần số hiệu); **Khởi hành** `/depart` (từ đây mới nhận GPS + cho mô phỏng); **Đã đến** `/arrive` chỉ khi GPS ≤ 30 m (+ sai số ≤ 50 m); bỏ qua GPS kèm lý do: người xử lý ca (đơn vị mình đi = người giữ ca; ca uỷ quyền = cán bộ xã được uỷ quyền, tỉnh không được) hoặc người đi hiện trường qua link (`/field/arrive` `{ reason }`, `FIELD_OVERRIDE`); dân luôn được; sau Khởi hành FE khoá "Quay lại bước trước"; kết thúc `/finish` (RESOLVED cần cả cán bộ + dân `/confirm`; tracking của dân có `officer_confirmed_at` / `citizen_confirmed_at`: cán bộ xác nhận trước thì màn dân tự bật hộp xác nhận, ca đóng thì văn bản tự mở, văn bản chờ dân ký thì tự mở lại; FALSE_ALARM / TEST đóng ngay; `force` đã bỏ → 400, không liên lạc được dân thì Huỷ ca). Sau khởi hành không uỷ quyền lại / đổi đơn vị (409); xã đã nhận ca uỷ quyền (ACCEPTED) thì tỉnh không sửa kế hoạch nữa (409, FE khoá "Quay lại bước trước" + nút Đổi); đang mô phỏng thì không đổi người phụ trách / điểm xuất phát / điểm đến (409).
- **GPS**: Traccar (`id` = số hiệu) → forward `/internal/traccar/positions`; trang link `/field/position` (app ưu tiên 20s); mô phỏng (`/simulate`, chỉ người đi, 3 s / điểm, tốc độ chỉnh ở Cài đặt) luôn nạp thẳng vào BE (nguồn `SIMULATION`; có `TRACCAR_OSMAND_URL` thì gửi thêm bản sao lên Traccar); đang mô phỏng thì bỏ điểm thật (Traccar, trang link) của lượt đó. Lệch tuyến > 50 m → tự tính lại (OSRM). GET plan có `last_position`, `arrival_check`, `tracker`, `receiver`, `simulation`.
- **Link người đi hiện trường** `/f/{token}`: `POST /dispatch-plan/field-link` cấp mã + SMS báo người đi được phân công, không kèm link (nhà mạng giữ / chặn tin có link từ SIM cá nhân; link gửi qua nút "Gửi qua Zalo" hoặc sao chép; cấp lại = mã mới, 429 trong 60s).
- **Gọi video**: tín hiệu STOMP `/app/calls` (`CallSignalMessage.to` officer|citizen|field|delegate), không lưu SDP; TURN Cloudflare (`/calls/ice-servers`).
- **Văn bản** `/incidents/{id}/document` (+ `/docx`, ký cán bộ / dân / ký hộ); nháp điền cán bộ / đơn vị = người xử lý (cán bộ được uỷ quyền nếu tỉnh uỷ quyền xuống, không thì người nhận ca cuối); dân xem qua tracking hoặc tra cứu (bản nháp khi chưa lưu).
- **Tra cứu** `POST /incidents/lookup` (mã ca + 4 số cuối SĐT): ca mở → cấp token mới (`open`, `incident`, `citizen_access_token`); 5 sai / 10' / mã → 429 (cả `/lookup/document`).
- **SMS** (SMS Gateway Android, `SMS_GATEWAY_*`, `APP_PUBLIC_URL`; chưa cấu hình = không gửi; ca demo không gửi; tin không dấu, không link, gửi kèm `withDeliveryReport`, log id tin để tra Sent / Delivered): đã tiếp nhận (10s sau lần nhận đầu, 20 tin / số / 24h), khởi hành, sắp tới (≤ 50 m), ca kết thúc cho lực lượng, báo người đi hiện trường được phân công.
- **Khoá SĐT báo khống**: 3 báo khống hoặc 4 lần dân huỷ "bấm nhầm" / 24h → khoá 24h; lần 3 / 30 ngày → vĩnh viễn. Mọi cán bộ (chưa giới hạn ADMIN): `/phone-locks` (danh sách, `/{id}/lift`), công tắc `GET/PUT /phone-locks/settings` `{ enabled }` (app_settings `PHONE_LOCK_ENABLED`, mặc định bật; tắt = không khoá mới, khoá cũ không chặn); FE: Cài đặt › Hệ thống › Khoá SĐT (5 khoá gần nhất + gỡ).
- **Ca demo**: `[DEMO] ...`, công tắc `GET/PUT /settings/demo` (sinh / ẩn, `simulation_speed_kmh` 5–300, áp cho lần Mô phỏng sau; bước Chọn tuyến FE tính thời gian dự kiến = quãng đường / tốc độ này). FE hỏi vị trí 3 s / lần; "Dự kiến tới nơi" tính phần tuyến còn lại từ vị trí xe.

## Khác
- Trạm: `GET /stations` (phân trang, cán bộ), `/stations/nearest`; dữ liệu `data/rescue_stations.json`, nạp lại khi checksum + `STATIONS_SEED_FORMAT` đổi (trạm không còn trong file thì xoá, trừ trạm đang có ca trỏ tới). Mỗi trạm có `auto_generated` (cột DB cùng tên): `true` = trạm Công an xã tự sinh cho đủ mỗi xã (địa chỉ / SĐT chưa có, toạ độ ước), `false` = trạm thật; trạm có nguồn công bố ghi `source_url`. Bảng đối chiếu nguồn: `docs/data-sources/`. `StationLevel` NATIONAL/PROVINCE/WARD/ENTERPRISE.
- Geo: `/geo/provinces`, `/geo/provinces/{id}/wards`, `/geo/resolve`, `/geo/road-snap`, `/geo/data-status` (3 mốc: `boundaries` = ngày bộ file ranh giới `geo/*.geojson`, hằng `WardDataSeeder.BOUNDARY_FILES_VERSION`, thay file thì sửa; `ward_names` = tên xã + sáp nhập, `BandoSync` đối chiếu `sapnhap.bando.com.vn` 6 giờ / lần, ADMIN `/geo/sync-now`; `rescue_stations`). Ranh giới tỉnh / xã chỉ nạp khi bảng rỗng. Thời tiết `/weather/current`.
- Cán bộ: `/officers/status` (lọc theo đơn vị, phân trang), `/unit/**` (thành viên, PIN, trực ban). Admin: tạo tài khoản, xuất Excel `/admin/accounts/export`.
- Thống kê: `GET /dashboard/summary`.
- Media: R2 private, link ký 10'. Tối đa 5 ảnh + 1 video / ca.

## Làm sau
- Báo hộ (PROXY), OTP, Google Routes qua BE, "Đã xử lý xong" từ trang link, quên mật khẩu, ghi âm / lưu video cuộc gọi, gộp ca trùng, lỗi mô phỏng "Rẽ đường khác".
- **Siết token cán bộ** (đã rà, chưa sửa): (1) access 24h / refresh 7 ngày quá dài cho máy dùng chung → access 15–30', refresh hạn tuyệt đối ~12h (1 ca) + hết khi bỏ không ~2h, thêm "Đổi cán bộ / Khoá màn hình" giữ bước chọn cán bộ; (2) `/auth/login` không giới hạn sai mật khẩu đơn vị + khoá PIN vĩnh viễn dễ bị đồng nghiệp cố tình khoá → giới hạn IP + email, khoá PIN tạm 15' (khoá hẳn lần 3 / ngày); (3) `refresh_tokens.token` lưu nguyên văn + refresh đã thu hồi gửi lại chỉ 401 → lưu sha256 (Flyway mới), gặp token dùng lại thì `disableOldTokens(mid)`, dọn dòng hết hạn; (4) `refreshToken` chỉ kiểm cán bộ, không kiểm tài khoản đơn vị còn hoạt động / chưa khoá. Nhỏ hơn: thu hồi chỉ ở RAM khi không Redis, token ở `localStorage`, ADMIN không PIN / 2FA.
