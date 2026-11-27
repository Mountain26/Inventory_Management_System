# MOUNTAIN Báo cáo rà soát

Ngày rà soát: 27/11/2026. Phạm vi gồm source code, cấu hình, migrations, lịch sử Git tại thư mục nhận được, kiểm thử và tài liệu nghiệp vụ. Các cải tiến tập trung vào tính nhất quán nhập xuất tồn và khả năng giải thích quyết định nghiệp vụ khi trình bày portfolio BA.

## Nguồn gốc và Git

Thư mục đầu vào không chứa `.git` riêng. Lệnh Git ban đầu tìm nhầm repository tại thư mục tài khoản Windows phía trên; đó không phải lịch sử của mã nguồn đang rà soát. Repository MOUNTAIN riêng đã được khởi tạo tại gốc dự án.

`LICENSE` ghi tác giả nền tảng là `bhimashankarkumbar`, năm 2026, theo MIT. Giữ nguyên thông báo này khi phát triển tiếp. Không tìm thấy tên tác giả đó trong các tệp văn bản còn lại của bản đầu vào. Các URL GitHub trong README frontend ban đầu là liên kết tài liệu React/Vite, không phải remote của chủ dự án. Hình cũ trong `docs/image*.png` là ảnh tham khảo từ bản nền, không phải bằng chứng demo phiên bản MOUNTAIN.

Các commit mới dùng danh tính Git đã cấu hình của người thực hiện, không thêm trailer đồng tác giả và không lùi ngày. Việc GitHub ánh xạ commit vào tài khoản phụ thuộc email commit đã liên kết với tài khoản đó. Không có remote hoặc thao tác push nào được thiết lập trong lần rà soát này.

## Lỗi và cải tiến đã xử lý

| Vấn đề trong bản nền | Hậu quả | Cách xử lý |
|---|---|---|
| PO nhận hàng chỉ cộng tổng tồn | Tồn MAIN và tổng lệch nhau | Dùng chung luồng ghi STOCK_IN cập nhật cả hai số dư và liên kết PO |
| Điều chỉnh luôn trừ số lượng, thiếu kiểm tra dấu | Số âm có thể tăng tồn ở loại mất/hỏng; thao tác tăng tồn không rõ | Số lượng mất/hỏng dương; điều chỉnh/sửa sai có dấu; chặn null, 0 và tràn số |
| Duyệt điều chỉnh không cập nhật tồn kho | Tổng và chi tiết kho lệch | Đồng bộ cập nhật tổng và kho mặc định trong transaction |
| Chỉ kiểm tra tổng khi xuất | Có thể xuất âm MAIN sau chuyển kho | Kiểm tra cả tồn tại kho mặc định |
| CORRECTION chưa bắt buộc bản ghi gốc | Không truy vết được sửa sai | Yêu cầu giao dịch COMPLETED cùng sản phẩm, lưu linkedTransaction |
| Tạo PO thiếu transaction, nhận hàng thiếu validation | PO mồ côi, nhận âm hoặc request rỗng | Transaction bao ngoài; kiểm tra số lượng, giá, dòng trùng và trạng thái |
| Thiếu version trên kho, chuyển kho, PO, dòng PO, hóa đơn, yêu cầu điều chỉnh | Ghi đè và áp dụng lặp khi có request đồng thời | Bổ sung optimistic version và phản hồi HTTP 409 |
| Người tạo có thể tự duyệt | Không tách trách nhiệm | Người duyệt/từ chối phải là Admin/Manager khác người tạo |
| Role bên ngoài gọi được API nội bộ chỉ yêu cầu đăng nhập | Lộ dữ liệu kho, PO và hóa đơn | Giới hạn API nội bộ cho role nội bộ; hóa đơn và thông báo cho Admin/Manager |
| Token sai có thể gây lỗi không rõ; client coi token bất kỳ là đã đăng nhập | Phiên đăng nhập sai và lỗi HTTP 500 | JWT lỗi trả 401; client kiểm tra expiry và kết thúc phiên khi API trả 401 |
| Đăng ký công khai cấp STAFF | Người ngoài tự có quyền nội bộ | Tắt mặc định; tạo Admin đầu bằng cấu hình bootstrap có chủ đích |
| Sản phẩm mới nhận currentQuantity từ client | Tồn đầu kỳ không có sổ | Bắt đầu ở 0, ghi tồn bằng STOCK_IN |
| Thanh toán có thể vượt tổng hóa đơn | Công nợ âm | Chặn trả vượt số dư; kiểm tra độ chính xác và ngày hóa đơn |
| Giao diện dùng type/status sai, thiếu nút duyệt | Không gửi được điều chỉnh, bỏ sót chờ duyệt | Sửa payload, PENDING_APPROVAL, thao tác duyệt và thông báo tác động tồn |
| Form nhận PO dùng state cũ sau await | Lần đầu mở modal không có đúng dữ liệu | Dùng dữ liệu trả về từ API, tải lại dòng trước khi nhận |
| Báo cáo gọi giá trị mọi PO là total spend | Cộng cả nháp/hủy; nhầm đặt hàng với trả tiền | Tách giá trị đặt đã duyệt và giá trị thực nhận; dùng truy vấn tổng hợp thay N+1 |
| Invoice UI đọc balanceAmount không có trong response | Số dư và giới hạn thanh toán trống | Trả balanceAmount tính từ totalAmount - paidAmount |
| Thiếu migration V1 và cú pháp ALTER không phù hợp MySQL | Không dựng được schema trống | Thêm V1, sửa ALTER và thêm constraints/indexes |
| Docker thiếu cấu hình runtime, kiểm tra GET endpoint đăng nhập | Backend không healthy, frontend không khởi động | Cấu hình theo biến môi trường; actuator health; sửa lệnh Java |
| Frontend gọi localhost:8080 cố định | Trình duyệt máy khác gọi sai backend | Dùng /api cùng origin, Vite proxy và Nginx proxy |
| Scanner gọi API instance thay vì static và reset không đúng thư viện | Camera không quét được hoặc không được dừng | Dùng API @zxing/browser đang cài và scanner controls.stop |
| Tải mọi trang và scanner ngay đầu | Bundle khởi đầu lớn | Lazy load theo route; gói entry khoảng 795 KB xuống 287 KB, số liệu build cục bộ chưa gzip |
| Tài liệu V1 khác tính năng V2 thực tế | Khó xác định yêu cầu và chứng minh nghiệm thu | Viết phạm vi hiện tại, quy tắc, state diagrams, use cases, UAT và hướng dẫn học |

## Kết quả kiểm tra

Backend có kiểm thử service và kiểm thử tích hợp H2 với transaction thật. Các ca tích hợp kiểm tra nhận từng phần, rollback nhiều dòng, sửa sai có liên kết, chặn áp dụng lặp, chuyển kho, phân quyền, báo cáo NCC và cạnh tranh khi xuất kho. Bài kiểm tra MySQL dùng Testcontainers và cấu hình Flyway + Hibernate validate; cần Docker đang chạy.

Kết quả ngày 27/11/2026: 68 bài backend và 18 bài frontend qua; 4 bài MySQL bị bỏ qua vì Docker chưa sẵn sàng. Frontend có kiểm thử đăng nhập, hết phiên, payload điều chỉnh, thao tác duyệt, sản phẩm và vòng đời camera. Lint và build qua, được dùng làm điều kiện CI.

Chrome headless đã hiển thị màn hình đăng nhập ở 1440px và 390px, luồng tồn kho ở 1440px và 1024px, PO và báo cáo NCC ở 1440px bằng API fixture. Kiểm tra ảnh phát hiện và sửa form tồn kho tràn chiều ngang ở 1024px. Dữ liệu fixture chỉ kiểm tra hiển thị; chưa thay thế E2E với backend MySQL hoặc thử camera thật.

Migrations MySQL và Docker Compose chưa được kiểm chứng runtime trên máy này vì Docker engine không sẵn sàng. H2 không xác nhận được cú pháp migration, khóa và ràng buộc đặc thù MySQL. Khi dùng database đã có schema cũ phải lập migration nâng cấp riêng và đối chiếu trước; không chạy lại các script V1/V2 đã sửa trên database có Flyway history cũ hoặc tự động repair checksum.

## Phần cần phát triển tiếp

| Ưu tiên | Khoảng trống | Tiêu chí hoàn tất |
|---|---|---|
| P1 | Idempotency key cho yêu cầu ghi | Request lặp cùng key trả cùng kết quả; không tạo thêm nhập, thanh toán hoặc điều chỉnh sau sự cố mạng |
| P1 | Nhật ký hành động còn thiếu nhiều thao tác ngoài login | Ghi người, hành động, đối tượng, thời điểm và thay đổi cho master data, PO, chuyển kho, duyệt và thanh toán trong giao dịch phù hợp |
| P1 | Chưa lưu từng lần thanh toán | Có payment ledger với người thu/chi, tham chiếu, thời điểm; đối chiếu được paidAmount với các lần thanh toán |
| P1 | Chưa có thời điểm duyệt riêng và warehouse trên sổ inventory | Báo cáo movement theo ngày ghi sổ và kho tái dựng được số dư, kể cả yêu cầu tạo trước nhưng duyệt sau |
| P1 | Mô hình API trả trực tiếp entity | Tách DTO, đặc biệt portal, để chỉ trả trường cần dùng và tránh lộ metadata, email nhân sự hoặc thông tin nội bộ trong entity lồng nhau |
| P1 | Quản trị master data và liên kết supplier chưa đầy đủ | Validation thống nhất, màn hình quản trị user/supplier, kiểm tra đổi trạng thái đồng thời với nghiệp vụ |
| P2 | Tìm kiếm và phân trang phần lớn ở client hoặc chưa có | Server pagination, filter, sort; thử bằng bộ dữ liệu 10.000 sản phẩm |
| P2 | Thay đổi thuộc tính sản phẩm trong lúc đang vận hành | Quy định rõ thay đổi unit, danh mục và barcode; không để đổi đơn vị làm mất ý nghĩa số dư |
| P2 | Gửi email trong transaction và log chưa phản ánh trạng thái gửi | Outbox hoặc after-commit; phân biệt queued/sent/failed và cơ chế retry |
| P2 | Dashboard thiếu một số KPI trong tài liệu V1 | Định nghĩa metric, nguồn dữ liệu, múi giờ và xử lý lỗi API trước khi bổ sung |
| P2 | Chưa kiểm tra camera thật, mobile, accessibility và E2E với MySQL | Chạy trên thiết bị và database mục tiêu; có kết quả kèm ảnh và response |

Những mục này là backlog, không được giới thiệu như tính năng đã hoàn tất. Hệ thống hiện phục vụ portfolio học tập và demo có kiểm soát.

## Truy vấn đối chiếu tồn

```sql
SELECT p.id, p.sku, p.current_quantity AS product_total,
       COALESCE(SUM(ws.current_quantity), 0) AS warehouse_total
FROM product p
LEFT JOIN warehouse_stock ws ON ws.product_id = p.id
GROUP BY p.id, p.sku, p.current_quantity
HAVING p.current_quantity <> COALESCE(SUM(ws.current_quantity), 0);
```

Kết quả rỗng nghĩa là hai mức tồn khớp tại thời điểm truy vấn. Nếu có chênh lệch, điều tra nguồn trước khi ghi điều chỉnh; không tự gán chênh lệch vào kho MAIN để che lỗi dữ liệu.

## Tài liệu kỹ thuật đối chiếu

Cú pháp migration theo [MySQL 8 ALTER TABLE](https://dev.mysql.com/doc/refman/8.0/en/alter-table.html). Cấu hình dùng Flyway quản lý schema và Hibernate validate theo nguyên tắc một cơ chế tạo schema trong [Spring Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html).
