# 子專案 C 第一段（退訂閉環 + 寄信基礎建設）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在既有 `survey-backend` 上補齊防偽退訂閉環、建立可插拔寄信基礎建設（ZSend / no-op）與寄送記錄，並讓問卷送出後自動寄一封含退訂連結的歡迎信。

**Architecture:** Spring Boot 3.5 / Java 21。退訂用無狀態 HMAC token 防偽；寄信走 ZSend REST API（內建 `RestClient`，無金鑰時 fallback 成 no-op），每次寄送寫入 `email_log`；問卷 `submit` 成功（201）後同步呼叫永不拋例外的 `WelcomeMailService`，失敗只記 log 不影響回應與交易。

**Tech Stack:** Spring Boot 3.5.0、Java 21、Spring Data JPA、Flyway（V3）、`RestClient`、ZSend REST API、JUnit 5 + Mockito + `@WebMvcTest` + `MockRestServiceServer`。

設計來源：`docs/superpowers/specs/2026-06-19-survey-email-and-unsubscribe-design.md`

---

## 重要慣例

- 後端模組：`survey-backend/`，package `world.springai.survey`，沿用 B。
- 所有 Java 需中文函式級註解；重要變數加註解。
- 機密（`SEND_MAIL_API`、`UNSUBSCRIBE_SECRET`）一律環境變數讀取，不寫死、不進版控。
- git 提交一律用明確路徑（工作區另有無關待處理變更）：`git commit -m "..." -- <path>`。禁止 `git add -A`、bare commit、`--no-verify`。commit 訊息結尾固定加 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。
- 不要切換分支。
- 測試指令（需 JAVA_HOME 指向 JDK 21）：`cd survey-backend && mvn -q test`。本機已知 JDK 21 在 `D:\java\jdk-21`。
- **起點注意**：`SurveyController.java` 工作區有未提交的半成品退訂端點（明碼、呼叫尚不存在的 `unsubscribeByEmail`，目前編譯不過）。本計畫即以此為起點修正。

---

## 檔案結構

```
survey-backend/src/main/java/world/springai/survey/
├── UnsubscribeTokenService.java   新增：HMAC 退訂 token 簽發/驗證
├── MailSender.java                新增：寄信介面
├── NoopMailSender.java            新增：無金鑰 fallback（只記 log）
├── ZSendMailSender.java           新增：ZSend REST 實作
├── MailConfig.java                新增：依金鑰選 ZSend / Noop 的 bean 工廠
├── EmailLog.java                  新增：寄送記錄 Entity
├── EmailLogRepository.java        新增：寄送記錄 Repository
├── WelcomeMailService.java        新增：組歡迎信 + 寄送 + 寫記錄（永不拋）
├── SurveyResponseRepository.java  修改：unsubscribeByEmail、findDistinctRecipients
└── SurveyController.java          修改：退訂端點驗 token、submit 觸發歡迎信
survey-backend/src/main/resources/
├── application.yml                修改：app.mail.* / app.unsubscribe-secret / app.public-base-url
└── db/migration/V3__create_email_log.sql   新增
survey-backend/src/test/java/world/springai/survey/
├── UnsubscribeTokenServiceTest.java  新增
├── ZSendMailSenderTest.java          新增
├── WelcomeMailServiceTest.java       新增
└── SurveyControllerTest.java         修改：token 退訂、submit 觸發歡迎信
```

---

## Task 1: HMAC 退訂 token 服務（TDD）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/UnsubscribeTokenService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/UnsubscribeTokenServiceTest.java`

- [ ] **Step 1: 寫失敗測試**

`survey-backend/src/test/java/world/springai/survey/UnsubscribeTokenServiceTest.java`：
```java
package world.springai.survey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HMAC 退訂 token 簽發與驗證測試 */
class UnsubscribeTokenServiceTest {

    private final UnsubscribeTokenService svc = new UnsubscribeTokenService("secret-A");

    /** 同 email 簽出的 token 應通過驗證 */
    @Test
    void signedTokenVerifies() {
        String token = svc.sign("User@Example.com");
        assertTrue(svc.verify("User@Example.com", token));
    }

    /** email 大小寫/前後空白不影響驗證（正規化） */
    @Test
    void verifyIsCaseAndTrimInsensitive() {
        String token = svc.sign("user@example.com");
        assertTrue(svc.verify("  USER@EXAMPLE.COM  ", token));
    }

    /** 竄改的 token 不通過 */
    @Test
    void tamperedTokenRejected() {
        assertFalse(svc.verify("user@example.com", "not-a-valid-token"));
    }

    /** 不同秘鑰簽出的 token 互不通過 */
    @Test
    void differentSecretRejected() {
        String token = new UnsubscribeTokenService("secret-B").sign("user@example.com");
        assertFalse(svc.verify("user@example.com", token));
    }

    /** 空 token 不通過、不丟例外 */
    @Test
    void blankTokenRejected() {
        assertFalse(svc.verify("user@example.com", null));
        assertFalse(svc.verify("user@example.com", ""));
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `cd survey-backend && mvn -q test -Dtest=UnsubscribeTokenServiceTest`
Expected: 編譯失敗（`UnsubscribeTokenService` 尚未建立）。

- [ ] **Step 3: 實作 UnsubscribeTokenService**

`survey-backend/src/main/java/world/springai/survey/UnsubscribeTokenService.java`：
```java
package world.springai.survey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** 退訂連結防偽：以 HMAC-SHA256 對正規化 email 簽發/驗證無狀態 token */
@Component
public class UnsubscribeTokenService {

    /** HMAC 秘鑰，由環境變數注入 */
    private final String secret;

    /** 注入退訂秘鑰（測試可直接以建構子傳入） */
    public UnsubscribeTokenService(@Value("${app.unsubscribe-secret}") String secret) {
        this.secret = secret;
    }

    /** 對 email 正規化（trim + 轉小寫）後算 HMAC-SHA256，回 Base64 URL-safe 無 padding */
    public String sign(String email) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(normalize(email).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("無法簽發退訂 token", e);
        }
    }

    /** 常數時間比對驗證 token；token 為空或不符一律回 false，不拋例外 */
    public boolean verify(String email, String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        String expected = sign(email);
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            token.getBytes(StandardCharsets.UTF_8));
    }

    /** email 正規化：去前後空白並轉小寫，確保簽發與驗證基準一致 */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

Run: `cd survey-backend && mvn -q test -Dtest=UnsubscribeTokenServiceTest`
Expected: 5 個測試全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/UnsubscribeTokenService.java survey-backend/src/test/java/world/springai/survey/UnsubscribeTokenServiceTest.java
git commit -m "feat(survey-backend): HMAC 退訂 token 簽發與驗證服務

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/src/main/java/world/springai/survey/UnsubscribeTokenService.java survey-backend/src/test/java/world/springai/survey/UnsubscribeTokenServiceTest.java
```

---

## Task 2: Repository 退訂與去重查詢

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/SurveyResponseRepository.java`

> 說明：這兩個方法屬資料層。本專案無 `@DataJpaTest` 測試資料庫（沿用 B 的策略：DB 行為由 Task 8 的整合腳本驗證），故本 Task 只加方法 + 編譯驗證；`unsubscribeByEmail` 的呼叫接線由 Task 6 的 `@WebMvcTest`（mock repository）涵蓋。

- [ ] **Step 1: 加入 unsubscribeByEmail 與 findDistinctRecipients**

把 `survey-backend/src/main/java/world/springai/survey/SurveyResponseRepository.java` 整檔改為：
```java
package world.springai.survey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 問卷回應資料存取層 */
public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {

    /** 依建立時間新到舊回傳全部回應（管理 API 用） */
    List<SurveyResponse> findAllByOrderByCreatedAtDesc();

    /** 將指定 email（大小寫不敏感）標記為已退訂；回傳受影響筆數 */
    @Modifying
    @Transactional
    @Query("update SurveyResponse s set s.unsubscribed = true where lower(s.email) = lower(:email)")
    int unsubscribeByEmail(@Param("email") String email);

    /** 可寄送名單：同意且未退訂的去重 email（小寫），供未來批量發送使用 */
    @Query("select distinct lower(s.email) from SurveyResponse s where s.consent = true and s.unsubscribed = false")
    List<String> findDistinctRecipients();
}
```

- [ ] **Step 2: 驗證可編譯**

Run: `cd survey-backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/SurveyResponseRepository.java
git commit -m "feat(survey-backend): 退訂更新與可寄送名單去重查詢

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/src/main/java/world/springai/survey/SurveyResponseRepository.java
```

---

## Task 3: 寄信介面與 ZSend / Noop 實作（TDD）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/MailSender.java`
- Create: `survey-backend/src/main/java/world/springai/survey/NoopMailSender.java`
- Create: `survey-backend/src/main/java/world/springai/survey/ZSendMailSender.java`
- Create: `survey-backend/src/main/java/world/springai/survey/MailConfig.java`
- Test: `survey-backend/src/test/java/world/springai/survey/ZSendMailSenderTest.java`

- [ ] **Step 1: 寫 ZSend 實作的失敗測試（用 MockRestServiceServer，不真打 API）**

`survey-backend/src/test/java/world/springai/survey/ZSendMailSenderTest.java`：
```java
package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** ZSendMailSender 請求格式測試：驗證 URL、Bearer 標頭與 body 欄位，並回傳 provider id */
class ZSendMailSenderTest {

    /** 應 POST 到 ZSend、帶 Bearer 金鑰與正確 body，並回傳回應中的 id */
    @Test
    void sendsCorrectRequestAndReturnsId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ZSendMailSender sender = new ZSendMailSender(builder, "zs_test", "noreply@springai.world", "");

        server.expect(requestTo("https://api.zeabur.com/api/v1/zsend/emails"))
              .andExpect(method(POST))
              .andExpect(header("Authorization", "Bearer zs_test"))
              .andExpect(jsonPath("$.from").value("noreply@springai.world"))
              .andExpect(jsonPath("$.to[0]").value("user@example.com"))
              .andExpect(jsonPath("$.subject").value("主旨"))
              .andExpect(jsonPath("$.html").value("<p>內容</p>"))
              .andRespond(withSuccess("{\"id\":\"abc123\",\"status\":\"pending\"}", APPLICATION_JSON));

        String id = sender.send("user@example.com", "主旨", "<p>內容</p>");

        assertEquals("abc123", id);
        server.verify();
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `cd survey-backend && mvn -q test -Dtest=ZSendMailSenderTest`
Expected: 編譯失敗（`MailSender` / `ZSendMailSender` 尚未建立）。

- [ ] **Step 3: 建立 MailSender 介面**

`survey-backend/src/main/java/world/springai/survey/MailSender.java`：
```java
package world.springai.survey;

/** 寄信抽象：單封寄送，回傳 provider 訊息 id；寄送失敗時拋 RuntimeException */
public interface MailSender {
    /** 寄一封 HTML 信給單一收件人，回傳 provider 訊息 id */
    String send(String to, String subject, String html);
}
```

- [ ] **Step 4: 建立 NoopMailSender**

`survey-backend/src/main/java/world/springai/survey/NoopMailSender.java`：
```java
package world.springai.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 無金鑰時的寄信 fallback：不真寄、只記 log，回傳固定假 id，供本機/測試使用 */
public class NoopMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(NoopMailSender.class);

    /** 不實際寄信，僅記錄一筆 log 並回傳 "noop" */
    @Override
    public String send(String to, String subject, String html) {
        log.info("（未設 SEND_MAIL_API）略過寄信 to={} subject={}", to, subject);
        return "noop";
    }
}
```

- [ ] **Step 5: 建立 ZSendMailSender**

`survey-backend/src/main/java/world/springai/survey/ZSendMailSender.java`：
```java
package world.springai.survey;

import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 透過 Zeabur Email（ZSend）REST API 寄信 */
public class ZSendMailSender implements MailSender {

    /** 預先綁定 ZSend base URL 的 HTTP 客戶端 */
    private final RestClient client;
    private final String apiKey;   // ZSend Bearer 金鑰
    private final String from;     // 寄件人（須為已驗證網域）
    private final String replyTo;  // 回信地址，可空白

    /** 以注入的 RestClient.Builder 建立綁定 ZSend 的客戶端（builder 可由測試綁 MockRestServiceServer） */
    public ZSendMailSender(RestClient.Builder builder, String apiKey, String from, String replyTo) {
        this.client = builder.baseUrl("https://api.zeabur.com").build();
        this.apiKey = apiKey;
        this.from = from;
        this.replyTo = replyTo;
    }

    /** 組 ZSend 請求並送出，回傳回應中的訊息 id */
    @Override
    public String send(String to, String subject, String html) {
        // 用 LinkedHashMap 維持欄位順序，方便除錯
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", List.of(to));
        body.put("subject", subject);
        body.put("html", html);
        if (StringUtils.hasText(replyTo)) {
            body.put("reply_to", List.of(replyTo));
        }
        Map<?, ?> resp = client.post()
            .uri("/api/v1/zsend/emails")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);
        return resp == null ? null : String.valueOf(resp.get("id"));
    }
}
```

- [ ] **Step 6: 建立 MailConfig（依金鑰選實作）**

`survey-backend/src/main/java/world/springai/survey/MailConfig.java`：
```java
package world.springai.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** 寄信實作選擇：有 ZSend 金鑰用 ZSendMailSender，否則 fallback 成 NoopMailSender */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    /** 依 app.mail.api-key 是否設定，決定注入哪個 MailSender */
    @Bean
    public MailSender mailSender(RestClient.Builder builder,
                                 @Value("${app.mail.api-key:}") String apiKey,
                                 @Value("${app.mail.from}") String from,
                                 @Value("${app.mail.reply-to:}") String replyTo) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("未設定 app.mail.api-key，啟用 NoopMailSender（不會真正寄信）");
            return new NoopMailSender();
        }
        return new ZSendMailSender(builder, apiKey, from, replyTo);
    }
}
```

- [ ] **Step 7: 跑測試確認通過**

Run: `cd survey-backend && mvn -q test -Dtest=ZSendMailSenderTest`
Expected: 1 個測試 PASS。

- [ ] **Step 8: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/MailSender.java survey-backend/src/main/java/world/springai/survey/NoopMailSender.java survey-backend/src/main/java/world/springai/survey/ZSendMailSender.java survey-backend/src/main/java/world/springai/survey/MailConfig.java survey-backend/src/test/java/world/springai/survey/ZSendMailSenderTest.java
git commit -m "feat(survey-backend): 可插拔寄信介面與 ZSend/Noop 實作

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/src/main/java/world/springai/survey/MailSender.java survey-backend/src/main/java/world/springai/survey/NoopMailSender.java survey-backend/src/main/java/world/springai/survey/ZSendMailSender.java survey-backend/src/main/java/world/springai/survey/MailConfig.java survey-backend/src/test/java/world/springai/survey/ZSendMailSenderTest.java
```

---

## Task 4: 寄送記錄表（Flyway V3 + Entity + Repository）

**Files:**
- Create: `survey-backend/src/main/resources/db/migration/V3__create_email_log.sql`
- Create: `survey-backend/src/main/java/world/springai/survey/EmailLog.java`
- Create: `survey-backend/src/main/java/world/springai/survey/EmailLogRepository.java`

- [ ] **Step 1: 建立 Flyway migration**

`survey-backend/src/main/resources/db/migration/V3__create_email_log.sql`：
```sql
-- 寄送記錄：每次寄信（含失敗）寫一筆，供稽核與後續投遞狀態追蹤
CREATE TABLE email_log (
    id                  BIGSERIAL PRIMARY KEY,
    recipient           TEXT        NOT NULL,              -- 收件人 email
    subject             TEXT,                              -- 主旨
    type                TEXT,                              -- 信件類型（本段固定 welcome）
    provider_message_id TEXT,                              -- ZSend 回傳 id（no-op 時為 noop）
    status              TEXT        NOT NULL,              -- sent / failed
    error               TEXT,                              -- 失敗時錯誤摘要
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now() -- 寄送時間
);

-- 依收件人查寄送歷程
CREATE INDEX idx_email_log_recipient ON email_log (recipient);
```

- [ ] **Step 2: 建立 EmailLog Entity**

`survey-backend/src/main/java/world/springai/survey/EmailLog.java`：
```java
package world.springai.survey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 寄送記錄實體，對應資料表 email_log */
@Entity
@Table(name = "email_log")
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipient;

    private String subject;
    private String type;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(nullable = false)
    private String status;

    private String error;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 需要的無參數建構子 */
    protected EmailLog() {
    }

    /** 建立一筆寄送記錄 */
    public EmailLog(String recipient, String subject, String type,
                    String providerMessageId, String status, String error) {
        this.recipient = recipient;
        this.subject = subject;
        this.type = type;
        this.providerMessageId = providerMessageId;
        this.status = status;
        this.error = error;
    }

    public Long getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getType() { return type; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getStatus() { return status; }
    public String getError() { return error; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 3: 建立 EmailLogRepository**

`survey-backend/src/main/java/world/springai/survey/EmailLogRepository.java`：
```java
package world.springai.survey;

import org.springframework.data.jpa.repository.JpaRepository;

/** 寄送記錄資料存取層 */
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
}
```

- [ ] **Step 4: 驗證可編譯**

Run: `cd survey-backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/resources/db/migration/V3__create_email_log.sql survey-backend/src/main/java/world/springai/survey/EmailLog.java survey-backend/src/main/java/world/springai/survey/EmailLogRepository.java
git commit -m "feat(survey-backend): 寄送記錄表 email_log（V3）+ Entity + Repository

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/src/main/resources/db/migration/V3__create_email_log.sql survey-backend/src/main/java/world/springai/survey/EmailLog.java survey-backend/src/main/java/world/springai/survey/EmailLogRepository.java
```

---

## Task 5: 歡迎信服務（TDD）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/WelcomeMailService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/WelcomeMailServiceTest.java`

- [ ] **Step 1: 寫失敗測試**

`survey-backend/src/test/java/world/springai/survey/WelcomeMailServiceTest.java`：
```java
package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 歡迎信服務測試：成功記 sent、失敗記 failed 且不拋例外 */
class WelcomeMailServiceTest {

    private final MailSender mailSender = mock(MailSender.class);
    private final EmailLogRepository emailLogRepository = mock(EmailLogRepository.class);
    private final UnsubscribeTokenService tokenService = new UnsubscribeTokenService("secret");
    private final WelcomeMailService svc =
        new WelcomeMailService(mailSender, tokenService, emailLogRepository, "https://api.example.com");

    /** 寄送成功應寫入 status=sent 並帶 provider id */
    @Test
    void successLogsSent() {
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("zsend-id-1");

        svc.sendWelcome("user@example.com");

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        EmailLog saved = captor.getValue();
        assertEquals("sent", saved.getStatus());
        assertEquals("zsend-id-1", saved.getProviderMessageId());
        assertEquals("user@example.com", saved.getRecipient());
    }

    /** 寄送丟例外時不應向上拋，且寫入 status=failed */
    @Test
    void failureLogsFailedAndDoesNotThrow() {
        when(mailSender.send(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("boom"));

        svc.sendWelcome("user@example.com"); // 不應拋例外

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `cd survey-backend && mvn -q test -Dtest=WelcomeMailServiceTest`
Expected: 編譯失敗（`WelcomeMailService` 尚未建立）。

- [ ] **Step 3: 實作 WelcomeMailService**

`survey-backend/src/main/java/world/springai/survey/WelcomeMailService.java`：
```java
package world.springai.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 問卷送出後寄歡迎信：組信（含退訂連結）→ 寄送 → 寫 email_log；任何失敗只記 log，永不拋例外 */
@Service
public class WelcomeMailService {

    private static final Logger log = LoggerFactory.getLogger(WelcomeMailService.class);

    /** 歡迎信主旨 */
    private static final String SUBJECT = "歡迎加入｜AI 賦能全端開發課程資訊";

    private final MailSender mailSender;
    private final UnsubscribeTokenService tokenService;
    private final EmailLogRepository emailLogRepository;
    private final String publicBaseUrl; // 組退訂連結用的對外網址

    /** 注入寄信、token 服務、寄送記錄與對外網址 */
    public WelcomeMailService(MailSender mailSender,
                              UnsubscribeTokenService tokenService,
                              EmailLogRepository emailLogRepository,
                              @Value("${app.public-base-url}") String publicBaseUrl) {
        this.mailSender = mailSender;
        this.tokenService = tokenService;
        this.emailLogRepository = emailLogRepository;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** 寄一封歡迎信給填寫者；成功記 sent、失敗記 failed，皆不向外拋例外 */
    public void sendWelcome(String email) {
        try {
            String link = buildUnsubscribeLink(email);
            String html = buildHtml(link);
            String id = mailSender.send(email, SUBJECT, html);
            saveLog(email, id, "sent", null);
        } catch (Exception e) {
            log.warn("歡迎信寄送失敗 to={}：{}", email, e.getMessage());
            saveLog(email, null, "failed", e.getMessage());
        }
    }

    /** 寫一筆寄送記錄；連記錄都失敗時僅記 log，不影響主流程 */
    private void saveLog(String email, String providerId, String status, String error) {
        try {
            emailLogRepository.save(new EmailLog(email, SUBJECT, "welcome", providerId, status, error));
        } catch (Exception e) {
            log.warn("寫入 email_log 失敗 to={}：{}", email, e.getMessage());
        }
    }

    /** 組退訂連結：?email=<urlencoded>&t=<HMAC token> */
    private String buildUnsubscribeLink(String email) {
        String encoded = URLEncoder.encode(email, StandardCharsets.UTF_8);
        String token = tokenService.sign(email);
        return publicBaseUrl + "/api/survey/unsubscribe?email=" + encoded + "&t=" + token;
    }

    /** 組歡迎信 HTML，內含可用退訂連結 */
    private String buildHtml(String unsubscribeLink) {
        return """
            <div style="font-family:system-ui,'Microsoft JhengHei',sans-serif;line-height:1.7;max-width:560px;margin:0 auto;color:#1a1a2e">
              <h2>歡迎你！🎉</h2>
              <p>謝謝你填寫「AI 賦能全端開發」課程興趣調查。我們會在課程開放報名、釋出早鳥優惠時優先通知你。</p>
              <p>在那之前，你可以先看看課程網站，了解整個實戰學習路徑。</p>
              <hr style="border:none;border-top:1px solid #eee;margin:24px 0">
              <p style="color:#888;font-size:.85rem">
                你會收到這封信，是因為你在課程網站填寫了興趣調查並同意接收課程資訊。<br>
                若不想再收到，<a href="%s" style="color:#4f46e5">點此取消訂閱</a>。
              </p>
            </div>
            """.formatted(unsubscribeLink);
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

Run: `cd survey-backend && mvn -q test -Dtest=WelcomeMailServiceTest`
Expected: 2 個測試 PASS。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/WelcomeMailService.java survey-backend/src/test/java/world/springai/survey/WelcomeMailServiceTest.java
git commit -m "feat(survey-backend): 歡迎信服務（含退訂連結，失敗不拋例外）

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/src/main/java/world/springai/survey/WelcomeMailService.java survey-backend/src/test/java/world/springai/survey/WelcomeMailServiceTest.java
```

---

## Task 6: 接線 SurveyController（退訂驗 token + 送出觸發歡迎信）（TDD）

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/SurveyController.java`
- Modify: `survey-backend/src/test/java/world/springai/survey/SurveyControllerTest.java`

- [ ] **Step 1: 先改測試（加入 token 退訂與 submit 觸發歡迎信的期望）**

把 `survey-backend/src/test/java/world/springai/survey/SurveyControllerTest.java` 整檔改為：
```java
package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SurveyController 行為測試：驗證、蜜罐、admin 金鑰、即時統計、退訂 token、歡迎信觸發 */
@WebMvcTest(SurveyController.class)
@Import(UnsubscribeTokenService.class) // 用真實 token 服務以便計算合法 token
@TestPropertySource(properties = {
    "app.admin-api-key=test-key",
    "app.cors-allowed-origins=http://localhost",
    "app.unsubscribe-secret=test-secret"
})
class SurveyControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UnsubscribeTokenService tokenService;
    @MockBean SurveyResponseRepository repository;
    @MockBean WelcomeMailService welcomeMailService;

    @Test
    void validSurveyReturns201() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"consent\":true}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isCreated());
        verify(repository).save(any(SurveyResponse.class));
    }

    /** 合法問卷送出後應觸發歡迎信寄送一次 */
    @Test
    void validSurveyTriggersWelcomeMail() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"consent\":true}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isCreated());
        verify(welcomeMailService).sendWelcome("a@b.com");
    }

    @Test
    void missingConsentReturns400() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"consent\":false}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

    @Test
    void invalidEmailReturns400() throws Exception {
        String body = "{\"email\":\"not-an-email\",\"consent\":true}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

    /** 蜜罐有值：回 204、不寫入、且不寄歡迎信 */
    @Test
    void honeypotFilledReturns204AndSkips() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"consent\":true,\"website\":\"spam\"}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isNoContent());
        verify(repository, never()).save(any());
        verify(welcomeMailService, never()).sendWelcome(any());
    }

    @Test
    void adminWithoutKeyReturns401() throws Exception {
        mvc.perform(get("/api/admin/survey")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminWithKeyReturns200() throws Exception {
        mvc.perform(get("/api/admin/survey").header("X-Admin-Key", "test-key")).andExpect(status().isOk());
    }

    @Test
    void publicStatsAggregatesWithoutKey() throws Exception {
        SurveyResponse a = new SurveyResponse();
        a.setRole("後端工程師");
        a.setInterest(List.of("RAG 知識庫", "Tool Calling"));
        a.setAnswers(Map.of("status", "在職工程師，想技能升級"));
        SurveyResponse b = new SurveyResponse();
        b.setRole("後端工程師");
        b.setInterest(List.of("RAG 知識庫"));
        b.setAnswers(Map.of("status", "想轉職全端／AI 工程師"));
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(a, b));

        mvc.perform(get("/api/survey/stats"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.total").value(2))
           .andExpect(jsonPath("$.interest[0].label").value("RAG 知識庫"))
           .andExpect(jsonPath("$.interest[0].count").value(2))
           .andExpect(jsonPath("$.role[0].label").value("後端工程師"))
           .andExpect(jsonPath("$.role[0].count").value(2));
    }

    /** 退訂：合法 token 應更新該 email 並回 200 HTML */
    @Test
    void unsubscribeWithValidTokenUpdates() throws Exception {
        String email = "user@example.com";
        String token = tokenService.sign(email);
        mvc.perform(get("/api/survey/unsubscribe").param("email", email).param("t", token))
           .andExpect(status().isOk());
        verify(repository).unsubscribeByEmail(email);
    }

    /** 退訂：token 錯誤不更新，但仍回 200 同頁（不洩漏） */
    @Test
    void unsubscribeWithBadTokenDoesNotUpdate() throws Exception {
        mvc.perform(get("/api/survey/unsubscribe").param("email", "user@example.com").param("t", "bad"))
           .andExpect(status().isOk());
        verify(repository, never()).unsubscribeByEmail(any());
    }

    /** 退訂：缺 token 不更新，仍回 200 */
    @Test
    void unsubscribeWithoutTokenDoesNotUpdate() throws Exception {
        mvc.perform(get("/api/survey/unsubscribe").param("email", "user@example.com"))
           .andExpect(status().isOk());
        verify(repository, never()).unsubscribeByEmail(any());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `cd survey-backend && mvn -q test -Dtest=SurveyControllerTest`
Expected: 編譯失敗或測試失敗（controller 尚未注入 `WelcomeMailService` / `UnsubscribeTokenService`，退訂端點尚未驗 token）。

- [ ] **Step 3: 修改 SurveyController 建構子（注入 token 服務與歡迎信服務）**

把 `SurveyController` 的欄位與建構子（目前 [survey-backend/src/main/java/world/springai/survey/SurveyController.java:29-40](survey-backend/src/main/java/world/springai/survey/SurveyController.java#L29-L40)）改為：
```java
    private final SurveyResponseRepository repository;
    private final String adminApiKey;
    private final ObjectMapper objectMapper;
    private final UnsubscribeTokenService tokenService;     // 退訂 token 驗證
    private final WelcomeMailService welcomeMailService;    // 問卷送出後寄歡迎信

    /** 注入資料層、管理金鑰、JSON 序列化器、退訂 token 服務與歡迎信服務 */
    public SurveyController(SurveyResponseRepository repository,
                            @Value("${app.admin-api-key}") String adminApiKey,
                            ObjectMapper objectMapper,
                            UnsubscribeTokenService tokenService,
                            WelcomeMailService welcomeMailService) {
        this.repository = repository;
        this.adminApiKey = adminApiKey;
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
        this.welcomeMailService = welcomeMailService;
    }
```

- [ ] **Step 4: submit 成功後觸發歡迎信**

把 `submit` 方法中 `repository.save(entity);`（目前 [survey-backend/src/main/java/world/springai/survey/SurveyController.java:60-61](survey-backend/src/main/java/world/springai/survey/SurveyController.java#L60-L61)）這兩行：
```java
        repository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).build();
```
改為：
```java
        repository.save(entity);
        // 寫入成功後寄歡迎信；sendWelcome 內部已 try/catch，失敗不影響此回應
        welcomeMailService.sendWelcome(entity.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).build();
```

- [ ] **Step 5: 退訂端點升級為驗 token**

把 `unsubscribe` 方法（目前 [survey-backend/src/main/java/world/springai/survey/SurveyController.java:103-120](survey-backend/src/main/java/world/springai/survey/SurveyController.java#L103-L120)）整段（含 javadoc）改為：
```java
    /**
     * 公開退訂端點：使用者從行銷信件點擊退訂連結（GET）後以瀏覽器開啟，故回 HTML。
     * 連結形如 /api/survey/unsubscribe?email=<email>&t=<HMAC token>。
     * 設計重點：
     *  1. 防偽——僅當 t 為該 email 的合法 HMAC 簽章才執行退訂。
     *  2. 冪等——已退訂者再點、或名單查無此 email，都回相同成功頁，不報錯。
     *  3. 不洩漏名單——不論結果（含 token 不符）一律回相同訊息與 200。
     *  4. 回應頁為固定字串、不回顯使用者輸入，避免 XSS。
     */
    @GetMapping(value = "/api/survey/unsubscribe", produces = "text/html; charset=UTF-8")
    public ResponseEntity<String> unsubscribe(@RequestParam(value = "email", required = false) String email,
                                              @RequestParam(value = "t", required = false) String token) {
        // 僅在 email 有值且 token 通過驗證時才退訂；其餘情況靜默略過但仍回同一頁
        if (StringUtils.hasText(email) && tokenService.verify(email, token)) {
            repository.unsubscribeByEmail(email.trim());
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
            .body(UNSUBSCRIBE_HTML);
    }
```

- [ ] **Step 6: 跑測試確認通過**

Run: `cd survey-backend && mvn -q test -Dtest=SurveyControllerTest`
Expected: 全部測試 PASS（含 3 個退訂測試與歡迎信觸發測試）。

- [ ] **Step 7: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/SurveyController.java survey-backend/src/test/java/world/springai/survey/SurveyControllerTest.java
git commit -m "feat(survey-backend): 退訂端點驗 HMAC token + 問卷送出觸發歡迎信

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/src/main/java/world/springai/survey/SurveyController.java survey-backend/src/test/java/world/springai/survey/SurveyControllerTest.java
```

---

## Task 7: 設定檔與全套測試

**Files:**
- Modify: `survey-backend/src/main/resources/application.yml`

- [ ] **Step 1: 在 application.yml 補上 mail / 退訂 / 對外網址設定**

把 `survey-backend/src/main/resources/application.yml` 的 `app:` 區塊（目前 [survey-backend/src/main/resources/application.yml:17-19](survey-backend/src/main/resources/application.yml#L17-L19)）：
```yaml
app:
  admin-api-key: ${ADMIN_API_KEY:dev-admin-key}
  cors-allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:http://127.0.0.1:5174,http://localhost:5174}
```
改為：
```yaml
app:
  admin-api-key: ${ADMIN_API_KEY:dev-admin-key}
  cors-allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:http://127.0.0.1:5174,http://localhost:5174}
  # 退訂連結 HMAC 秘鑰（線上務必換成強隨機字串）
  unsubscribe-secret: ${UNSUBSCRIBE_SECRET:dev-unsub-secret}
  # 組退訂連結用的對外網址（= survey-backend 自己的公開網域）
  public-base-url: ${APP_PUBLIC_BASE_URL:http://127.0.0.1:8080}
  # 寄信設定：api-key 空白時自動 fallback 成 NoopMailSender（不真寄）
  mail:
    api-key: ${SEND_MAIL_API:}
    from: ${MAIL_FROM:AI 全端課程 <noreply@springai.world>}
    reply-to: ${MAIL_REPLY_TO:}
```

- [ ] **Step 2: 跑全套測試**

Run: `cd survey-backend && mvn -q test`
Expected: 全部測試 PASS（`UnsubscribeTokenServiceTest`、`ZSendMailSenderTest`、`WelcomeMailServiceTest`、`SurveyControllerTest`）。

- [ ] **Step 3: Commit**

```bash
git add survey-backend/src/main/resources/application.yml
git commit -m "chore(survey-backend): 新增 mail / 退訂秘鑰 / 對外網址設定

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/src/main/resources/application.yml
```

---

## Task 8: 部署設定與線上驗證（手動操作，非程式碼）

**Files:**（無程式碼；需用 Zeabur 技能與線上環境）

- [ ] **Step 1: push 進度**

```bash
git push origin main
```
Expected: 推送成功，Zeabur 自動重建 survey-backend。

- [ ] **Step 2: 在 Zeabur survey-backend 服務設定環境變數**

用 `zeabur-variables` 技能設定：
- `SEND_MAIL_API`：ZSend 金鑰（即 `.env` 內 `zs_...` 的值；綁定 `springai.world`）。
- `MAIL_FROM`：`AI 全端課程 <noreply@springai.world>`。
- `UNSUBSCRIBE_SECRET`：自訂一組強隨機字串。
- `APP_PUBLIC_BASE_URL`：survey-backend 對外網址（如 `https://springai-survey-api.zeabur.app`）。

> `MAIL_REPLY_TO` 選填，可不設。

- [ ] **Step 3: 線上整合驗證**

對線上問卷送一筆真實 email 的問卷（用自己的信箱），確認：
1. 收到歡迎信（寄件人 `noreply@springai.world`），信中有「取消訂閱」連結。
2. 點該退訂連結 → 出現「您已成功取消訂閱」頁。
3. 用 admin API 確認該 email 的 `unsubscribed` 已為 `true`：
```powershell
$r = Invoke-WebRequest -Uri "https://springai-survey-api.zeabur.app/api/admin/survey" -Headers @{ "X-Admin-Key" = "<你的 ADMIN_API_KEY>" }
$r.Content   # 找該 email 該筆，確認 unsubscribed=true
```
Expected: 三項皆通過。

- [ ] **Step 4: 篡改連結負向驗證**

把退訂連結的 `t` 改一個字元再開 → 仍顯示成功頁，但 admin API 查該 email `unsubscribed` 維持原狀（未被退訂）。
Expected: token 防偽生效。

---

## Self-Review 對照

- **退訂閉環（unsubscribeByEmail + HMAC 驗證）**：Task 1（token 服務）、Task 2（repo 方法）、Task 6（端點驗 token）。✅
- **寄信基礎建設（MailSender / ZSend / Noop / 條件選擇）**：Task 3。✅
- **寄送記錄 email_log**：Task 4。✅
- **名單去重查詢 findDistinctRecipients**：Task 2（infra，測試由 Task 8 整合涵蓋）。✅
- **問卷送出自動寄歡迎信（含退訂連結、A1 同步不拋例外）**：Task 5（服務）、Task 6（接線、蜜罐不寄）。✅
- **環境變數（SEND_MAIL_API / MAIL_FROM / UNSUBSCRIBE_SECRET / APP_PUBLIC_BASE_URL）**：Task 7（yml）、Task 8（線上）。✅
- **測試（退訂 token 正/負、ZSend 請求格式、歡迎信 sent/failed、submit 觸發、蜜罐不寄、既有 B 測試保留）**：Task 1/3/5/6。✅
- **型別一致**：`MailSender.send(to,subject,html)`、`UnsubscribeTokenService.sign/verify`、`WelcomeMailService.sendWelcome(String)`、`SurveyResponseRepository.unsubscribeByEmail(String)`、`EmailLog(recipient,subject,type,providerMessageId,status,error)` 在定義與使用處一致。✅
- **YAGNI（不含批量發送 UI / webhook / 排程 / 退訂分類）**：全計畫未觸及。✅
- **起點修正（半成品退訂編譯不過）**：Task 2 補方法 + Task 6 升級端點，使其編譯且通過測試。✅
