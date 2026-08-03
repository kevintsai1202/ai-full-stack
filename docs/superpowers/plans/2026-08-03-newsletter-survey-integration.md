# 電子報問卷整合系統實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 自訂問卷可嵌入電子報成一鍵投票卡，接續頁收完整填答並發點，dashboard 看歸因分析。

**Architecture:** 沿用既有 `form_definition` schema 平台與 `__PROMO_RT__` 逐收件人替換機制；新增 `survey_vote` 投票表與通道感知提交路徑（跳過訂閱漏斗的 consent／legacy 副作用）；標記 `<!--survey:FORM_KEY-->` 於信件／讀者頁／預覽三通道分流展開。設計依據 `docs/superpowers/specs/2026-08-03-newsletter-survey-integration-design.md`。

**Tech Stack:** Spring Boot（JdbcTemplate＋JPA 併用，同既有 form／promo 套件）、Flyway、PostgreSQL、vanilla JS（admin.html／reader 模板）、Playwright 驗證腳本。

## Global Constraints

- 所有指令在 `survey-backend/` 下執行；mvn 必須 `JAVA_HOME=/d/java/jdk-21`（系統預設 JDK8 會編譯失敗，錯誤訊息會誤導）。
- 整合測試需本機 5433 PG：先 `docker start survey-test-db`。
- 所有程式碼中文註解（函式級必備）；訊息文案面向讀者可直接顯示。
- 工作分支 `agent/newsletter-survey-integration`（已存在，spec 在其上）；每個 Task 完成即 commit。
- 手動設定 `Content-Type` 回應 HTML 時必須 `new MediaType(TEXT_HTML, StandardCharsets.UTF_8)`（否則中文亂碼）。
- `templates/reader/*.html` 新模板必載 `/tracking.js` 與 `/r/reader-nav.js`（ReaderNavGuardTest 強制）。
- Flyway 版號：**V21**（現最大 V20）。
- 佔位符字面：收件人 token `__PROMO_RT__`（沿用）、campaign id `__SURVEY_CID__`（本案新增）。

---

### Task 1: V21 migration＋CreditTxn 擴充

**Files:**
- Create: `src/main/resources/db/migration/V21__newsletter_survey_integration.sql`
- Modify: `src/main/java/world/springai/survey/reader/CreditTxn.java`（加 `surveyFormKey` 欄位與 `REASON_SURVEY_REWARD` 常數，比照既有 `promoProposalId` 寫法）
- Modify: `src/main/java/world/springai/survey/reader/CreditTxnRepository.java`（加 `existsByReaderIdAndSurveyFormKeyAndReason`）
- Test: `src/test/java/world/springai/survey/promo/SurveyRewardConstraintTest.java`

**Interfaces:**
- Produces: `credit_txn.survey_form_key` 欄、`CreditTxn.REASON_SURVEY_REWARD = "SURVEY_REWARD"`、`CreditTxn.setSurveyFormKey(String)`、`CreditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(Long, String, String)`；`survey_vote` 表、`form_definition.email_vote_field_key` 欄。

- [ ] **Step 1: 撰寫 V21 migration**

```sql
-- V21__newsletter_survey_integration.sql
-- 電子報問卷整合：一鍵投票表、信中題指定欄、填答發點防重發。
-- 設計依據 docs/superpowers/specs/2026-08-03-newsletter-survey-integration-design.md §4

-- 信中一鍵投票（含讀者頁快投）
CREATE TABLE survey_vote (
    id            BIGSERIAL PRIMARY KEY,
    form_key      VARCHAR(100) NOT NULL,
    field_key     VARCHAR(100) NOT NULL,
    option_value  TEXT NOT NULL,
    campaign_id   BIGINT REFERENCES campaign(id),
    channel       VARCHAR(10) NOT NULL,
    identity_type VARCHAR(10) NOT NULL,
    identity_key  VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_survey_vote_channel CHECK (channel IN ('EMAIL', 'WEB')),
    CONSTRAINT ck_survey_vote_identity CHECK (identity_type IN ('RECIPIENT', 'READER', 'ANON'))
);
-- 具名一人一票（跨期同問卷仍一票，後投 upsert 覆蓋）；匿名不受限
CREATE UNIQUE INDEX uq_survey_vote_identity
    ON survey_vote (form_key, identity_type, identity_key)
    WHERE identity_type <> 'ANON';
CREATE INDEX idx_survey_vote_form ON survey_vote (form_key, campaign_id);

-- 信中一鍵題指定（必須是該版本的 select 單選欄位，應用層驗證）
ALTER TABLE form_definition ADD COLUMN email_vote_field_key TEXT;

-- 填答發點：每人每問卷一次（partial unique 防併發重發，比照 uq_credit_txn_promo_refund）
ALTER TABLE credit_txn ADD COLUMN survey_form_key VARCHAR(100);
CREATE UNIQUE INDEX uq_credit_txn_survey_reward
    ON credit_txn (reader_id, survey_form_key)
    WHERE reason = 'SURVEY_REWARD';
```

- [ ] **Step 2: CreditTxn 加欄位（比照 promoProposalId 既有寫法）**

在 `CreditTxn.java` 加：常數 `public static final String REASON_SURVEY_REWARD = "SURVEY_REWARD";`、欄位 `@Column(name = "survey_form_key") private String surveyFormKey;`、getter/setter（中文註解：發點對象問卷，防重發唯一鍵成分）。`CreditTxnRepository` 加 `boolean existsByReaderIdAndSurveyFormKeyAndReason(Long readerId, String surveyFormKey, String reason);`

- [ ] **Step 3: 寫併發兜底整合測試（先跑應 RED：表不存在）**

```java
package world.springai.survey.promo; // 與 PromoCreditTxnConstraintTest 同套件同基底

/** V21 發點唯一約束：同 reader 同問卷第二筆 SURVEY_REWARD 必須撞 UNIQUE */
class SurveyRewardConstraintTest extends /* 沿用 PromoCreditTxnConstraintTest 的基底與資料準備方式 */ {
    @Test
    void 同讀者同問卷第二筆SURVEY_REWARD撞唯一約束() {
        // 依 PromoCreditTxnConstraintTest 既有 helper 建 reader；再存兩筆同 (reader, formKey) 的 SURVEY_REWARD
        CreditTxn first = new CreditTxn(readerId, 20, CreditTxn.REASON_SURVEY_REWARD, null, "問卷獎勵", null);
        first.setSurveyFormKey("reader-poll");
        creditTxnRepository.saveAndFlush(first);
        CreditTxn dup = new CreditTxn(readerId, 20, CreditTxn.REASON_SURVEY_REWARD, null, "問卷獎勵", null);
        dup.setSurveyFormKey("reader-poll");
        assertThrows(DataIntegrityViolationException.class,
            () -> creditTxnRepository.saveAndFlush(dup));
    }
}
```

先開啟 `PromoCreditTxnConstraintTest.java` 照抄其基底類別、@SpringBootTest 屬性與 reader 建立 helper——那是 V19 同型測試，結構完全對應。

- [ ] **Step 4: 跑測試確認 GREEN**

Run: `docker start survey-test-db && JAVA_HOME=/d/java/jdk-21 mvn test -Dtest=SurveyRewardConstraintTest`
Expected: PASS（migration 套用＋約束生效）

- [ ] **Step 5: Commit** `feat(survey): V21 migration——survey_vote、信中題欄、發點防重發`

---

### Task 2: CreditPolicy.surveyReward()＋AppSetting key

**Files:**
- Modify: `src/main/java/world/springai/survey/AppSettingService.java`（加常數 `CREDIT_SURVEY_REWARD = "CREDIT_SURVEY_REWARD"`，比照 `CREDIT_REFERRAL_REWARD`）
- Modify: `src/main/java/world/springai/survey/reader/CreditPolicy.java`
- Test: `src/test/java/world/springai/survey/reader/CreditPolicyTest.java`（既有檔案加案例）

**Interfaces:**
- Produces: `CreditPolicy.surveyReward()` → `int`（AppSetting 未設時後備 20）。

- [ ] **Step 1: 加失敗測試**（照 `CreditPolicyTest` 既有 referralReward 案例格式）：未設定回 20、設定 `"50"` 回 50。
- [ ] **Step 2: RED 確認** Run: `JAVA_HOME=/d/java/jdk-21 mvn test -Dtest=CreditPolicyTest` → 編譯失敗（方法不存在）。
- [ ] **Step 3: 實作**

```java
/** 問卷完整填答獎勵（點）；未設定時後備 20 */
public int surveyReward() {
    return parseOrDefault(appSettingService.get(
        AppSettingService.CREDIT_SURVEY_REWARD, DEFAULT_SURVEY_REWARD));
}
```

常數 `static final String DEFAULT_SURVEY_REWARD = "20";`，`parseOrDefault` 沿用既有 private helper（開檔確認實際名稱，與 signupGrant() 同一支）。

- [ ] **Step 4: GREEN 確認**＋**Step 5: Commit** `feat(survey): CreditPolicy.surveyReward（AppSetting 可調、後備 20）`

---

### Task 3: SurveyVote entity＋repository

**Files:**
- Create: `src/main/java/world/springai/survey/form/SurveyVote.java`
- Create: `src/main/java/world/springai/survey/form/SurveyVoteRepository.java`
- Test: `src/test/java/world/springai/survey/form/SurveyVoteRepositoryTest.java`（@DataJpaTest 不可行——專案整合測試都走 @SpringBootTest＋5433 PG，照 SurveyRewardConstraintTest 模式）

**Interfaces:**
- Produces:

```java
public class SurveyVote {
    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_WEB = "WEB";
    public static final String IDENTITY_RECIPIENT = "RECIPIENT";
    public static final String IDENTITY_READER = "READER";
    public static final String IDENTITY_ANON = "ANON";
    /** 建構子；updatedAt/createdAt 由 @PrePersist 設（勿用 @CreationTimestamp，見知識庫教訓） */
    public SurveyVote(String formKey, String fieldKey, String optionValue,
                      Long campaignId, String channel, String identityType, String identityKey)
    // getter 全套＋setOptionValue/setCampaignId/setChannel/setUpdatedAt（upsert 改票用）
}
public interface SurveyVoteRepository extends JpaRepository<SurveyVote, Long> {
    Optional<SurveyVote> findByFormKeyAndIdentityTypeAndIdentityKey(
        String formKey, String identityType, String identityKey);
}
```

- [ ] **Step 1: 失敗測試**：存一筆 RECIPIENT 票→`findByFormKeyAndIdentityTypeAndIdentityKey` 找得到；存兩筆同身分票 `saveAndFlush` 第二筆撞 `DataIntegrityViolationException`；ANON（identityKey null）兩筆都存成功。
- [ ] **Step 2: RED**（entity 不存在編譯失敗）→ **Step 3: 實作** entity（`@Entity @Table(name="survey_vote")`，`@PrePersist` 設 created/updated）→ **Step 4: GREEN** → **Step 5: Commit** `feat(survey): SurveyVote 實體與唯一身分約束`

---

### Task 4: 建立新問卷（FormSchemaService.createForm＋端點）

**Files:**
- Modify: `src/main/java/world/springai/survey/form/FormSchemaService.java`
- Modify: `src/main/java/world/springai/survey/form/FormSchemaController.java`
- Test: `src/test/java/world/springai/survey/form/FormSchemaServiceCreateFormTest.java`

**Interfaces:**
- Produces: `FormSchemaService.createForm(String formKey, String title)` → `FormDefinition`（v1 DRAFT 空欄位）；`POST /api/admin/forms` body `{"formKey":"reader-poll","title":"讀者意見調查"}` → 200；格式不符 400、重複 409。

- [ ] **Step 1: 失敗測試**（mock JdbcTemplate 不可行——FormSchemaService 全靠 SQL，走 5433 PG 整合測試，照 Task 1 模式）：

```java
@Test void 建立新問卷_v1_DRAFT() {
    FormSchemaService.FormDefinition form = service.createForm("reader-poll", "讀者意見調查");
    assertEquals(1, form.version());
    assertEquals("DRAFT", form.status());
}
@Test void formKey格式不符拒絕() {
    assertThrows(ResponseStatusException.class, () -> service.createForm("Bad_Key!", "x")); // 400
}
@Test void formKey重複拒絕() {
    service.createForm("dup-poll", "一");
    ResponseStatusException e = assertThrows(ResponseStatusException.class,
        () -> service.createForm("dup-poll", "二"));
    assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
}
```

- [ ] **Step 2: RED** → **Step 3: 實作**

```java
/** 建立全新問卷：v1 DRAFT 空殼；formKey 限 [a-z0-9-]{3,50} 且不可重複 */
@Transactional
public FormDefinition createForm(String formKey, String title) {
    if (formKey == null || !formKey.matches("[a-z0-9-]{3,50}")) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "表單代號限小寫英數與連字號（3–50 字）");
    }
    if (!StringUtils.hasText(title)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "表單標題為必填");
    }
    Integer exists = jdbc.queryForObject(
        "SELECT count(*) FROM form_definition WHERE form_key = ?", Integer.class, formKey);
    if (exists != null && exists > 0) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "表單代號已存在");
    }
    jdbc.update("""
        INSERT INTO form_definition (form_key, version, title, status, public_analytics_enabled)
        VALUES (?, 1, ?, 'DRAFT', FALSE)
        """, formKey, title.trim());
    return getDefinition(formKey, 1);
}
```

Controller 加（AdminKeyGuard 保護方式照同檔既有 admin 端點）：

```java
/** 建立全新問卷（v1 草稿空殼）；欄位之後用既有欄位編輯端點補 */
public record CreateFormRequest(String formKey, String title) {}
@PostMapping("/api/admin/forms")
public FormSchemaService.FormDefinition createForm(@RequestBody CreateFormRequest request) {
    return service.createForm(request.formKey(), request.title());
}
```

- [ ] **Step 4: GREEN** → **Step 5: Commit** `feat(survey): 建立全新問卷 API（POST /api/admin/forms）`

---

### Task 5: 信中題指定（email_vote_field_key 讀寫＋可嵌入清單）

**Files:**
- Modify: `src/main/java/world/springai/survey/form/FormSchemaService.java`（`FormDefinition` record 加 `emailVoteFieldKey` 欄——同步修 `definition()` 對映與所有 SELECT 語句補選 `email_vote_field_key`）
- Modify: `src/main/java/world/springai/survey/form/FormSchemaController.java`
- Test: `src/test/java/world/springai/survey/form/EmailVoteQuestionTest.java`

**Interfaces:**
- Produces:

```java
/** 信中一鍵題完整描述；options 為選項文字（依序，optionIndex 對映） */
public record EmailVoteQuestion(String formKey, String title, String fieldKey,
                                String label, List<String> options) {}
public Optional<EmailVoteQuestion> emailVoteQuestion(String formKey) // 最新已發布版；未設/非select/欄位不存在→empty
public void updateEmailVoteField(String formKey, int version, String fieldKey) // null=清除；非該版select欄位→400
public List<EmailVoteQuestion> listEmbeddable() // 全部已發布＋已設信中題（供編輯器插入選單）
```

端點：`PUT /api/admin/forms/{formKey}/versions/{version}/email-vote-field` body `{"fieldKey":"rating"}`；`GET /api/admin/forms/embeddable`。

- [ ] **Step 1: 失敗測試**：建問卷＋加 select 欄（用既有 upsertField 端點對應的 service 方法）＋發布→`emailVoteQuestion` 回 fieldKey 與 options；未發布→empty；指定 long_text 欄→400；`listEmbeddable` 只含已設信中題的問卷。
- [ ] **Step 2: RED** → **Step 3: 實作**（updateEmailVoteField 先查該版欄位 `SELECT field_type FROM form_field ff JOIN form_definition fd ON ...`，非 `select` 拋 400；emailVoteQuestion 以 `getDefinition(formKey, null)` 取已發布版、404 轉 empty 捕捉 `ResponseStatusException`）→ **Step 4: GREEN** → **Step 5: Commit** `feat(survey): 信中一鍵題指定與可嵌入問卷清單`

---

### Task 6: SurveyVoteService（驗證＋歸戶＋upsert）

**Files:**
- Create: `src/main/java/world/springai/survey/form/SurveyVoteService.java`
- Test: `src/test/java/world/springai/survey/form/SurveyVoteServiceTest.java`（純 Mockito 單元測試，照 PromoClickServiceTest 模式）

**Interfaces:**
- Consumes: `FormSchemaService.emailVoteQuestion`、`PromoRecipientTokenService.verify(String)`→`Optional<String>`、`ReaderSessionService.readReaderId(String, OffsetDateTime)`→`Optional<Long>`、`CampaignRepository.existsById`、`SurveyVoteRepository`（Task 3）。
- Produces:

```java
@Service
public class SurveyVoteService {
    /** 驗證＋落票；empty＝目標不合法（controller 轉 404），present＝接續頁 redirect 路徑 */
    public Optional<String> vote(String formKey, String fieldKey, int optionIndex,
                                 Long campaignId, String rt, String sessionCookie)
}
```

- [ ] **Step 1: 失敗測試**（核心案例，全 mock）：

```java
@Test void 合法投票_RECIPIENT歸戶_upsert改票() {
    givenQuestion("reader-poll", "rating", List.of("很有幫助", "普通", "沒幫助"));
    when(tokenService.verify("tok")).thenReturn(Optional.of("a@b.c"));
    when(campaignRepository.existsById(9L)).thenReturn(true);
    // 第一次：insert
    assertTrue(service.vote("reader-poll", "rating", 0, 9L, "tok", null).isPresent());
    verify(voteRepository).save(argThat(v -> "很有幫助".equals(v.getOptionValue())
        && SurveyVote.IDENTITY_RECIPIENT.equals(v.getIdentityType())));
    // 第二次同身分：改票（先查到既有列→setOptionValue 再 save）
    SurveyVote existing = new SurveyVote("reader-poll", "rating", "很有幫助", 9L,
        SurveyVote.CHANNEL_EMAIL, SurveyVote.IDENTITY_RECIPIENT, "a@b.c");
    when(voteRepository.findByFormKeyAndIdentityTypeAndIdentityKey(
        "reader-poll", SurveyVote.IDENTITY_RECIPIENT, "a@b.c")).thenReturn(Optional.of(existing));
    service.vote("reader-poll", "rating", 2, 9L, "tok", null);
    assertEquals("沒幫助", existing.getOptionValue());
}
@Test void 問卷未發布或欄位不符回empty不落票() {
    when(formSchemaService.emailVoteQuestion("ghost")).thenReturn(Optional.empty());
    assertTrue(service.vote("ghost", "rating", 0, null, null, null).isEmpty());
    verify(voteRepository, never()).save(any());
}
@Test void optionIndex超界回empty() { /* givenQuestion 3 選項，index 3 → empty */ }
@Test void campaign不存在照轉址但不落票() {
    givenQuestion(...); when(campaignRepository.existsById(0L)).thenReturn(false);
    assertTrue(service.vote("reader-poll", "rating", 1, 0L, null, null).isPresent()); // 轉址照給
    verify(voteRepository, never()).save(any()); // 涵蓋測試信 c=0
}
@Test void 匿名insert不查重() { /* 無 rt 無 session → IDENTITY_ANON、identityKey null、直接 save */ }
@Test void 落票DB失敗不擋轉址() { when(voteRepository.save(any())).thenThrow(new RuntimeException("db down")); assertTrue(service.vote(...).isPresent()); }
```

- [ ] **Step 2: RED** → **Step 3: 實作**要點：

```java
public Optional<String> vote(String formKey, String fieldKey, int optionIndex,
                             Long campaignId, String rt, String sessionCookie) {
    Optional<EmailVoteQuestion> q = formSchemaService.emailVoteQuestion(formKey);
    if (q.isEmpty() || !q.get().fieldKey().equals(fieldKey)
        || optionIndex < 0 || optionIndex >= q.get().options().size()) {
        return Optional.empty(); // controller 轉 404，不洩漏 schema
    }
    String redirect = "/r/survey/" + formKey + "?voted=" + optionIndex
        + (campaignId != null ? "&c=" + campaignId : "")
        + (rt != null && !rt.isBlank() ? "&rt=" + rt : "");
    // c 參數存在但 campaign 不存在（含測試信 c=0）→ 照轉址不落票
    if (campaignId != null && !campaignRepository.existsById(campaignId)) {
        return Optional.of(redirect);
    }
    try {
        recordVote(q.get(), optionIndex, campaignId, rt, sessionCookie);
    } catch (RuntimeException e) {
        log.warn("問卷投票記錄失敗 form={}，轉址照常", formKey, e); // best-effort 同 promo 哲學
    }
    return Optional.of(redirect);
}
```

`recordVote`：身分優先序 rt→RECIPIENT(email)／session→READER(String.valueOf(readerId))／ANON；channel＝rt 有值取 EMAIL、否則 WEB；具名先 `findByFormKeyAndIdentityTypeAndIdentityKey` 有列改值（setOptionValue/setCampaignId/setChannel/setUpdatedAt(now)）沒列新建；ANON 一律新建。

- [ ] **Step 4: GREEN** → **Step 5: Commit** `feat(survey): SurveyVoteService 驗證、歸戶與一人一票 upsert`

---

### Task 7: 投票端點＋ReaderEntryHostFilter 放行

**Files:**
- Create: `src/main/java/world/springai/survey/form/SurveyVoteController.java`
- Modify: `src/main/java/world/springai/survey/reader/ReaderEntryHostFilter.java`（放行 `GET /s/v/` 與 `GET /r/survey/`，比照 `/promo/c/` 區塊）
- Test: `src/test/java/world/springai/survey/form/SurveyVoteControllerTest.java`、`src/test/java/world/springai/survey/reader/ReaderEntryHostFilterTest.java`（既有檔案加兩個放行案例）

**Interfaces:**
- Produces: `GET /s/v/{formKey}?f=&o=&c=&rt=` → 302 到 service 回傳路徑；empty → 404。

- [ ] **Step 1: 失敗測試**（standalone MockMvc 照 PromoClickControllerTest 模式）：合法→302＋Location 含 `/r/survey/reader-poll?voted=1`；service 回 empty→404；`o` 非數字→404（`@RequestParam int` 綁定失敗要接 handler——直接宣告 `String o` 自行 parse，parse 失敗回 404，避免 500）。ReaderEntryHostFilterTest 加：讀者網域 `GET /s/v/1` 與 `GET /r/survey/x` 放行。
- [ ] **Step 2: RED** → **Step 3: 實作**

```java
/** 一鍵投票：目標合法即落票（best-effort）並 302 到接續頁；不合法一律 404 */
@RestController
public class SurveyVoteController {
    @GetMapping("/s/v/{formKey}")
    public ResponseEntity<Void> vote(@PathVariable String formKey,
            @RequestParam(value = "f", required = false) String fieldKey,
            @RequestParam(value = "o", required = false) String optionIndex,
            @RequestParam(value = "c", required = false) Long campaignId,
            @RequestParam(value = "rt", required = false) String rt,
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        int index;
        try { index = Integer.parseInt(optionIndex); }
        catch (NumberFormatException | NullPointerException e) {
            return ResponseEntity.notFound().build();
        }
        return voteService.vote(formKey, fieldKey, index, campaignId, rt, sessionCookie)
            .map(path -> ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, path).<Void>build())
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
```

- [ ] **Step 4: GREEN**（含 ReaderEntryHostFilterTest 全綠）→ **Step 5: Commit** `feat(survey): 一鍵投票端點 /s/v/ 與讀者網域放行`

---

### Task 8: SurveyBlockRenderer（三通道標記展開）

**Files:**
- Create: `src/main/java/world/springai/survey/newsletter/SurveyBlockRenderer.java`
- Test: `src/test/java/world/springai/survey/newsletter/SurveyBlockRendererTest.java`

**Interfaces:**
- Consumes: `FormSchemaService.emailVoteQuestion`／`listEmbeddable`。
- Produces:

```java
@Service
public class SurveyBlockRenderer {
    public static final String CID_PLACEHOLDER = "__SURVEY_CID__";
    /** 標記樣式：<!--survey:FORM_KEY-->，比對規則同 promo 成對標記的字串層級處理 */
    static final Pattern MARKER = Pattern.compile("<!--survey:([a-z0-9-]+)-->");
    /** 信件通道：選項按鈕連結 {readerBase}/s/v/{key}?f=&o=&c=__SURVEY_CID__&rt=__PROMO_RT__ */
    public String expandForEmail(String html, String readerBaseUrl)
    /** 讀者頁通道：選項按鈕連結 /s/v/...?c={campaignId}（無 rt，session 歸戶）＋「繼續填完整問卷」連結 */
    public String expandForWeb(String html, Long campaignId)
    /** 預覽通道：卡片視覺樣＋「預覽不計票」標示，連結一律 href="#" */
    public String expandForPreview(String html)
    /** 寄送前驗證：內文含標記但問卷不可嵌入（未發布/未設信中題）→ 拋 IllegalArgumentException 擋寄送 */
    public void assertEmbeddable(String markdown)
}
```

- [ ] **Step 1: 失敗測試**：email 展開含 `__PROMO_RT__` 與 `__SURVEY_CID__` 與三選項連結（`o=0..2`）且 HTML 註解不殘留；問卷不可嵌入時標記保留原樣（無害註解，安全降級同 promo 不成對原則）；web 展開連結含 `c=9` 無 rt；preview 展開含「預覽不計票」且無 `/s/v/` 真連結；`assertEmbeddable` 對壞標記拋例外、對無標記內文靜默通過；選項文字含 `<` 要跳脫（HtmlTemplate.escapeHtml）。
- [ ] **Step 2: RED** → **Step 3: 實作**：卡片用 email-safe 單格 table＋inline style（配色沿用 reader 主題但底色改 `#eef3fb`／左條 `#1d4ed8` 與工商卡區隔）；選項按鈕逐一 `<a>` inline-block。展開時以 `MARKER.matcher` 逐一替換，`emailVoteQuestion` empty 就跳過該標記。
- [ ] **Step 4: GREEN** → **Step 5: Commit** `feat(survey): 問卷標記三通道展開器`

---

### Task 9: 渲染接線（寄送／讀者頁／預覽＋CID 替換）

**Files:**
- Modify: `src/main/java/world/springai/survey/newsletter/CampaignService.java`
- Modify: `src/main/java/world/springai/survey/newsletter/CampaignDeliveryService.java`
- Modify: `src/main/java/world/springai/survey/reader/ReaderPageController.java`
- Test: `src/test/java/world/springai/survey/newsletter/CampaignSurveyWiringTest.java`

**Interfaces:**
- Consumes: Task 8 全部方法。
- 接線點（實作前先開檔對行號，以下為 2026-08-03 主幹位置）：
  1. `CampaignService.mailBodyHtml(markdown, slug)`（約 L296 呼叫處的實作內）：`toHtml` 之後補 `surveyBlockRenderer.expandForEmail(html, readerBaseUrl)`；readerBaseUrl 取得方式照 `PromoPlacementService` 的讀者網域解析（同一後備邏輯）。
  2. `CampaignService.send()`／`schedule()`：建立 Campaign 取得 id 後、`renderFor` 逐收件人之前，`bodyHtml = bodyHtml.replace(SurveyBlockRenderer.CID_PLACEHOLDER, String.valueOf(campaignId))`。**寄送前驗證**：`assertEmbeddable(markdown)` 放在建 Campaign 之前（比照 promo `assertCommittable` 預檢位置）。
  3. 測試信路徑（約 L176 `renderFor(body, to, null, count)`）：替換 `CID_PLACEHOLDER` 為 `"0"`（c=0 → Task 6 已保證不落票）。
  4. `CampaignDeliveryService`（約 L406 逐收件人替換處）：batch 讀存檔 bodyHtml，若含 CID 佔位符（重寄舊排程相容）同樣以 `campaign.getId()` 替換。
  5. `ReaderPageController`（L178-180 `toHtml` 之後）：`contentHtml = surveyBlockRenderer.expandForWeb(contentHtml, campaign.getId())`（該 controller 內已有 campaign 實體，開檔確認變數名）。
  6. Admin 預覽路徑（`articlePreview` 或 doPreview 對應端點，開檔確認）：`expandForPreview`。

- [ ] **Step 1: 失敗測試**：`mailBodyHtml` 產物含投票卡與兩個佔位符；send 流程後最終信體不含 `__SURVEY_CID__`（被 campaignId 取代）；測試信信體含 `c=0`；壞標記擋 send 並回明確錯誤。測試組裝照 `CampaignService` 既有單元測試（開 `src/test/java/world/springai/survey/newsletter/` 下 CampaignService 相關測試檔照抄 mock 佈線）。
- [ ] **Step 2: RED** → **Step 3: 實作接線** → **Step 4: GREEN＋全套回歸** `JAVA_HOME=/d/java/jdk-21 mvn test` → **Step 5: Commit** `feat(survey): 問卷卡寄送／讀者頁／預覽接線與 CID 替換`

---

### Task 10: NewsletterSubmissionService（通道感知提交＋發點）

**Files:**
- Create: `src/main/java/world/springai/survey/form/NewsletterSubmissionService.java`
- Modify: `src/main/java/world/springai/survey/form/FormSchemaService.java`（把 private `validateAnswers` 提為 package-private，供本服務重用；不動其邏輯）
- Test: `src/test/java/world/springai/survey/form/NewsletterSubmissionServiceTest.java`

**Interfaces:**
- Consumes: `AudiencePlatformService.mergePerson/upsertIdentity/upsertRecord/replaceFacts`（簽名見 `FormSchemaService.submit()` 現行呼叫）、`PromoRecipientTokenService.verify`、`ReaderSessionService.readReaderId`、`ReaderRepository.findByEmail`（開檔確認方法名，登入流程已有 email 查 reader）、`ReaderRepository.addCredits`、`CreditTxnRepository`（Task 1）、`CreditPolicy.surveyReward()`（Task 2）。
- Produces:

```java
@Service
public class NewsletterSubmissionService {
    public record SubmitRequest(Map<String, Object> answers, Long campaignId, String rt) {}
    public record SubmitResult(String submissionId, boolean rewarded, int rewardCredits, String rewardHint) {}
    /** 身分解析失敗拋 401 ResponseStatusException；其餘驗證失敗 400 */
    @Transactional
    public SubmitResult submit(String formKey, SubmitRequest request, String sessionCookie)
}
```

- [ ] **Step 1: 失敗測試**：
  - rt 歸戶提交→`upsertRecord` 以 source `newsletter_survey` 呼叫、raw 含 campaignId／channel；**絕不**呼叫 `appendConsent` 與 legacy repository（`verifyNoInteractions`——這條測試是 §3.2 衝突解法的守衛）。
  - email 對應 reader 且首次→`addCredits(readerId, 20)`＋credit_txn 寫 `SURVEY_REWARD`（surveyFormKey=formKey）；`existsBy...` 回 true 時不重發、`rewarded=false`。
  - email 非 reader→照收答案、`rewarded=false`、`rewardHint` 含「訂閱」。
  - 無 rt 無 session→401。
- [ ] **Step 2: RED** → **Step 3: 實作**：身分解析（rt→email；session→readerId→reader.getEmail()）；`formSchemaService.getDefinition(formKey, null)`＋`validateAnswers`；audience 寫入鏈比照 `submit()` 但**跳過** consent／legacy；發點：`readerRepository.findByEmail(email)` 有 reader 且 `!existsByReaderIdAndSurveyFormKeyAndReason(...)` → `addCredits` 條件式 UPDATE＋帳本（同一交易，失敗自然回滾）。
- [ ] **Step 4: GREEN** → **Step 5: Commit** `feat(survey): 電子報通道提交（跳過訂閱副作用）＋完填發點冪等`

---

### Task 11: 提交端點

**Files:**
- Modify: `src/main/java/world/springai/survey/form/FormSchemaController.java`
- Test: `src/test/java/world/springai/survey/form/NewsletterSubmissionControllerTest.java`

**Interfaces:**
- Produces: `POST /api/forms/{formKey}/newsletter-submissions` body `{"answers":{...},"campaignId":9,"rt":"..."}`（rt 可省、改用 session cookie）→ 200 `SubmitResult` JSON；401／400 轉譯照 `PromoPortalController.apply` 的例外轉譯模式。

- [ ] **Step 1: 失敗測試**（standalone MockMvc＋mock service）：200 帶 `rewarded`；service 拋 401→401 JSON；拋 400→400 訊息透傳。
- [ ] **Step 2: RED** → **Step 3: 實作**（controller 方法五行內，全部委派）→ **Step 4: GREEN** → **Step 5: Commit** `feat(survey): newsletter-submissions 端點`

---

### Task 12: 接續填答頁 /r/survey/{formKey}

**Files:**
- Create: `src/main/resources/templates/reader/survey.html`
- Create: `src/main/java/world/springai/survey/form/SurveyPortalController.java`
- Modify: `src/test/java/world/springai/survey/ReaderNavGuardTest.java`（`STATIC_NAV_TEMPLATES` 加 `survey.html`，javadoc 補理由：常由信件進入、非登入態）
- Test: `src/test/java/world/springai/survey/form/SurveyPortalControllerTest.java`

**Interfaces:**
- Produces: `GET /r/survey/{formKey}?voted=&c=&rt=` → 200 HTML。模板佔位符：`<!--FORM_TITLE-->`、`<!--FORM_KEY-->`、`<!--VOTED_BANNER-->`（有 voted 才有內容）、`<!--IDENTITY_BLOCK-->`（具名：「以 ○○○ 身分作答」＋「不是你？」連 `/r/login?redirect=/r/survey/{formKey}`；匿名：登入引導＋唯讀提示）、`<!--FIELDS_JSON-->`（schema JSON 嵌入，前端動態長表單）、`<!--REWARD_CREDITS-->`。

- [ ] **Step 1: 失敗測試**：已發布問卷 200＋含表單標題與 schema JSON；rt 有效→頁面含遮罩後身分（顯示 email 遮罩格式 `a***@b.c`，遮罩函式寫在 controller、測試斷言不含完整 email——信轉寄防洩漏）；未歸戶→含「登入作答」與 disabled 表單標記；未發布問卷→404；`Cache-Control: private, no-store`（含身分資訊）。
- [ ] **Step 2: RED** → **Step 3: 實作**：controller 照 `PromoClickController.respond` 的 HtmlTemplate 渲染模式（UTF-8 charset 鐵律）；模板骨架抄 `promo-contact.html`（靜態導覽終點頁）＋前端 JS：讀 `FIELDS_JSON` 動態長欄位（select→radio 群、multi_select→checkbox 群、long_text→textarea、short_text/email→input；required 標示）、`voted` 預選信中題、submit fetch `POST /api/forms/{key}/newsletter-submissions`（帶 rt 與 campaignId query 原值）、成功顯示「已收到＋獲得 N 點」或 rewardHint。
- [ ] **Step 4: GREEN**（含 ReaderNavGuardTest）→ **Step 5: Commit** `feat(survey): 接續填答頁與 schema 動態表單`

---

### Task 13: 投票統計 API＋analytics campaign 篩選

**Files:**
- Create: `src/main/java/world/springai/survey/form/SurveyVoteStatsService.java`
- Modify: `src/main/java/world/springai/survey/form/FormSchemaController.java`（加 votes 端點）
- Modify: `src/main/java/world/springai/survey/form/FormSchemaService.java`（`analytics(...)` 加 `Long campaignId` 參數：非 null 時 record 過濾 raw->>'campaignId'；既有呼叫端傳 null）
- Test: `src/test/java/world/springai/survey/form/SurveyVoteStatsServiceTest.java`

**Interfaces:**
- Produces: `GET /api/admin/analytics/forms/{formKey}/votes` →

```json
{"options":[{"value":"很有幫助","named":12,"anon":3}],
 "byCampaign":[{"campaignId":9,"votes":15,"submissions":6,"conversionRate":0.4}],
 "totalVotes":15,"totalNamed":12}
```

`/api/admin/analytics/forms/{formKey}` 加選用參數 `campaignId`。

- [ ] **Step 1: 失敗測試**（5433 PG 整合測試：塞 survey_vote 樣本列驗證聚合；conversionRate＝該 campaign 完整填答數÷票數，填答數查 audience_record raw->>'campaignId'）。
- [ ] **Step 2: RED** → **Step 3: 實作**（JdbcTemplate GROUP BY 聚合；空資料回零值結構不回 404）→ **Step 4: GREEN** → **Step 5: Commit** `feat(survey): 投票統計與 campaign 歸因分析 API`

---

### Task 14: admin.html＋E2E 腳本＋全案驗證

**Files:**
- Modify: `src/main/resources/static/admin.html`
- Create: `scripts/preview-survey-card.mjs`
- Modify: `scripts/verify-admin.mjs`（新增問卷段落）
- Test: 視覺腳本本身＋全套 mvn test

**admin.html 四處（照既有 vanilla JS 慣例，`$` helper 與 `api()`）：**

1. 「彈性表單分析」工具列加「建立新問卷」按鈕→prompt 收 formKey/title→`POST /api/admin/forms`→重載表單下拉。
2. 表單欄位編輯區加「信中一鍵題」下拉（選項＝該版本 select 欄位＋「（未設定）」）→`PUT .../email-vote-field`。
3. 編輯器工具列加「插入問卷」按鈕（放工商按鈕旁）：`GET /api/admin/forms/embeddable` 長選單→選定後在游標處插入獨立一行 `<!--survey:FORM_KEY-->`（插入邏輯照工商按鈕 `applyMarkdownFormat` 的換行規則）。
4. 「彈性表單分析」加「信中投票」卡：`GET .../votes` → `renderBars` 選項票數＋named/anon 分層＋byCampaign 表格（票數/完填/轉換率）；期別篩選下拉接 analytics `campaignId` 參數。

**preview-survey-card.mjs**（照 `preview-promo-contact.mjs` 模式）：離線組 email 卡與接續頁樣本 HTML，桌機/手機截圖到 `target/survey-preview/`，並實測接續頁動態表單長出欄位（斷言 radio 群數量）。

**verify-admin.mjs 新段落**：建立問卷→加 select 欄→設信中題→發布→編輯器插入標記→預覽斷言投票卡出現且「預覽不計票」。

- [ ] **Step 1: 先跑 verify-admin.mjs 新段落確認 RED**（線上舊版無按鈕逾時）
- [ ] **Step 2: 實作四處 UI** → **Step 3: 跑 preview-survey-card.mjs 看截圖**（Read 工具目視兩視口）
- [ ] **Step 4: 全案驗證**：`docker start survey-test-db && JAVA_HOME=/d/java/jdk-21 mvn test`（全綠）→ merge main → push → GraphQL 查 deployment commitSHA RUNNING → 對線上重跑 `verify-admin.mjs`
- [ ] **Step 5: Commit** `feat(survey): admin 問卷管理／插入／投票統計 UI 與 E2E`

---

## Self-Review 記錄

- **Spec 覆蓋**：§4 資料模型→Task 1,3；§5 標記渲染→Task 8,9；§6 投票端點→Task 6,7；§7 接續頁與發點→Task 10,11,12；§8 admin 補強→Task 4,5,14；§9 dashboard→Task 13,14；§10 錯誤處理分散於各 task 測試；§11 測試策略→各 task＋Task 14 全案驗證。無缺口。
- **Spec 差異微調**（實作精化，非變更決策）：投票落票條件由「campaign 未發送不落票」精化為「campaign 不存在不落票＋測試信一律 `c=0`」——因 bodyHtml 生成早於 Campaign 建立，改用 `__SURVEY_CID__` 佔位符延後注入（見 Task 9）；SCHEDULED 補寄場景照常計票。
- **型別一致性**：`EmailVoteQuestion`（Task 5 定義，Task 6/8 消費）、`CID_PLACEHOLDER`（Task 8 定義，Task 9 消費）、`SubmitResult`（Task 10 定義，Task 11/12 消費）已核對。
