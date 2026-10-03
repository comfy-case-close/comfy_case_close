# inventory (FEATURE) — chưa triển khai

Schema `inventory` đã được tạo ở `001-shared` nhưng **chưa có bảng nào**.

Bảng dự kiến: `item`, `recipe`, `recipe_line`, `stock_movement`, `waste_log`, `purchase_suggestion`.

**Mọi bảng phải có `business_id`** — nếu không, `900-rls` sẽ không bật RLS cho nó và `rls-guard.sh` sẽ đỏ.
