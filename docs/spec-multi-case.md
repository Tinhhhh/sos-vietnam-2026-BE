# Spec: SMS "đã tiếp nhận", tra cứu vào lại ca, nhiều ca / số điện thoại

> Chốt với user 27/10/2026. **Chưa làm** (làm sau). Migration dành sẵn: **V19** (V18 = `incidents.report_type` của Chat 2).

## 1. Khái niệm
- **Ca đang mở**: PENDING / VERIFYING / RESOLVING, tạo trong 24 giờ gần nhất (giống `app.incident.resume-hours`). Ca mở cũ hơn 24 giờ coi như bỏ dở, không tính.
- **Cùng số**: so số điện thoại đã chuẩn hoá (`0912…` = `+84912…`).
- **Giới hạn**: mỗi số tối đa **2 ca đang mở**.
- Ca PENDING quá `auto-cancel-minutes` vẫn tự huỷ NO_RESPONDER như cũ, không tính vào giới hạn.

## 2. Dân bấm SOS (gửi form)
BE kiểm số đang có bao nhiêu ca mở. **Không trả chi tiết ca** (loại, giờ, địa chỉ) cho người chỉ gõ số điện thoại: chỉ trả số lượng, chi tiết xem qua Tra cứu (cần mã ca).

| Số đó đang có | Màn dân |
|---|---|
| 0 ca mở | Tạo ca như bây giờ |
| 1 ca mở | Cửa sổ: "Số điện thoại này đã có 1 tin báo đang mở. Quay lại tin đang mở bằng Tra cứu." Ba nút: **Tra cứu ngay** (mở màn Tra cứu), **Báo sự việc khác** (tạo ca mới), **Trở lại menu** |
| 2 ca mở | Cửa sổ: "Không thể tạo thêm tin báo (đã có 2 tin đang mở)." Hai nút: **Tra cứu** / **Trở lại menu** |

- Thay cho cách hiện tại "bấm SOS cùng số trong 24 giờ thì BE trả lại chính ca cũ" (`resumed=true`): quay lại ca cũ giờ đi qua Tra cứu.
- "Báo sự việc khác": không bắt buộc khác loại sự cố; ca mới phân luồng / leo thang như ca thường.
- Chống chiếm phiên giữ nguyên: lần trước khai họ tên thật, lần này khai tên khác -> 409.

## 3. Trình duyệt của dân
- Mỗi tab gắn 1 ca qua URL `/sos/{mã ca}`; tải lại vẫn đúng ca, 2 tab = 2 ca.
- localStorage: danh sách ca đang mở của máy (id, mã truy cập, loại, giờ tạo) thay cho 1 khoá `active_sos_incident`; ca đóng thì tự bỏ. Mã truy cập đã lưu riêng từng ca (`sos_citizen_access_token_{id}`).
- Mở `sosvie.site` từ đầu: 0 ca -> trang chủ; 1 ca -> vào thẳng màn gọi; 2 ca -> chọn "Bạn có 2 tin báo đang mở".
- Đang ở màn gọi ca 1 mà máy có ca 2: dòng nhỏ "Bạn có 1 tin báo khác đang mở" để chuyển. Mỗi màn chỉ đổ chuông cuộc gọi của ca mình.
- Cùng 1 ca ở 2 tab: chạy bình thường (chung mã truy cập). Vào lại bằng Tra cứu thì cấp mã mới, tab cũ mất quyền.

## 4. Cán bộ
- Thẻ ca + Chi tiết ca: "SĐT này đang có 1 ca khác: SOS-… (loại · trạng thái)", bấm mở ca kia.
- 2 ca cùng số **không** cần 1 cán bộ giữ cả hai: mỗi ca phân luồng riêng (có thể khác lực lượng).
- **Ca trùng: chỉ huỷ** (nút "Huỷ ca" sẵn có, không làm gộp ca). Hộp lý do có **tag gợi ý ở FE** (vd "Trùng ca SOS-…", "Báo nhầm", "Đã xử lý ở ca khác"): bấm tag thì FE điền chữ vào ô lý do để cán bộ sửa tiếp; API vẫn chỉ nhận `reason` là chữ như cũ, không có trường tag.

## 5. SMS "đã tiếp nhận" + mã ca
- Gửi **1 lần / ca**, lúc cán bộ nhận ca **lần đầu** (không gửi lại khi giao ca, chuyển lên tỉnh, nhả rồi nhận lại). Không gửi: ca `[DEMO]`, ca không có SĐT, cổng SMS chưa cấu hình. V19: `incidents.received_sms_at` (chống gửi trùng + biết đã gửi lúc nào).
- Nội dung không dấu, không link, ~140 ký tự: `SOS VN: Tin bao cua ban da duoc tiep nhan, can bo dang lien he. Ma ca: ABCD-EFGH. Mat ket noi: vao sosvie.site > Tra cuu, nhap ma + 4 so cuoi SDT.`
- `/tracking` trả `lookup_code` từ lúc ca có người nhận (hiện chỉ khi RESOLVED); màn chờ của dân hiện "Mã ca" + nút sao chép.
- Mỗi ca 1 mã riêng, 1 tin riêng.

## 6. Tra cứu dùng chung (`POST /incidents/lookup`, mã ca + 4 số cuối SĐT)
- Ca **đang mở**: cấp mã truy cập mới (mã cũ hết hiệu lực), trả `{ open: true, incident, citizen_access_token }`; FE thêm ca vào danh sách trên máy rồi vào thẳng màn gọi đúng ca đó.
- Ca **đã đóng**: trả kết quả + văn bản như hiện nay.
- Sai mã / sai số: cùng 1 lỗi (404) như hiện nay. Giới hạn sai: 5 lần / 10 phút / mã ca.

## 7. Đã chốt thêm (27/10/2026)
- SMS **có** chữ `sosvie.site` (không `https://`, không mã truy cập).
- Ca **Báo hộ** có tính vào giới hạn 2 ca / số.
- **Mã ca hiện trên màn chờ ngay lúc tạo ca** (miễn phí, không SMS): dân mất tab trước khi có người nhận vẫn có mã để Tra cứu.

## 8. Chống dò mã + spam SMS (user duyệt 27/10/2026)
- **Giới hạn sai Tra cứu** (chống dò 4 số cuối khi biết mã ca): 5 lần sai / 10 phút / mã ca, quá thì mã đó khoá tra cứu 10 phút.
- **Chống spam đốt tiền SMS** (ai cũng báo được, tin giả):
  - SMS chỉ gửi khi cán bộ nhận ca (đã có): tin giả không ai nhận thì tự huỷ sau 60', không tốn tin.
  - Tối đa **20 SMS / số / 24 giờ**.
  - Tin xếp hàng ~10 giây sau khi nhận ca; ca bị huỷ / báo khống trước khi tin đi thì không gửi.

## 8b. Trạng thái làm (27/10/2026)
- **FE làm trước** theo spec này; **BE chưa làm** (mục 9 BE là việc chờ). Chỗ FE cần API mà BE chưa có thì FE hiện "Tính năng đang chờ phát triển" khi người dùng bấm vào.
- FE đã xong (27/10/2026): URL `/sos/{id}` mỗi tab 1 ca, localStorage `sos_open_incidents` (tự chuyển khoá cũ `active_sos_incident`), "/" có 1 ca vào thẳng / 2 ca hiện chọn ca, dòng "Bạn có N tin báo khác đang mở"; form SOS đọc 409 `data.code` = OPEN_CASE_EXISTS / OPEN_CASE_LIMIT (body lỗi `{ message, data: { code, open_count } }`) và gửi lại `new_case: true`; màn chờ khung "Mã ca" (chưa có mã -> "Tính năng đang chờ phát triển"); Tra cứu: ca mở có nút "Vào lại màn hình theo dõi / gọi" (BE trả `open`, `incident`, `citizen_access_token` thì vào `/sos/{id}`, chưa có thì báo "đang chờ phát triển"), 429 báo nhập sai quá nhiều; cán bộ: thẻ ca "SĐT này đang có N ca khác" (BE `other_open_cases`, chưa có thì lấy ca cùng SĐT trong hàng đợi), hộp Huỷ ca có tag điền lý do. Đang gửi `resumed=true` (BE cũ) thì FE vẫn chạy như một ca mới.
- Lưu ý BE: khi bật `new_case`, nhớ thêm trường này vào `CreateIncidentRequest` (field lạ -> 400).

## 9. Danh sách việc

### BE
1. **V19** `incidents.received_sms_at` (timestamp): lúc gửi SMS "đã tiếp nhận"; null = chưa gửi.
2. **Tạo ca** (`POST /incidents`, `IncidentServiceImpl.createIncident`):
   - Đếm ca đang mở của số (chuẩn hoá SĐT, ACTIVE, tạo trong 24h, tính cả Báo hộ).
   - 0 ca: tạo như cũ. 1 ca: body không có `new_case: true` -> 409 `{ code: OPEN_CASE_EXISTS, open_count: 1 }`, có thì tạo ca mới. 2 ca: 409 `{ code: OPEN_CASE_LIMIT, open_count: 2 }`. Không trả loại / giờ / địa chỉ của ca cũ.
   - Bỏ đường "bấm SOS cùng số thì trả lại ca cũ" (`resumed=true`). Giữ chặn khai tên thật khác (409).
   - Response tạo ca có `lookup_code`.
3. **`/tracking`** (dân) trả `lookup_code` ở mọi trạng thái (hiện chỉ khi RESOLVED).
4. **SMS lúc nhận ca lần đầu** (`claim`): `received_sms_at` null, không phải ca demo, có SĐT, cổng SMS đã cấu hình, số đó chưa nhận 20 SMS trong 24h -> hẹn gửi sau ~10 giây (`incidentTaskScheduler`); tới giờ kiểm lại ca còn mở (không huỷ / báo khống) mới gửi rồi ghi `received_sms_at`. Không gửi lại khi giao ca / chuyển lên tỉnh / nhả rồi nhận lại.
5. **Tra cứu** (`POST /incidents/lookup`):
   - Ca đang mở: đổi mã truy cập (mã cũ hết hiệu lực), trả `{ open: true, incident, citizen_access_token }`.
   - Ca đã đóng: như cũ (`open: false`).
   - Sai mã / sai số: 404 như cũ; quá 5 lần sai / 10 phút / mã ca -> 429 "Thử lại sau N phút" (RAM, 1 instance).
   - Cùng áp dụng cho `/lookup/document` (đọc văn bản) để không lách giới hạn.
6. **Hàng đợi + Chi tiết ca** trả `other_open_cases` [{ id, type_label, status_label }] = ca khác đang mở cùng SĐT (cán bộ bấm mở ca kia).
7. Unit test: đếm ca / 409 / `new_case`, Báo hộ tính vào giới hạn, SMS 1 lần / ca + 20 tin / số / 24h + huỷ trước khi gửi thì không gửi + ca demo không gửi, tra cứu ca mở đổi mã + khoá sau 5 lần sai.

### FE
1. **Form SOS** (`sos-request-modal`):
   - 409 `OPEN_CASE_EXISTS`: cửa sổ "Số điện thoại này đã có 1 tin báo đang mở. Quay lại tin đang mở bằng Tra cứu." + **Tra cứu ngay** / **Báo sự việc khác** (gửi lại kèm `new_case: true`) / **Trở lại menu**.
   - 409 `OPEN_CASE_LIMIT`: "Không thể tạo thêm tin báo (đã có 2 tin đang mở)." + **Tra cứu** / **Trở lại menu**.
   - Bỏ xử lý `resumed`.
2. **Lưu ca trên máy** (`stores/active-incident`): danh sách ca đang mở thay cho 1 ca; mỗi tab gắn 1 ca qua URL `/sos/{mã ca}`.
3. **Trang chủ dân**: 0 ca -> menu; 1 ca -> vào thẳng màn gọi; 2 ca -> chọn "Bạn có 2 tin báo đang mở".
4. **Màn chờ / màn gọi**: hiện **"Mã ca: XXXX-XXXX"** + nút Sao chép ngay từ lúc tạo ca; có ca thứ 2 trên máy thì dòng "Bạn có 1 tin báo khác đang mở" để chuyển.
5. **Tra cứu**: ca đang mở -> thêm vào danh sách trên máy, vào `/sos/{mã ca}` (màn gọi); ca đã đóng -> như cũ; 429 -> "Nhập sai quá nhiều lần, thử lại sau N phút".
6. **Cán bộ**: thẻ hàng đợi + Chi tiết ca hiện "SĐT này đang có 1 ca khác: SOS-…" (bấm mở); hộp **Huỷ ca** có tag gợi ý ("Trùng ca SOS-…", "Báo nhầm", "Đã xử lý ở ca khác") điền chữ vào ô lý do, API giữ nguyên.
7. E2E: 0 / 1 / 2 ca mở (3 cửa sổ), Báo sự việc khác, 2 tab 2 ca, đóng tab rồi Tra cứu vào lại màn gọi (máy khác), tab cũ mất quyền, khoá sau 5 lần sai, mã ca hiện lúc tạo, SMS 1 tin lúc nhận ca (cổng giả), giao ca / lên tỉnh không gửi thêm, ca huỷ trong 10 giây không gửi, ca demo không gửi, thẻ cán bộ hiện ca cùng số, tag lý do huỷ.

## 10. Ngoài phạm vi
- OTP xác minh SĐT ("gửi SOS trước, xác minh sau").
- Gộp ca trùng.
