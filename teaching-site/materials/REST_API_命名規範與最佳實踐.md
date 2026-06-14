# REST API 命名規範與最佳實踐

## URL 命名原則

| 原則 | 範例 | 說明 |
|------|------|------|
| 複數名詞 | `/api/customers` | 資源使用複數 |
| 小寫 + 連字號 | `/api/order-items` | 不用底線或駝峰 |
| 巢狀資源 | `/api/customers/123/orders` | 表達從屬關係 |
| 版本前綴 | `/api/v2/customers` | API 版本控制 |

## HTTP 方法對應

| 方法 | 動作 | 範例 |
|------|------|------|
| GET | 讀取 | `GET /api/customers` |
| POST | 新增 | `POST /api/customers` |
| PUT | 完整更新 | `PUT /api/customers/1` |
| PATCH | 部分更新 | `PATCH /api/customers/1` |
| DELETE | 刪除 | `DELETE /api/customers/1` |

## 狀態碼選用

- `200 OK` — 成功
- `201 Created` — 新增成功
- `204 No Content` — 刪除成功
- `400 Bad Request` — 驗證失敗
- `401 Unauthorized` — 未登入
- `403 Forbidden` — 無權限
- `404 Not Found` — 資源不存在
- `500 Internal Server Error` — 伺服器錯誤

## Spring Boot 實作範例

```java
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @GetMapping
    public List<Customer> list() { ... }

    @GetMapping("/{id}")
    public Customer get(@PathVariable Long id) { ... }

    @PostMapping
    public ResponseEntity<Customer> create(@Valid @RequestBody Customer req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```
