# MOUNTAIN UAT và truy vết yêu cầu

Các kịch bản dưới đây mô tả kết quả mong đợi và chỉ ra kiểm thử tự động tương ứng. Đây là bộ tiêu chí nghiệm thu cho portfolio; trạng thái chạy tự động được ghi riêng trong audit, không thay thế xác nhận nghiệm thu của khách hàng.

## Dữ liệu chuẩn bị

Tạo một Admin, một Manager khác người tạo chứng từ và một Staff. Tạo sản phẩm P1, đơn vị piece, ngưỡng tồn thấp 10; tạo NCC S1; dùng kho MAIN mặc định và kho BRANCH. Sản phẩm mới có tồn 0. Ghi STOCK_IN 100 vào MAIN trước các kịch bản xuất và chuyển kho. Mỗi kịch bản phải dùng dữ liệu độc lập hoặc đưa về số dư được nêu trong cột đầu vào.

## Kịch bản nghiệm thu

| Mã | Quy tắc | Thao tác và đầu vào | Kết quả mong đợi | Bằng chứng tự động |
|---|---|---|---|---|
| UAT-01 | BR-01,09 | PO 10, nhận 4 rồi nhận 6 | PARTIALLY_RECEIVED rồi RECEIVED; tổng và MAIN lần lượt 4,10; hai STOCK_IN có PO tham chiếu | partialAndFullReceiptsUpdateAllStockAndReferenceOrder |
| UAT-02 | BR-09 | PO 10, nhận 11 | Từ chối; PO vẫn APPROVED; tồn không đổi | overReceiptIsRejectedWithoutStockImpact |
| UAT-03 | BR-03,09 | Nhận PO với danh sách rỗng, null, 0 hoặc -1 | HTTP 400, không có biến động | receiptCannotBeEmptyOrContainNonPositiveQuantities |
| UAT-04 | BR-09 | Gửi trùng ID dòng PO trong cùng request | HTTP 400 trước khi ghi nhận | duplicateReceiptLinesAreRejected |
| UAT-05 | BR-10 | Lần nhận có dòng đầu hợp lệ, dòng sau không tồn tại | Rollback dòng đầu, tồn, sổ giao dịch và trạng thái PO | invalidLaterReceiptRollsBackEarlierLinesAndStock |
| UAT-06 | BR-10 | Tạo PO có dòng sản phẩm không tồn tại | Không để lại PO hoặc dòng mồ côi | invalidPoCreationLeavesNoOrphanOrderOrLines |
| UAT-07 | BR-05 | Tồn 20, tạo yêu cầu LOST 3 rồi từ chối | Tồn luôn 20; request REJECTED | pendingAndRejectedAdjustmentsDoNotChangeStock |
| UAT-08 | BR-04 | Tồn 20, duyệt +5, -2, DAMAGED 3 | Kết thúc tồn 20 ở cả tổng và kho | signedAdjustmentsAndDamageUseCorrectDirection |
| UAT-09 | BR-06 | Người yêu cầu là Manager tự duyệt điều chỉnh | Từ chối, tồn giữ nguyên | requesterCannotApproveOwnAdjustment |
| UAT-10 | BR-06 | Người tạo PO tự duyệt | Từ chối, PO vẫn chờ | creatorCannotApproveOwnOrder |
| UAT-11 | BR-13 | Duyệt điều chỉnh đã COMPLETED | Từ chối, không trừ tồn lần hai | approvedAdjustmentCannotBeAppliedTwice |
| UAT-12 | BR-14 | Nhập 20, tạo CORRECTION -2 tham chiếu giao dịch nhập | Tồn 18, giao dịch nhập gốc vẫn 20 | correctionLinksOriginalWithoutChangingIt |
| UAT-13 | BR-14 | CORRECTION không có tham chiếu | Từ chối, không lưu yêu cầu | correctionMustReferenceOriginalMovement |
| UAT-14 | BR-11,13 | MAIN 100, hoàn tất chuyển 80 sang BRANCH; hoàn tất lại | MAIN 20, BRANCH 80, tổng 100; lần hai bị chặn | transfersPreserveGlobalTotalAndCannotBeCompletedTwice |
| UAT-15 | BR-02 | Sau UAT-14, xuất 30 tại MAIN | Từ chối dù tổng là 100; không thay đổi số dư | transfersPreserveGlobalTotalAndCannotBeCompletedTwice |
| UAT-16 | BR-02,20 | Tồn 100, hai transaction đồng thời xuất 60 | Chỉ một thành công; còn 40; chỉ một dòng xuất được commit | concurrentDispatchesCannotOversell |
| UAT-17 | BR-07 | Nhập vào sản phẩm inactive | Từ chối trước khi ghi sổ | inactiveProductCannotReceiveStock |
| UAT-18 | BR-09 | Nhập tay kèm referencePurchaseOrderId | Từ chối, hướng dẫn dùng endpoint nhận PO | manualReceiptCannotBypassPoReceiving |
| UAT-19 | BR-16 | Hóa đơn 100, đã trả 80; trả 20.01 | Từ chối, đã trả vẫn 80 | cannotOverpayInvoice |
| UAT-20 | BR-16 | Hóa đơn 100, đã trả 80; trả đúng 20 | PAID, đã trả 100 | exactBalanceMarksInvoicePaid |
| UAT-21 | BR-15,16 | Thanh toán 0.001 | Từ chối do quá hai chữ số thập phân | rejectsFractionalCents |
| UAT-22 | BR-17 | Tạo hóa đơn cho PO DRAFT | Từ chối | rejectsInvoicesForUnapprovedOrders |
| UAT-23 | Quyền | CUSTOMER đọc products nội bộ; SUPPLIER_CONTACT đọc sổ kho; STAFF đọc invoices | HTTP 403 | externalRolesCannotReadInternalStockOrInvoices |
| UAT-24 | Phiên | Không token hoặc token sai định dạng | HTTP 401 | unauthenticatedAndMalformedTokensReturn401 |
| UAT-25 | Giao diện | Gửi DAMAGED 2 kèm lý do | Payload dùng type, hiện thông báo chờ duyệt và chưa đổi tồn | StockTransactions.test.jsx |
| UAT-26 | Giao diện | Token hết hạn hoặc API trả 401 | Kết thúc phiên; yêu cầu đăng nhập | AuthContext.test.jsx |
| UAT-27 | BR-19 | Ngừng kho còn hàng hoặc chuyển kho PENDING | Từ chối | Kiểm tra thủ công bổ sung |
| UAT-28 | BR-12 | Tạo chuyển kho, xuất bớt hàng nguồn rồi hoàn tất chuyển | Từ chối nếu nguồn thiếu; giữ PENDING; không tăng đích | Kiểm tra thủ công bổ sung |
| UAT-29 | Báo cáo | PO được duyệt 10 × 10 USD, đã nhận 4; thêm PO DRAFT 99 × 10 USD | Ordered value 100, received value 40, số PO được duyệt 1; không cộng DRAFT | supplierReportSeparatesApprovedCommitmentsFromReceivedGoods |

## Cách chạy và ghi kết quả

Chạy `mvn test -B` trong `backend` và `npm test`, `npm run lint`, `npm run build` trong `frontend`. `InventoryWorkflowIntegrationTest` cố định database H2 và transaction thật, gồm kiểm tra cạnh tranh đồng thời, để các biến datasource bên ngoài không ghi đè vào database thử nghiệm.

`ProductControllerIntegrationTest` chạy MySQL, Flyway và Hibernate validate. Trên CI, nó dùng dịch vụ MySQL của workflow qua MYSQL_TEST_URL, MYSQL_TEST_USERNAME và MYSQL_TEST_PASSWORD. Có thể đặt ba biến này tới một database thử nghiệm riêng trên máy phát triển để chạy đủ bộ kiểm thử mà không cần Docker. Khi không đặt MYSQL_TEST_URL, bộ kiểm thử thử khởi tạo MySQL 8 bằng Testcontainers và chỉ chạy nếu Docker sẵn sàng. Lần kiểm chứng trên MySQL 8.0.44 ngày 27/11/2026 có 72 bài backend qua, không có bài bị bỏ qua.

Khi kiểm tra thủ công, ghi ngày chạy, người chạy, dữ liệu đầu vào, actual result, ảnh hoặc response API và mã lỗi nếu có. Không đánh dấu một ca Passed chỉ vì đã viết kịch bản. Để nghiệm thu đầy đủ cần chạy MySQL migrations trên database trống, kiểm tra quyền bằng từng role và hoàn tất các ca thủ công còn lại.
