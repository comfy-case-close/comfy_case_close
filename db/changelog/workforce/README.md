# workforce (FEATURE) — chưa triển khai

Schema `workforce` đã được tạo ở `001-shared` nhưng **chưa có bảng nào**.

Khi triển khai, đặt migration vào đây. Nhắc lại ranh giới đã chốt ở ADR-0003 §14.3:

| | Trả lời câu hỏi | Nhịp đổi |
|---|---|---|
| `identity` | *"anh Tâm **là ai**"* | vài lần/tháng |
| `workforce` | *"anh Tâm **làm ca nào** tuần này"* | hàng chục lần/ngày |

Bảng dự kiến: `shift_schedule`, `shift_assignment`, `timeclock_entry`, `leave_request`, `shift_task`, `performance_review`.

**Mọi bảng phải có `business_id`** — nếu không, `900-rls` sẽ không bật RLS cho nó và `rls-guard.sh` sẽ đỏ.
