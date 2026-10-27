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

## 7. Còn mở (hỏi user khi bắt tay làm)
- Có để chữ `sosvie.site` trong SMS không (ít bị lọc spam hơn nếu bỏ, nhưng dân không biết vào đâu)?
- Giới hạn sai 5 lần / 10 phút: giữ hay đổi?
- Ca **Báo hộ** (Chat 2, đang khoá) có tính vào giới hạn 2 ca / số không?
- Ca PENDING chưa có ai nhận thì chưa có SMS: dân mất tab lúc đó lấy mã ca ở đâu? (Gợi ý: hiện "Mã ca" ngay khi tạo ca trên màn chờ, không đợi nhận.)

## 8. Ngoài phạm vi
- OTP xác minh SĐT ("gửi SOS trước, xác minh sau").
- Gộp ca trùng.
