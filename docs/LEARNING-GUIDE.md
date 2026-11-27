# MOUNTAIN Hướng dẫn học và trình bày dự án

Mục tiêu là giải thích được một nghiệp vụ từ yêu cầu đến dữ liệu, API, kiểm thử và giới hạn. Đọc [BUSINESS-ANALYSIS.md](BUSINESS-ANALYSIS.md) trước, rồi đi theo các luồng dưới đây và tự chạy ví dụ bằng dữ liệu riêng.

## Lộ trình thực hành

| Buổi | Công việc | Sản phẩm bạn tự tạo |
|---|---|---|
| 1 | Chạy ứng dụng, tạo Admin và Manager riêng, tạo danh mục và sản phẩm | Sơ đồ vai trò và danh sách thuật ngữ bằng lời của bạn |
| 2 | Đọc entity và migrations; vẽ quan hệ PO, dòng PO, sản phẩm, kho và giao dịch | ERD có cardinality và mô tả nguồn ghi của từng số dư |
| 3 | Tạo PO 10, gửi duyệt, nhận 4 rồi nhận 6 | Sequence diagram và ảnh trước/sau mỗi bước |
| 4 | Tạo LOST, ADJUSTMENT +5 và -3, CORRECTION có liên kết | Bảng quyết định loại giao dịch, dấu số lượng và quyền duyệt |
| 5 | Chuyển 80 trong số 100 sang kho phụ; thử xuất 30 tại MAIN | Giải thích vì sao tổng tồn đủ nhưng xuất bị chặn |
| 6 | Chạy kiểm thử rollback và concurrency; đọc HTTP 400/401/403/409 | Báo cáo 3 lỗi với steps, expected, actual và nguyên nhân |
| 7 | Viết lại một user story, acceptance criteria và UAT bằng lời của bạn | Một bộ yêu cầu có thể giao cho developer mà không cần đoán |

## Đọc code theo luồng

Nhận PO bắt đầu ở `PurchaseOrderController.receiveItems`, sang `PurchaseOrderService.receiveItems`, rồi `InventoryTransactionService.recordPurchaseOrderReceipt`. Theo dõi thứ tự kiểm tra dòng thuộc PO, số còn thiếu, cập nhật quantityReceived, tồn tổng, tồn MAIN, ghi STOCK_IN và quyết định trạng thái PO. Tìm annotation transaction và giải thích điều gì xảy ra nếu dòng thứ hai lỗi.

Điều chỉnh bắt đầu ở `requestAdjustment`. So sánh dữ liệu trước và sau `approveAdjustment`: thời điểm gửi không thay đổi tồn, lúc duyệt phải lấy tồn hiện tại. Giải thích vì sao LOST 3 và ADJUSTMENT -3 đều giảm tồn nhưng không có cùng ý nghĩa nghiệp vụ.

Chuyển kho bắt đầu ở `WarehouseService.initiateTransfer`, kết thúc ở `completeTransfer`. Chỉ ra vì sao PENDING chưa giữ chỗ hàng và vì sao cần kiểm tra lại nguồn ở bước hoàn tất. `@Version` giúp phát hiện ghi đè; nó không tự tạo cơ chế giữ chỗ.

Phân quyền cần đọc cả `SecurityConfig` và `@PreAuthorize` trên controller. Ẩn một nút trên React chưa đủ bảo vệ API. Dùng token của Customer gọi API nội bộ để kiểm chứng.

## Câu hỏi phỏng vấn nên trả lời được

1. Tại sao cần lưu cả tổng tồn và tồn từng kho? Lợi ích đọc nhanh đi kèm rủi ro đối chiếu nào?
2. Khi nhận từng phần, dữ liệu nào đổi và điều kiện nào quyết định RECEIVED?
3. Nếu người dùng bấm lại sau khi mất mạng, vì sao optimistic locking chưa giải quyết toàn bộ vấn đề idempotency?
4. Vì sao không sửa trực tiếp một giao dịch đã hoàn tất? Bản ghi CORRECTION giúp truy vết gì?
5. Khi số lượng thực tế thay đổi trong thời gian chờ duyệt, lấy số trước điều chỉnh ở thời điểm nào?
6. Khi chỉ có một Manager thì quy tắc không tự duyệt gây khó khăn gì? Bạn hỏi stakeholder và đề xuất ngoại lệ ra sao?
7. Đâu là khác biệt giữa ordered value, received value và paid amount trong báo cáo NCC?
8. H2 kiểm tra được điều gì và vì sao vẫn phải chạy migrations trên MySQL?
9. Nếu doanh nghiệp cần hàng đang vận chuyển, phải bổ sung trạng thái, số dư và người chịu trách nhiệm nào?
10. Nếu chứng minh hiệu quả dự án, bạn sẽ đo tỷ lệ sai lệch tồn, tỷ lệ nhận vượt đặt và thời gian đối chiếu thế nào?

## Mô tả CV khi đã tự kiểm chứng

“MOUNTAIN — dự án cá nhân phân tích và mở rộng hệ thống quản lý kho từ nền tảng mã nguồn mở. Mô hình hóa luồng PO, nhận từng phần và điều chỉnh tồn; xây dựng quy tắc nghiệp vụ, ma trận quyền, tiêu chí chấp nhận và UAT; kiểm chứng tính nhất quán tồn kho và xử lý giao dịch đồng thời bằng kiểm thử tự động.”

Điều chỉnh câu này theo phần bạn đã học, chạy và có thể tự giải thích. Số commit không phải số tuần làm việc; không dùng số liệu hiệu quả vận hành khi chưa có đo lường thực tế. Bối cảnh giả định, quyết định đang triển khai và tính năng dự kiến phải được phân biệt khi demo.
