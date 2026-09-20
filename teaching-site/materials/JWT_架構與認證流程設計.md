# JWT 架構與認證流程設計

**本文件是第四章登入與權限控管的實作規格。** 課堂上的提示詞只寫重點，細節（依賴版本、資料表欄位、Token 規格、端點規則）一律以本文件為準；把這份 `.md` 整份附給 AI Agent，它就不必猜。

## 認證流程

```
客戶端                    伺服器
  │                        │
  │── POST /auth/login ────│ 驗證帳密
  │                        │ 產生 JWT Token
  │◄── { token: "..." } ──│
  │                        │
  │── GET /api/customers ──│ 驗證 JWT
  │   Authorization:       │ 取出角色
  │   Bearer <token>       │ 執行授權檢查
  │◄── 200 OK ────────────│
```

## JWT 結構

```
header.payload.signature

header:  { "alg": "RS256", "typ": "JWT" }
payload: { "sub": "user1", "roles": ["ADMIN"], "iat": 1700000000 }
```

## 依賴配置（pom.xml）

Token 的簽署與解析使用目前主流且穩定的 `io.jsonwebtoken`（jjwt）套件，三個 artifact 缺一不可：`jjwt-api` 是編譯期 API，`jjwt-impl` 與 `jjwt-jackson` 是執行期實作。

```xml
<!-- Spring Security Starter -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT (jjwt) 相關依賴 -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.13.0</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.13.0</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.13.0</version>
    <scope>runtime</scope>
</dependency>
```

## app_users 資料表與示範帳號

以 Flyway migration 新增 `app_users` 表，密碼一律以 BCrypt 雜湊儲存，啟動時若無帳號就建立四個示範帳號；`app_users.manager_id` 自我參照直屬主管，`sales`、`sales2` 皆指向 `manager`。

| 欄位 | 型別 | 說明 |
|---|---|---|
| `username` | varchar，唯一 | 登入帳號 |
| `password_hash` | varchar | BCrypt 雜湊後的密碼，不存明碼 |
| `display_name` | varchar | 顯示名稱 |
| `role` | varchar | `SALES` / `MANAGER` / `ADMIN` |
| `enabled` | boolean | 停用帳號用，預設 true |

| 示範帳號 | 角色 | 密碼 |
|---|---|---|
| `sales` | `SALES` 業務 | `password123` |
| `sales2` | `SALES` 業務（第二位，用來示範看不到別人的客戶） | `password123` |
| `manager` | `MANAGER` 主管 | `password123` |
| `admin` | `ADMIN` 管理員 | `password123` |

## Token 與密鑰規格

| 項目 | 規格 |
|---|---|
| 登入端點 | `POST /api/auth/login`，成功回傳 token 與使用者資訊（id、username、displayName、role） |
| claims | `sub`（帳號）、`uid`（使用者 id）、`role`（角色）、`exp`（到期時間） |
| 有效期 | 8 小時 |
| 簽章密鑰 | 由環境變數 `APP_SECURITY_JWT_SECRET` 讀取，長度不足 32 字元就拒絕啟動 |
| 存放方式 | 密鑰不可寫死在程式碼或 commit 進 git |
| Session | 無狀態（`SessionCreationPolicy.STATELESS`），不建立 HttpSession |

## 端點角色規則

規則集中寫在 `SecurityConfig` 的 `requestMatchers`，不要散落在各個 Controller。

| 路徑 | 需要的身分 |
|---|---|
| `/api/auth/login`、`/api/health` | 免登入 |
| `DELETE /api/customers/**` | 僅 `ADMIN` |
| `/api/manager/**` | `MANAGER` 或 `ADMIN` |
| `/api/admin/**` | 僅 `ADMIN` |
| `/swagger-ui/**`、`/v3/api-docs/**` | 需登入；並在 OpenAPI 宣告 `bearerAuth` 讓 Authorize 按鈕可用 |
| 其餘 API | 已登入即可 |

未登入回 **401**、權限不足回 **403**，兩者都使用 ProblemDetail 格式。更細的功能／資料權限（哪個角色看得到哪幾筆）請見講義《CRM_角色權限矩陣與RBAC實作規格》。

## Spring Security 設定重點

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/**").permitAll()
            .requestMatchers(POST, "/api/customers/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
}
```
