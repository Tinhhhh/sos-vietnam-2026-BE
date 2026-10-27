# Link cho người phụ trách hiện trường (đã duyệt 26/10/2026, CHƯA làm)

Người phụ trách hiện trường (`incident_dispatches.field_member_id`, đặc biệt người PCCC / CSGT được chỉ định) không đăng nhập
hệ thống. Họ nhận một link riêng, mở trên điện thoại để xem thông tin ca, tuyến đường (kiểu Grab tài xế), gọi video, báo
"Đã đến" / "Đã xử lý xong" và gửi GPS từ trình duyệt. Chưa có SMS: cán bộ sao chép link gửi thủ công (Zalo...).

## BE
1. V16 `incident_dispatches`: `field_token_hash` (sha256, cùng kiểu citizen token), `field_token_expires_at` (hết khi ca đóng).
2. Cấp mã khi lượt sang EN_ROUTE (đơn vị tự đi `/depart`, xã xuất phát, PCCC/CSGT `/delegate`, đổi người -> mã cũ hết hiệu lực).
   Mã chỉ trả một lần cho cán bộ vừa thao tác. `POST /dispatch-plan/field-link` cấp lại mã mới (mã cũ hết hiệu lực).
   Sau này cấp lại thì tự gửi SMS; hiện tại chỉ tạo mã mới để sao chép.
3. API công khai, header `X-SOS-Field-Token` (không đăng nhập):
   - `GET /api/v1/field/plan`: thông tin ca, người báo tin + SĐT, điểm đến, tuyến, vị trí cuối.
   - `POST /field/position { lat, lng, speed, heading, accuracy }`: vào cùng luồng điểm Traccar (tự tính lại tuyến, chấm xe).
   - `POST /field/arrive`, `/field/confirm` ("Đã đến", "Đã xử lý xong" phía lực lượng).
   - Ca đóng / mã sai -> 401 / 410.
4. Hai nguồn GPS song song (app Traccar + trình duyệt): **ưu tiên điểm từ app** (chính xác hơn); chỉ dùng điểm trình duyệt
   khi app không gửi điểm nào trong khoảng gần đây (vd 20 giây). Ghi nguồn (TRACCAR / BROWSER) để màn theo dõi hiện.
5. Unit test: mã, quyền, hết hạn, đổi người, ưu tiên nguồn GPS.

## FE
6. Trang `/f/:token` (điện thoại trước): bản đồ tuyến (đoạn đã đi xám), điểm xe dừng, vị trí mình; ETA, quãng đường,
   người báo tin, mô tả ca; nút "Mở Google Maps chỉ đường", "Đã đến", "Đã xử lý xong"; "Bật gửi vị trí" (xin quyền GPS,
   gửi 5 giây / lần, giữ màn hình sáng, báo "đang gửi / đã dừng").
   - Nút **Gọi = gọi video** như cán bộ <-> dân (WebRTC + tín hiệu STOMP hiện có). Ba bên gọi qua lại được: người nhận ca
     (cán bộ giữ ca), người dân, người phụ trách hiện trường.
   - **Đang trong cuộc gọi mà bên thứ ba gọi tới** (áp dụng cho MỌI bên, kể cả dân): người nhận thấy hộp thoại, nội dung
     theo vai trò người gọi, vd dân đang gọi với người nhận ca mà người phụ trách hiện trường gọi tới -> dân thấy
     "Người phụ trách hiện trường muốn nối máy với bạn. Huỷ cuộc gọi hiện tại để nối máy?" (Có / Không); ngược lại người
     nhận ca gọi tới khi dân đang nói với người phụ trách hiện trường -> "Cán bộ nhận ca muốn nối máy với bạn...".
     Có = cúp cuộc cũ (bên kia nhận "đã kết thúc"), nhận cuộc mới; Không = người gọi nhận báo bận.
   - Cần mở rộng tín hiệu gọi (`CallController`) cho vai trò người phụ trách hiện trường (principal theo field token)
     và cho phép dân <-> người phụ trách hiện trường gọi nhau.
7. Phía cán bộ, ngay sau khi chỉ định / xuất phát: hộp "Link cho người phụ trách" (link, nút Sao chép, nút "Cấp lại mã"
   khi lỡ tắt web). Bước Theo dõi cũng có "Cấp lại mã".
8. E2E đủ 5 luồng (CA xã, CA tỉnh, tỉnh uỷ quyền xã, PCCC, CSGT): mở link khổ 375, GPS trình duyệt giả, ưu tiên app,
   gọi video + hộp thoại đang bận.

## Lưu ý
- GPS trình duyệt chỉ chạy trên HTTPS / localhost; thử điện thoại thật phải qua `sosvie.site`.
- Tắt màn hình / chuyển app thì trình duyệt ngừng gửi GPS -> app Traccar vẫn là nguồn chính.
- Link = chìa khoá xem ca: chỉ người được chỉ định, hết hạn khi ca đóng.
