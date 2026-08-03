# 課程優惠券寄送系統實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 站長建立課程優惠券活動（固定欄位自填），以 SurveyFilter 篩選問卷填答者＋逐人勾選後批次寄送，同活動同人終身一次。

**Architecture:** 獨立 coupon 模組（新 `coupon` 套件＋V22 表）；名單查詢包裝既有 `AudienceSearchService.SurveyFilter`（固定 `consentStatus=CONFIRMED`）；寄送迴圈比照 `InviteService`（email_log 冪等／limit／逐封容錯）；信件版型 `CouponMailRenderer` 放 `mail` 套件。設計依據 `docs/superpowers/specs/2026-08-04-coupon-campaign-design.md`。

**Tech Stack:** Spring Boot（JPA entity＋JdbcTemplate 併用）、Flyway、PostgreSQL、vanilla JS（admin.html）、Playwright 驗證腳本。

## Global Constraints

- 所有指令在 `survey-backend/` 下執行；mvn 必須 `JAVA_HOME=/d/java/jdk-21`（系統預設 JDK8 會編譯失敗）。
- 整合測試需本機 5433 PG：先 `docker start survey-test-db`。
- 所有程式碼中文註解（函式級必備）；工作分支 `agent/coupon-campaign`（已存在）；每 Task 一 commit＋`Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` 尾行。
- Flyway 版號：**V22**（現最大 V21）。
- admin 端點保護：`@RequestHeader("X-Admin-Key")`＋`guard.verify(key)`（照 FormSchemaController 既有寫法）。
- admin.html 動態值進 DOM 一律 `textContent`；信件動態值一律 HTML 跳脫。
- email_log 冪等 type 字面：`"coupon:" + campaignId`。
- **新增讀者網域頁面／端點時檢查 ReaderEntryHostFilter**——本案全部端點只走 admin 網域，不需放行（若實作中發現要在讀者網域曝露任何路徑，先回報）。

---

### Task 1: V22 migration＋CouponCampaign entity＋repository

**Files:**
- Create: `src/main/resources/db/migration/V22__coupon_campaign.sql`
- Create: `src/main/java/world/springai/survey/coupon/CouponCampaign.java`
- Create: `src/main/java/world/springai/survey/coupon/CouponCampaignRepository.java`
- Test: `src/test/java/world/springai/survey/coupon/CouponCampaignRepositoryTest.java`

**Interfaces:**
- Produces:

```java
public class CouponCampaign {
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SENT = "SENT";
    /** answerFilter 以 JSON 字串儲存（jsonb 欄），空條件為 "{}" */
    public CouponCampaign(String courseName, String pitch, String courseUrl,
                          String couponCode, LocalDate expiresAt,
                          String formKey, String answerFilter)
    // getter 全套；setStatus/setSentAt/setSentCount（寄送後更新）
}
public interface CouponCampaignRepository extends JpaRepository<CouponCampaign, Long> {
    List<CouponCampaign> findAllByOrderByCreatedAtDesc();
}
```

- [ ] **Step 1: 撰寫 V22 migration**（spec §4 SQL 逐字，含 `ck_coupon_status`、`ck_coupon_course_url CHECK (course_url LIKE 'https://%')`）
- [ ] **Step 2: 先寫整合測試看 RED**（基底照 `src/test/java/world/springai/survey/form/SurveyVoteRepositoryTest.java` 的 @SpringBootTest＋@DynamicPropertySource＋獨立 DB 名模式）：

```java
@Test void 儲存與讀回活動_預設DRAFT() {
    CouponCampaign saved = repository.save(new CouponCampaign(
        "AI 全端開發", "推薦文案", "https://hahow.in/cr/x", "SAVE300",
        LocalDate.of(2026, 9, 30), "reader-poll", "{}"));
    assertEquals(CouponCampaign.STATUS_DRAFT, saved.getStatus());
    assertNotNull(saved.getCreatedAt()); // @PrePersist 寫回實體（不可用 @CreationTimestamp）
}
@Test void courseUrl非https撞DB_CHECK() {
    assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(
        new CouponCampaign("課", "文", "http://x.com", "C", null, "k", "{}")));
}
```

- [ ] **Step 3: 實作 entity**（`@Entity @Table(name="coupon_campaign")`；`answer_filter` 欄位 `@Column(columnDefinition="jsonb")` 需 `@JdbcTypeCode(SqlTypes.JSON)` 或以 String＋`::jsonb` 寫入——先看專案內既有 jsonb entity 寫法（grep `jsonb` in main/java），照既有慣例；找不到前例就用 String 欄位＋migration 端 jsonb、JPA 以 `columnDefinition = "jsonb"`＋Hibernate 6 `@JdbcTypeCode(SqlTypes.JSON)`）
- [ ] **Step 4: GREEN**：`docker start survey-test-db && JAVA_HOME=/d/java/jdk-21 mvn test -Dtest=CouponCampaignRepositoryTest`
- [ ] **Step 5: Commit** `feat(coupon): V22 migration 與 CouponCampaign 實體`

---

### Task 2: CouponMailRenderer（mail 套件）

**Files:**
- Create: `src/main/java/world/springai/survey/mail/CouponMailRenderer.java`
- Test: `src/test/java/world/springai/survey/mail/CouponMailRendererTest.java`

**Interfaces:**
- Consumes: `CouponCampaign`（Task 1 getter）。
- Produces:

```java
@Service
public class CouponMailRenderer {
    /** 主旨：課程名稱＋優惠語 */
    public String subject(CouponCampaign campaign)   // 例："《AI 全端開發》讀者專屬優惠"
    /**
     * email-safe 優惠卡（單格 table＋inline style，琥珀底 #fef3c7、左條 5px #d97706）。
     * @param formTitle 寄送原因顯示的問卷名稱（footer：「你收到這封信是因為你填過問卷『{formTitle}』」）
     * @param unsubscribeLink 退訂連結（逐收件人，由呼叫端以 SubscriptionLinkBuilder.unsubscribeLink(email) 產生）
     */
    public String body(CouponCampaign campaign, String formTitle, String unsubscribeLink)
}
```

- [ ] **Step 1: 失敗測試**：

```java
@Test void 優惠卡含課程名文案優惠碼與按鈕() {
    String html = renderer.body(campaign("AI 全端開發", "超值文案", "https://hahow.in/cr/x",
        "SAVE300", LocalDate.of(2026, 9, 30)), "讀者意見調查", "https://x/unsub");
    assertTrue(html.contains("AI 全端開發"));
    assertTrue(html.contains("SAVE300"));
    assertTrue(html.contains("href=\"https://hahow.in/cr/x\""));
    assertTrue(html.contains("優惠至 2026-09-30"));
    assertTrue(html.contains("讀者意見調查")); // 寄送原因
    assertTrue(html.contains("https://x/unsub")); // 退訂
}
@Test void 期限為null不顯示期限行() { /* expiresAt=null → 不含「優惠至」 */ }
@Test void 動態值跳脫_課程名含HTML() { /* courseName="A<b>" → 含 A&lt;b&gt;、不含 A<b> */ }
```

- [ ] **Step 2: RED** → **Step 3: 實作**（跳脫 helper 複製 `SurveyBlockRenderer` 的 private escapeHtml 五 replace 寫法並註解出處；優惠碼用 `<code>` 等寬＋虛線框 inline style）→ **Step 4: GREEN** → **Step 5: Commit** `feat(coupon): 優惠券信件版型渲染器`

---

### Task 3: CouponRecipientService（名單查詢）

**Files:**
- Create: `src/main/java/world/springai/survey/coupon/CouponRecipientService.java`
- Test: `src/test/java/world/springai/survey/coupon/CouponRecipientServiceTest.java`（mock AudienceSearchService＋EmailLogRepository）

**Interfaces:**
- Consumes: `AudienceSearchService.search(SearchRequest)` → `SearchResult(items: List<Map<String,Object>>, total, ...)`；`SurveyFilter(formKey, version, answers)`；`Filters` record 完整簽名**開檔為準**（`AudienceSearchService.java` 約 L82-101，consentStatus 帶 `List.of("CONFIRMED")` 或該欄實際型別）；items 中 email／name 的 map 鍵名**開檔查證**（看 search() 的 SELECT 與 item.put）。`EmailLogRepository.findByTypeAndStatus(String, String)`（InviteService 同款）。
- Produces:

```java
@Service
public class CouponRecipientService {
    /** 單一收件人：email 完整值（admin 介面）、稱呼、是否已寄過本活動 */
    public record Recipient(String email, String name, boolean alreadySent) {}
    /** 以活動快照條件查命中名單（固定 consent=CONFIRMED），alreadySent 由 email_log type=coupon:{id} 判定 */
    public List<Recipient> resolve(CouponCampaign campaign)
    /** 子集驗證：requested 中不屬於命中集合者（正規化小寫比對）；空清單=全部合法 */
    public List<String> findIllegal(CouponCampaign campaign, List<String> requested)
}
```

- [ ] **Step 1: 失敗測試**：resolve 只帶 CONFIRMED（verify search 參數含 consent 條件與 SurveyFilter formKey/answers）；alreadySent 依 email_log 集合標記（大小寫不敏感）；findIllegal 混入外部 email 回傳該 email、全合法回空。answers 解析：campaign.answerFilter JSON 字串 `{"pick_topic":"RAG"}` → SurveyFilter.answers Map。
- [ ] **Step 2: RED** → **Step 3: 實作**（分頁迴圈拉全量：size=200 逐頁到 total；ObjectMapper 解析 answerFilter）→ **Step 4: GREEN** → **Step 5: Commit** `feat(coupon): 名單查詢與子集驗證（consent 固定 CONFIRMED）`

---

### Task 4: CouponSendService（寄送迴圈）

**Files:**
- Create: `src/main/java/world/springai/survey/coupon/CouponSendService.java`
- Test: `src/test/java/world/springai/survey/coupon/CouponSendServiceTest.java`（全 mock）

**Interfaces:**
- Consumes: Task 2 `CouponMailRenderer.subject/body`、Task 3 `CouponRecipientService.findIllegal`、`MailSender.send(to, subject, html)` → String id、`EmailLog(recipient, subject, type, providerMessageId, status, error)` 六參建構子與 `findByTypeAndStatus`、`SubscriptionLinkBuilder.unsubscribeLink(email)`、`FormSchemaService.listDefinitions()`（取 formKey 最新版 title 當寄送原因；查無 fallback 用 formKey 原字串，不擋寄送）。
- Produces:

```java
@Service
public class CouponSendService {
    public record SendResult(int attempted, int sent, int skipped, int failed, int remaining) {}
    /** 子集驗證不過拋 400 ResponseStatusException（訊息列出違規 email）；名單空/全已寄拋 400。
     *  刻意不加 @Transactional：迴圈夾外部 ZSend 呼叫、副作用不可回滾，
     *  部分失敗保留已寫入 email_log 比整批回滾誠實（同 CampaignService.send/InviteService 前例）。 */
    public SendResult send(long campaignId, List<String> emails, Integer limit)
}
```

- [ ] **Step 1: 失敗測試**（迴圈語意逐字比照 InviteService 模式）：

```java
@Test void 子集驗證失敗回400含違規email() { /* findIllegal 回 ["evil@x.com"] → 400、訊息含該 email、mailSender 零互動 */ }
@Test void 已寄過自動跳過並計入skipped() { /* email_log type=coupon:9 已含 a@x → 只寄 b@x，skipped=1 */ }
@Test void limit截斷計入remaining() { /* 3 人 limit=2 → attempted=2, remaining=1 */ }
@Test void 單封失敗不中斷且記failed() { /* mailSender 對第一封拋例外 → failed=1、第二封照寄、email_log 記 failed 列 */ }
@Test void 首次寄送後活動標SENT並累計sent_count() { /* setStatus(SENT)、setSentAt 非空、setSentCount 累加 */ }
@Test void 名單全部已寄拋400() {}
```

- [ ] **Step 2: RED** → **Step 3: 實作**（type=`"coupon:"+campaignId`；每封 body 以 `unsubscribeLink(email)` 個人化；EmailLog 成功 status="sent"、失敗 status="failed"＋error 訊息）→ **Step 4: GREEN** → **Step 5: Commit** `feat(coupon): 寄送迴圈——子集驗證、冪等、額度與容錯`

---

### Task 5: AdminCouponController（四端點）

**Files:**
- Create: `src/main/java/world/springai/survey/coupon/AdminCouponController.java`
- Test: `src/test/java/world/springai/survey/coupon/AdminCouponControllerTest.java`（standalone MockMvc＋mock services＋ApiExceptionHandler，converter 佈線照 `NewsletterSubmissionControllerTest`）

**Interfaces:**
- Produces（Task 6 前端逐字依此）：
  - `POST /api/admin/coupons` body `{"courseName","pitch","courseUrl","couponCode","expiresAt","formKey","answerFilter"}` → 200 活動 JSON；courseUrl 非 https／必填缺 → 400（應用層先驗，DB CHECK 兜底）。
  - `GET /api/admin/coupons` → 活動陣列（依 createdAt 倒序）。
  - `POST /api/admin/coupons/{id}/preview-recipients` → `[{email,name,alreadySent}]`；活動不存在 404。
  - `POST /api/admin/coupons/{id}/send` body `{"emails":[...],"limit":100}` → `SendResult` JSON；400/404 透傳。
  - 全端點 `X-Admin-Key`＋`guard.verify(key)`。

- [ ] **Step 1: 失敗測試**（建立 200／url 非 https 400／清單 200／preview 404／send 200 與 400 透傳，各含 JSON 斷言）→ **Step 2: RED** → **Step 3: 實作**（controller 薄層全委派；建立時驗證集中在 service 或 controller 擇一並測試釘住）→ **Step 4: GREEN** → **Step 5: Commit** `feat(coupon): admin 優惠券 API`

---

### Task 6: admin.html「優惠券」分頁

**Files:**
- Modify: `src/main/resources/static/admin.html`

**Interfaces:**
- Consumes: Task 5 四端點（路徑與欄位逐字）；問卷下拉資料 `GET /api/admin/forms`（既有）；答案條件欄位選項 `GET /api/forms/{formKey}`（既有 schema 端點，取 select/multi_select 欄位與 options）。

實作要點（照 admin.html 既有慣例：tab 結構、`$` helper、`api()`、pill、`cell()` textContent helper）：

1. 新 tab「優惠券」：建立表單（課程名稱／推薦文案 textarea／課程連結／優惠碼／優惠期限 date）＋「儲存活動」。
2. 名單區：問卷下拉＋條件列（欄位下拉→值下拉，選填一組；對應 answerFilter `{fieldKey: value}`）→「查詢名單」→ 表格（checkbox 預設勾選、alreadySent 列灰顯且預設不勾、表頭全選/全不選、即時顯示勾選數）。
3. 寄送：`confirm()` 含活動名與人數 → send → 結果摘要顯示（sent/skipped/failed/remaining）。
4. 活動列表：狀態 pill（DRAFT/SENT 沿用既有 pill class）、sent_count、sent_at；「選取」載入該活動到名單區可補寄。

- [ ] **Step 1: 實作四區塊** → **Step 2: 手動驗證**：`JAVA_HOME=/d/java/jdk-21 mvn spring-boot:run`（postgres profile 照專案慣例——若本地啟動繁瑣，改在 Task 7 的 verify-admin 段落驗證，本步驟至少做語法確認＋畫面 DOM 靜態檢查）→ **Step 3: Commit** `feat(coupon): admin 優惠券分頁 UI`

---

### Task 7: 視覺腳本＋E2E＋全案驗證

**Files:**
- Create: `scripts/preview-coupon-mail.mjs`
- Modify: `scripts/verify-admin.mjs`

**Interfaces:**
- Consumes: Task 2 版型結構（腳本離線重現樣本 HTML，標頭註明需與 CouponMailRenderer 手動同步——同 preview-survey-card.mjs 慣例）；Task 5/6 端點與 UI。

- [ ] **Step 1: preview-coupon-mail.mjs**（照 `scripts/preview-survey-card.mjs` 模式）：樣本活動（含期限／無期限兩版）→ 桌機/手機截圖到 `target/coupon-preview/`；斷言優惠碼文字存在。
- [ ] **Step 2: verify-admin.mjs 新段落**：切到優惠券分頁→建立活動（unique 名稱）→選問卷（用 verify 段落早前建立的 verify-survey-* 問卷）→查詢名單→斷言表格出現與勾選數顯示→**停在寄送確認框前**（絕不實際寄送）。
- [ ] **Step 3: 全案驗證**：`docker start survey-test-db && JAVA_HOME=/d/java/jdk-21 mvn test` 全綠；跑 preview 腳本並目視截圖。
- [ ] **Step 4: Commit** `feat(coupon): 優惠券視覺驗證與 E2E 腳本`（merge/push/部署驗證由 controller 於最終審查後統一執行）

---

## Self-Review 記錄

- **Spec 覆蓋**：§4→Task 1；§5→Task 5；§6→Task 3,4；§7→Task 2；§8→Task 6；§9 錯誤表分散於 Task 4,5 測試；§10→各 task＋Task 7。無缺口。
- **型別一致性**：`Recipient(email,name,alreadySent)`（Task 3 定義、Task 5/6 消費）、`SendResult` 五欄（Task 4 定義、Task 5/6 消費）、email_log type 字面 `coupon:{id}`（Task 3/4 同字面）已核對。
- **既知風險**：`Filters` record 參數眾多且以現檔為準——Task 3 已明確指示開檔查證而非臆測；jsonb entity 寫法指示先找專案前例。
