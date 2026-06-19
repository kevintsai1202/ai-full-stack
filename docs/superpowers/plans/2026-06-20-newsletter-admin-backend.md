# 寄信後台（批量電子報）後端 API Implementation Plan（第二段 · 後端）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `survey-backend` 上建立受 `X-Admin-Key` 保護的批量電子報後端 API：收件名單篩選、Markdown 渲染、立即(batch)/排程(schedule) 發送（每人各自退訂連結）、campaign 記錄與寄送歷史、取消排程。

**Architecture:** Spring Boot 3.5 / Java 21。沿用第一段的 `UnsubscribeTokenService` / `MailSender` / `EmailLog`。新增 `commonmark` 渲染、`EmailTemplate` 共用外框、`AdminKeyGuard` 集中金鑰驗證、`campaign` 資料表、`CampaignService`（立即用 ZSend `/emails/batch`、排程用 `/emails/schedule`）。立即批量回單一 job_id、排程逐封回可取消 id。

**Tech Stack:** Spring Boot 3.5.0、Java 21、Spring Data JPA、Flyway（V4）、`RestClient`、commonmark-java、JUnit 5 + Mockito + `@WebMvcTest` + `MockRestServiceServer`。

設計來源：`docs/superpowers/specs/2026-06-20-newsletter-admin-design.md`

---

## 重要慣例

- 後端模組 `survey-backend/`，package `world.springai.survey`，沿用第一段。
- 所有 Java 中文函式級註解；機密走環境變數。
- git 一律明確路徑提交：`git commit -m "..." -- <path>`；禁止 `git add -A`、bare commit、`--no-verify`。commit 訊息結尾加 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。不切換分支（在 main）。
- 測試：`cd survey-backend && mvn -q test`（JDK 21；本機 `D:\java\jdk-21`）。
- 既有可用元件：`UnsubscribeTokenService.sign/verify`、`MailSender.send`、`ZSendMailSender(RestClient.Builder,apiKey,from,replyTo)`、`NoopMailSender`、`MailConfig`、`EmailLog(recipient,subject,type,providerMessageId,status,error)`、`EmailLogRepository`、`WelcomeMailService`、`SurveyResponseRepository.findDistinctRecipients`、`SurveyController`（admin 金鑰目前內嵌於 `list()`）。

---

## 檔案結構

```
survey-backend/
├── pom.xml                                 修改：加 commonmark 依賴
├── src/main/java/world/springai/survey/
│   ├── MarkdownRenderer.java               新增：markdown→HTML
│   ├── AdminKeyGuard.java                  新增：集中 X-Admin-Key 驗證
│   ├── EmailTemplate.java                  新增：品牌外框 + 退訂頁腳（共用）
│   ├── MailSender.java                     修改：加 Email record + sendBatch/schedule/cancelScheduled
│   ├── NoopMailSender.java                 修改：實作新方法
│   ├── ZSendMailSender.java               修改：實作 batch/schedule/cancel
│   ├── Campaign.java                       新增：電子報實體
│   ├── CampaignRepository.java             新增
│   ├── EmailLog.java                       修改：加 campaignId + setStatus
│   ├── EmailLogRepository.java             修改：加 findByCampaignId / findByCampaignIdAndStatus
│   ├── SurveyResponseRepository.java       修改：加 findRecipients(role,interest) native query
│   ├── RecipientService.java               新增：收件名單篩選
│   ├── CampaignService.java                新增：發送 / 預覽 / 測試 / 歷史 / 取消
│   ├── AdminCampaignController.java        新增：/api/admin 端點
│   ├── WelcomeMailService.java             修改：改用 EmailTemplate
│   └── SurveyController.java               修改：改用 AdminKeyGuard
│   └── src/main/resources/db/migration/V4__create_campaign.sql   新增
└── src/test/java/world/springai/survey/
    ├── MarkdownRendererTest.java           新增
    ├── EmailTemplateTest.java              新增
    ├── ZSendMailSenderTest.java            修改：加 batch/schedule/cancel 測試
    ├── CampaignServiceTest.java            新增
    ├── AdminCampaignControllerTest.java    新增
    ├── WelcomeMailServiceTest.java         修改：建構子加 EmailTemplate
    └── SurveyControllerTest.java           修改：改用 AdminKeyGuard
```

---

## Task 1: commonmark 依賴 + MarkdownRenderer（TDD）

**Files:**
- Modify: `survey-backend/pom.xml`
- Create: `survey-backend/src/main/java/world/springai/survey/MarkdownRenderer.java`
- Test: `survey-backend/src/test/java/world/springai/survey/MarkdownRendererTest.java`

- [ ] **Step 1: 加 commonmark 依賴**

在 `survey-backend/pom.xml` 的 `<dependencies>` 區塊內（任一 dependency 之後）加入：
```xml
    <dependency>
      <groupId>org.commonmark</groupId>
      <artifactId>commonmark</artifactId>
      <version>0.22.0</version>
    </dependency>
```

- [ ] **Step 2: 寫失敗測試**

`survey-backend/src/test/java/world/springai/survey/MarkdownRendererTest.java`：
```java
package world.springai.survey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Markdown 轉 HTML 測試 */
class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    /** 標題與連結應轉成對應 HTML 標籤 */
    @Test
    void rendersHeadingAndLink() {
        String html = renderer.toHtml("# 標題\n\n[連結](https://example.com)");
        assertTrue(html.contains("<h1>標題</h1>"), html);
        assertTrue(html.contains("href=\"https://example.com\""), html);
    }

    /** null 輸入回空字串，不丟例外 */
    @Test
    void nullSafe() {
        assertEquals("", renderer.toHtml(null));
    }
}
```

- [ ] **Step 3: 跑測試確認失敗**

Run: `cd survey-backend && mvn -q test -Dtest=MarkdownRendererTest`
Expected: 編譯失敗（`MarkdownRenderer` 不存在）。

- [ ] **Step 4: 實作 MarkdownRenderer**

`survey-backend/src/main/java/world/springai/survey/MarkdownRenderer.java`：
```java
package world.springai.survey;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

/** 把電子報內文的 Markdown 轉成 HTML（管理者為可信作者） */
@Component
public class MarkdownRenderer {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    /** Markdown 轉 HTML；null 視為空字串 */
    public String toHtml(String markdown) {
        if (markdown == null) {
            return "";
        }
        return renderer.render(parser.parse(markdown));
    }
}
```

- [ ] **Step 5: 跑測試確認通過**

Run: `cd survey-backend && mvn -q test -Dtest=MarkdownRendererTest`
Expected: 2 測試 PASS。

- [ ] **Step 6: Commit**

```bash
git add survey-backend/pom.xml survey-backend/src/main/java/world/springai/survey/MarkdownRenderer.java survey-backend/src/test/java/world/springai/survey/MarkdownRendererTest.java
git commit -m "feat(survey-backend): commonmark 依賴與 MarkdownRenderer

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/pom.xml survey-backend/src/main/java/world/springai/survey/MarkdownRenderer.java survey-backend/src/test/java/world/springai/survey/MarkdownRendererTest.java
```

---

## Task 2: AdminKeyGuard + 重構 SurveyController（TDD）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/AdminKeyGuard.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/SurveyController.java`
- Modify: `survey-backend/src/test/java/world/springai/survey/SurveyControllerTest.java`

- [ ] **Step 1: 建立 AdminKeyGuard**

`survey-backend/src/main/java/world/springai/survey/AdminKeyGuard.java`：
```java
package world.springai.survey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 集中管理 X-Admin-Key 驗證；供所有 /api/admin 端點共用，避免重複 */
@Component
public class AdminKeyGuard {

    private final String adminApiKey;

    /** 注入管理金鑰 */
    public AdminKeyGuard(@Value("${app.admin-api-key}") String adminApiKey) {
        this.adminApiKey = adminApiKey;
    }

    /** 以固定時間比對驗證金鑰；不符拋 401 */
    public void verify(String key) {
        if (key == null || !MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8), adminApiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid admin key");
        }
    }
}
```

- [ ] **Step 2: 改 SurveyControllerTest（改用 guard，行為不變）**

把 `SurveyControllerTest` 類別上的 `@Import(UnsubscribeTokenService.class)` 改為：
```java
@Import({UnsubscribeTokenService.class, AdminKeyGuard.class})
```
（其餘測試不動；`adminWithoutKeyReturns401` / `adminWithKeyReturns200` 應仍通過。）

- [ ] **Step 3: 跑測試確認失敗**

Run: `cd survey-backend && mvn -q test -Dtest=SurveyControllerTest`
Expected: 失敗（`AdminKeyGuard` 尚未被 `SurveyController` 使用、或建構子不符）。

- [ ] **Step 4: 重構 SurveyController 改用 AdminKeyGuard**

在 `SurveyController` 移除 `adminApiKey` 欄位與其 `@Value` 注入，改注入 `AdminKeyGuard`。

把欄位與建構子改為：
```java
    private final SurveyResponseRepository repository;
    private final ObjectMapper objectMapper;
    private final UnsubscribeTokenService tokenService;
    private final WelcomeMailService welcomeMailService;
    private final AdminKeyGuard adminKeyGuard;

    /** 注入資料層、JSON 序列化器、退訂 token 服務、歡迎信服務與管理金鑰守衛 */
    public SurveyController(SurveyResponseRepository repository,
                            ObjectMapper objectMapper,
                            UnsubscribeTokenService tokenService,
                            WelcomeMailService welcomeMailService,
                            AdminKeyGuard adminKeyGuard) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
        this.welcomeMailService = welcomeMailService;
        this.adminKeyGuard = adminKeyGuard;
    }
```

把 `list(...)` 方法開頭的金鑰檢查：
```java
        if (key == null || !MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8), adminApiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid admin key");
        }
```
改為：
```java
        adminKeyGuard.verify(key);
```

移除不再使用的 import：`org.springframework.beans.factory.annotation.Value`（若無其他用途）、`java.nio.charset.StandardCharsets`、`java.security.MessageDigest`、`org.springframework.http.HttpStatus`（若 submit 仍用 `HttpStatus.CREATED` 則保留 HttpStatus）、`org.springframework.web.server.ResponseStatusException`（若無其他用途則移除）。以 `cd survey-backend && mvn -q -DskipTests compile` 確認無未使用 import 造成的錯誤（未使用 import 在 Java 不致編譯失敗，但仍清掉）。

> 註：`submit` 仍使用 `HttpStatus.CREATED`，故保留 `import org.springframework.http.HttpStatus;`。

- [ ] **Step 5: 跑測試確認通過**

Run: `cd survey-backend && mvn -q test -Dtest=SurveyControllerTest`
Expected: 全部 PASS。

- [ ] **Step 6: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/AdminKeyGuard.java survey-backend/src/main/java/world/springai/survey/SurveyController.java survey-backend/src/test/java/world/springai/survey/SurveyControllerTest.java
git commit -m "refactor(survey-backend): 抽出 AdminKeyGuard 集中管理金鑰驗證

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/src/main/java/world/springai/survey/AdminKeyGuard.java survey-backend/src/main/java/world/springai/survey/SurveyController.java survey-backend/src/test/java/world/springai/survey/SurveyControllerTest.java
```

---

## Task 3: EmailTemplate 共用外框 + 重構 WelcomeMailService（TDD）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/EmailTemplate.java`
- Test: `survey-backend/src/test/java/world/springai/survey/EmailTemplateTest.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/WelcomeMailService.java`
- Modify: `survey-backend/src/test/java/world/springai/survey/WelcomeMailServiceTest.java`

- [ ] **Step 1: 寫 EmailTemplate 失敗測試**

`survey-backend/src/test/java/world/springai/survey/EmailTemplateTest.java`：
```java
package world.springai.survey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 信件外框模板測試 */
class EmailTemplateTest {

    private final EmailTemplate template = new EmailTemplate();

    /** 輸出應包含內文、退訂連結與「取消訂閱」字樣 */
    @Test
    void wrapContainsBodyAndUnsubscribeLink() {
        String out = template.wrap("<p>內文段落</p>", "https://api.example.com/api/survey/unsubscribe?email=a%40b.com&t=tok");
        assertTrue(out.contains("<p>內文段落</p>"), out);
        assertTrue(out.contains("https://api.example.com/api/survey/unsubscribe?email=a%40b.com&t=tok"), out);
        assertTrue(out.contains("取消訂閱"), out);
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `cd survey-backend && mvn -q test -Dtest=EmailTemplateTest`
Expected: 編譯失敗（`EmailTemplate` 不存在）。

- [ ] **Step 3: 實作 EmailTemplate**

`survey-backend/src/main/java/world/springai/survey/EmailTemplate.java`：
```java
package world.springai.survey;

import org.springframework.stereotype.Component;

/** 信件品牌外框：把內文 HTML 套上版面與退訂頁腳，歡迎信與電子報共用 */
@Component
public class EmailTemplate {

    /** 以固定外框包住內文，並在頁腳放入該收件人的退訂連結 */
    public String wrap(String bodyHtml, String unsubscribeLink) {
        return """
            <div style="font-family:system-ui,'Microsoft JhengHei',sans-serif;line-height:1.7;max-width:560px;margin:0 auto;color:#1a1a2e">
              %s
              <hr style="border:none;border-top:1px solid #eee;margin:24px 0">
              <p style="color:#888;font-size:.85rem">
                你會收到這封信，是因為你在課程網站填寫了興趣調查並同意接收課程資訊。<br>
                若不想再收到，<a href="%s" style="color:#4f46e5">點此取消訂閱</a>。
              </p>
            </div>
            """.formatted(bodyHtml, unsubscribeLink);
    }
}
```

- [ ] **Step 4: 重構 WelcomeMailService 改用 EmailTemplate**

在 `WelcomeMailService` 加入 `EmailTemplate` 依賴並改寫 `buildHtml`。

把欄位與建構子改為（新增 `emailTemplate`）：
```java
    private final MailSender mailSender;
    private final UnsubscribeTokenService tokenService;
    private final EmailLogRepository emailLogRepository;
    private final EmailTemplate emailTemplate;
    private final String publicBaseUrl;

    /** 注入寄信、token 服務、寄送記錄、信件模板與對外網址 */
    public WelcomeMailService(MailSender mailSender,
                              UnsubscribeTokenService tokenService,
                              EmailLogRepository emailLogRepository,
                              EmailTemplate emailTemplate,
                              @Value("${app.public-base-url}") String publicBaseUrl) {
        this.mailSender = mailSender;
        this.tokenService = tokenService;
        this.emailLogRepository = emailLogRepository;
        this.emailTemplate = emailTemplate;
        this.publicBaseUrl = publicBaseUrl;
    }
```

把原本的 `buildHtml(String unsubscribeLink)` 整個方法改為（只組「歡迎內文」再交給模板套外框）：
```java
    /** 組歡迎信 HTML：歡迎內文交給共用模板套外框與退訂頁腳 */
    private String buildHtml(String unsubscribeLink) {
        String body = """
            <h2>歡迎你！🎉</h2>
            <p>謝謝你填寫「AI 賦能全端開發」課程興趣調查。我們會在課程開放報名、釋出早鳥優惠時優先通知你。</p>
            <p>在那之前，你可以先看看課程網站，了解整個實戰學習路徑。</p>
            """;
        return emailTemplate.wrap(body, unsubscribeLink);
    }
```

- [ ] **Step 5: 修 WelcomeMailServiceTest 建構子**

在 `WelcomeMailServiceTest` 把建立 svc 的那行改為傳入 `EmailTemplate`：
```java
    private final EmailTemplate emailTemplate = new EmailTemplate();
    private final WelcomeMailService svc =
        new WelcomeMailService(mailSender, tokenService, emailLogRepository, emailTemplate, "https://api.example.com");
```
（其餘測試不動。）

- [ ] **Step 6: 跑測試確認通過**

Run: `cd survey-backend && mvn -q test -Dtest=EmailTemplateTest,WelcomeMailServiceTest`
Expected: 全部 PASS（EmailTemplate 1 + WelcomeMailService 2）。

- [ ] **Step 7: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/EmailTemplate.java survey-backend/src/test/java/world/springai/survey/EmailTemplateTest.java survey-backend/src/main/java/world/springai/survey/WelcomeMailService.java survey-backend/src/test/java/world/springai/survey/WelcomeMailServiceTest.java
git commit -m "refactor(survey-backend): 抽出 EmailTemplate 共用外框，歡迎信改用之

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/src/main/java/world/springai/survey/EmailTemplate.java survey-backend/src/test/java/world/springai/survey/EmailTemplateTest.java survey-backend/src/main/java/world/springai/survey/WelcomeMailService.java survey-backend/src/test/java/world/springai/survey/WelcomeMailServiceTest.java
```

---

## Task 4: MailSender 擴充 batch/schedule/cancel（TDD）

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/MailSender.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/NoopMailSender.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/ZSendMailSender.java`
- Modify: `survey-backend/src/test/java/world/springai/survey/ZSendMailSenderTest.java`

- [ ] **Step 1: 在 ZSendMailSenderTest 加 batch/schedule/cancel 失敗測試**

在 `ZSendMailSenderTest` 類別內，於既有測試之後加入以下 import 與測試（import 放檔案上方既有 import 區）：
```java
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
```
測試方法：
```java
    /** sendBatch 應 POST 到 /emails/batch，body 用 emails 陣列，回 job_id */
    @Test
    void sendBatchPostsEmailsArrayAndReturnsJobId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ZSendMailSender sender = new ZSendMailSender(builder, "zs_test", "noreply@springai.world", "");

        server.expect(requestTo("https://api.zeabur.com/api/v1/zsend/emails/batch"))
              .andExpect(method(POST))
              .andExpect(header("Authorization", "Bearer zs_test"))
              .andExpect(jsonPath("$.emails[0].from").value("noreply@springai.world"))
              .andExpect(jsonPath("$.emails[0].to[0]").value("a@example.com"))
              .andExpect(jsonPath("$.emails[1].to[0]").value("b@example.com"))
              .andRespond(withSuccess("{\"job_id\":\"job-1\",\"status\":\"pending\",\"total_count\":2}", APPLICATION_JSON));

        String jobId = sender.sendBatch(List.of(
            new MailSender.Email("a@example.com", "主旨", "<p>A</p>"),
            new MailSender.Email("b@example.com", "主旨", "<p>B</p>")));

        assertEquals("job-1", jobId);
        server.verify();
    }

    /** schedule 應 POST 到 /emails/schedule，帶 scheduled_at，回 id */
    @Test
    void schedulePostsScheduledAtAndReturnsId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ZSendMailSender sender = new ZSendMailSender(builder, "zs_test", "noreply@springai.world", "");

        server.expect(requestTo("https://api.zeabur.com/api/v1/zsend/emails/schedule"))
              .andExpect(method(POST))
              .andExpect(jsonPath("$.to[0]").value("a@example.com"))
              .andExpect(jsonPath("$.scheduled_at").value("2030-01-01T00:00:00Z"))
              .andRespond(withSuccess("{\"id\":\"sched-1\",\"status\":\"enqueued\"}", APPLICATION_JSON));

        String id = sender.schedule(
            new MailSender.Email("a@example.com", "主旨", "<p>A</p>"),
            Instant.parse("2030-01-01T00:00:00Z"));

        assertEquals("sched-1", id);
        server.verify();
    }

    /** cancelScheduled 應 DELETE /emails/scheduled/{id}，2xx 回 true */
    @Test
    void cancelScheduledDeletesAndReturnsTrue() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ZSendMailSender sender = new ZSendMailSender(builder, "zs_test", "noreply@springai.world", "");

        server.expect(requestTo("https://api.zeabur.com/api/v1/zsend/emails/scheduled/sched-1"))
              .andExpect(method(DELETE))
              .andExpect(header("Authorization", "Bearer zs_test"))
              .andRespond(withNoContent());

        assertTrue(sender.cancelScheduled("sched-1"));
        server.verify();
    }
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `cd survey-backend && mvn -q test -Dtest=ZSendMailSenderTest`
Expected: 編譯失敗（`MailSender.Email` / `sendBatch` / `schedule` / `cancelScheduled` 不存在）。

- [ ] **Step 3: 擴充 MailSender 介面**

把 `survey-backend/src/main/java/world/springai/survey/MailSender.java` 整檔改為：
```java
package world.springai.survey;

import java.time.Instant;
import java.util.List;

/** 寄信抽象：單封、批量、排程與取消排程 */
public interface MailSender {

    /** 一封信的內容（寄件人由實作端的設定提供） */
    record Email(String to, String subject, String html) {}

    /** 寄一封 HTML 信給單一收件人，回傳 provider 訊息 id */
    String send(String to, String subject, String html);

    /** 批量立即寄送（呼叫端需自行切成每批 ≤100 封），回傳整批的 job id */
    String sendBatch(List<Email> emails);

    /** 排程單封到未來時間，回傳該封的 provider id（可用於取消） */
    String schedule(Email email, Instant scheduledAt);

    /** 取消一封排程信；成功回 true */
    boolean cancelScheduled(String providerId);
}
```

- [ ] **Step 4: NoopMailSender 實作新方法**

在 `NoopMailSender` 加入（保留既有 `send`）：
```java
import java.time.Instant;
import java.util.List;
```
方法：
```java
    /** 不實際寄送，僅記 log，回傳固定 job id */
    @Override
    public String sendBatch(List<Email> emails) {
        log.info("（未設 SEND_MAIL_API）略過批量寄信 count={}", emails.size());
        return "noop-batch";
    }

    /** 不實際排程，僅記 log，回傳固定 id */
    @Override
    public String schedule(Email email, Instant scheduledAt) {
        log.info("（未設 SEND_MAIL_API）略過排程寄信 to={} at={}", email.to(), scheduledAt);
        return "noop-sched";
    }

    /** 不實際取消，僅記 log，回傳 true */
    @Override
    public boolean cancelScheduled(String providerId) {
        log.info("（未設 SEND_MAIL_API）略過取消排程 id={}", providerId);
        return true;
    }
```

- [ ] **Step 5: ZSendMailSender 實作新方法**

在 `ZSendMailSender` 加入 import：
```java
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
```
加入方法（沿用既有 `client`/`apiKey`/`from`/`replyTo` 欄位）：
```java
    /** 批量立即寄送：body 為 {emails:[...]}；回傳整批 job_id */
    @Override
    public String sendBatch(List<Email> emails) {
        List<Map<String, Object>> arr = new ArrayList<>();
        for (Email e : emails) {
            arr.add(toPayload(e));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("emails", arr);
        Map<?, ?> resp = client.post()
            .uri("/api/v1/zsend/emails/batch")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);
        return resp == null ? null : String.valueOf(resp.get("job_id"));
    }

    /** 排程單封：在 payload 加 scheduled_at（ISO 8601）；回傳該封 id */
    @Override
    public String schedule(Email email, Instant scheduledAt) {
        Map<String, Object> body = toPayload(email);
        body.put("scheduled_at", DateTimeFormatter.ISO_INSTANT.format(scheduledAt));
        Map<?, ?> resp = client.post()
            .uri("/api/v1/zsend/emails/schedule")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);
        return resp == null ? null : String.valueOf(resp.get("id"));
    }

    /** 取消排程信：DELETE /emails/scheduled/{id}；2xx 未拋例外即回 true */
    @Override
    public boolean cancelScheduled(String providerId) {
        client.delete()
            .uri("/api/v1/zsend/emails/scheduled/{id}", providerId)
            .header("Authorization", "Bearer " + apiKey)
            .retrieve()
            .toBodilessEntity();
        return true;
    }

    /** 把一封 Email 組成 ZSend 單封 payload（含寄件人與選填 reply_to） */
    private Map<String, Object> toPayload(Email email) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from);
        m.put("to", List.of(email.to()));
        m.put("subject", email.subject());
        m.put("html", email.html());
        if (StringUtils.hasText(replyTo)) {
            m.put("reply_to", List.of(replyTo));
        }
        return m;
    }
```
> 既有 `send(...)` 方法保留不動。可順手把 `send` 內組 body 的重複改用 `toPayload`，但非必要；若改，確保既有 `ZSendMailSenderTest.sendsCorrectRequestAndReturnsId` 仍綠。

- [ ] **Step 6: 跑測試確認通過**

Run: `cd survey-backend && mvn -q test -Dtest=ZSendMailSenderTest`
Expected: 4 測試 PASS（原 1 + 新 3）。

- [ ] **Step 7: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/MailSender.java survey-backend/src/main/java/world/springai/survey/NoopMailSender.java survey-backend/src/main/java/world/springai/survey/ZSendMailSender.java survey-backend/src/test/java/world/springai/survey/ZSendMailSenderTest.java
git commit -m "feat(survey-backend): MailSender 擴充批量/排程/取消（ZSend batch/schedule/cancel）

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/src/main/java/world/springai/survey/MailSender.java survey-backend/src/main/java/world/springai/survey/NoopMailSender.java survey-backend/src/main/java/world/springai/survey/ZSendMailSender.java survey-backend/src/test/java/world/springai/survey/ZSendMailSenderTest.java
```

---

## Task 5: campaign 資料模型（V4 + Entity + Repo + EmailLog 擴充）

**Files:**
- Create: `survey-backend/src/main/resources/db/migration/V4__create_campaign.sql`
- Create: `survey-backend/src/main/java/world/springai/survey/Campaign.java`
- Create: `survey-backend/src/main/java/world/springai/survey/CampaignRepository.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/EmailLog.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/EmailLogRepository.java`

- [ ] **Step 1: 建立 Flyway V4**

`survey-backend/src/main/resources/db/migration/V4__create_campaign.sql`：
```sql
-- 電子報發送批次：每次發送一筆，供歷史檢視與取消排程
CREATE TABLE campaign (
    id              BIGSERIAL PRIMARY KEY,
    subject         TEXT        NOT NULL,            -- 主旨
    markdown        TEXT        NOT NULL,            -- 原始 Markdown 內文
    body_html       TEXT,                            -- 渲染後 HTML（稽核/重寄）
    filter_role     TEXT,                            -- 篩選：身分（null=不限）
    filter_interest TEXT,                            -- 篩選：想學主題（null=不限）
    mode            TEXT        NOT NULL,            -- now | schedule
    scheduled_at    TIMESTAMPTZ,                     -- 排程時間（mode=schedule）
    recipient_count INT         NOT NULL DEFAULT 0,  -- 收件人數
    accepted_count  INT         NOT NULL DEFAULT 0,  -- 成功提交數
    failed_count    INT         NOT NULL DEFAULT 0,  -- 失敗數
    status          TEXT        NOT NULL,            -- sending|sent|scheduled|cancelled|failed
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- email_log 關聯到 campaign（歡迎信為 null）
ALTER TABLE email_log ADD COLUMN campaign_id BIGINT;
CREATE INDEX idx_email_log_campaign ON email_log (campaign_id);
```

- [ ] **Step 2: 建立 Campaign Entity**

`survey-backend/src/main/java/world/springai/survey/Campaign.java`：
```java
package world.springai.survey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 電子報發送批次，對應資料表 campaign */
@Entity
@Table(name = "campaign")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String markdown;

    @Column(name = "body_html")
    private String bodyHtml;

    @Column(name = "filter_role")
    private String filterRole;

    @Column(name = "filter_interest")
    private String filterInterest;

    @Column(nullable = false)
    private String mode;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "accepted_count", nullable = false)
    private int acceptedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** JPA 需要的無參數建構子 */
    protected Campaign() {
    }

    /** 建立一筆發送批次（統計與狀態之後再更新） */
    public Campaign(String subject, String markdown, String bodyHtml,
                    String filterRole, String filterInterest,
                    String mode, OffsetDateTime scheduledAt,
                    int recipientCount, String status) {
        this.subject = subject;
        this.markdown = markdown;
        this.bodyHtml = bodyHtml;
        this.filterRole = filterRole;
        this.filterInterest = filterInterest;
        this.mode = mode;
        this.scheduledAt = scheduledAt;
        this.recipientCount = recipientCount;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getSubject() { return subject; }
    public String getMarkdown() { return markdown; }
    public String getBodyHtml() { return bodyHtml; }
    public String getFilterRole() { return filterRole; }
    public String getFilterInterest() { return filterInterest; }
    public String getMode() { return mode; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public int getRecipientCount() { return recipientCount; }
    public int getAcceptedCount() { return acceptedCount; }
    public int getFailedCount() { return failedCount; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setAcceptedCount(int acceptedCount) { this.acceptedCount = acceptedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public void setStatus(String status) { this.status = status; }
}
```

- [ ] **Step 3: 建立 CampaignRepository**

`survey-backend/src/main/java/world/springai/survey/CampaignRepository.java`：
```java
package world.springai.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 電子報批次資料存取層 */
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    /** 依建立時間新到舊列出（歷史頁用） */
    List<Campaign> findAllByOrderByCreatedAtDesc();
}
```

- [ ] **Step 4: EmailLog 加 campaignId 與 setStatus**

在 `EmailLog` 加入欄位（放在 `error` 之後、`createdAt` 之前）：
```java
    @Column(name = "campaign_id")
    private Long campaignId;
```
保留既有 6 參數建構子（歡迎信用，campaignId 留 null），新增一個 7 參數建構子並讓 6 參數建構子委派：
```java
    /** 建立一筆寄送記錄（不指定 campaign，campaignId 為 null） */
    public EmailLog(String recipient, String subject, String type,
                    String providerMessageId, String status, String error) {
        this(recipient, subject, type, providerMessageId, status, error, null);
    }

    /** 建立一筆寄送記錄（可指定所屬 campaign） */
    public EmailLog(String recipient, String subject, String type,
                    String providerMessageId, String status, String error, Long campaignId) {
        this.recipient = recipient;
        this.subject = subject;
        this.type = type;
        this.providerMessageId = providerMessageId;
        this.status = status;
        this.error = error;
        this.campaignId = campaignId;
    }
```
> 注意：原本的 6 參數建構子（直接賦值版）要刪掉，改成上面這個委派版，避免兩個同簽章建構子衝突。
加入 getter 與 setStatus：
```java
    public Long getCampaignId() { return campaignId; }
    public void setStatus(String status) { this.status = status; }
```

- [ ] **Step 5: EmailLogRepository 加查詢**

把 `EmailLogRepository` 改為：
```java
package world.springai.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 寄送記錄資料存取層 */
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    /** 某 campaign 的所有寄送記錄 */
    List<EmailLog> findByCampaignId(Long campaignId);
    /** 某 campaign 中特定狀態的寄送記錄（取消排程用） */
    List<EmailLog> findByCampaignIdAndStatus(Long campaignId, String status);
}
```

- [ ] **Step 6: 驗證可編譯**

Run: `cd survey-backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 7: Commit**

```bash
git add survey-backend/src/main/resources/db/migration/V4__create_campaign.sql survey-backend/src/main/java/world/springai/survey/Campaign.java survey-backend/src/main/java/world/springai/survey/CampaignRepository.java survey-backend/src/main/java/world/springai/survey/EmailLog.java survey-backend/src/main/java/world/springai/survey/EmailLogRepository.java
git commit -m "feat(survey-backend): campaign 資料表（V4）+ email_log 關聯 campaign

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/src/main/resources/db/migration/V4__create_campaign.sql survey-backend/src/main/java/world/springai/survey/Campaign.java survey-backend/src/main/java/world/springai/survey/CampaignRepository.java survey-backend/src/main/java/world/springai/survey/EmailLog.java survey-backend/src/main/java/world/springai/survey/EmailLogRepository.java
```

---

## Task 6: RecipientService（篩選 role / interest）

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/SurveyResponseRepository.java`
- Create: `survey-backend/src/main/java/world/springai/survey/RecipientService.java`

> 說明：`findRecipients` 為 jsonb native query，無測試 DB 不做單元測試（由 Task 9 整合驗證）；`CampaignService` / controller 測試會 mock `RecipientService`。

- [ ] **Step 1: SurveyResponseRepository 加 findRecipients**

在 `SurveyResponseRepository` 介面內加入（保留既有方法）：
```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```
（若已 import 則略過）方法：
```java
    /**
     * 可寄送名單（去重小寫 email）：同意且未退訂；
     * role 為 null 不限；interest 為 null 不限，否則用 jsonb 包含比對。
     */
    @Query(value = """
        select distinct lower(email) from survey_response
        where consent = true and unsubscribed = false
          and (:role is null or role = :role)
          and (:interest is null or interest @> jsonb_build_array(:interest))
        """, nativeQuery = true)
    java.util.List<String> findRecipients(@Param("role") String role, @Param("interest") String interest);
```

- [ ] **Step 2: 建立 RecipientService**

`survey-backend/src/main/java/world/springai/survey/RecipientService.java`：
```java
package world.springai.survey;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 依條件取得可寄送名單（同意、未退訂、去重） */
@Service
public class RecipientService {

    private final SurveyResponseRepository repository;

    public RecipientService(SurveyResponseRepository repository) {
        this.repository = repository;
    }

    /** 取得符合篩選的去重收件 email；role/interest 空字串視為不限 */
    public List<String> recipients(String role, String interest) {
        return repository.findRecipients(blankToNull(role), blankToNull(interest));
    }

    /** 空白字串轉 null，讓 native query 的「is null 不限」生效 */
    private String blankToNull(String v) {
        return StringUtils.hasText(v) ? v : null;
    }
}
```

- [ ] **Step 3: 驗證可編譯**

Run: `cd survey-backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/SurveyResponseRepository.java survey-backend/src/main/java/world/springai/survey/RecipientService.java
git commit -m "feat(survey-backend): 收件名單篩選（role/interest jsonb）RecipientService

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/src/main/java/world/springai/survey/SurveyResponseRepository.java survey-backend/src/main/java/world/springai/survey/RecipientService.java
```

---

## Task 7: CampaignService（發送 / 預覽 / 測試 / 取消）（TDD）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/CampaignService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/CampaignServiceTest.java`

- [ ] **Step 1: 寫失敗測試**

`survey-backend/src/test/java/world/springai/survey/CampaignServiceTest.java`：
```java
package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CampaignService：立即走 batch、排程走 schedule、每封含個人化退訂連結、統計與失敗處理 */
class CampaignServiceTest {

    private final MailSender mailSender = mock(MailSender.class);
    private final RecipientService recipientService = mock(RecipientService.class);
    private final CampaignRepository campaignRepository = mock(CampaignRepository.class);
    private final EmailLogRepository emailLogRepository = mock(EmailLogRepository.class);
    private final MarkdownRenderer markdownRenderer = new MarkdownRenderer();
    private final EmailTemplate emailTemplate = new EmailTemplate();
    private final UnsubscribeTokenService tokenService = new UnsubscribeTokenService("secret");

    private final CampaignService svc = new CampaignService(
        mailSender, recipientService, campaignRepository, emailLogRepository,
        markdownRenderer, emailTemplate, tokenService, "https://api.example.com");

    /** 立即發送：呼叫 sendBatch，每封 html 含該收件人的退訂連結，campaign 記為 sent、accepted=2 */
    @Test
    void immediateSendUsesBatchWithPersonalizedLinks() {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@x.com", "b@x.com"));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));
        when(mailSender.sendBatch(anyList())).thenReturn("job-1");

        CampaignService.SendResult r = svc.send("主旨", "# 內文", null, null, "now", null);

        assertEquals(2, r.recipientCount());
        assertEquals(2, r.accepted());
        assertEquals(0, r.failed());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MailSender.Email>> captor = ArgumentCaptor.forClass(List.class);
        verify(mailSender).sendBatch(captor.capture());
        List<MailSender.Email> sent = captor.getValue();
        assertEquals(2, sent.size());
        // 每封都含「自己 email」的退訂連結
        assertTrue(sent.get(0).html().contains("email=a%40x.com"), sent.get(0).html());
        assertTrue(sent.get(1).html().contains("email=b%40x.com"), sent.get(1).html());
        // 內文 markdown 有被渲染
        assertTrue(sent.get(0).html().contains("內文"));
        verify(mailSender, never()).schedule(any(), any());
    }

    /** 排程發送：對每位收件人呼叫 schedule，帶 scheduledAt，campaign 記為 scheduled */
    @Test
    void scheduledSendUsesScheduleApi() {
        Instant at = Instant.parse("2030-01-01T00:00:00Z");
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@x.com"));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));
        when(mailSender.schedule(any(), eq(at))).thenReturn("sched-1");

        CampaignService.SendResult r = svc.send("主旨", "內文", null, null, "schedule", at);

        assertEquals(1, r.accepted());
        verify(mailSender).schedule(any(), eq(at));
        verify(mailSender, never()).sendBatch(anyList());
    }

    /** 批量丟例外：整批記 failed、不中斷 */
    @Test
    void batchFailureCountsFailed() {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@x.com", "b@x.com"));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));
        when(mailSender.sendBatch(anyList())).thenThrow(new RuntimeException("429"));

        CampaignService.SendResult r = svc.send("主旨", "內文", null, null, "now", null);

        assertEquals(0, r.accepted());
        assertEquals(2, r.failed());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `cd survey-backend && mvn -q test -Dtest=CampaignServiceTest`
Expected: 編譯失敗（`CampaignService` 不存在）。

- [ ] **Step 3: 實作 CampaignService**

`survey-backend/src/main/java/world/springai/survey/CampaignService.java`：
```java
package world.springai.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 電子報發送：渲染內文、組個人化退訂連結、立即(batch)/排程(schedule) 發送、記錄 campaign 與 email_log */
@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);
    private static final int BATCH_SIZE = 100; // ZSend 單次 batch 上限

    private final MailSender mailSender;
    private final RecipientService recipientService;
    private final CampaignRepository campaignRepository;
    private final EmailLogRepository emailLogRepository;
    private final MarkdownRenderer markdownRenderer;
    private final EmailTemplate emailTemplate;
    private final UnsubscribeTokenService tokenService;
    private final String publicBaseUrl;

    public CampaignService(MailSender mailSender,
                           RecipientService recipientService,
                           CampaignRepository campaignRepository,
                           EmailLogRepository emailLogRepository,
                           MarkdownRenderer markdownRenderer,
                           EmailTemplate emailTemplate,
                           UnsubscribeTokenService tokenService,
                           @Value("${app.public-base-url}") String publicBaseUrl) {
        this.mailSender = mailSender;
        this.recipientService = recipientService;
        this.campaignRepository = campaignRepository;
        this.emailLogRepository = emailLogRepository;
        this.markdownRenderer = markdownRenderer;
        this.emailTemplate = emailTemplate;
        this.tokenService = tokenService;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** 發送結果摘要 */
    public record SendResult(Long campaignId, int recipientCount, int accepted, int failed) {}

    /** 預覽：把 markdown 渲染並套外框（用示意退訂連結） */
    public String preview(String subject, String markdown) {
        String body = markdownRenderer.toHtml(markdown);
        return emailTemplate.wrap(body, publicBaseUrl + "/api/survey/unsubscribe?email=preview%40example.com&t=preview");
    }

    /** 寄一封測試信給指定信箱（立即、單封） */
    public String sendTest(String subject, String markdown, String to) {
        String html = renderFor(markdownRenderer.toHtml(markdown), to);
        return mailSender.send(to, subject, html);
    }

    /** 發送電子報：mode=now 用 batch、mode=schedule 用 schedule */
    public SendResult send(String subject, String markdown, String role, String interest,
                           String mode, Instant scheduledAt) {
        List<String> recipients = recipientService.recipients(role, interest);
        String bodyHtml = markdownRenderer.toHtml(markdown);
        boolean scheduled = "schedule".equals(mode);

        Campaign campaign = new Campaign(subject, markdown, bodyHtml, role, interest, mode,
            scheduledAt == null ? null : OffsetDateTime.ofInstant(scheduledAt, ZoneOffset.UTC),
            recipients.size(), scheduled ? "scheduled" : "sending");
        campaign = campaignRepository.save(campaign);
        Long campaignId = campaign.getId();

        int accepted = 0;
        int failed = 0;

        if (scheduled) {
            for (String email : recipients) {
                try {
                    String id = mailSender.schedule(
                        new MailSender.Email(email, subject, renderFor(bodyHtml, email)), scheduledAt);
                    emailLogRepository.save(new EmailLog(email, subject, "campaign", id, "scheduled", null, campaignId));
                    accepted++;
                } catch (Exception e) {
                    log.warn("排程寄信失敗 to={}：{}", email, e.getMessage());
                    emailLogRepository.save(new EmailLog(email, subject, "campaign", null, "failed", e.getMessage(), campaignId));
                    failed++;
                }
            }
        } else {
            for (int i = 0; i < recipients.size(); i += BATCH_SIZE) {
                List<String> chunk = recipients.subList(i, Math.min(i + BATCH_SIZE, recipients.size()));
                List<MailSender.Email> emails = new ArrayList<>();
                for (String email : chunk) {
                    emails.add(new MailSender.Email(email, subject, renderFor(bodyHtml, email)));
                }
                try {
                    String jobId = mailSender.sendBatch(emails);
                    for (String email : chunk) {
                        emailLogRepository.save(new EmailLog(email, subject, "campaign", jobId, "sent", null, campaignId));
                    }
                    accepted += chunk.size();
                } catch (Exception e) {
                    log.warn("批量寄信失敗 size={}：{}", chunk.size(), e.getMessage());
                    for (String email : chunk) {
                        emailLogRepository.save(new EmailLog(email, subject, "campaign", null, "failed", e.getMessage(), campaignId));
                    }
                    failed += chunk.size();
                }
            }
        }

        campaign.setAcceptedCount(accepted);
        campaign.setFailedCount(failed);
        campaign.setStatus(finalStatus(scheduled, accepted, failed));
        campaignRepository.save(campaign);

        return new SendResult(campaignId, recipients.size(), accepted, failed);
    }

    /** 取消某 campaign 的所有排程信 */
    public Map<String, Integer> cancelSchedule(Long campaignId) {
        List<EmailLog> rows = emailLogRepository.findByCampaignIdAndStatus(campaignId, "scheduled");
        int cancelled = 0;
        int failed = 0;
        for (EmailLog row : rows) {
            try {
                if (mailSender.cancelScheduled(row.getProviderMessageId())) {
                    row.setStatus("cancelled");
                    emailLogRepository.save(row);
                    cancelled++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                log.warn("取消排程失敗 id={}：{}", row.getProviderMessageId(), e.getMessage());
                failed++;
            }
        }
        campaignRepository.findById(campaignId).ifPresent(c -> {
            c.setStatus("cancelled");
            campaignRepository.save(c);
        });
        return Map.of("cancelled", cancelled, "failed", failed);
    }

    /** 歷史列表 */
    public List<Campaign> list() {
        return campaignRepository.findAllByOrderByCreatedAtDesc();
    }

    /** 把內文 HTML 套上「該收件人」的退訂連結 */
    private String renderFor(String bodyHtml, String email) {
        String link = publicBaseUrl + "/api/survey/unsubscribe?email="
            + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&t=" + tokenService.sign(email);
        return emailTemplate.wrap(bodyHtml, link);
    }

    /** 依是否排程與成敗決定最終狀態 */
    private String finalStatus(boolean scheduled, int accepted, int failed) {
        if (accepted == 0 && failed > 0) {
            return "failed";
        }
        return scheduled ? "scheduled" : "sent";
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

Run: `cd survey-backend && mvn -q test -Dtest=CampaignServiceTest`
Expected: 3 測試 PASS。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/CampaignService.java survey-backend/src/test/java/world/springai/survey/CampaignServiceTest.java
git commit -m "feat(survey-backend): CampaignService 立即/排程發送、個人化退訂、統計與取消

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/src/main/java/world/springai/survey/CampaignService.java survey-backend/src/test/java/world/springai/survey/CampaignServiceTest.java
```

---

## Task 8: AdminCampaignController + API（TDD）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/AdminCampaignController.java`
- Test: `survey-backend/src/test/java/world/springai/survey/AdminCampaignControllerTest.java`

- [ ] **Step 1: 寫失敗測試**

`survey-backend/src/test/java/world/springai/survey/AdminCampaignControllerTest.java`：
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AdminCampaignController：金鑰守衛 + 收件數 + 預覽 + 發送 */
@WebMvcTest(AdminCampaignController.class)
@Import(AdminKeyGuard.class)
@TestPropertySource(properties = {"app.admin-api-key=test-key"})
class AdminCampaignControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CampaignService campaignService;
    @MockBean RecipientService recipientService;

    /** 無金鑰一律 401 */
    @Test
    void recipientsWithoutKeyReturns401() throws Exception {
        mvc.perform(get("/api/admin/recipients"))
           .andExpect(status().isUnauthorized());
    }

    /** 有金鑰：回收件數與樣本 */
    @Test
    void recipientsWithKeyReturnsCount() throws Exception {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@x.com", "b@x.com"));
        mvc.perform(get("/api/admin/recipients").header("X-Admin-Key", "test-key"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.count").value(2));
    }

    /** 預覽：回渲染後 HTML */
    @Test
    void previewReturnsHtml() throws Exception {
        when(campaignService.preview(eq("主旨"), eq("# 內文"))).thenReturn("<div>內文</div>");
        mvc.perform(post("/api/admin/campaign/preview").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"主旨\",\"markdown\":\"# 內文\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.html").value("<div>內文</div>"));
    }

    /** 發送：立即模式回摘要 */
    @Test
    void sendNowReturnsSummary() throws Exception {
        when(campaignService.send(eq("主旨"), eq("內文"), any(), any(), eq("now"), any()))
            .thenReturn(new CampaignService.SendResult(7L, 3, 3, 0));
        mvc.perform(post("/api/admin/campaign/send").header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"主旨\",\"markdown\":\"內文\",\"mode\":\"now\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.campaignId").value(7))
           .andExpect(jsonPath("$.accepted").value(3));
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `cd survey-backend && mvn -q test -Dtest=AdminCampaignControllerTest`
Expected: 編譯失敗（`AdminCampaignController` 不存在）。

- [ ] **Step 3: 實作 AdminCampaignController**

`survey-backend/src/main/java/world/springai/survey/AdminCampaignController.java`：
```java
package world.springai.survey;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 寄信後台 API（全部經 AdminKeyGuard 驗 X-Admin-Key） */
@RestController
public class AdminCampaignController {

    private static final String KEY_HEADER = "X-Admin-Key";

    private final AdminKeyGuard guard;
    private final CampaignService campaignService;
    private final RecipientService recipientService;

    public AdminCampaignController(AdminKeyGuard guard,
                                   CampaignService campaignService,
                                   RecipientService recipientService) {
        this.guard = guard;
        this.campaignService = campaignService;
        this.recipientService = recipientService;
    }

    /** 預覽用請求 */
    public record PreviewRequest(String subject, String markdown) {}
    /** 測試寄送請求 */
    public record TestRequest(String subject, String markdown, String to) {}
    /** 篩選條件 */
    public record Filter(String role, String interest) {}
    /** 發送請求 */
    public record SendRequest(String subject, String markdown, Filter filter, String mode, String scheduledAt) {}

    /** 收件名單計數與樣本（前 5 筆） */
    @GetMapping("/api/admin/recipients")
    public Map<String, Object> recipients(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                          @RequestParam(required = false) String role,
                                          @RequestParam(required = false) String interest) {
        guard.verify(key);
        List<String> all = recipientService.recipients(role, interest);
        return Map.of("count", all.size(), "sample", all.stream().limit(5).toList());
    }

    /** 內文預覽 */
    @PostMapping("/api/admin/campaign/preview")
    public Map<String, String> preview(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                       @RequestBody PreviewRequest req) {
        guard.verify(key);
        return Map.of("html", campaignService.preview(req.subject(), req.markdown()));
    }

    /** 寄測試信給指定信箱 */
    @PostMapping("/api/admin/campaign/test")
    public Map<String, String> test(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                    @RequestBody TestRequest req) {
        guard.verify(key);
        return Map.of("providerId", campaignService.sendTest(req.subject(), req.markdown(), req.to()));
    }

    /** 發送電子報（立即或排程） */
    @PostMapping("/api/admin/campaign/send")
    public CampaignService.SendResult send(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                           @RequestBody SendRequest req) {
        guard.verify(key);
        String role = req.filter() == null ? null : req.filter().role();
        String interest = req.filter() == null ? null : req.filter().interest();
        Instant scheduledAt = null;
        if ("schedule".equals(req.mode())) {
            if (req.scheduledAt() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "排程模式需要 scheduledAt");
            }
            scheduledAt = Instant.parse(req.scheduledAt());
            if (!scheduledAt.isAfter(Instant.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "排程時間需為未來");
            }
        }
        return campaignService.send(req.subject(), req.markdown(), role, interest, req.mode(), scheduledAt);
    }

    /** 歷史列表 */
    @GetMapping("/api/admin/campaigns")
    public List<Campaign> campaigns(@RequestHeader(value = KEY_HEADER, required = false) String key) {
        guard.verify(key);
        return campaignService.list();
    }

    /** 取消某 campaign 的排程 */
    @DeleteMapping("/api/admin/campaigns/{id}/schedule")
    public Map<String, Integer> cancel(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                       @PathVariable Long id) {
        guard.verify(key);
        return campaignService.cancelSchedule(id);
    }
}
```

> 註：`GET /api/admin/campaigns/{id}` 明細（含每筆 email_log 狀態）非本段測試重點，先不做；若前端需要，於前端段補。本段以上述端點滿足前端主流程。

- [ ] **Step 4: 跑測試確認通過**

Run: `cd survey-backend && mvn -q test -Dtest=AdminCampaignControllerTest`
Expected: 4 測試 PASS。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/AdminCampaignController.java survey-backend/src/test/java/world/springai/survey/AdminCampaignControllerTest.java
git commit -m "feat(survey-backend): 寄信後台 API（recipients/preview/test/send/campaigns/cancel）

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>" -- survey-backend/src/main/java/world/springai/survey/AdminCampaignController.java survey-backend/src/test/java/world/springai/survey/AdminCampaignControllerTest.java
```

---

## Task 9: 全套測試 + 部署 + 線上整合驗證

**Files:**（無新程式；驗證與部署）

- [ ] **Step 1: 全套測試綠燈**

Run: `cd survey-backend && mvn -q test`
Expected: 全部 PASS（含第一段既有測試 + 本段 MarkdownRenderer/EmailTemplate/ZSendMailSender(4)/CampaignService(3)/AdminCampaignController(4)/SurveyController/WelcomeMailService/UnsubscribeTokenService）。

- [ ] **Step 2: push 觸發部署**

```bash
git push origin main
```
Expected: Zeabur 自動重建 survey-backend；`V4` migration 自動套用（ddl validate 通過代表 campaign/email_log schema 對齊）。

- [ ] **Step 3: 線上整合驗證（curl / PowerShell，需 ADMIN_API_KEY）**

設 `$ADMIN`（線上 `ADMIN_API_KEY`）、`$BASE="https://springai-survey.zeabur.app"`，依序驗證：
```powershell
# 1. 收件數（先送一筆同意問卷建名單，再查）
Invoke-WebRequest "$BASE/api/admin/recipients" -Headers @{ "X-Admin-Key"=$ADMIN } -SkipHttpErrorCheck | % Content
# 2. 預覽
$body = @{ subject="測試電子報"; markdown="# 你好`n`n這是 **測試** 內容。" } | ConvertTo-Json
Invoke-WebRequest "$BASE/api/admin/campaign/preview" -Method Post -ContentType "application/json; charset=utf-8" -Headers @{ "X-Admin-Key"=$ADMIN } -Body $body -SkipHttpErrorCheck | % Content
# 3. 測試寄送給自己
$t = @{ subject="測試電子報"; markdown="# 你好"; to="kevintsai1202@gmail.com" } | ConvertTo-Json
Invoke-WebRequest "$BASE/api/admin/campaign/test" -Method Post -ContentType "application/json; charset=utf-8" -Headers @{ "X-Admin-Key"=$ADMIN } -Body $t -SkipHttpErrorCheck | % Content
# 4. 排程一封（未來時間）→ 取 campaignId → 取消
$s = @{ subject="排程測試"; markdown="排程內容"; mode="schedule"; scheduledAt="2030-01-01T00:00:00Z" } | ConvertTo-Json
$r = Invoke-WebRequest "$BASE/api/admin/campaign/send" -Method Post -ContentType "application/json; charset=utf-8" -Headers @{ "X-Admin-Key"=$ADMIN } -Body $s -SkipHttpErrorCheck
$r.Content
# 用回傳 campaignId 取消：
# Invoke-WebRequest "$BASE/api/admin/campaigns/<id>/schedule" -Method Delete -Headers @{ "X-Admin-Key"=$ADMIN } -SkipHttpErrorCheck | % Content
```
Expected：① 回 `{count, sample}`；② 回含 `取消訂閱` 的 HTML；③ 回 `{providerId}` 且收到測試信；④ 發送回 `{campaignId, recipientCount, accepted, failed}`，取消回 `{cancelled, failed}`，且 ZSend `GET /emails/scheduled` 該筆消失。

> 清理：驗證用的測試問卷/排程記得清掉（排程用取消端點；測試問卷可用第一段方式刪）。

---

## Self-Review 對照

- **AdminKeyGuard 保護所有 /api/admin**：Task 2（guard）+ Task 8（每端點 verify）。✅
- **Markdown 渲染（伺服器端為準）+ 預覽端點**：Task 1（renderer）+ Task 7（preview）+ Task 8（/preview）。✅
- **共用 EmailTemplate（歡迎信改用）**：Task 3。✅
- **MailSender batch/schedule/cancel（ZSend 對應端點與 body 形狀）**：Task 4（含 MockRestServiceServer 驗 `{emails:[]}` / `scheduled_at` / DELETE）。✅
- **campaign 表 + email_log.campaign_id**：Task 5。✅
- **收件名單篩選（role/interest jsonb）**：Task 6（native query）+ 整合驗證。✅
- **CampaignService：立即(batch≤100)/排程(逐封)、每人個人化退訂連結、統計、失敗不中斷、取消排程**：Task 7（含對應測試）。✅
- **Admin API（recipients/preview/test/send/campaigns/cancel）+ 排程未來時間驗證**：Task 8。✅
- **批量回單一 job_id、排程回可取消 id 的不對稱**：Task 4 回傳值 + Task 7 email_log 記錄方式一致。✅
- **型別一致**：`MailSender.Email(to,subject,html)`、`sendBatch→String`、`schedule→String`、`cancelScheduled→boolean`、`CampaignService.SendResult(campaignId,recipientCount,accepted,failed)`、`EmailLog` 7 參數建構子 + `setStatus`、`Campaign` 建構子/`setStatus`/`setAcceptedCount`/`setFailedCount`、`RecipientService.recipients(role,interest)` 在定義與使用處一致。✅
- **YAGNI（不含 webhook/帳號/排程器/多選篩選/明細端點）**：未觸及（明細端點明示留前端段視需要再補）。✅
- **不破壞第一段**：歡迎信改用 EmailTemplate（輸出等價）、SurveyController 改用 AdminKeyGuard（行為不變），既有測試保留並更新建構子。✅
