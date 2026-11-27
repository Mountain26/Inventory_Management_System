# MOUNTAIN

Hệ thống quản lý mua hàng và vận hành kho, được phát triển tiếp thành dự án cá nhân phục vụ portfolio **Business Analyst**. Trọng tâm là quy trình PO, nhận hàng từng phần, kiểm soát nhập xuất tồn, phân quyền và tiêu chí nghiệm thu có thể kiểm chứng.

MOUNTAIN phát triển từ nền tảng mã nguồn mở MIT; thông báo tác giả gốc được giữ trong [LICENSE](LICENSE). Phần cải tiến và những giới hạn còn lại được mô tả trong [báo cáo rà soát](docs/AUDIT.md).

## Tài liệu BA

- [Phân tích nghiệp vụ](docs/BUSINESS-ANALYSIS.md): bối cảnh, phạm vi, ma trận quyền, 20 quy tắc nghiệp vụ, luồng trạng thái, use cases và từ điển dữ liệu.
- [UAT và truy vết yêu cầu](docs/UAT.md): 29 kịch bản, kết quả mong đợi và kiểm thử tương ứng.
- [Báo cáo rà soát](docs/AUDIT.md): lỗi đã sửa, kiểm chứng và backlog theo mức ưu tiên.
- [Hướng dẫn học và phỏng vấn](docs/LEARNING-GUIDE.md): lộ trình thực hành, luồng code và câu hỏi cần giải thích được.

Bối cảnh doanh nghiệp là giả định cho portfolio. Không có số liệu hiệu quả vận hành hoặc nghiệm thu khách hàng được tự gán cho dự án.

## Chức năng hiện tại

- Đăng nhập JWT, quyền Admin / Manager / Staff và hai role portal; đăng ký công khai mặc định tắt.
- Quản lý danh mục, sản phẩm, nhà cung cấp; sản phẩm mới bắt đầu với tồn 0.
- PO: DRAFT → PENDING_APPROVAL → APPROVED → PARTIALLY_RECEIVED → RECEIVED; người tạo không tự duyệt.
- Nhập xuất tại kho mặc định; kiểm tra cả tổng tồn và tồn tại kho thực hiện.
- Chuyển kho PENDING → COMPLETED hoặc CANCELLED; chưa giữ chỗ hàng và chưa mô hình hàng đang vận chuyển.
- Mất/hỏng, điều chỉnh có dấu và sửa sai có tham chiếu; chỉ tác động tồn khi người khác duyệt.
- Hóa đơn, số dư và thanh toán trong phạm vi đơn giản; chặn trả vượt số còn lại.
- Báo cáo NCC tách giá trị đặt đã duyệt và giá trị thực nhận, không gọi hai chỉ tiêu này là số tiền đã thanh toán.
- Portal nhà cung cấp, danh mục khách hàng, cảnh báo tồn thấp và quét barcode bằng camera.

Tổng tồn sản phẩm phải bằng tổng tồn tất cả kho. Luồng cập nhật và sổ giao dịch dùng chung transaction; optimistic version chặn ghi đè đồng thời. HTTP 409 yêu cầu tải lại và xem kết quả trước khi thử tiếp.

## Công nghệ

Java 21, Spring Boot 3.3, Spring Security, JPA/Hibernate, MySQL 8, Flyway, Maven; React 19, Vite 8, Axios, Tailwind; JUnit, Mockito, H2, Testcontainers và Vitest. Node.js 22 được dùng cho build và CI.

## Chạy với Docker

1. Sao chép `.env.example` thành `.env`. Thay các mật khẩu mẫu và đặt JWT_SECRET ngẫu nhiên tối thiểu 64 ký tự.
2. Ở lần đầu trên database trống, đặt APP_BOOTSTRAP_ENABLED=true và điền BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD của bạn.
3. Chạy `docker compose up --build`. Frontend tại http://localhost, API tại http://localhost:8080, health tại `/actuator/health`.
4. Sau khi tạo Admin thành công, đặt APP_BOOTSTRAP_ENABLED=false rồi chạy `docker compose up -d backend` trước những lần khởi động tiếp theo. Bootstrap không ghi đè tài khoản hoặc mật khẩu đã có.
5. Đăng nhập Admin và tạo một Manager khác qua POST `/api/auth/register-privileged`. Cần ít nhất hai người dùng độc lập để tạo và duyệt chứng từ.

Ví dụ body tạo Manager, gửi bằng token Admin:

```json
{
  "fullName": "Warehouse Manager",
  "email": "manager@mountain.local",
  "password": "replace-with-your-own-password",
  "role": "MANAGER"
}
```

Flyway tạo schema từ V1 trên database trống; Hibernate chỉ validate. Với database cũ đã có schema hoặc Flyway history, đọc phần nâng cấp trong [AUDIT.md](docs/AUDIT.md) trước khi chạy.

## Chạy phát triển cục bộ

Dùng MySQL 8.0.16 trở lên và tạo database `inventory_management_system` cùng user có quyền trên database đó. Khai báo SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD và JWT_SECRET trong môi trường chạy backend. Spring Boot không tự đọc file `.env` khi chạy Maven trực tiếp.

```text
cd backend
mvn spring-boot:run
```

Tạo Admin lần đầu bằng cùng các biến bootstrap ở phần trên, sau đó tắt bootstrap. Không cần sửa file cấu hình chứa mật khẩu trong source.

```text
cd frontend
npm ci
npm run dev
```

Frontend phát triển tại http://localhost:5173; Vite chuyển tiếp `/api` tới localhost:8080. Bản Docker dùng Nginx cùng origin. Có thể đặt VITE_API_BASE_URL nếu cần build cho API khác.

## Kiểm chứng

```text
cd backend
mvn test -B

cd ../frontend
npm test
npm run lint
npm run build
```

Kết quả trên máy rà soát ngày 27/11/2026: backend **68 bài qua**, frontend **18 bài qua**, lint và build qua. Backend có kiểm thử tích hợp H2 với transaction thật, gồm rollback, báo cáo NCC và xuất kho đồng thời. **4 bài MySQL/Testcontainers bị bỏ qua do Docker chưa sẵn sàng**; chưa xác nhận toàn bộ đường khởi động MySQL và Compose.

## Demo nghiệp vụ

Tạo sản phẩm tồn 0, PO 10 đơn vị giá 10 USD, dùng người khác duyệt. Nhận 4 rồi nhận 6 và kiểm tra cả PO, tổng tồn, kho MAIN và lịch sử STOCK_IN. Tiếp theo chuyển hàng sang kho phụ, thử xuất quá số còn ở MAIN và tạo yêu cầu LOST. Chứng minh yêu cầu chưa đổi tồn cho tới khi được duyệt. Chạy các ca lỗi trong UAT và giải thích vì sao dữ liệu không được cập nhật một phần.

## Phạm vi tiếp theo

Ưu tiên idempotency, nhật ký hành động đầy đủ, sổ từng lần thanh toán, thời điểm ghi sổ/warehouse của giao dịch, DTO cho portal và phân trang server. MOUNTAIN hiện là hệ thống học tập và demo có kiểm soát; chưa phải phần mềm kế toán hoặc ERP triển khai thực tế.
