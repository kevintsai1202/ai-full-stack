# Admin JWT 認證與已發布文章編輯 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓管理者以 magic-link 登入取得 JWT session，並能在後台直接修改已發布文章的內容欄位。

**Architecture:** 沿用 `ReaderSessionService` 既有的 JWT + httpOnly cookie 模式，另建一組 admin 專用的 session／連結／寄信服務；`AdminKeyGuard.verify(String)` 簽名不變，內部改為「金鑰或 JWT 擇一通過」，使 78 處呼叫點與 9 支驗證腳本零改動。文章編輯以新端點只更新內容欄位，完全不碰寄送、計費與 `bodyHtml`。

**Tech Stack:** Java 21、Spring Boot 3.5.14、JJWT 0.12.6、Flyway、PostgreSQL、JUnit 5、原生 HTML/JS（`admin.html`）

## Global Constraints

- **設計依據**：`docs/superpowers/specs/2026-08-06-admin-jwt-auth-and-article-editing-design.md`，決策 D1–D8 不得偏離。
- **JDK**：所有 Maven 指令前必須設定 `JAVA_HOME`，本機預設是 JDK 8 會直接編譯失敗。專案**無 mvnw**，使用系統 `mvn`。
- **測試指令**：`$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=<TestClass>`（PowerShell 7+）
- **測試風格**：純 JUnit 5，直接 `new` 服務物件不啟 Spring context，固定 `NOW` 常數，每個測試方法上方加中文 Javadoc（比照 `ReaderSessionServiceTest`）。
- **註解**：所有新增類別、方法、重要變數皆須中文註解（專案 CLAUDE.md 要求）。
- **Flyway 版號**：本輪使用 **V24**，現有最大為 `V23__survey_vote_reward.sql`。
- **不可變更**：`tier`、`credit_cost`、`slug`、`published_at`、`body_html`、`email_log`、`article_access`、`credit_txn`。
- **分支**：`agent/admin-jwt-article-editing`（已建立，spec 於 `dbc93f1`）。

---

## File Structure

### 新增（後端）

| 檔案 | 責任 |
| --- | --- |
| `db/migration/V24__admin_auth_and_campaign_updated_at.sql` | `login_token.purpose`、`campaign.updated_at` |
| `survey/admin/AdminAllowlist.java` | 判斷 email 是否為管理者（讀 `ADMIN_EMAILS`） |
| `survey/admin/AdminSessionService.java` | admin JWT 簽發／解析／cookie |
| `survey/admin/AdminSessionAccess.java` | 從 cookie 讀出並驗證 admin session（端點與 guard 共用） |
| `survey/admin/AdminSiteLinks.java` | 組 admin 站連結（`ADMIN_BASE_URL`） |
| `survey/admin/AdminLoginMailService.java` | 寄 admin 登入信 |
| `survey/admin/AdminAuthController.java` | login／verify／logout／me 四端點 |

### 修改（後端）

| 檔案 | 變更 |
| --- | --- |
| `reader/LoginToken.java` | 新增 `purpose` 欄位 |
| `reader/LoginTokenService.java` | `issue`／`consume` 加 purpose |
| `reader/LoginMailService.java` | 呼叫 `issue` 時傳 `reader` |
| `AdminKeyGuard.java` | 金鑰或 JWT 擇一通過 |
| `newsletter/Campaign.java` | 新增 `updatedAt` |
| `newsletter/CampaignService.java` | 新增 `updateContent` |
| `newsletter/AdminCampaignController.java` | 新增 `PUT /campaigns/{id}/content` |

### 修改（前端）

`static/admin.html`：登入 gate、右上工具列、日夜主題、編輯入口。

---

### Task 1: token 用途隔離與資料欄位

**Files:**

- Create: `survey-backend/src/main/resources/db/migration/V24__admin_auth_and_campaign_updated_at.sql`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/LoginToken.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/LoginTokenService.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/LoginMailService.java:71`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/LoginTokenPurposeTest.java`

**Interfaces:**

- Produces：`LoginTokenService.issue(String email, String purpose, OffsetDateTime now)`、`LoginTokenService.consume(String rawToken, String purpose, OffsetDateTime now)`、常數 `LoginToken.PURPOSE_READER = "reader"`、`LoginToken.PURPOSE_ADMIN = "admin"`。Task 5、6 會用到。

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** token 用途隔離：reader 的登入連結不得被拿去兌換 admin 權限 */
class LoginTokenPurposeTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-06T12:00:00+08:00");

    /** 以 reader 用途簽發的 token，用 admin 用途兌換必須失敗（提權防護） */
    @Test
    void readerTokenCannotBeConsumedAsAdmin() {
        LoginTokenService service = newService();

        String raw = service.issue("kevin@example.com", LoginToken.PURPOSE_READER, NOW);
        Optional<String> result = service.consume(raw, LoginToken.PURPOSE_ADMIN, NOW);

        assertTrue(result.isEmpty(), "reader token 不得兌換成 admin");
    }

    /** 用途相符時應正常兌換並回傳 email */
    @Test
    void adminTokenConsumesWithMatchingPurpose() {
        LoginTokenService service = newService();

        String raw = service.issue("kevin@example.com", LoginToken.PURPOSE_ADMIN, NOW);
        Optional<String> result = service.consume(raw, LoginToken.PURPOSE_ADMIN, NOW);

        assertEquals(Optional.of("kevin@example.com"), result);
    }
}
```

`newService()` 依專案既有測試如何建構 `LoginTokenService` 而定：讀 `LoginTokenServiceTest`（若存在）沿用其建構方式；若無既有測試，以 in-memory fake `LoginTokenRepository` 建構，fake 需實作 `save`、`findByTokenHash`、`markUsedIfUnused` 三個方法。

- [ ] **Step 2: 執行測試確認失敗**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=LoginTokenPurposeTest`
Expected: 編譯失敗，`PURPOSE_READER` 與三參數 `issue` 不存在。

- [ ] **Step 3: 實作**

`V24__admin_auth_and_campaign_updated_at.sql`：

```sql
-- admin 登入用途隔離：既有資料一律視為讀者登入
ALTER TABLE login_token ADD COLUMN purpose VARCHAR(16) NOT NULL DEFAULT 'reader';

-- 文章內容最後修改時間（僅記錄時間，不做修改歷史）
ALTER TABLE campaign ADD COLUMN updated_at TIMESTAMPTZ;
```

`LoginToken.java` 新增欄位與常數：

```java
/** 用途：讀者站登入 */
public static final String PURPOSE_READER = "reader";
/** 用途：管理後台登入 */
public static final String PURPOSE_ADMIN = "admin";

/** token 用途；防止讀者登入連結被拿去兌換管理權限 */
@Column(nullable = false)
private String purpose = PURPOSE_READER;

public String getPurpose() { return purpose; }
```

建構子新增四參數版本（保留三參數版本委派為 `PURPOSE_READER`）：

```java
public LoginToken(String tokenHash, String email, OffsetDateTime expiresAt, String purpose) {
    this(tokenHash, email, expiresAt);
    this.purpose = purpose;
}
```

`LoginTokenService.java`：

```java
/** 簽發指定用途的 token；沿用原方法者一律視為讀者登入 */
public String issue(String email, String purpose, OffsetDateTime now) {
    byte[] raw = new byte[TOKEN_BYTES];
    random.nextBytes(raw);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    repository.save(new LoginToken(hash(rawToken), normalize(email), now.plusMinutes(ttlMinutes), purpose));
    return rawToken;
}

/** 相容既有呼叫端：未指定用途即為讀者登入 */
public String issue(String email, OffsetDateTime now) {
    return issue(email, LoginToken.PURPOSE_READER, now);
}
```

`consume` 在既有的 `token.isUsed() || token.isExpired(now)` 檢查中加入用途比對（**必須放在原子更新之前**，用途不符就不該消耗掉 token）：

```java
public Optional<String> consume(String rawToken, String purpose, OffsetDateTime now) {
    if (!StringUtils.hasText(rawToken)) {
        return Optional.empty();
    }
    Optional<LoginToken> found = repository.findByTokenHash(hash(rawToken));
    if (found.isEmpty()) {
        return Optional.empty();
    }
    LoginToken token = found.get();
    // 用途不符一律拒絕，且不消耗 token：reader 的連結不得換到 admin 權限
    if (!purpose.equals(token.getPurpose()) || token.isUsed() || token.isExpired(now)) {
        return Optional.empty();
    }
    int updated = repository.markUsedIfUnused(token.getTokenHash(), now);
    if (updated == 0) {
        return Optional.empty();
    }
    return Optional.of(token.getEmail());
}

/** 相容既有呼叫端：未指定用途即為讀者登入 */
public Optional<String> consume(String rawToken, OffsetDateTime now) {
    return consume(rawToken, LoginToken.PURPOSE_READER, now);
}
```

`LoginMailService.java:71` 改為明示用途：

```java
String rawToken = tokenService.issue(email, LoginToken.PURPOSE_READER, now);
```

- [ ] **Step 4: 執行測試確認通過**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=LoginTokenPurposeTest,ReaderAuthControllerTest`
Expected: 全數 PASS（`ReaderAuthControllerTest` 用於確認既有讀者流程未被破壞）。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/resources/db/migration/V24__admin_auth_and_campaign_updated_at.sql \
        survey-backend/src/main/java/world/springai/survey/reader/LoginToken.java \
        survey-backend/src/main/java/world/springai/survey/reader/LoginTokenService.java \
        survey-backend/src/main/java/world/springai/survey/reader/LoginMailService.java \
        survey-backend/src/test/java/world/springai/survey/reader/LoginTokenPurposeTest.java
git commit -m "feat(auth): login_token 加入 purpose 用途隔離，防止讀者連結提權"
```

---

### Task 2: 管理者白名單

**Files:**

- Create: `survey-backend/src/main/java/world/springai/survey/admin/AdminAllowlist.java`
- Test: `survey-backend/src/test/java/world/springai/survey/admin/AdminAllowlistTest.java`

**Interfaces:**

- Produces：`AdminAllowlist.isAdmin(String email)` 回 `boolean`、`AdminAllowlist.isEnabled()` 回 `boolean`。Task 5、6、7 會用到。

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey.admin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 白名單比對：大小寫與空白容錯、未設定時停用 */
class AdminAllowlistTest {

    /** 名單內的 email 應通過，且比對不分大小寫、忽略前後空白 */
    @Test
    void matchesIgnoringCaseAndWhitespace() {
        AdminAllowlist allowlist = new AdminAllowlist(" Kevin@Example.com , other@example.com ");

        assertTrue(allowlist.isAdmin("kevin@example.com"));
        assertTrue(allowlist.isAdmin("  OTHER@EXAMPLE.COM  "));
    }

    /** 不在名單內的 email 一律拒絕 */
    @Test
    void rejectsUnlistedEmail() {
        AdminAllowlist allowlist = new AdminAllowlist("kevin@example.com");

        assertFalse(allowlist.isAdmin("attacker@example.com"));
        assertFalse(allowlist.isAdmin(null));
    }

    /** 未設定白名單時停用 JWT 登入，且任何 email 都不是管理者 */
    @Test
    void disabledWhenUnset() {
        AdminAllowlist allowlist = new AdminAllowlist("");

        assertFalse(allowlist.isEnabled());
        assertFalse(allowlist.isAdmin("kevin@example.com"));
    }
}
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=AdminAllowlistTest`
Expected: 編譯失敗，`AdminAllowlist` 不存在。

- [ ] **Step 3: 實作**

```java
package world.springai.survey.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理者白名單：由環境變數 {@code ADMIN_EMAILS} 指定，逗號分隔。
 *
 * <p>刻意不進資料庫（決策 D1）：目前僅一位管理者，換人只需改 Zeabur 變數。
 * 未設定時 {@link #isEnabled()} 為 false，JWT 登入路徑停用而金鑰路徑照常，
 * 確保漏設變數時後台不會完全無法進入。</p>
 */
@Component
public class AdminAllowlist {

    /** 正規化後的管理者 email 集合（小寫、去空白） */
    private final Set<String> emails;

    /** 注入白名單設定 */
    public AdminAllowlist(@Value("${app.admin.emails:}") String rawEmails) {
        this.emails = rawEmails == null ? Set.of() : Arrays.stream(rawEmails.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> s.toLowerCase())
            .collect(Collectors.toUnmodifiableSet());
    }

    /** 白名單是否已設定；未設定即停用 JWT 登入 */
    public boolean isEnabled() {
        return !emails.isEmpty();
    }

    /** 比對 email 是否為管理者；不分大小寫、忽略前後空白 */
    public boolean isAdmin(String email) {
        return email != null && emails.contains(email.trim().toLowerCase());
    }
}
```

於 `application.yml` 的 `app.admin` 區塊新增（該區塊已存在，內含 `entry-host`）：

```yaml
    emails: ${ADMIN_EMAILS:}
```

- [ ] **Step 4: 執行測試確認通過**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=AdminAllowlistTest`
Expected: 3 個測試全 PASS。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/admin/AdminAllowlist.java \
        survey-backend/src/test/java/world/springai/survey/admin/AdminAllowlistTest.java \
        survey-backend/src/main/resources/application.yml
git commit -m "feat(auth): 新增管理者 email 白名單，未設定時停用 JWT 登入"
```

---

### Task 3: admin session 服務

**Files:**

- Create: `survey-backend/src/main/java/world/springai/survey/admin/AdminSessionService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/admin/AdminSessionServiceTest.java`

**Interfaces:**

- Produces：`AdminSessionService.issueJwt(String email, OffsetDateTime now)` 回 `String`、`readEmail(String jwt, OffsetDateTime now)` 回 `Optional<String>`、`buildSessionCookie(String jwt)` 與 `buildClearCookie()` 回 `ResponseCookie`、常數 `COOKIE_NAME = "admin_session"`。Task 6、7 會用到。

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey.admin;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AdminSessionService：JWT 往返、過期與篡改拒絕、cookie 安全屬性 */
class AdminSessionServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-06T12:00:00+08:00");
    /** HS256 要求秘鑰 ≥ 32 bytes */
    private static final String SECRET = "admin-test-secret-at-least-32-bytes!!!!";

    private AdminSessionService httpsService() {
        return new AdminSessionService(SECRET, 7, "https://admin.example.com");
    }

    /** 簽發後應能讀回同一個 email */
    @Test
    void issuedJwtRoundTripsEmail() {
        AdminSessionService service = httpsService();

        String jwt = service.issueJwt("kevin@example.com", NOW);

        assertEquals(Optional.of("kevin@example.com"), service.readEmail(jwt, NOW.plusDays(1)));
    }

    /** 超過效期的 JWT 一律視為未登入 */
    @Test
    void expiredJwtIsRejected() {
        AdminSessionService service = httpsService();

        String jwt = service.issueJwt("kevin@example.com", NOW);

        assertTrue(service.readEmail(jwt, NOW.plusDays(8)).isEmpty());
    }

    /** 被篡改的 JWT 一律視為未登入，且不得拋出例外 */
    @Test
    void tamperedJwtIsRejected() {
        AdminSessionService service = httpsService();

        String jwt = service.issueJwt("kevin@example.com", NOW);

        assertTrue(service.readEmail(jwt + "x", NOW).isEmpty());
        assertTrue(service.readEmail("not-a-jwt", NOW).isEmpty());
    }

    /** cookie 必須 httpOnly、SameSite=Lax，https 站台需帶 Secure */
    @Test
    void cookieCarriesSecurityAttributes() {
        ResponseCookie cookie = httpsService().buildSessionCookie("token");

        assertEquals("admin_session", cookie.getName());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("Lax", cookie.getSameSite());
    }

    /** http 站台（本機開發）不得帶 Secure，否則瀏覽器會丟棄 cookie */
    @Test
    void plainHttpSiteOmitsSecureFlag() {
        AdminSessionService service = new AdminSessionService(SECRET, 7, "http://127.0.0.1:8080");

        assertFalse(service.buildSessionCookie("token").isSecure());
    }
}
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=AdminSessionServiceTest`
Expected: 編譯失敗，`AdminSessionService` 不存在。

- [ ] **Step 3: 實作**

```java
package world.springai.survey.admin;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Optional;

/**
 * 管理後台的登入態：簽發／解析 JWT 並組 session cookie。
 *
 * <p>刻意與 {@code ReaderSessionService} 分離並使用不同秘鑰：讀者 token 的簽發若
 * 出現瑕疵，不應蔓延到權限更大的後台。</p>
 */
@Service
public class AdminSessionService {

    private static final Logger log = LoggerFactory.getLogger(AdminSessionService.class);

    /** session cookie 名稱 */
    public static final String COOKIE_NAME = "admin_session";

    /** JWT 簽章金鑰（HS256） */
    private final SecretKey key;
    /** 登入態有效天數 */
    private final int ttlDays;
    /** cookie 是否帶 Secure；依對外網址是否為 https 自動決定，本機 http 下不可帶否則會被丟棄 */
    private final boolean secureCookie;

    /** 注入 JWT 秘鑰、效期與後台對外網址 */
    public AdminSessionService(@Value("${app.admin.jwt-secret}") String secret,
                               @Value("${app.admin.jwt-ttl-days}") int ttlDays,
                               @Value("${app.admin.base-url:${app.public-base-url}}") String baseUrl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlDays = ttlDays;
        this.secureCookie = baseUrl != null && baseUrl.trim().toLowerCase().startsWith("https://");
    }

    /** 簽發 JWT，subject 為管理者 email */
    public String issueJwt(String email, OffsetDateTime now) {
        return Jwts.builder()
            .subject(email)
            .issuedAt(Date.from(now.toInstant()))
            .expiration(Date.from(now.plusDays(ttlDays).toInstant()))
            .signWith(key)
            .compact();
    }

    /** 從 JWT 讀出 email；簽章不符、過期、格式錯誤一律回 empty，不拋例外 */
    public Optional<String> readEmail(String jwt, OffsetDateTime now) {
        if (!StringUtils.hasText(jwt)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(now.toInstant()))
                .build()
                .parseSignedClaims(jwt)
                .getPayload()
                .getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("管理者 session 無效：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 組 session cookie：httpOnly 防 XSS、SameSite=Lax 防 CSRF */
    public ResponseCookie buildSessionCookie(String jwt) {
        return ResponseCookie.from(COOKIE_NAME, jwt)
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofDays(ttlDays))
            .build();
    }

    /** 組登出用的清除 cookie */
    public ResponseCookie buildClearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build();
    }
}
```

`application.yml` 的 `app.admin` 區塊新增：

```yaml
    base-url: ${ADMIN_BASE_URL:${APP_PUBLIC_BASE_URL:http://127.0.0.1:8080}}
    jwt-secret: ${ADMIN_JWT_SECRET:dev-admin-jwt-secret-change-me-32chars}
    jwt-ttl-days: ${ADMIN_JWT_TTL_DAYS:7}
```

- [ ] **Step 4: 執行測試確認通過**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=AdminSessionServiceTest`
Expected: 5 個測試全 PASS。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/admin/AdminSessionService.java \
        survey-backend/src/test/java/world/springai/survey/admin/AdminSessionServiceTest.java \
        survey-backend/src/main/resources/application.yml
git commit -m "feat(auth): 新增 admin session 服務（JWT + httpOnly cookie）"
```

---

### Task 4: admin 站連結組裝

**Files:**

- Create: `survey-backend/src/main/java/world/springai/survey/admin/AdminSiteLinks.java`
- Test: `survey-backend/src/test/java/world/springai/survey/admin/AdminSiteLinksTest.java`

**Interfaces:**

- Produces：`AdminSiteLinks.verifyLogin(String rawToken)` 回完整 URL 字串。Task 5 會用到。

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey.admin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** admin 登入連結必須指向後台網域，否則 cookie 會種在錯誤的 host */
class AdminSiteLinksTest {

    /** 連結應以設定的後台網址為基底，並帶上 token 查詢參數 */
    @Test
    void verifyLoginPointsToAdminHost() {
        AdminSiteLinks links = new AdminSiteLinks("https://admin.springai.world");

        assertEquals("https://admin.springai.world/api/admin/login/verify?t=abc123",
            links.verifyLogin("abc123"));
    }

    /** 尾端斜線與前後空白不得造成重複斜線 */
    @Test
    void trailingSlashIsNormalized() {
        AdminSiteLinks links = new AdminSiteLinks("  https://admin.springai.world/  ");

        assertEquals("https://admin.springai.world/api/admin/login/verify?t=abc123",
            links.verifyLogin("abc123"));
    }
}
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=AdminSiteLinksTest`
Expected: 編譯失敗，`AdminSiteLinks` 不存在。

- [ ] **Step 3: 實作**

```java
package world.springai.survey.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 後台站連結組裝器。
 *
 * <p>登入信裡的連結必須指向後台網域，否則 cookie 會種在讀者站的 host 上而後台讀不到
 * （cookie 為 host-only）。</p>
 */
@Component
public class AdminSiteLinks {

    /** 後台對外網址，已去除尾端斜線 */
    private final String baseUrl;

    /** 注入後台對外網址 */
    public AdminSiteLinks(@Value("${app.admin.base-url:${app.public-base-url}}") String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** 組 magic-link 兌換連結 */
    public String verifyLogin(String rawToken) {
        return baseUrl + "/api/admin/login/verify?t="
            + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 4: 執行測試確認通過**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=AdminSiteLinksTest`
Expected: 2 個測試全 PASS。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/admin/AdminSiteLinks.java \
        survey-backend/src/test/java/world/springai/survey/admin/AdminSiteLinksTest.java
git commit -m "feat(auth): 新增後台站連結組裝器"
```

---

### Task 5: admin 登入信寄送

**Files:**

- Create: `survey-backend/src/main/java/world/springai/survey/admin/AdminLoginMailService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/admin/AdminLoginMailServiceTest.java`

**Interfaces:**

- Consumes：`AdminAllowlist.isAdmin`、`AdminSiteLinks.verifyLogin`、`LoginTokenService.issue(email, purpose, now)`、`LoginToken.PURPOSE_ADMIN`
- Produces：`AdminLoginMailService.sendIfAdmin(String email, OffsetDateTime now)` 回 `void`（刻意不回報是否寄出，避免呼叫端不慎把結果洩漏給前端）。Task 6 會用到。

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey.admin;

import org.junit.jupiter.api.Test;
import world.springai.survey.mail.MailSender;
import world.springai.survey.reader.LoginToken;
import world.springai.survey.reader.LoginTokenService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** admin 登入信：只寄給白名單，且必須以 admin 用途簽發 token */
class AdminLoginMailServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-06T12:00:00+08:00");

    /** 白名單內的 email 應收到信，且 token 用途為 admin */
    @Test
    void sendsToAllowlistedEmailWithAdminPurpose() throws Exception {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        MailSender mailSender = mock(MailSender.class);
        when(tokenService.issue(eq("kevin@example.com"), eq(LoginToken.PURPOSE_ADMIN), any()))
            .thenReturn("raw-token");

        AdminLoginMailService service = new AdminLoginMailService(
            new AdminAllowlist("kevin@example.com"), tokenService, mailSender,
            new AdminSiteLinks("https://admin.example.com"));

        service.sendIfAdmin("kevin@example.com", NOW);

        verify(tokenService).issue(eq("kevin@example.com"), eq(LoginToken.PURPOSE_ADMIN), any());
        verify(mailSender).send(eq("kevin@example.com"), any(), any());
    }

    /** 非白名單的 email 不得寄信，也不得簽發任何 token */
    @Test
    void doesNotSendToUnlistedEmail() throws Exception {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        MailSender mailSender = mock(MailSender.class);

        AdminLoginMailService service = new AdminLoginMailService(
            new AdminAllowlist("kevin@example.com"), tokenService, mailSender,
            new AdminSiteLinks("https://admin.example.com"));

        service.sendIfAdmin("attacker@example.com", NOW);

        verify(tokenService, never()).issue(any(), any(), any());
        verify(mailSender, never()).send(any(), any(), any());
    }

    /** 寄信失敗不得往外拋例外，否則端點會回 500 而洩漏該 email 是管理者 */
    @Test
    void swallowsMailFailure() throws Exception {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        MailSender mailSender = mock(MailSender.class);
        when(tokenService.issue(any(), any(), any())).thenReturn("raw-token");
        when(mailSender.send(any(), any(), any())).thenThrow(new RuntimeException("smtp down"));

        AdminLoginMailService service = new AdminLoginMailService(
            new AdminAllowlist("kevin@example.com"), tokenService, mailSender,
            new AdminSiteLinks("https://admin.example.com"));

        service.sendIfAdmin("kevin@example.com", NOW);  // 不應拋例外
    }
}
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=AdminLoginMailServiceTest`
Expected: 編譯失敗，`AdminLoginMailService` 不存在。

- [ ] **Step 3: 實作**

```java
package world.springai.survey.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import world.springai.survey.mail.MailSender;
import world.springai.survey.reader.LoginToken;
import world.springai.survey.reader.LoginTokenService;

import java.time.OffsetDateTime;

/**
 * 寄送管理後台的 magic-link 登入信。
 *
 * <p><b>只寄給白名單</b>，且刻意不回報「有沒有寄出」：呼叫端一律回相同訊息，
 * 避免這個端點被拿來逐一測試哪個 email 是管理者。</p>
 */
@Service
public class AdminLoginMailService {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginMailService.class);

    /** 登入信主旨 */
    private static final String SUBJECT = "管理後台登入連結";

    private final AdminAllowlist allowlist;
    private final LoginTokenService tokenService;
    private final MailSender mailSender;
    private final AdminSiteLinks siteLinks;

    /** 注入白名單、token 服務、寄信與連結組裝器 */
    public AdminLoginMailService(AdminAllowlist allowlist,
                                 LoginTokenService tokenService,
                                 MailSender mailSender,
                                 AdminSiteLinks siteLinks) {
        this.allowlist = allowlist;
        this.tokenService = tokenService;
        this.mailSender = mailSender;
        this.siteLinks = siteLinks;
    }

    /** 若 email 為管理者則寄出登入信；否則靜默略過（不得讓呼叫端得知差異） */
    public void sendIfAdmin(String email, OffsetDateTime now) {
        if (!allowlist.isAdmin(email)) {
            log.info("非管理者的後台登入請求，略過寄送");
            return;
        }
        try {
            String rawToken = tokenService.issue(email, LoginToken.PURPOSE_ADMIN, now);
            mailSender.send(email, SUBJECT, buildHtml(siteLinks.verifyLogin(rawToken)));
        } catch (Exception e) {
            // 不得往外拋：端點回 500 等於告訴對方這個 email 是管理者
            log.warn("後台登入信寄送失敗：{}", e.getMessage());
        }
    }

    /** 組登入信 HTML；交易信，刻意不含退訂連結 */
    private String buildHtml(String loginLink) {
        return """
            <div style="font-family:system-ui,'Microsoft JhengHei',sans-serif;line-height:1.7;max-width:560px;margin:0 auto;color:#102033">
              <h2>管理後台登入</h2>
              <p>這個連結 15 分鐘內有效，而且只能使用一次。</p>
              <p style="text-align:center;margin:28px 0">
                <a href="%s" style="background:#0d9488;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:700">登入後台</a>
              </p>
              <p style="color:#8190a3;font-size:.85rem">若不是你本人操作，請忽略這封信。</p>
            </div>
            """.formatted(loginLink);
    }
}
```

- [ ] **Step 4: 執行測試確認通過**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=AdminLoginMailServiceTest`
Expected: 3 個測試全 PASS。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/admin/AdminLoginMailService.java \
        survey-backend/src/test/java/world/springai/survey/admin/AdminLoginMailServiceTest.java
git commit -m "feat(auth): 新增 admin 登入信寄送（僅白名單、不洩漏寄送結果）"
```

---

### Task 6: admin 認證端點

**Files:**

- Create: `survey-backend/src/main/java/world/springai/survey/admin/AdminAuthController.java`
- Create: `survey-backend/src/main/java/world/springai/survey/admin/AdminSessionAccess.java`
- Test: `survey-backend/src/test/java/world/springai/survey/admin/AdminAuthControllerTest.java`

**Interfaces:**

- Consumes：`AdminLoginMailService.sendIfAdmin`、`LoginTokenService.consume(rawToken, purpose, now)`、`AdminAllowlist.isAdmin`、`AdminSessionService`
- Produces：四個端點 `POST /api/admin/login`、`GET /api/admin/login/verify`、`POST /api/admin/logout`、`GET /api/admin/me`；以及 `AdminSessionAccess.readEmail(HttpServletRequest, OffsetDateTime)` 回 `Optional<String>`。端點供 Task 10、11 的前端呼叫；`AdminSessionAccess` 供 Task 7 的 `AdminKeyGuard` 使用，因此**必須是 public Spring bean**（`AdminKeyGuard` 位於上層 package，package-private 存取不到）。

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey.admin;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import world.springai.survey.reader.LoginToken;
import world.springai.survey.reader.LoginTokenService;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** admin 認證端點：白名單二次比對、用途隔離、cookie 種發 */
class AdminAuthControllerTest {

    private static final String SECRET = "admin-test-secret-at-least-32-bytes!!!!";

    /** token 有效且仍在白名單內：應種 cookie 並導向後台 */
    @Test
    void verifyIssuesCookieForAllowlistedEmail() {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        when(tokenService.consume(eq("tok"), eq(LoginToken.PURPOSE_ADMIN), any()))
            .thenReturn(Optional.of("kevin@example.com"));

        AdminAuthController controller = newController(tokenService, "kevin@example.com");
        ResponseEntity<Void> response = controller.verifyLogin("tok");

        assertEquals(302, response.getStatusCode().value());
        assertEquals("/admin.html", response.getHeaders().getFirst(HttpHeaders.LOCATION));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).startsWith("admin_session="));
    }

    /**
     * token 有效但該 email 已不在白名單：必須拒絕。
     * 白名單來自環境變數，可能在簽發後被調整（管理者換人），故 verify 端必須二次比對。
     */
    @Test
    void verifyRejectsEmailRemovedFromAllowlist() {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        when(tokenService.consume(eq("tok"), eq(LoginToken.PURPOSE_ADMIN), any()))
            .thenReturn(Optional.of("former@example.com"));

        AdminAuthController controller = newController(tokenService, "kevin@example.com");
        ResponseEntity<Void> response = controller.verifyLogin("tok");

        assertEquals(302, response.getStatusCode().value());
        assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "不得種 cookie");
    }

    /** token 無效：導回登入頁且不種 cookie */
    @Test
    void verifyRejectsInvalidToken() {
        LoginTokenService tokenService = mock(LoginTokenService.class);
        when(tokenService.consume(any(), any(), any())).thenReturn(Optional.empty());

        AdminAuthController controller = newController(tokenService, "kevin@example.com");
        ResponseEntity<Void> response = controller.verifyLogin("bad");

        assertEquals(302, response.getStatusCode().value());
        assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    }

    /** 建構受測 controller，白名單內容可指定 */
    private AdminAuthController newController(LoginTokenService tokenService, String allowlist) {
        AdminAllowlist list = new AdminAllowlist(allowlist);
        return new AdminAuthController(
            mock(AdminLoginMailService.class), tokenService, list,
            new AdminSessionService(SECRET, 7, "https://admin.example.com"));
    }
}
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=AdminAuthControllerTest`
Expected: 編譯失敗，`AdminAuthController` 不存在。

- [ ] **Step 3: 實作**

```java
package world.springai.survey.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.reader.LoginToken;
import world.springai.survey.reader.LoginTokenService;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 管理後台認證端點。
 *
 * <p>全部掛在 {@code /api/admin/} 之下，天然通過 {@code AdminEntryHostFilter} 的
 * 路徑白名單，不需修改該 filter。</p>
 */
@RestController
public class AdminAuthController {

    private final AdminLoginMailService loginMailService;
    private final LoginTokenService tokenService;
    private final AdminAllowlist allowlist;
    private final AdminSessionService sessionService;

    /** 注入登入信、token、白名單與 session 服務 */
    public AdminAuthController(AdminLoginMailService loginMailService,
                               LoginTokenService tokenService,
                               AdminAllowlist allowlist,
                               AdminSessionService sessionService) {
        this.loginMailService = loginMailService;
        this.tokenService = tokenService;
        this.allowlist = allowlist;
        this.sessionService = sessionService;
    }

    /** 登入請求內容 */
    public record LoginRequest(@NotBlank @Email String email) {}

    /**
     * 請求後台登入信。
     *
     * <p><b>一律回相同結果</b>，不論該 email 是否為管理者——回應若有差異，
     * 這個端點就會變成管理者名單的查詢工具。</p>
     */
    @PostMapping("/api/admin/login")
    public Map<String, Boolean> requestLogin(@Valid @RequestBody LoginRequest request) {
        loginMailService.sendIfAdmin(request.email(), OffsetDateTime.now());
        return Map.of("accepted", true);
    }

    /**
     * 承接 magic link：兌換 admin 用途的 token、二次比對白名單後種 cookie。
     *
     * <p>白名單來自環境變數，可能在 token 簽發後被調整，故此處必須再次比對，
     * 確保已撤下的信箱無法用手上的舊連結登入。</p>
     */
    @GetMapping("/api/admin/login/verify")
    public ResponseEntity<Void> verifyLogin(@RequestParam("t") String token) {
        OffsetDateTime now = OffsetDateTime.now();
        Optional<String> email = tokenService.consume(token, LoginToken.PURPOSE_ADMIN, now);

        if (email.isEmpty() || !allowlist.isAdmin(email.get())) {
            return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, "/admin.html?error=invalid")
                .build();
        }

        String jwt = sessionService.issueJwt(email.get(), now);
        return ResponseEntity.status(302)
            .header(HttpHeaders.LOCATION, "/admin.html")
            .header(HttpHeaders.SET_COOKIE, sessionService.buildSessionCookie(jwt).toString())
            .build();
    }

    /** 登出：清除 session cookie */
    @PostMapping("/api/admin/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, sessionService.buildClearCookie().toString())
            .build();
    }

    /** 回傳目前登入者；未登入回 401，供前端決定是否顯示登入 gate */
    @GetMapping("/api/admin/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        Optional<String> email = sessionAccess.readEmail(request, OffsetDateTime.now());
        if (email.isEmpty() || !allowlist.isAdmin(email.get())) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(Map.of("email", email.get(), "mode", "jwt"));
    }
}
```

建構子改為注入五個依賴（`sessionAccess` 為新增，欄位宣告與指派比照其他四個）：

```java
public AdminAuthController(AdminLoginMailService loginMailService,
                           LoginTokenService tokenService,
                           AdminAllowlist allowlist,
                           AdminSessionService sessionService,
                           AdminSessionAccess sessionAccess) {
```

同時建立共用的 cookie 讀取 bean。**必須是 public 且為 Spring bean**：`AdminKeyGuard`（Task 7）位於上層 package `world.springai.survey`，package-private 的類別存取不到。

```java
package world.springai.survey.admin;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;

/** 從請求 cookie 取出並驗證 admin session；供認證端點與 AdminKeyGuard 共用 */
@Component
public class AdminSessionAccess {

    private final AdminSessionService sessionService;

    /** 注入 session 服務 */
    public AdminSessionAccess(AdminSessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** 讀取並驗證 admin_session cookie，回傳管理者 email */
    public Optional<String> readEmail(HttpServletRequest request, OffsetDateTime now) {
        if (request == null || request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
            .filter(c -> AdminSessionService.COOKIE_NAME.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .flatMap(jwt -> sessionService.readEmail(jwt, now));
    }
}
```

測試中的 `newController()` 需一併傳入 `new AdminSessionAccess(sessionService)`。

- [ ] **Step 4: 執行測試確認通過**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=AdminAuthControllerTest`
Expected: 3 個測試全 PASS。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/admin/AdminAuthController.java \
        survey-backend/src/main/java/world/springai/survey/admin/AdminSessionAccess.java \
        survey-backend/src/test/java/world/springai/survey/admin/AdminAuthControllerTest.java
git commit -m "feat(auth): 新增 admin 登入/驗證/登出/me 端點"
```

---

### Task 7: AdminKeyGuard 金鑰與 JWT 並存

**Files:**

- Modify: `survey-backend/src/main/java/world/springai/survey/AdminKeyGuard.java`
- Test: `survey-backend/src/test/java/world/springai/survey/AdminKeyGuardTest.java`

**Interfaces:**

- Consumes：`AdminSessionService`、`AdminAllowlist`、`AdminSessionReader.readEmail`
- Produces：`AdminKeyGuard.verify(String key)` **簽名不變**（78 處呼叫點零改動）。

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.admin.AdminAllowlist;
import world.springai.survey.admin.AdminSessionService;

import jakarta.servlet.http.Cookie;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** AdminKeyGuard：金鑰與 admin session 兩條路徑皆可通過，皆無則 401 */
class AdminKeyGuardTest {

    private static final String SECRET = "admin-test-secret-at-least-32-bytes!!!!";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-06T12:00:00+08:00");

    /** 正確金鑰應通過（既有 78 處呼叫點與 9 支腳本的行為必須維持） */
    @Test
    void correctApiKeyPasses() {
        assertDoesNotThrow(() -> newGuard().verify("secret-key"));
    }

    /** 帶有效 admin_session cookie 時，即使未給金鑰也應通過 */
    @Test
    void validSessionCookiePasses() {
        AdminSessionService session = new AdminSessionService(SECRET, 7, "https://admin.example.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AdminSessionService.COOKIE_NAME,
            session.issueJwt("kevin@example.com", NOW)));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        try {
            assertDoesNotThrow(() -> newGuard().verify(null));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    /** 金鑰錯誤且無 session：必須回 401 */
    @Test
    void wrongKeyWithoutSessionFails() {
        RequestContextHolder.resetRequestAttributes();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> newGuard().verify("wrong-key"));

        assertEquals(401, ex.getStatusCode().value());
    }

    /** 建構受測 guard；白名單含測試用 email */
    private AdminKeyGuard newGuard() {
        return new AdminKeyGuard("secret-key",
            new AdminSessionService(SECRET, 7, "https://admin.example.com"),
            new AdminAllowlist("kevin@example.com"));
    }
}
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=AdminKeyGuardTest`
Expected: 編譯失敗，`AdminKeyGuard` 建構子只接受一個參數。

- [ ] **Step 3: 實作**

```java
package world.springai.survey;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.admin.AdminAllowlist;
import world.springai.survey.admin.AdminSessionAccess;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;

/**
 * 集中管理後台端點的身分驗證；供所有 {@code /api/admin} 端點共用。
 *
 * <p><b>兩條路徑擇一通過</b>：機器（驗證腳本、CI）帶 {@code X-Admin-Key}，
 * 瀏覽器帶 {@code admin_session} cookie。方法簽名刻意維持不變，讓既有 78 處
 * 呼叫點與 9 支驗證腳本零改動。</p>
 */
@Component
public class AdminKeyGuard {

    private final String adminApiKey;
    private final AdminSessionAccess sessionAccess;
    private final AdminAllowlist allowlist;

    /** 注入管理金鑰、session 存取與白名單 */
    public AdminKeyGuard(@Value("${app.admin-api-key}") String adminApiKey,
                         AdminSessionAccess sessionAccess,
                         AdminAllowlist allowlist) {
        this.adminApiKey = adminApiKey;
        this.sessionAccess = sessionAccess;
        this.allowlist = allowlist;
    }

    /** 金鑰或 admin session 任一通過即放行；皆無則 401 */
    public void verify(String key) {
        if (matchesApiKey(key)) {
            return;
        }
        if (hasValidSession()) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid admin credential");
    }

    /** 以固定時間比對金鑰，避免時序側通道 */
    private boolean matchesApiKey(String key) {
        return key != null && MessageDigest.isEqual(
            key.getBytes(StandardCharsets.UTF_8), adminApiKey.getBytes(StandardCharsets.UTF_8));
    }

    /** 從當前請求取 cookie 驗證 session；email 須仍在白名單內 */
    private boolean hasValidSession() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return false;
        }
        return sessionAccess.readEmail(request, OffsetDateTime.now())
            .filter(allowlist::isAdmin)
            .isPresent();
    }

    /**
     * 取得當前請求。
     *
     * <p>透過 {@code RequestContextHolder} 取得而非由呼叫端傳入，是本方案的支點：
     * 若改為參數傳遞，78 處呼叫點全部都要改。</p>
     */
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
```

`AdminSessionAccess` 已於 Task 6 建立為 public Spring bean，本 task 直接注入使用，不需新增或修改該類別。

- [ ] **Step 4: 執行測試確認通過**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test`
Expected: **全部測試** PASS（本 task 改動了 78 處呼叫點共用的類別，必須跑全套）。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/AdminKeyGuard.java \
        survey-backend/src/test/java/world/springai/survey/AdminKeyGuardTest.java
git commit -m "feat(auth): AdminKeyGuard 支援金鑰與 JWT 並存，呼叫點簽名不變"
```

---

### Task 8: 文章內容更新服務

**Files:**

- Modify: `survey-backend/src/main/java/world/springai/survey/newsletter/Campaign.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/newsletter/CampaignService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/newsletter/CampaignUpdateContentTest.java`

**Interfaces:**

- Produces：`CampaignService.updateContent(long campaignId, String subject, String markdown, String coverEmoji, Long coverMediaId, List<String> tags, OffsetDateTime now)` 回 `void`；`Campaign.getUpdatedAt()` / `setUpdatedAt()`。Task 9 會用到。

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 文章內容更新：只動內容欄位，不碰計費、slug 與寄出的信件快照 */
class CampaignUpdateContentTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-06T12:00:00+08:00");

    /** 應更新 subject/markdown 並記錄 updated_at */
    @Test
    void updatesContentFields() {
        Campaign campaign = existingCampaign();
        CampaignRepository repo = mock(CampaignRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(campaign));
        CampaignMetadataService metadata = mock(CampaignMetadataService.class);

        newService(repo, metadata).updateContent(1L, "新標題", "新內文", "📮", null, List.of("AI"), NOW);

        assertEquals("新標題", campaign.getSubject());
        assertEquals("新內文", campaign.getMarkdown());
        assertNotNull(campaign.getUpdatedAt());
        verify(repo).save(campaign);
    }

    /** 絕不可更動計費、slug 與已寄出信件的 HTML 快照 */
    @Test
    void neverTouchesBillingSlugOrMailSnapshot() {
        Campaign campaign = existingCampaign();
        String originalBody = campaign.getBodyHtml();
        String originalSlug = campaign.getSlug();
        CampaignRepository repo = mock(CampaignRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(campaign));

        newService(repo, mock(CampaignMetadataService.class))
            .updateContent(1L, "新標題", "新內文", null, null, List.of(), NOW);

        assertEquals(originalBody, campaign.getBodyHtml(), "信件快照不得變動");
        assertEquals(originalSlug, campaign.getSlug(), "slug 不得變動");
        assertEquals(Campaign.TIER_BASIC, campaign.getTier(), "tier 不得變動");
        assertEquals(12, campaign.getCreditCost(), "解鎖點數不得變動");
    }

    /** 封面與標籤必須交給既有的 metadata 服務，先驗證後更新 */
    @Test
    void delegatesCoverAndTagsToMetadataService() {
        Campaign campaign = existingCampaign();
        CampaignRepository repo = mock(CampaignRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(campaign));
        CampaignMetadataService metadata = mock(CampaignMetadataService.class);

        newService(repo, metadata).updateContent(1L, "標題", "內文", "📮", 7L, List.of("AI"), NOW);

        verify(metadata).validate("📮", List.of("AI"), 7L);
        verify(metadata).update(1L, "📮", List.of("AI"), 7L);
    }

    /**
     * 絕不可寫入 email_log——那代表信被重寄了一次。
     * 這是本端點與 reschedule 最關鍵的差異（spec §4.3、§6）。
     */
    @Test
    void neverWritesEmailLog() {
        Campaign campaign = existingCampaign();
        CampaignRepository repo = mock(CampaignRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(campaign));
        EmailLogRepository emailLog = mock(EmailLogRepository.class);

        newServiceWithEmailLog(repo, emailLog)
            .updateContent(1L, "新標題", "新內文", null, null, List.of(), NOW);

        verify(emailLog, never()).save(any());
        verify(emailLog, never()).saveAll(any());
    }
}
```

`newServiceWithEmailLog(repo, emailLog)` 與 `newService(...)` 相同，只是把 `EmailLogRepository` 換成傳入的 mock（`CampaignService` 因 reschedule 而持有此依賴，故必須明確驗證未被使用）。測試需 import `world.springai.survey.mail.EmailLogRepository` 與 `org.mockito.Mockito.never`。

`existingCampaign()` 與 `newService()` 依 `CampaignService` 實際建構子撰寫：先讀 `CampaignService` 的建構子參數清單，未使用到的依賴一律傳 `mock(...)`。`existingCampaign()` 需建出 `slug="nl-test"`、`tier=TIER_BASIC`、`creditCost=12`、`bodyHtml="<p>original</p>"` 的已發布文章。

- [ ] **Step 2: 執行測試確認失敗**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=CampaignUpdateContentTest`
Expected: 編譯失敗，`updateContent` 與 `getUpdatedAt` 不存在。

- [ ] **Step 3: 實作**

`Campaign.java` 新增欄位：

```java
/** 內容最後修改時間；僅記錄時間，不保存修改歷史（決策 D8） */
@Column(name = "updated_at")
private OffsetDateTime updatedAt;

public OffsetDateTime getUpdatedAt() { return updatedAt; }
public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
```

`CampaignService.java` 新增方法：

```java
/**
 * 更新已發布文章的內容欄位。
 *
 * <p><b>刻意不做三件事</b>：不重寄信（不碰 {@code email_log} 與排程）、
 * 不改 {@code bodyHtml}（那是寄出信件的歷史快照，改它等於竄改「當初寄了什麼」）、
 * 不動解鎖與扣點。讀者站即時渲染 markdown，因此更新完成即生效。</p>
 */
@Transactional
public void updateContent(long campaignId, String subject, String markdown,
                          String coverEmoji, Long coverMediaId, List<String> tags,
                          OffsetDateTime now) {
    Campaign campaign = campaignRepository.findById(campaignId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "campaign not found"));

    // 封面與標籤走既有服務，確保與 publish 路徑的驗證規則一致
    metadataService.validate(coverEmoji, tags, coverMediaId);

    campaign.setSubject(subject);
    campaign.setMarkdown(markdown);
    campaign.setUpdatedAt(now);
    campaignRepository.save(campaign);

    metadataService.update(campaignId, coverEmoji, tags, coverMediaId);
}
```

若 `CampaignService` 目前未注入 `CampaignMetadataService`，於建構子加入該依賴並更新既有的 bean 建立處。

- [ ] **Step 4: 執行測試確認通過**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=CampaignUpdateContentTest`
Expected: 3 個測試全 PASS。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/newsletter/Campaign.java \
        survey-backend/src/main/java/world/springai/survey/newsletter/CampaignService.java \
        survey-backend/src/test/java/world/springai/survey/newsletter/CampaignUpdateContentTest.java
git commit -m "feat(newsletter): 新增文章內容更新服務，不動計費與信件快照"
```

---

### Task 9: 文章內容更新端點

**Files:**

- Modify: `survey-backend/src/main/java/world/springai/survey/newsletter/AdminCampaignController.java`
- Test: `survey-backend/src/test/java/world/springai/survey/newsletter/AdminCampaignContentEndpointTest.java`

**Interfaces:**

- Consumes：`CampaignService.updateContent`、`AdminKeyGuard.verify`
- Produces：`PUT /api/admin/campaigns/{id}/content`。Task 12 的前端會呼叫。

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey.newsletter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 內容更新端點：驗證金鑰、轉呼叫服務層 */
class AdminCampaignContentEndpointTest {

    /** 應先驗證管理身分，再以請求內容呼叫服務層 */
    @Test
    void verifiesCredentialThenDelegates() {
        AdminKeyGuardStub guard = new AdminKeyGuardStub();
        CampaignService service = mock(CampaignService.class);
        AdminCampaignController controller = newController(guard, service);

        controller.updateContent("key", 5L,
            new AdminCampaignController.ContentRequest("標題", "內文", "📮", 7L, List.of("AI")));

        org.junit.jupiter.api.Assertions.assertTrue(guard.verified, "必須先驗證身分");
        verify(service).updateContent(eq(5L), eq("標題"), eq("內文"), eq("📮"), eq(7L),
            eq(List.of("AI")), any());
    }
}
```

`AdminKeyGuardStub` 為繼承 `AdminKeyGuard` 並覆寫 `verify` 記錄呼叫的測試替身；`newController` 依 `AdminCampaignController` 實際建構子建立，未用到的依賴傳 `mock(...)`。

- [ ] **Step 2: 執行測試確認失敗**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=AdminCampaignContentEndpointTest`
Expected: 編譯失敗，`ContentRequest` 與 `updateContent` 端點不存在。

- [ ] **Step 3: 實作**

於 `AdminCampaignController` 新增：

```java
/** 內容更新請求；刻意不含 tier、creditCost、slug，帶了也不會被採用 */
public record ContentRequest(String subject, String markdown, String coverEmoji,
                             Long coverMediaId, List<String> tags) {}

/**
 * 更新已發布文章的內容欄位，<b>不寄任何信</b>。
 *
 * <p>與 reschedule 徹底分開：reschedule 會用新內容重寄整批信，這條端點只改
 * 資料庫內容。讀者站即時渲染 markdown，因此回應成功即代表網頁已更新。</p>
 */
@PutMapping("/api/admin/campaigns/{id}/content")
public Map<String, Object> updateContent(
        @RequestHeader(value = KEY_HEADER, required = false) String key,
        @PathVariable("id") Long id,
        @RequestBody ContentRequest req) {
    guard.verify(key);
    OffsetDateTime now = OffsetDateTime.now();
    campaignService.updateContent(id, req.subject(), req.markdown(),
        req.coverEmoji(), req.coverMediaId(), req.tags(), now);
    return Map.of("updated", true, "updatedAt", now.toString());
}
```

- [ ] **Step 4: 執行測試確認通過**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test -Dtest=AdminCampaignContentEndpointTest,CampaignUpdateContentTest`
Expected: 全數 PASS。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/newsletter/AdminCampaignController.java \
        survey-backend/src/test/java/world/springai/survey/newsletter/AdminCampaignContentEndpointTest.java
git commit -m "feat(newsletter): 新增已發布文章內容更新端點"
```

---

### Task 10: 後台登入 gate

**Files:**

- Modify: `survey-backend/src/main/resources/static/admin.html`（既有 `#gate-key` 區塊，約第 748、779、3011 行）

**Interfaces:**

- Consumes：`GET /api/admin/me`、`POST /api/admin/login`
- Produces：全域函式 `adminAuthMode()` 回 `'jwt' | 'key'`，Task 11 會用到。

- [ ] **Step 1: 改造 gate 畫面**

在既有 gate 區塊插入登入表單，金鑰輸入改為常駐的次要入口：

```html
<div id="gate-login">
  <h2>管理後台登入</h2>
  <input id="gate-email" type="email" placeholder="管理者 email" autocomplete="email">
  <button id="gate-send" type="button">寄送登入連結</button>
  <p id="gate-login-msg" class="muted"></p>
  <a href="#" id="gate-use-key" class="muted">改用管理金鑰登入</a>
</div>
<div id="gate-key-box" hidden>
  <input id="gate-key" type="password" placeholder="管理金鑰">
  <button id="gate-key-go" type="button">進入</button>
</div>
```

- [ ] **Step 2: 接上登入流程**

```javascript
/** 進站先問後端是否已有有效 session；有就直接進後台 */
async function bootAuth() {
  try {
    const res = await fetch('/api/admin/me');
    if (res.ok) {
      const me = await res.json();
      window.__adminMode = 'jwt';
      window.__adminEmail = me.email;
      return true;
    }
  } catch (e) { /* 視為未登入 */ }
  // 有金鑰也算已登入（機器/緊急路徑）
  if (sessionStorage.getItem(KEY)) { window.__adminMode = 'key'; return true; }
  return false;
}

/** 目前的驗證模式，供工具列顯示 */
function adminAuthMode() { return window.__adminMode || 'key'; }

document.getElementById('gate-send').addEventListener('click', async () => {
  const email = document.getElementById('gate-email').value.trim();
  await fetch('/api/admin/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email })
  });
  // 一律顯示相同訊息，不透露該 email 是否為管理者
  document.getElementById('gate-login-msg').textContent =
    '若該信箱為管理員，登入連結已寄出，請至信箱查收。';
});

document.getElementById('gate-use-key').addEventListener('click', (e) => {
  e.preventDefault();
  document.getElementById('gate-key-box').hidden = false;
});
```

將既有進站流程的入口改為 `bootAuth()` 的結果決定顯示 gate 或後台主畫面。

- [ ] **Step 3: 手動驗證**

啟動後端後開 `http://127.0.0.1:8080/admin.html`：未登入應看到登入表單與「改用管理金鑰登入」連結；點該連結應出現金鑰輸入框；輸入既有金鑰應能照舊進入後台。

- [ ] **Step 4: 確認既有腳本未受影響**

Run: `node scripts/verify-admin.mjs`
Expected: 與改動前結果相同（腳本走 `X-Admin-Key` header，不經 gate UI）。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/resources/static/admin.html
git commit -m "feat(admin-ui): 登入 gate 改為 magic-link，金鑰入口改為常駐次要選項"
```

---

### Task 11: 右上工具列與日夜切換

**Files:**

- Modify: `survey-backend/src/main/resources/static/admin.html`

**Interfaces:**

- Consumes：`adminAuthMode()`（Task 10）、`GET /api/admin/me`、`POST /api/admin/logout`

- [ ] **Step 1: 加入工具列 HTML**

```html
<div class="admin-toolbar">
  <span id="tb-identity" class="muted"></span>
  <button id="tb-theme" type="button" title="切換日夜模式" aria-label="切換日夜模式">☀</button>
  <button id="tb-logout" type="button" title="登出" aria-label="登出">⏻</button>
</div>
```

- [ ] **Step 2: 加入暗色主題變數**

於既有 `:root` 變數定義之後加入。**這組色值需要你定案**——`--accent`／`--amber`／`--danger` 在暗底上的對比度必須實際看畫面判斷，以下為起始值：

```css
:root[data-theme="dark"] {
  --bg: #16181c;
  --fg: #e8e8e8;
  --muted: #9aa0a6;
  --border: #2c2f36;
  --border-strong: #3a3f47;
  --accent-soft: #1c3a36;
}
```

- [ ] **Step 3: 接上主題與登出行為**

```javascript
/** 套用主題並記住偏好；首次進站跟隨系統設定 */
function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  localStorage.setItem('admin-theme', theme);
  document.getElementById('tb-theme').textContent = theme === 'dark' ? '🌙' : '☀';
}

const savedTheme = localStorage.getItem('admin-theme')
  || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
applyTheme(savedTheme);

document.getElementById('tb-theme').addEventListener('click', () => {
  const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
  applyTheme(next);
});

/** 登出：JWT 模式呼叫後端清 cookie，金鑰模式清 sessionStorage */
document.getElementById('tb-logout').addEventListener('click', async () => {
  if (adminAuthMode() === 'jwt') {
    await fetch('/api/admin/logout', { method: 'POST' });
  }
  sessionStorage.removeItem(KEY);
  location.reload();
});

document.getElementById('tb-identity').textContent =
  adminAuthMode() === 'jwt' ? (window.__adminEmail || '') : '金鑰模式';
```

- [ ] **Step 4: 視覺驗證**

以 Playwright 對亮／暗兩種模式各截一張圖比對對比度：

```bash
node scripts/verify-admin.mjs
```

若專案無現成截圖腳本，於 `survey-backend/scripts/verify-admin-theme.mjs` 新增可重跑腳本（依 CLAUDE.md 瀏覽器自動化規範，需為檔案而非一次性指令），內容為：開 `admin.html`、切換 `data-theme`、各截一張 PNG 至 `scripts/output/`。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/resources/static/admin.html survey-backend/scripts/
git commit -m "feat(admin-ui): 右上工具列加入登出與日夜切換"
```

---

### Task 12: 文章編輯入口與整體迴歸

**Files:**

- Modify: `survey-backend/src/main/resources/static/admin.html`

**Interfaces:**

- Consumes：`PUT /api/admin/campaigns/{id}/content`（Task 9）、既有編輯器的表單元件

- [ ] **Step 1: 列表加入編輯按鈕**

於歷史文章列表每列渲染處加入按鈕，並以 `data-id` 帶出 campaign id：

```javascript
`<button type="button" class="btn-edit" data-id="${c.id}">編輯</button>`
```

- [ ] **Step 2: 載入內容進既有編輯器**

```javascript
/** 進入編輯模式：載入現有內容，鎖住不可編輯欄位 */
async function enterEditMode(campaignId) {
  const res = await fetch(`/api/admin/campaigns/${campaignId}`, { headers: authHeaders() });
  const c = await res.json();
  document.getElementById('compose-subject').value = c.subject;
  document.getElementById('compose-markdown').value = c.markdown;
  window.__editingCampaignId = campaignId;
  // 計費與識別欄位在編輯模式一律唯讀（決策 D3）
  ['compose-tier', 'compose-credit-cost', 'compose-slug'].forEach(id => {
    const el = document.getElementById(id);
    if (el) { el.readOnly = true; el.disabled = true; }
  });
}
```

若後端無單筆取得端點，改由列表既有資料帶入（列表已含 `subject` 與 `markdown` 時直接使用，避免新增端點）。

- [ ] **Step 3: 存檔分流**

```javascript
/** 存檔：編輯模式走內容更新端點，新建模式維持原有 publish/send */
async function saveCompose() {
  if (window.__editingCampaignId) {
    await fetch(`/api/admin/campaigns/${window.__editingCampaignId}/content`, {
      method: 'PUT',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({
        subject: document.getElementById('compose-subject').value,
        markdown: document.getElementById('compose-markdown').value,
        coverEmoji: currentCoverEmoji(),
        coverMediaId: currentCoverMediaId(),
        tags: currentTags()
      })
    });
    window.__editingCampaignId = null;
    return;
  }
  // …既有的新建流程維持不變…
}
```

`currentCoverEmoji()` / `currentCoverMediaId()` / `currentTags()` 沿用既有 publish 流程組請求時所用的同一組取值函式，不另寫。

- [ ] **Step 4: 端到端驗證**

依序執行並確認全數通過（這是 D4「零改動」承諾的驗收）：

```bash
node scripts/verify-admin.mjs
node scripts/verify-admin-quota.mjs
node scripts/verify-admin-reader.mjs
node scripts/verify-admin-cost-prefill.mjs
node scripts/verify-publish-endpoint.mjs
node scripts/verify-sidebar-and-vote.mjs
node scripts/verify-stage-c.mjs
```

再跑一次後端全套測試：`$env:JAVA_HOME='D:\java\jdk-21'; mvn -q test`

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/resources/static/admin.html
git commit -m "feat(admin-ui): 歷史文章列表加入編輯入口與存檔分流"
```

---

## 部署設定

上線前需於 Zeabur 設定下列環境變數（缺 `ADMIN_EMAILS` 時 JWT 登入停用、金鑰照常）：

| 變數 | 值 |
| --- | --- |
| `ADMIN_EMAILS` | `kevintsai1202@gmail.com` |
| `ADMIN_BASE_URL` | `https://admin.springai.world` |
| `ADMIN_JWT_SECRET` | 32 字元以上隨機字串 |
| `ADMIN_JWT_TTL_DAYS` | `7` |
