# MOUNTAIN Frontend

Giao diện React cho mua hàng và vận hành kho. Dùng Node.js 22, `npm ci`, sau đó `npm run dev`. Vite phục vụ tại localhost:5173 và proxy `/api` tới backend tại localhost:8080.

`npm test` chạy Vitest; `npm run lint` kiểm tra ESLint; `npm run build` tạo `dist`. Bản Docker dùng Nginx và proxy cùng origin. VITE_API_BASE_URL là cấu hình build tùy chọn khi API nằm ở origin khác.

Quyền và token được kiểm tra tại backend. Kiểm tra expiry trên client và ẩn thao tác theo role chỉ hỗ trợ luồng sử dụng. Các trang tải theo route để không tải thư viện camera ở màn hình đăng nhập.

Đọc [tài liệu BA](../docs/BUSINESS-ANALYSIS.md), [UAT](../docs/UAT.md) và [audit](../docs/AUDIT.md) để biết quy tắc và giới hạn hiện tại.
