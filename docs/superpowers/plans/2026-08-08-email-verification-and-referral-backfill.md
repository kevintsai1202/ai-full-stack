# 信箱驗證 CTA 與推薦獎勵補發 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓「信箱確認」這一段流程真的存在 —— 歡迎信帶確認 CTA、既有訂閱者補寄確認信、歷史推薦獎勵補發、後台漏斗語意修正並新增訂閱者邀請成效統計。

**Architecture:** 四部分互不阻塞：A 改 `WelcomeMailService` 一個方法；B 把 `ReferralGrowthService.confirmAndReward` 的主體抽成私有 `settle(...)` 後多開一個特權入口 `backfillAndApprove(...)`，再加一支 admin 端點；C 擴充既有 `dashboard` 端點回應與 `admin.html`；D 新增 `newsletter.ReconfirmService` 並複用 `AdminCampaignController.clampLimit()` 的額度守門。

**Tech Stack:** Java 21 / Spring Boot / JPA + JdbcTemplate / PostgreSQL / JUnit 5 + Mockito + AssertJ / 原生 HTML+JS（`admin.html`，無框架無建置）/ Playwright（`scripts/verify-*.mjs`）

**Spec:** [2026-08-08-email-verification-and-referral-backfill-design.md](../specs/2026-08-08-email-verification-and-referral-backfill-design.md)

## Global Constraints

- **零 Flyway migration**：現行最高 `V26` 必須保持不動，本計畫不新增 migration 檔。
- **測試指令必須指定 JDK 21**：`JAVA_HOME=D:/java/jdk-21 mvn test`。系統預設 `java` 是 1.8，會編譯失敗且錯誤訊息會誤導成編碼／檔案損壞問題。
- **專案沒有 `mvnw`**，一律使用系統 `mvn`。
- **測試風格**：純 JUnit 5，直接 `new` 服務物件搭配 Mockito mock，**不啟動 Spring context**；斷言用 AssertJ `assertThat`（`ReferralGrowthServiceTest` 慣例）或 JUnit `assertEquals`（`WelcomeMailServiceTest` 慣例），沿用該檔案既有風格不要混用。
- **所有程式碼需中文註解**，函式層級註解為必要，重要變數與物件也要註解。
- **`admin.html` 禁用 `innerHTML`**：security hook 會攔。一律用 `document.createElement` + `textContent`，按鈕用 `onclick` 直接綁定（既有 `cell()` helper 可用）。
- **`confirmAndReward` 的對外行為不得改變**：`ReferralGrowthServiceTest` 與 `ReferralIdempotencyTest` 若變紅，代表抽取動作改壞了公開路徑 —— 修程式，不要改測試。
- **email 對外顯示一律遮罩**：用既有的 `ReferralGrowthService.maskEmail(String)`（`public static`）。
- **A 與 D 的信件文案都不得提及推薦獎勵**（spec D7）；D 的文案不得主動說明不點確認的後果，也不得暗示會被取消訂閱（spec D8）。

---

## File Structure

| 檔案 | 動作 | 責任 |
| --- | --- | --- |
| `survey-backend/src/main/java/world/springai/survey/audience/WelcomeMailService.java` | 修改 | 歡迎信組信與寄送；新增確認 CTA |
| `survey-backend/src/test/java/world/springai/survey/audience/WelcomeMailServiceTest.java` | 修改 | 斷言確認連結進入信件 HTML |
| `survey-backend/src/main/java/world/springai/survey/reader/ReferralGrowthService.java` | 修改 | 抽出 `settle(...)`，新增 `backfillAndApprove(...)` |
| `survey-backend/src/test/java/world/springai/survey/reader/ReferralGrowthServiceTest.java` | 修改 | 補發路徑的略過風控、時點、冪等測試 |
| `survey-backend/src/main/java/world/springai/survey/reader/AdminReferralGrowthController.java` | 修改 | 補發端點；dashboard 新增 `confirmedByLink` 與 `referrerStats` |
| `survey-backend/src/main/java/world/springai/survey/newsletter/ReconfirmService.java` | 新增 | 對既有未確認訂閱者批次補寄確認信 |
| `survey-backend/src/test/java/world/springai/survey/newsletter/ReconfirmServiceTest.java` | 新增 | 名單口徑、冪等、逐封容錯 |
| `survey-backend/src/main/java/world/springai/survey/newsletter/AdminCampaignController.java` | 修改 | 補寄端點 + 待補寄人數，複用 `clampLimit()` |
| `survey-backend/src/main/resources/static/admin.html` | 修改 | 漏斗標籤、新 KPI、邀請成效表、補寄按鈕 |
| `survey-backend/scripts/verify-growth-funnel.mjs` | 修改 | 更新標籤斷言，新增 KPI 與成效表斷言 |

---

## Task 1: 歡迎信加入信箱驗證 CTA

**Files:**

- Modify: `survey-backend/src/main/java/world/springai/survey/audience/WelcomeMailService.java:38-67`
- Test: `survey-backend/src/test/java/world/springai/survey/audience/WelcomeMailServiceTest.java`

**Interfaces:**

- Consumes: `SubscriptionLinkBuilder.confirmLink(String email)` → `String`（已存在，且 `WelcomeMailService` 已注入 `linkBuilder`，**不需要改建構子**）
- Produces: 無新公開 API。行為變更：`sendWelcome` 寄出的 HTML 內含 `confirmLink(email)` 的結果。

- [ ] **Step 1: 寫失敗測試**

在 `WelcomeMailServiceTest` 新增測試。注意既有測試的 `successLogsSent` 只 stub 了 `unsubscribeLink`，新增 CTA 後 `confirmLink` 未 stub 會回 `null` 而讓 href 變成字面 `"null"` —— 所以既有測試也要補一行 stub，否則它會通過但信件內容是壞的。

```java
    /**
     * 歡迎信必須含個人化確認連結。
     *
     * <p>沒有這個連結，讀者就沒有任何途徑觸發 /subscription/confirm，
     * 「信箱確認」漏斗與推薦獎勵發放都會恆為 0（spec §1.2 斷點 2）。
     * 這裡直接 capture 寄出的 HTML，不只驗證 EmailLog——後者發現不了缺連結。</p>
     */
    @Test
    void welcomeMailContainsConfirmLink() {
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("zsend-id-2");
        when(linkBuilder.unsubscribeLink("user@example.com")).thenReturn("UNSUB_LINK_MARKER");
        when(linkBuilder.confirmLink("user@example.com")).thenReturn("CONFIRM_LINK_MARKER");

        svc.sendWelcome("user@example.com");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertTrue(html.getValue().contains("CONFIRM_LINK_MARKER"), "歡迎信必須含確認連結");
        assertTrue(html.getValue().contains("UNSUB_LINK_MARKER"), "退訂連結不可因新增 CTA 而遺失");
    }
```

同時修改既有的 `successLogsSent`，在 `when(linkBuilder.unsubscribeLink(...))` 之後補一行：

```java
        when(linkBuilder.confirmLink("user@example.com")).thenReturn("CONFIRM_LINK_MARKER");
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `JAVA_HOME=D:/java/jdk-21 mvn test -pl . -Dtest=WelcomeMailServiceTest`

Expected: FAIL —— `welcomeMailContainsConfirmLink` 因 `歡迎信必須含確認連結` 斷言失敗（HTML 內沒有該字串）。

- [ ] **Step 3: 實作**

`WelcomeMailService.sendWelcome` 改為同時取兩個連結：

```java
    /** 寄一封歡迎信給填寫者；成功記 sent、失敗記 failed，皆不向外拋例外 */
    public void sendWelcome(String email) {
        try {
            String unsubscribeLink = linkBuilder.unsubscribeLink(email);
            // 確認連結：讓讀者能主動驗證信箱可達性，同時是推薦獎勵發放的唯一觸發點
            String confirmLink = linkBuilder.confirmLink(email);
            String html = buildHtml(unsubscribeLink, confirmLink);
            String id = mailSender.send(email, SUBJECT, html);
            saveLog(email, id, "sent", null);
        } catch (Exception e) {
            log.warn("歡迎信寄送失敗 to={}：{}", email, e.getMessage());
            saveLog(email, null, "failed", e.getMessage());
        }
    }
```

`buildHtml` 改為兩參數並加入 CTA 區塊。**文案刻意不提推薦獎勵**（spec D7）：歡迎信是全體收件，多數人沒有推薦人。

```java
    /** 組歡迎信 HTML：歡迎內文＋信箱確認 CTA，外框與退訂頁腳交給共用模板 */
    private String buildHtml(String unsubscribeLink, String confirmLink) {
        String body = """
            <h2>歡迎你！🎉</h2>
            <p>謝謝你填寫「AI 賦能全端開發」課程興趣調查。我們會在課程開放報名、釋出早鳥優惠時優先通知你。</p>
            <p>在那之前，你可以先看看課程網站，了解整個實戰學習路徑。</p>
            <div style="margin:28px 0;padding:20px;border:1px solid #dce5ee;border-radius:12px;background:#f7fafc;text-align:center">
              <p style="margin:0 0 14px;font-weight:700">順手確認一下你的信箱</p>
              <p style="margin:0 0 18px;color:#5c6b7d;font-size:.92rem">
                點一下按鈕，我們就能確認這個地址收得到信，之後的內容不會漏掉。
              </p>
              <a href="%s" style="display:inline-block;background:#0d9488;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:700">確認我的信箱</a>
            </div>
            """.formatted(confirmLink);
        return emailTemplate.wrap(body, unsubscribeLink);
    }
```

- [ ] **Step 4: 執行測試確認通過**

Run: `JAVA_HOME=D:/java/jdk-21 mvn test -pl . -Dtest=WelcomeMailServiceTest`

Expected: PASS（3 個測試全綠）

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/audience/WelcomeMailService.java survey-backend/src/test/java/world/springai/survey/audience/WelcomeMailServiceTest.java
git commit -m "feat(audience): 歡迎信加入信箱驗證 CTA"
```

---

## Task 2: `ReferralGrowthService` 抽出 `settle` 並新增補發入口

**Files:**

- Modify: `survey-backend/src/main/java/world/springai/survey/reader/ReferralGrowthService.java:57-97`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReferralGrowthServiceTest.java`

**Interfaces:**

- Consumes: 既有私有成員 `assessRisk(Long, OffsetDateTime)`、`campaignMultiplier(String, OffsetDateTime)`、`sourceSlugOf(SurveyResponse)`、`grantAll(ReferralConversion, Reader)`、`normalize(String)`、record `Risk(int score, String reasons, boolean reviewRequired)`
- Produces:
  - `public Outcome confirmAndReward(String inviteeEmail)` —— **簽章與行為完全不變**
  - `public Outcome backfillAndApprove(String inviteeEmail, OffsetDateTime occurredAt)` —— 新增，Task 3 呼叫
  - `private Outcome settle(String inviteeEmail, OffsetDateTime occurredAt, boolean withRiskCheck)`

- [ ] **Step 1: 寫失敗測試**

在 `ReferralGrowthServiceTest` 新增三個測試。`setUp()` 既有的 mock 已經足夠，不需要改動。

```java
    /**
     * 補發路徑必須略過速度規則直接核准（spec D4）。
     *
     * <p>為什麼這個測試不可省：補發是連續執行、confirmed_at 都落在同一瞬間，
     * 若照跑 assessRisk，任何帶 3 人以上的推薦人會全數落入 PENDING_REVIEW，
     * 等於補發完還要人工按 16 次核准。這裡把速度計數 stub 成必然觸發的值，
     * 斷言補發仍然直接發點。</p>
     */
    @Test
    void backfillSkipsRiskAndApprovesDirectly() {
        // 速度規則門檻是 3，這裡回 9 —— 若補發跑風控必然變 PENDING_REVIEW
        when(conversions.countByReferrerIdAndConfirmedAtAfter(anyLong(), any())).thenReturn(9L);
        OffsetDateTime submittedAt = OffsetDateTime.parse("2026-07-01T10:00:00Z");

        ReferralGrowthService.Outcome outcome =
            service.backfillAndApprove("invitee@example.com", submittedAt);

        assertThat(outcome).isEqualTo(ReferralGrowthService.Outcome.REWARDED);
        ArgumentCaptor<CreditTxn> txn = ArgumentCaptor.forClass(CreditTxn.class);
        verify(credits).saveAndFlush(txn.capture());
        assertThat(txn.getValue().getDelta()).isEqualTo(100);
    }

    /**
     * 補發的轉換時點必須是呼叫端傳入的歷史時間，不是 now()（spec D5）。
     *
     * <p>時點錯不只是資料難看：campaignMultiplier(sourceSlug, now) 用該時間查
     * 當時有效的活動倍率，用 now() 會把今天的倍率套到去年的轉換上，直接發錯點數。</p>
     */
    @Test
    void backfillUsesSuppliedOccurredAtAsConfirmedAt() {
        OffsetDateTime submittedAt = OffsetDateTime.parse("2026-07-01T10:00:00Z");

        service.backfillAndApprove("invitee@example.com", submittedAt);

        ArgumentCaptor<ReferralConversion> saved =
            ArgumentCaptor.forClass(ReferralConversion.class);
        verify(conversions, org.mockito.Mockito.atLeastOnce()).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues())
            .anyMatch(c -> submittedAt.equals(c.getConfirmedAt())
                && ReferralConversion.STATUS_APPROVED.equals(c.getStatus()));
    }

    /** 已經處理過的轉換重跑補發不得再發點（冪等）。 */
    @Test
    void backfillIsIdempotentForAlreadyConfirmedConversion() {
        ReferralConversion existing = new ReferralConversion(
            "invitee@example.com", 7L, "CODE1234", "ai-agent-guide");
        existing.confirm(ReferralConversion.STATUS_APPROVED, 0, "", 100, 1, 100, 20,
            OffsetDateTime.parse("2026-07-01T10:00:00Z"));
        when(conversions.findForUpdate("invitee@example.com")).thenReturn(Optional.of(existing));

        ReferralGrowthService.Outcome outcome = service.backfillAndApprove(
            "invitee@example.com", OffsetDateTime.parse("2026-07-01T10:00:00Z"));

        assertThat(outcome).isEqualTo(ReferralGrowthService.Outcome.ALREADY_PROCESSED);
        verify(credits, never()).saveAndFlush(any(CreditTxn.class));
    }
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `JAVA_HOME=D:/java/jdk-21 mvn test -pl . -Dtest=ReferralGrowthServiceTest`

Expected: 編譯失敗 —— `cannot find symbol: method backfillAndApprove(String,OffsetDateTime)`

- [ ] **Step 3: 實作**

把 `confirmAndReward` 現有主體（原 L59-97）整段搬進新的私有 `settle`，兩個公開方法各自委派。**注意參數命名為 `withRiskCheck` 而非 `assessRisk`** —— 後者會與同名私有方法在閱讀上混淆（雖然 Java 的方法與變數命名空間不衝突、能編譯）。

```java
    /**
     * 確認訂閱後計算風險並發獎；同推薦人會鎖列，避免上限與里程碑競態。
     *
     * <p>公開端點（讀者點確認信）的唯一入口：一律跑風控、時點一律為現在。</p>
     */
    @Transactional
    public Outcome confirmAndReward(String inviteeEmail) {
        return settle(inviteeEmail, OffsetDateTime.now(ZoneOffset.UTC), true);
    }

    /**
     * 補發歷史推薦轉換：直接核准、略過風控，轉換時點由呼叫端指定。
     *
     * <p><b>這是特權路徑，唯一呼叫點必須在 AdminKeyGuard 之後</b>
     * （{@code AdminReferralGrowthController.backfill}）。刻意不做成
     * {@code confirmAndReward(email, skipRisk)} 的多載或參數——那會讓公開端點的
     * 呼叫鏈有機會傳錯一個布林值就整段繞過風控，而且錯誤在程式碼審查時
     * 幾乎看不出來。方法名本身標示它是特權入口，是這裡唯一的防線。</p>
     *
     * <p><b>為什麼 occurredAt 由呼叫端傳入而非用 now()</b>：
     * {@link #campaignMultiplier(String, OffsetDateTime)} 以該時間查當時有效的
     * 活動倍率。補發歷史轉換若傳 now()，會把今天的活動倍率套到過去的轉換上，
     * 發出的點數與當時的規則不符。呼叫端應傳該人最早一筆問卷的 created_at。</p>
     */
    @Transactional
    public Outcome backfillAndApprove(String inviteeEmail, OffsetDateTime occurredAt) {
        return settle(inviteeEmail, occurredAt, false);
    }

    /**
     * 歸因、建立轉換、計算獎勵與發放的共用主體。
     *
     * @param occurredAt    轉換時點；決定 confirmed_at 與活動倍率的查詢基準
     * @param withRiskCheck true 為公開路徑（跑每日上限與速度規則）；
     *                      false 為補發路徑（直接核准）
     */
    private Outcome settle(String inviteeEmail, OffsetDateTime occurredAt, boolean withRiskCheck) {
        String invitee = normalize(inviteeEmail);
        Optional<SurveyResponse> response = surveyResponses
            .findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(invitee);
        Optional<String> code = response.flatMap(ReferralService::referralCodeOf);
        if (code.isEmpty()) return Outcome.NO_REFERRER;
        Optional<Reader> found = readers.findByReferralCode(code.get());
        if (found.isEmpty()) return Outcome.NO_REFERRER;
        Reader referrer = readers.findByIdForUpdate(found.get().getId()).orElseThrow();
        if (normalize(referrer.getEmail()).equals(invitee)) return Outcome.SELF_INVITE;

        ReferralConversion conversion = conversions.findForUpdate(invitee)
            .orElseGet(() -> conversions.saveAndFlush(new ReferralConversion(
                invitee, referrer.getId(), code.get(), sourceSlugOf(response.orElse(null)))));
        if (conversion.getConfirmedAt() != null) {
            return ReferralConversion.STATUS_PENDING_REVIEW.equals(conversion.getStatus())
                ? Outcome.PENDING_REVIEW : Outcome.ALREADY_PROCESSED;
        }

        // 補發路徑不跑風控：零分、無理由、不需審核（spec D4）
        Risk risk = withRiskCheck ? assessRisk(referrer.getId(), occurredAt) : new Risk(0, "", false);
        int multiplier = campaignMultiplier(conversion.getSourceSlug(), occurredAt);
        int baseReward = policy.referralReward();
        int inviteeReward = policy.referralInviteeReward();
        int totalReward = Math.multiplyExact(baseReward, multiplier);
        String status = risk.reviewRequired()
            ? ReferralConversion.STATUS_PENDING_REVIEW : ReferralConversion.STATUS_APPROVED;
        conversion.confirm(status, risk.score(), risk.reasons(), baseReward,
            multiplier, totalReward, inviteeReward, occurredAt);
        conversions.saveAndFlush(conversion);

        if (risk.reviewRequired()) {
            log.warn("邀請轉人工審核：referrerId={} invitee={} reasons={}",
                referrer.getId(), maskEmail(invitee), risk.reasons());
            return Outcome.PENDING_REVIEW;
        }
        grantAll(conversion, referrer);
        return Outcome.REWARDED;
    }
```

- [ ] **Step 4: 執行測試確認通過**

Run: `JAVA_HOME=D:/java/jdk-21 mvn test -pl . -Dtest='ReferralGrowthServiceTest+ReferralIdempotencyTest+ReferralRewardListenerTest'`

Expected: PASS。**既有測試全綠是本任務的驗收重點** —— 它們證明抽取動作沒有改變公開路徑行為。任何一個變紅就是抽取出錯，修程式不要改測試。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/reader/ReferralGrowthService.java survey-backend/src/test/java/world/springai/survey/reader/ReferralGrowthServiceTest.java
git commit -m "feat(reader): 新增推薦獎勵補發入口 backfillAndApprove"
```

---

## Task 3: 補發端點 `POST /api/admin/referrals/backfill`

**Files:**

- Modify: `survey-backend/src/main/java/world/springai/survey/reader/AdminReferralGrowthController.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/AdminReferralBackfillTest.java`（新增）

**Interfaces:**

- Consumes: `ReferralGrowthService.backfillAndApprove(String, OffsetDateTime)`（Task 2）、`AdminKeyGuard.verify(String)`、`JdbcTemplate`
- Produces: `public Map<String, Object> backfill(String key, boolean dryRun)` 對應 `POST /api/admin/referrals/backfill?dryRun={true|false}`。回應鍵：`dryRun`、`scanned`、`rewarded`、`alreadyProcessed`、`selfInvite`、`noReferrer`、`failed`、`candidates`（僅 dryRun 時有值，元素為 `{email(遮罩), occurredAt}`）

- [ ] **Step 1: 寫失敗測試**

新增檔案 `AdminReferralBackfillTest.java`。用 mock 的 `JdbcTemplate` 回傳掃描結果，驗證編排邏輯（掃描 → 逐筆委派 → 計數彙總），不驗證 SQL 本身。

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import world.springai.survey.AdminKeyGuard;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 推薦獎勵補發端點：掃描口徑委派、Outcome 彙總與 dryRun 不寫入。 */
class AdminReferralBackfillTest {

    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-07-01T10:00:00Z");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-07-02T11:00:00Z");

    private AdminKeyGuard guard;
    private JdbcTemplate jdbc;
    private ReferralGrowthService growth;
    private AdminReferralGrowthController controller;

    /** 兩位候選人的掃描結果，供各案例共用。 */
    @BeforeEach
    void setUp() {
        guard = mock(AdminKeyGuard.class);
        jdbc = mock(JdbcTemplate.class);
        growth = mock(ReferralGrowthService.class);
        controller = new AdminReferralGrowthController(guard, jdbc,
            mock(ReferralConversionRepository.class),
            mock(ReferralCampaignRepository.class), growth);

        when(jdbc.queryForList(anyString())).thenReturn(List.of(
            Map.of("email", "a@example.com", "occurred_at", T1),
            Map.of("email", "b@example.com", "occurred_at", T2)));
    }

    /** 正式執行：逐筆帶各自的時點委派，並依 Outcome 彙總計數。 */
    @Test
    void backfillDelegatesPerCandidateAndAggregatesOutcomes() {
        when(growth.backfillAndApprove("a@example.com", T1))
            .thenReturn(ReferralGrowthService.Outcome.REWARDED);
        when(growth.backfillAndApprove("b@example.com", T2))
            .thenReturn(ReferralGrowthService.Outcome.ALREADY_PROCESSED);

        Map<String, Object> result = controller.backfill("key", false);

        assertThat(result.get("scanned")).isEqualTo(2);
        assertThat(result.get("rewarded")).isEqualTo(1);
        assertThat(result.get("alreadyProcessed")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(0);
        verify(growth).backfillAndApprove("a@example.com", T1);
        verify(growth).backfillAndApprove("b@example.com", T2);
    }

    /** 單筆拋例外不得中斷整批，計入 failed。 */
    @Test
    void backfillCountsFailureAndContinues() {
        when(growth.backfillAndApprove("a@example.com", T1))
            .thenThrow(new IllegalStateException("boom"));
        when(growth.backfillAndApprove("b@example.com", T2))
            .thenReturn(ReferralGrowthService.Outcome.REWARDED);

        Map<String, Object> result = controller.backfill("key", false);

        assertThat(result.get("failed")).isEqualTo(1);
        assertThat(result.get("rewarded")).isEqualTo(1);
    }

    /** dryRun 只回名單且 email 必須遮罩，絕不呼叫發獎。 */
    @Test
    void dryRunListsMaskedCandidatesWithoutGranting() {
        Map<String, Object> result = controller.backfill("key", true);

        assertThat(result.get("dryRun")).isEqualTo(true);
        assertThat(result.get("scanned")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates =
            (List<Map<String, Object>>) result.get("candidates");
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).get("email")).isEqualTo("a***@example.com");
        verify(growth, never()).backfillAndApprove(anyString(), any());
    }

    /**
     * 掃描 SQL 必須保留 consent／unsubscribed 兩道守門（spec §4.2）。
     *
     * <p>這兩個條件是「只對真正成立且未退訂的訂閱發獎」的唯一實作處。
     * 用 mock 的 JdbcTemplate 無法驗證 SQL 語意，但可以驗證條件字串沒被刪掉——
     * 少了它們，補發會對退訂者與未同意者發點，那是合規問題而非小 bug。</p>
     */
    @Test
    void scanSqlKeepsConsentAndUnsubscribedGuards() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        controller.backfill("key", true);

        verify(jdbc).queryForList(sql.capture());
        assertThat(sql.getValue()).contains("sr.consent = true");
        assertThat(sql.getValue()).contains("sr.unsubscribed = false");
        assertThat(sql.getValue()).contains("group by lower(sr.email)");
        assertThat(sql.getValue()).contains("min(sr.created_at)");
    }

    /** 金鑰必須先驗；守衛拋例外時不得掃描。 */
    @Test
    void guardRunsBeforeScanning() {
        org.mockito.Mockito.doThrow(new RuntimeException("401"))
            .when(guard).verify(eq("bad"));

        try {
            controller.backfill("bad", true);
        } catch (RuntimeException expected) {
            // 預期被守衛擋下
        }

        verify(jdbc, never()).queryForList(anyString());
    }
}
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `JAVA_HOME=D:/java/jdk-21 mvn test -pl . -Dtest=AdminReferralBackfillTest`

Expected: 編譯失敗 —— `cannot find symbol: method backfill(String,boolean)`

- [ ] **Step 3: 實作**

在 `AdminReferralGrowthController` 新增端點。掃描 SQL 依 spec §4.2：`consent = true and unsubscribed = false` 是守門，`group by lower(email)` 處理同一 email 多筆問卷，`min(created_at)` 作為轉換時點。

```java
    /** 補發掃描：帶推薦碼、已同意且未退訂者，取最早一筆問卷時間作為轉換時點。 */
    private static final String BACKFILL_SCAN_SQL = """
        select lower(sr.email) as email, min(sr.created_at) as occurred_at
          from survey_response sr
          join reader r on r.referral_code = (sr.answers ->> '_ref')
         where sr.answers ? '_ref'
           and sr.consent = true
           and sr.unsubscribed = false
         group by lower(sr.email)
         order by min(sr.created_at)
        """;

    /**
     * 補發歷史推薦獎勵：對「帶推薦碼且訂閱已成立」者建立轉換並直接核准發點。
     *
     * <p>為什麼需要這支端點：{@code confirmAndReward} 是唯一發獎入口，而它只由
     * 讀者點確認信觸發。在歡迎信加上確認 CTA 之前，沒有任何人收到過含確認連結的信，
     * 因此所有歷史推薦的獎勵都沒發出去（spec §1.3）。
     *
     * <p><b>逐筆容錯不中斷整批</b>：一位推薦人的資料異常不該讓其餘十幾筆都補不到。
     * 失敗計入 {@code failed} 並記 ERROR 供人工處理。
     *
     * <p><b>冪等</b>：完全依賴 {@code referral_conversion} 的 invitee 唯一鍵與
     * {@code uq_credit_txn_referral_note}。重跑會全部回 ALREADY_PROCESSED。
     *
     * @param dryRun true 時只回傳掃描名單（email 遮罩）與筆數，不發放任何點數
     */
    @PostMapping("/api/admin/referrals/backfill")
    public Map<String, Object> backfill(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        guard.verify(key);
        List<Map<String, Object>> candidates = jdbc.queryForList(BACKFILL_SCAN_SQL);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dryRun", dryRun);
        result.put("scanned", candidates.size());

        if (dryRun) {
            result.put("candidates", candidates.stream().map(row -> {
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("email", ReferralGrowthService.maskEmail((String) row.get("email")));
                view.put("occurredAt", row.get("occurred_at"));
                return view;
            }).toList());
            return result;
        }

        int rewarded = 0, alreadyProcessed = 0, selfInvite = 0, noReferrer = 0, failed = 0;
        for (Map<String, Object> row : candidates) {
            String email = (String) row.get("email");
            try {
                OffsetDateTime occurredAt = toOffsetDateTime(row.get("occurred_at"));
                ReferralGrowthService.Outcome outcome = growth.backfillAndApprove(email, occurredAt);
                switch (outcome) {
                    case REWARDED -> rewarded++;
                    case SELF_INVITE -> selfInvite++;
                    case NO_REFERRER -> noReferrer++;
                    // 補發不跑風控，PENDING_REVIEW 只可能來自先前已存在的待審轉換
                    case ALREADY_PROCESSED, PENDING_REVIEW -> alreadyProcessed++;
                }
            } catch (Exception e) {
                failed++;
                log.error("推薦獎勵補發失敗（其餘筆數繼續）：{}",
                    ReferralGrowthService.maskEmail(email), e);
            }
        }
        result.put("rewarded", rewarded);
        result.put("alreadyProcessed", alreadyProcessed);
        result.put("selfInvite", selfInvite);
        result.put("noReferrer", noReferrer);
        result.put("failed", failed);
        return result;
    }

    /**
     * 把 JDBC 回傳的時間值轉為 OffsetDateTime。
     *
     * <p>PostgreSQL 的 timestamptz 經 JdbcTemplate 可能回 OffsetDateTime 或
     * java.sql.Timestamp（依驅動版本與欄位推導而異），兩者都要能吃。</p>
     */
    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime odt) return odt;
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        throw new IllegalStateException("無法解析的時間型別：" + value);
    }
```

需要在檔案頂端補這些 import（`RequestParam` 與 logger 目前都還沒有）：

```java
import org.springframework.web.bind.annotation.RequestParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

並在類別內宣告 logger：

```java
    private static final Logger log = LoggerFactory.getLogger(AdminReferralGrowthController.class);
```

- [ ] **Step 4: 執行測試確認通過**

Run: `JAVA_HOME=D:/java/jdk-21 mvn test -pl . -Dtest=AdminReferralBackfillTest`

Expected: PASS（6 個測試）

測試檔需要 `import org.mockito.ArgumentCaptor;`。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/reader/AdminReferralGrowthController.java survey-backend/src/test/java/world/springai/survey/reader/AdminReferralBackfillTest.java
git commit -m "feat(reader): 新增推薦獎勵補發端點含 dryRun"
```

---

## Task 4: dashboard 新增 `confirmedByLink` 與 `referrerStats`

**Files:**

- Modify: `survey-backend/src/main/java/world/springai/survey/reader/AdminReferralGrowthController.java:43-93`

**Interfaces:**

- Consumes: `JdbcTemplate`
- Produces: `GET /api/admin/referrals/dashboard` 回應新增兩個頂層鍵：
  - `confirmedByLink` → `Long`（真實點過確認連結的人數）
  - `referrerStats` → `List<Map<String,Object>>`，每筆鍵為 `email`（已遮罩）、`clicks`、`submissions`、`conversions`、`rewarded`、`badges`

- [ ] **Step 1: 寫失敗測試**

在 `AdminReferralBackfillTest` 同檔新增 —— 這兩項與補發同屬 controller 的彙總職責，共用 mock 設定即可，不值得為此開新檔。

```java
    /**
     * dashboard 必須回傳真實信箱確認數（來自 audience_consent），
     * 與 referral_conversion 的「轉換成立」是兩個不同的指標（spec D1）。
     */
    @Test
    void dashboardExposesConfirmedByLinkAndReferrerStats() {
        // dashboard 內多支 count 查詢共用同一個 stub，回 0 即可；本測試只驗新欄位存在與接線
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
            Map.of("email", "a@example.com", "clicks", 42, "submissions", 6,
                   "conversions", 6, "rewarded", 600, "badges", 1)));

        Map<String, Object> result = controller.dashboard("key");

        assertThat(result).containsKey("confirmedByLink");
        assertThat(result).containsKey("referrerStats");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stats =
            (List<Map<String, Object>>) result.get("referrerStats");
        assertThat(stats.get(0).get("email")).isEqualTo("a***@example.com");
        assertThat(stats.get(0).get("clicks")).isEqualTo(42);
    }
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `JAVA_HOME=D:/java/jdk-21 mvn test -pl . -Dtest=AdminReferralBackfillTest#dashboardExposesConfirmedByLinkAndReferrerStats`

Expected: FAIL —— `Expecting map to contain key "confirmedByLink"`

- [ ] **Step 3: 實作**

在 `dashboard` 方法內，`result.put("funnel", ...)` 之後加入真實確認數：

```java
        // 真實信箱確認數：只有實際點過確認連結的人會留下 source_key='confirmation-link'。
        // 這與 funnel 的 confirmed（轉換成立）刻意分開——後者含補發，前者純粹是點擊行為。
        // channel 明確寫出：目前 EMAIL 是唯一管道，未來新增管道時這個數字不會無聲混入別的管道。
        result.put("confirmedByLink", count("""
            select count(distinct p.id)
              from audience_person p
              join audience_consent c on c.person_id = p.id
             where c.channel = 'EMAIL'
               and c.status = 'CONFIRMED'
               and c.source_key = 'confirmation-link'
            """));
```

在 `result.put("topArticles", ...)` 之後加入邀請成效表：

```java
        // 訂閱者邀請成效：四個來源各自先聚合再 left join，避免多對多放大筆數。
        // 只列有活動者——多數讀者從未分享，全列出來會讓有意義的資料被淹沒。
        result.put("referrerStats", jdbc.queryForList("""
            with click_stats as (
              select referrer_id, count(*) clicks
                from referral_click group by referrer_id
            ), submit_stats as (
              select r.id referrer_id, count(*) submissions
                from survey_response sr
                join reader r on r.referral_code = (sr.answers ->> '_ref')
               where sr.answers ? '_ref'
               group by r.id
            ), conv_stats as (
              select referrer_id,
                     count(*) filter (where confirmed_at is not null) conversions,
                     coalesce(sum(referrer_reward) filter (where status = 'APPROVED'), 0) rewarded
                from referral_conversion group by referrer_id
            ), badge_stats as (
              select reader_id, count(*) badges
                from referral_badge group by reader_id
            )
            select r.email,
                   coalesce(c.clicks, 0) clicks,
                   coalesce(s.submissions, 0) submissions,
                   coalesce(v.conversions, 0) conversions,
                   coalesce(v.rewarded, 0) rewarded,
                   coalesce(b.badges, 0) badges
              from reader r
              left join click_stats c on c.referrer_id = r.id
              left join submit_stats s on s.referrer_id = r.id
              left join conv_stats v on v.referrer_id = r.id
              left join badge_stats b on b.reader_id = r.id
             where coalesce(c.clicks, 0) + coalesce(s.submissions, 0)
                   + coalesce(v.conversions, 0) > 0
             order by conversions desc, clicks desc
             limit 50
            """).stream().map(row -> {
                Map<String, Object> view = new LinkedHashMap<>(row);
                // email 對外一律遮罩
                view.put("email", ReferralGrowthService.maskEmail((String) row.get("email")));
                return view;
            }).toList());
```

- [ ] **Step 4: 執行測試確認通過**

Run: `JAVA_HOME=D:/java/jdk-21 mvn test -pl . -Dtest=AdminReferralBackfillTest`

Expected: PASS（6 個測試）

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/reader/AdminReferralGrowthController.java survey-backend/src/test/java/world/springai/survey/reader/AdminReferralBackfillTest.java
git commit -m "feat(reader): dashboard 新增真實信箱確認數與訂閱者邀請成效"
```

---

## Task 5: admin 成長分頁 UI（漏斗標籤、KPI、成效表、補發按鈕）

**Files:**

- Modify: `survey-backend/src/main/resources/static/admin.html:648-660`（成長分頁 markup）與 `:3037-3066`（`loadGrowth`）

**Interfaces:**

- Consumes: `GET /api/admin/referrals/dashboard` 的 `funnel`、`confirmedByLink`、`referrerStats`（Task 4）；`POST /api/admin/referrals/backfill?dryRun=`（Task 3）
- Produces: 無對外 API。DOM 契約供 Task 8 的驗證腳本使用：`#referrer-stats tbody`、`#growth-backfill-btn`、`#growth-backfill-dry-btn`

- [ ] **Step 1: 改 markup**

把成長分頁的說明文字與漏斗區塊調整，並在文章成效表之後插入新表與補發按鈕。找到 `<section class="view" id="growth-view" hidden>` 內的 section-head，把說明句改為：

```html
      <div><h2 style="margin:0">分享漏斗</h2><p class="hint">分享點擊 → 填表 → 轉換成立；點擊採每日唯一訪客計數。「信箱確認」另計，只算實際點過確認信連結的人。</p></div>
```

在 `#growth-articles` 表格所屬區塊之後插入：

```html
    <h3 style="margin:18px 0 6px">訂閱者邀請成效</h3>
    <p class="hint">只列出有分享活動的讀者；email 已遮罩。依轉換成立數排序，最多 50 筆。</p>
    <div class="table-wrap">
      <table id="referrer-stats">
        <thead><tr><th>推薦人</th><th>分享點擊</th><th>完成填表</th><th>轉換成立</th><th>已發點數</th><th>里程碑</th></tr></thead>
        <tbody></tbody>
      </table>
    </div>

    <h3 style="margin:18px 0 6px">歷史推薦獎勵補發</h3>
    <p class="hint">對「帶推薦碼且訂閱已成立」者建立轉換並直接核准發點。可重複執行，已處理過的不會重複發放。</p>
    <div class="form-row">
      <button class="btn ghost" id="growth-backfill-dry-btn">試算（不發放）</button>
      <button class="btn" id="growth-backfill-btn">執行補發</button>
    </div>
    <p class="hint" id="growth-backfill-result"></p>
```

- [ ] **Step 2: 改 `loadGrowth` 的漏斗與 KPI 標籤**

把 `loadGrowth` 內的漏斗與 KPI 兩段改為（第三層改名「轉換成立」，KPI 的「信箱確認」改吃 `confirmedByLink`）：

```javascript
      renderFunnelChart($('#share-funnel-chart'),[
        {label:'分享點擊',count:f.clicks},{label:'完成填表',count:f.submitted},
        {label:'轉換成立',count:f.confirmed},{label:'審核通過',count:f.approved}]);
```

```javascript
      [['分享點擊',f.clicks],['完成填表',f.submitted],['轉換成立',f.confirmed],
       ['信箱確認',data.confirmedByLink],['點擊→填表',f.clickToSubmitRate+'%'],['填表→成立',f.submitToConfirmRate+'%']]
```

- [ ] **Step 3: 渲染成效表**

在 `loadGrowth` 內 `data.topArticles.forEach(...)` 之後加入。**用 `cell()` helper 與 `textContent`，不可用 `innerHTML`**（security hook 會攔）：

```javascript
      const referrerBody=$('#referrer-stats tbody');referrerBody.replaceChildren();
      if(!data.referrerStats||!data.referrerStats.length){const tr=document.createElement('tr'),td=cell('目前沒有分享活動');td.colSpan=6;tr.append(td);referrerBody.append(tr);}
      (data.referrerStats||[]).forEach(row=>{const tr=document.createElement('tr');
        tr.append(cell(row.email),cell(row.clicks),cell(row.submissions),cell(row.conversions),cell(row.rewarded),cell(row.badges));referrerBody.append(tr);});
```

- [ ] **Step 4: 加補發函式並綁定按鈕**

在 `deactivateGrowthCampaign` 之後新增：

```javascript
  /**
   * 執行推薦獎勵補發。dryRun 時只顯示掃描結果不發放；
   * 正式執行前要二次確認，因為它會真的寫入點數帳本。
   */
  async function runGrowthBackfill(dryRun){
    if(!dryRun&&!confirm('確定執行補發？將對掃描到的名單建立轉換並發放點數（可重複執行，已處理過的不會重複發放）。'))return;
    const target=$('#growth-backfill-result');target.textContent='執行中…';
    try{
      const data=await api(`/api/admin/referrals/backfill?dryRun=${dryRun}`,{method:'POST'});
      target.textContent=dryRun
        ?`試算：掃描到 ${data.scanned} 筆待補發名單（${(data.candidates||[]).map(c=>c.email).join('、')||'無'}）`
        :`完成：掃描 ${data.scanned}、發放 ${data.rewarded}、已處理過 ${data.alreadyProcessed}、無推薦人 ${data.noReferrer}、自我邀請 ${data.selfInvite}、失敗 ${data.failed}`;
      if(!dryRun)await loadGrowth();
    }catch(e){if(e.message!=='401')target.textContent='補發失敗：'+e.message;}
  }
```

在事件綁定區（`$('#growth-refresh')` 附近）加入：

```javascript
    $('#growth-backfill-dry-btn').onclick=()=>runGrowthBackfill(true);
    $('#growth-backfill-btn').onclick=()=>runGrowthBackfill(false);
```

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/resources/static/admin.html
git commit -m "feat(admin): 成長分頁修正漏斗語意並新增邀請成效表與補發操作"
```

---

## Task 6: `ReconfirmService` —— 對既有未確認訂閱者補寄確認信

**Files:**

- Create: `survey-backend/src/main/java/world/springai/survey/newsletter/ReconfirmService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/newsletter/ReconfirmServiceTest.java`（新增）

**Interfaces:**

- Consumes: `JdbcTemplate`（名單查詢）、`MailSender.send(String to, String subject, String html)` → `String providerId`、`EmailLogRepository.save(EmailLog)`、`EmailTemplate.wrap(String bodyHtml, String unsubscribeLink)`、`SubscriptionLinkBuilder.confirmLink(String)` / `.unsubscribeLink(String)`
- Produces:
  - `public record ReconfirmResult(int recipientCount, int accepted, int failed, int alreadySent, int alreadyConfirmed, int remaining)`
  - `public int pendingCount()` —— 待補寄人數，供後台顯示
  - `public ReconfirmResult sendReconfirmations(Integer limit)`
  - `static final String LOG_TYPE = "reconfirm"`

`alreadySent` 與 `alreadyConfirmed` 是給操作者回答「為什麼只寄了 60 封而不是 72 封」—— 沒有這兩個數字，被 SQL 排除掉的人就是黑箱。

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey.newsletter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailSender;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 補寄確認信：名單口徑由 SQL 負責，本測試驗證寄送編排、內容與逐封容錯。 */
class ReconfirmServiceTest {

    private JdbcTemplate jdbc;
    private MailSender mailSender;
    private EmailLogRepository emailLogRepository;
    private SubscriptionLinkBuilder linkBuilder;
    private ReconfirmService service;

    /** 用真實 EmailTemplate 以驗證外框與退訂頁腳確實套上。 */
    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        mailSender = mock(MailSender.class);
        emailLogRepository = mock(EmailLogRepository.class);
        linkBuilder = mock(SubscriptionLinkBuilder.class);
        service = new ReconfirmService(jdbc, mailSender, emailLogRepository,
            new EmailTemplate(), linkBuilder);

        when(linkBuilder.confirmLink(anyString())).thenReturn("CONFIRM_LINK_MARKER");
        when(linkBuilder.unsubscribeLink(anyString())).thenReturn("UNSUB_LINK_MARKER");
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn("zsend-id");
        // alreadySent／alreadyConfirmed 兩支統計查詢，本檔案不驗其數值
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    }

    /**
     * 信件必須含確認連結與退訂連結，且不得提及推薦獎勵（spec D7）
     * 或說明不點確認的後果（spec D8）。
     */
    @Test
    void mailContainsConfirmLinkAndStaysNeutral() {
        when(jdbc.queryForList(anyString(), eq(String.class)))
            .thenReturn(List.of("a@example.com"));

        ReconfirmService.ReconfirmResult result = service.sendReconfirmations(null);

        assertThat(result.accepted()).isEqualTo(1);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertThat(html.getValue()).contains("CONFIRM_LINK_MARKER");
        assertThat(html.getValue()).contains("UNSUB_LINK_MARKER");
        assertThat(html.getValue()).doesNotContain("{{confirmLink}}");
        assertThat(html.getValue()).doesNotContain("獎勵");
        assertThat(html.getValue()).doesNotContain("取消訂閱你的");
    }

    /** limit 小於名單時只寄前 limit 封，其餘回報於 remaining。 */
    @Test
    void limitSplitsBatchAndReportsRemaining() {
        when(jdbc.queryForList(anyString(), eq(String.class)))
            .thenReturn(List.of("a@example.com", "b@example.com", "c@example.com"));

        ReconfirmService.ReconfirmResult result = service.sendReconfirmations(2);

        assertThat(result.recipientCount()).isEqualTo(2);
        assertThat(result.accepted()).isEqualTo(2);
        assertThat(result.remaining()).isEqualTo(1);
        verify(mailSender, times(2)).send(anyString(), anyString(), anyString());
    }

    /** 單封失敗不中斷整批，且失敗要寫 email_log status=failed。 */
    @Test
    void failureIsLoggedAndBatchContinues() {
        when(jdbc.queryForList(anyString(), eq(String.class)))
            .thenReturn(List.of("a@example.com", "b@example.com"));
        when(mailSender.send(eq("a@example.com"), anyString(), anyString()))
            .thenThrow(new RuntimeException("boom"));

        ReconfirmService.ReconfirmResult result = service.sendReconfirmations(null);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.accepted()).isEqualTo(1);
        ArgumentCaptor<EmailLog> logs = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository, times(2)).save(logs.capture());
        assertThat(logs.getAllValues()).anyMatch(l -> "failed".equals(l.getStatus()));
        assertThat(logs.getAllValues()).allMatch(l -> ReconfirmService.LOG_TYPE.equals(l.getType()));
    }

    /** 空名單不寄信也不報錯。 */
    @Test
    void emptyAudienceSendsNothing() {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());

        ReconfirmService.ReconfirmResult result = service.sendReconfirmations(null);

        assertThat(result.recipientCount()).isZero();
        verify(mailSender, org.mockito.Mockito.never())
            .send(anyString(), anyString(), anyString());
    }

    /**
     * 名單 SQL 必須保留三道排除條件（spec §4.4）。
     *
     * <p>冪等與防騷擾完全由這段 SQL 保證，不是由 Java 邏輯保證——
     * 少了 email_log 的排除，每次按按鈕都會重寄給同一批人；
     * 少了 audience_consent 的排除，已經確認過的人會被重複打擾。
     * mock 的 JdbcTemplate 驗不了語意，但驗得了條件沒被刪掉。</p>
     */
    @Test
    void pendingSqlKeepsAllThreeExclusions() {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);

        service.sendReconfirmations(null);

        verify(jdbc).queryForList(sql.capture(), eq(String.class));
        assertThat(sql.getValue()).contains("sr.consent = true");
        assertThat(sql.getValue()).contains("sr.unsubscribed = false");
        assertThat(sql.getValue()).contains("source_key = 'confirmation-link'");
        assertThat(sql.getValue()).contains("el.type = 'reconfirm'");
    }
}
```

測試檔需要 `import static org.mockito.ArgumentMatchers.eq;`。`EmailLog.getType()` 已存在（`EmailLog.java:70`），上面的 `allMatch` 斷言可直接使用。

- [ ] **Step 2: 執行測試確認失敗**

Run: `JAVA_HOME=D:/java/jdk-21 mvn test -pl . -Dtest=ReconfirmServiceTest`

Expected: 編譯失敗 —— `cannot find symbol: class ReconfirmService`

- [ ] **Step 3: 實作**

```java
package world.springai.survey.newsletter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import world.springai.survey.audience.SubscriptionLinkBuilder;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailSender;

/**
 * 對「已訂閱但從未點過確認連結」的既有名單補寄一次確認信。
 *
 * <p><b>為什麼不擴充 {@link InviteService}</b>：那支服務的名單口徑是
 * {@code consent = false}（匯入的待確認名單），本服務要的是
 * {@code consent = true} 但沒有確認紀錄的人——條件正好相反。
 * {@code InviteService} 已有 sendInvites／sendReminders 兩個高度相似的方法，
 * 塞進第三種語意只會讓那個檔案更難讀。</p>
 *
 * <p><b>本服務屬行銷側，必須受寄信額度 reserve 約束</b>：讀者不在等這封信，
 * 晚一天寄沒有損失，而 magic link 登入信是讀者當下盯著信箱等的那一封。
 * 額度收斂由 {@code AdminCampaignController.clampLimit()} 在端點層負責
 * （見 {@code MailQuotaService} 對「確認信」兩種語意的區分）。</p>
 */
@Service
public class ReconfirmService {

    private static final Logger log = LoggerFactory.getLogger(ReconfirmService.class);

    /** email_log 類型；每人終身只補寄一次靠它判斷 */
    static final String LOG_TYPE = "reconfirm";

    /** 補寄信主旨 */
    private static final String SUBJECT = "請確認你的訂閱信箱｜AI 賦能全端開發";

    /**
     * 待補寄名單：已同意、未退訂、沒有確認連結紀錄、沒被補寄過。
     *
     * <p>第三個條件（audience_consent）避免騷擾已經確認過的人；
     * 第四個條件（email_log）讓每人終身只收到一次。</p>
     */
    private static final String PENDING_SQL = """
        select distinct lower(sr.email)
          from survey_response sr
         where sr.consent = true
           and sr.unsubscribed = false
           and not exists (
             select 1 from audience_person p
               join audience_consent c on c.person_id = p.id
              where p.email_normalized = lower(sr.email)
                and c.channel = 'EMAIL'
                and c.status = 'CONFIRMED'
                and c.source_key = 'confirmation-link')
           and not exists (
             select 1 from email_log el
              where lower(el.recipient) = lower(sr.email)
                and el.type = 'reconfirm'
                and el.status = 'sent')
         order by lower(sr.email)
        """;

    private final JdbcTemplate jdbc;
    private final MailSender mailSender;
    private final EmailLogRepository emailLogRepository;
    private final EmailTemplate emailTemplate; // 共用信件外框（含退訂頁腳）
    private final SubscriptionLinkBuilder linkBuilder; // 確認／退訂連結組裝的唯一擁有者

    /** 注入名單查詢、寄信、寄送記錄、信件外框與連結組裝器 */
    public ReconfirmService(JdbcTemplate jdbc,
                            MailSender mailSender,
                            EmailLogRepository emailLogRepository,
                            EmailTemplate emailTemplate,
                            SubscriptionLinkBuilder linkBuilder) {
        this.jdbc = jdbc;
        this.mailSender = mailSender;
        this.emailLogRepository = emailLogRepository;
        this.emailTemplate = emailTemplate;
        this.linkBuilder = linkBuilder;
    }

    /** 已補寄過的人數（供操作者理解名單為何變小） */
    private static final String ALREADY_SENT_SQL = """
        select count(distinct lower(el.recipient))
          from email_log el
         where el.type = 'reconfirm' and el.status = 'sent'
        """;

    /** 已透過確認連結確認過的人數（同上，屬被排除的另一半原因） */
    private static final String ALREADY_CONFIRMED_SQL = """
        select count(distinct p.id)
          from audience_person p
          join audience_consent c on c.person_id = p.id
         where c.channel = 'EMAIL'
           and c.status = 'CONFIRMED'
           and c.source_key = 'confirmation-link'
        """;

    /**
     * 補寄結果摘要。
     *
     * @param recipientCount   本次實際嘗試寄送數
     * @param accepted         寄送成功數
     * @param failed           寄送失敗數
     * @param alreadySent      先前已補寄過而被排除的人數
     * @param alreadyConfirmed 已點過確認連結而被排除的人數
     * @param remaining        因 limit 未寄的剩餘數
     */
    public record ReconfirmResult(int recipientCount, int accepted, int failed,
                                  int alreadySent, int alreadyConfirmed, int remaining) {}

    /** 待補寄人數，供後台顯示按鈕旁的數字 */
    public int pendingCount() {
        return jdbc.queryForList(PENDING_SQL, String.class).size();
    }

    /** 單一 count 查詢的取值；null 視為 0 */
    private int countOf(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value.intValue();
    }

    /**
     * 對待補寄名單逐封寄確認信；單封失敗不中斷整批。
     *
     * @param limit 單次寄送上限；null 或 &lt;= 0 視為不限（端點層已先收斂到額度內）
     */
    public ReconfirmResult sendReconfirmations(Integer limit) {
        List<String> pending = jdbc.queryForList(PENDING_SQL, String.class);
        List<String> targets = (limit != null && limit > 0 && limit < pending.size())
            ? pending.subList(0, limit) : pending;
        int remaining = pending.size() - targets.size();

        int accepted = 0;
        int failed = 0;
        for (String email : targets) {
            try {
                String html = buildHtml(linkBuilder.unsubscribeLink(email),
                    linkBuilder.confirmLink(email));
                String id = mailSender.send(email, SUBJECT, html);
                emailLogRepository.save(new EmailLog(email, SUBJECT, LOG_TYPE, id, "sent", null));
                accepted++;
            } catch (Exception e) {
                log.warn("補寄確認信失敗 to={}：{}", email, e.getMessage());
                emailLogRepository.save(
                    new EmailLog(email, SUBJECT, LOG_TYPE, null, "failed", e.getMessage()));
                failed++;
            }
        }
        return new ReconfirmResult(targets.size(), accepted, failed,
            countOf(ALREADY_SENT_SQL), countOf(ALREADY_CONFIRMED_SQL), remaining);
    }

    /**
     * 組補寄信 HTML。
     *
     * <p>文案刻意<b>不提推薦獎勵</b>（全體收件，多數人沒有推薦人），
     * 也<b>不主動說明不點確認的後果</b>——但同樣不得暗示會被取消訂閱，
     * 那是欺騙。訴求收斂在「信箱可達性」這一件對每個收件人都成立的事。</p>
     */
    private String buildHtml(String unsubscribeLink, String confirmLink) {
        String body = """
            <h2>請確認你的訂閱信箱</h2>
            <p>你先前訂閱了這份電子報，訂閱目前仍然有效——這封信不是重新徵求你的同意。</p>
            <p>我們最近加上了信箱確認機制。沒有經過確認的地址，我們無法分辨「你收到了但還沒打開」
               和「這封信根本沒送達」——打錯字的地址、已停用的信箱、被歸進垃圾信匣的，
               在數據上長得一模一樣。</p>
            <div style="margin:28px 0;padding:20px;border:1px solid #dce5ee;border-radius:12px;background:#f7fafc;text-align:center">
              <p style="margin:0 0 18px;color:#5c6b7d;font-size:.92rem">點一下按鈕即可完成，只需幾秒。</p>
              <a href="%s" style="display:inline-block;background:#0d9488;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:700">確認我的信箱</a>
            </div>
            """.formatted(confirmLink);
        return emailTemplate.wrap(body, unsubscribeLink);
    }
}
```

- [ ] **Step 4: 執行測試確認通過**

Run: `JAVA_HOME=D:/java/jdk-21 mvn test -pl . -Dtest=ReconfirmServiceTest`

Expected: PASS（5 個測試）

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/newsletter/ReconfirmService.java survey-backend/src/test/java/world/springai/survey/newsletter/ReconfirmServiceTest.java
git commit -m "feat(newsletter): 新增既有訂閱者補寄確認信服務"
```

---

## Task 7: 補寄端點與 admin 按鈕

**Files:**

- Modify: `survey-backend/src/main/java/world/springai/survey/newsletter/AdminCampaignController.java`
- Modify: `survey-backend/src/main/resources/static/admin.html`（邀請分頁）

**Interfaces:**

- Consumes: `ReconfirmService.sendReconfirmations(Integer)`、`ReconfirmService.pendingCount()`（Task 6）、既有 `clampLimit(Integer)`
- Produces:
  - `POST /api/admin/campaign/reconfirm`，body `{"limit": <int|null>}` → `ReconfirmService.ReconfirmResult`
  - `GET /api/admin/campaign/reconfirm/pending` → `Map.of("pending", int)`

- [ ] **Step 1: 寫失敗測試**

新增 `survey-backend/src/test/java/world/springai/survey/newsletter/AdminReconfirmEndpointTest.java`。重點是驗證額度守門 —— 補寄信屬行銷側，額度必須讓位給 magic link 登入信（spec 前置查證 3）。

```java
package world.springai.survey.newsletter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.AdminKeyGuard;
import world.springai.survey.audience.RecipientService;
import world.springai.survey.mail.MailQuotaService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 補寄確認信端點：額度收斂與 409 守門。 */
class AdminReconfirmEndpointTest {

    private MailQuotaService mailQuotaService;
    private ReconfirmService reconfirmService;
    private AdminCampaignController controller;

    /** 只裝配本測試需要的協作者，其餘傳 mock。 */
    @BeforeEach
    void setUp() {
        mailQuotaService = mock(MailQuotaService.class);
        reconfirmService = mock(ReconfirmService.class);
        controller = new AdminCampaignController(mock(AdminKeyGuard.class),
            mock(CampaignService.class), mock(RecipientService.class),
            mock(InviteService.class), mailQuotaService, reconfirmService);
        when(reconfirmService.sendReconfirmations(any()))
            .thenReturn(new ReconfirmService.ReconfirmResult(0, 0, 0, 0, 0, 0));
    }

    /** 行銷可用額度為 0 時必須回 409，絕不可放行整份名單。 */
    @Test
    void rejectsWhenMarketingQuotaExhausted() {
        when(mailQuotaService.current()).thenReturn(quotaWithMarketingBatchMax(0));

        assertThatThrownBy(() ->
                controller.reconfirm("key", new AdminCampaignController.ReconfirmRequest(null)))
            .isInstanceOf(ResponseStatusException.class);

        verify(reconfirmService, never()).sendReconfirmations(any());
    }

    /**
     * limit 為 null（不限）時必須被收斂成行銷可用上限。
     *
     * <p>把 null 或 0 原樣傳給 ReconfirmService 的後果是「整份名單全寄」，
     * 而且正好發生在額度最吃緊的時候——與意圖完全相反。</p>
     */
    @Test
    void nullLimitIsClampedToMarketingBatchMax() {
        when(mailQuotaService.current()).thenReturn(quotaWithMarketingBatchMax(30));

        controller.reconfirm("key", new AdminCampaignController.ReconfirmRequest(null));

        verify(reconfirmService).sendReconfirmations(eq(30));
    }

    /** 前端送來的 limit 小於上限時原樣採用。 */
    @Test
    void smallerLimitIsPreserved() {
        when(mailQuotaService.current()).thenReturn(quotaWithMarketingBatchMax(30));

        controller.reconfirm("key", new AdminCampaignController.ReconfirmRequest(10));

        verify(reconfirmService).sendReconfirmations(eq(10));
    }

    /** 待補寄人數端點原樣透傳服務的計數。 */
    @Test
    void pendingEndpointReturnsServiceCount() {
        when(reconfirmService.pendingCount()).thenReturn(72);

        assertThat(controller.reconfirmPending("key").get("pending")).isEqualTo(72);
    }

    /**
     * 只有 marketingBatchMax 影響本測試，其餘欄位填中性值。
     *
     * <p>Quota 共 16 個 component：source、status，接著 11 個 long
     * （dailyQuota、dailySent、dailyRemaining、monthlyQuota、monthlySent、
     * monthlyRemaining、remaining、batchMax、reserve、marketingRemaining、
     * marketingBatchMax），最後 overageBillingEnabled、quotaResetAt、monthlyResetAt。</p>
     */
    private static MailQuotaService.Quota quotaWithMarketingBatchMax(long marketingBatchMax) {
        return new MailQuotaService.Quota("fallback", "unknown",
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, marketingBatchMax,
            false, null, null);
    }
}
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `JAVA_HOME=D:/java/jdk-21 mvn test -pl . -Dtest=AdminReconfirmEndpointTest`

Expected: 編譯失敗 —— `cannot find symbol: method reconfirm(...)`

- [ ] **Step 3: 實作端點**

`AdminCampaignController` 建構子新增一個依賴。宣告欄位：

```java
    /** 既有訂閱者補寄確認信服務 */
    private final ReconfirmService reconfirmService;
```

**這個檔案有兩個建構子，兩個都要改** —— 只改一個會編譯失敗（`final` 欄位未指派）。兩者都在參數末端加 `ReconfirmService reconfirmService` 並賦值：

- `AdminCampaignController.java:50` 的 `@Autowired` 完整建構子
- `AdminCampaignController.java:67` 的「舊單元測試相容建構式」（Step 1 的測試用的就是這個，改完為 6 個參數）

**已查證這個舊建構子目前在整個 `survey-backend/src` 下沒有任何呼叫點**（`grep -rn "new AdminCampaignController("` 無結果），所以加參數不會破壞任何既有測試。它是留給早已不存在的舊測試的死碼；本任務讓它重新有用途，而不是再加一個只為相容而存在的多載。

新增兩支端點，放在 `remind` 方法之後：

```java
    /** 補寄確認信請求：單次寄送上限（配合寄信額度；null 為不限，仍會被收斂到額度內） */
    public record ReconfirmRequest(Integer limit) {}

    /**
     * 對「已訂閱但從未點過確認連結」者補寄一次確認信，需提供有效金鑰。
     *
     * <p>與邀請信同樣走 {@link #clampLimit(Integer)}：本信屬行銷側，
     * 讀者不在等它，額度必須讓位給 magic link 登入信。</p>
     */
    @PostMapping("/api/admin/campaign/reconfirm")
    public ReconfirmService.ReconfirmResult reconfirm(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @RequestBody(required = false) ReconfirmRequest req) {
        guard.verify(key);
        return reconfirmService.sendReconfirmations(
            clampLimit(req == null ? null : req.limit()));
    }

    /** 待補寄確認信人數，供後台按鈕旁顯示，需提供有效金鑰 */
    @GetMapping("/api/admin/campaign/reconfirm/pending")
    public Map<String, Object> reconfirmPending(
            @RequestHeader(value = KEY_HEADER, required = false) String key) {
        guard.verify(key);
        return Map.of("pending", reconfirmService.pendingCount());
    }
```

- [ ] **Step 4: 執行測試確認通過**

Run: `JAVA_HOME=D:/java/jdk-21 mvn test -pl . -Dtest='AdminReconfirmEndpointTest+InviteServiceTest'`

Expected: PASS（新增 4 個測試；`InviteServiceTest` 一併跑是因為它與 `clampLimit` 共用同一條額度路徑，要確認沒被改壞）

- [ ] **Step 5: 加 admin UI**

在邀請分頁 `#invite-send-btn` / `#invite-remind-btn` 所在的按鈕列，加入第三顆按鈕與人數顯示：

```html
        <button class="btn ghost" id="reconfirm-btn">補寄確認信<span id="reconfirm-pending"></span></button>
```

新增函式（放在 `doSendReminders` 之後）：

```javascript
  /** 載入待補寄確認信人數，顯示在按鈕上。 */
  async function loadReconfirmPending(){
    try{
      const data=await api('/api/admin/campaign/reconfirm/pending');
      $('#reconfirm-pending').textContent=data.pending?`（${data.pending}）`:'';
    }catch(e){/* 人數只是輔助資訊，失敗不打擾操作者 */}
  }

  /** 對既有未確認訂閱者補寄確認信；每人終身只寄一次，重按不會重寄。 */
  async function doReconfirm(){
    if(!confirm('確定對「已訂閱但從未確認信箱」者補寄確認信？每人只會收到一次。'))return;
    try{
      const data=await api('/api/admin/campaign/reconfirm',{method:'POST',body:JSON.stringify({limit:null})});
      showMsg('invite-msg',`補寄完成：嘗試 ${data.recipientCount}、成功 ${data.accepted}、失敗 ${data.failed}、剩餘 ${data.remaining}（已排除：確認過 ${data.alreadyConfirmed}、補寄過 ${data.alreadySent}）`,true);
      await loadReconfirmPending();
    }catch(e){if(e.message!=='401')showMsg('invite-msg','補寄失敗：'+e.message,false);}
  }
```

在事件綁定區 `$('#invite-remind-btn').onclick=doSendReminders;` 之後加入：

```javascript
    $('#reconfirm-btn').onclick=doReconfirm;
```

並在 `$('#invite-refresh').onclick=()=>{doInviteOverview();doQuota();};` 內加上 `loadReconfirmPending();`，改為：

```javascript
    $('#invite-refresh').onclick=()=>{doInviteOverview();doQuota();loadReconfirmPending();};
```

- [ ] **Step 6: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/newsletter/AdminCampaignController.java survey-backend/src/test/java/world/springai/survey/newsletter/AdminReconfirmEndpointTest.java survey-backend/src/main/resources/static/admin.html
git commit -m "feat(admin): 新增補寄確認信端點與後台操作入口"
```

---

## Task 8: 驗證腳本更新與全套測試

**Files:**

- Modify: `survey-backend/scripts/verify-growth-funnel.mjs`

**Interfaces:**

- Consumes: Task 5 建立的 DOM 契約 `#referrer-stats tbody`、`#growth-backfill-btn`；Task 5 改名後的漏斗標籤「轉換成立」

- [ ] **Step 1: 修正渲染完成判定（不改會直接逾時）**

`verify-growth-funnel.mjs:180` 的 `expectedShareParts` **不是斷言，是 `mockDashboardAndLoad` 內 `page.waitForFunction` 的渲染完成判定**。Task 5 把第三層改名成「轉換成立」後，這個 predicate 永遠不成立，腳本會在 10 秒逾時後整支失敗 —— 而且錯誤訊息只是 timeout，看不出真因。

把該行改為：

```javascript
  const expectedShareParts = [`分享點擊${f.clicks}`, `完成填表${f.submitted}`, `轉換成立${f.confirmed}`, `審核通過${f.approved}`];
```

- [ ] **Step 2: 更新 mock fixture**

這支腳本用 `page.route` **攔截並偽造整份 dashboard 回應**，所以新欄位必須進 fixture，否則 `admin.html` 讀到 `undefined`，KPI 會渲染出字面的「undefined」。

在 `NORMAL_DASHBOARD`（約 L82）的 `topArticles: [],` 之後加入兩個新鍵：

```javascript
  confirmedByLink: 12,
  referrerStats: [
    { email: 'a***@example.com', clicks: 42, submissions: 6, conversions: 6, rewarded: 600, badges: 1 },
    { email: 'b***@example.com', clicks: 31, submissions: 4, conversions: 4, rewarded: 400, badges: 0 },
  ],
```

`INVERTED_DASHBOARD`、`NARROW_READER_DASHBOARD`、`NARROW_INVERTED_READER_DASHBOARD` 都以 `...NORMAL_DASHBOARD` 展開，會自動繼承，不需個別修改。

- [ ] **Step 3: 新增斷言**

在第 1 段（正常資料）的檢查之後、`// ---- 2. 倒掛資料` 之前插入。**用腳本既有的 `ok(condition, message)` helper，不要用 `throw new Error`** —— 那是這支腳本的既有慣例，`ok()` 會累積結果並在最後統一報告通過/失敗統計。

```javascript
  // ---- 1b. 本輪新增：真實信箱確認 KPI 與訂閱者邀請成效表 ----
  const kpiLabels = await readTexts(page, '#growth-kpis .kpi-label');
  ok(kpiLabels.includes('轉換成立'),
    `KPI 含「轉換成立」（實際：${JSON.stringify(kpiLabels)}）`);
  ok(kpiLabels.includes('信箱確認'),
    `KPI 含「信箱確認」——真實點擊數，與「轉換成立」是兩個不同指標（實際：${JSON.stringify(kpiLabels)}）`);

  const kpiText = await page.locator('#growth-kpis').textContent();
  ok(kpiText.includes('12'), `信箱確認 KPI 顯示 confirmedByLink 的值 12`);
  ok(!kpiText.includes('undefined'),
    `KPI 區塊不得出現 undefined（confirmedByLink 未接上時會這樣）`);

  const referrerRows = await page.locator('#referrer-stats tbody tr').count();
  ok(referrerRows === 2, `訂閱者邀請成效表渲染 2 列（實際 ${referrerRows} 列）`);
  const referrerText = await page.locator('#referrer-stats tbody').textContent();
  ok(referrerText.includes('a***@example.com'), `成效表顯示遮罩後的 email`);
  ok(!referrerText.includes('@example.com') || referrerText.includes('***'),
    `成效表不得出現未遮罩的完整 email`);

  ok(await page.locator('#growth-backfill-btn').count() === 1, `補發按鈕存在`);
  ok(await page.locator('#growth-backfill-dry-btn').count() === 1, `補發試算按鈕存在`);
```

- [ ] **Step 4: 執行驗證腳本**

Run（需先啟動後端）：

```bash
cd survey-backend && node scripts/verify-growth-funnel.mjs
```

Expected: 全部檢查通過

- [ ] **Step 5: 執行全套後端測試**

Run: `JAVA_HOME=D:/java/jdk-21 mvn clean test`

Expected: 全綠。特別確認 `ReferralGrowthServiceTest`、`ReferralIdempotencyTest`、`ReferralRewardListenerTest`、`SubscriptionControllerTest`、`WelcomeMailServiceTest`、`InviteServiceTest` 皆通過。

- [ ] **Step 6: Commit**

```bash
git add survey-backend/scripts/verify-growth-funnel.mjs
git commit -m "test(admin): 驗證腳本更新漏斗標籤並涵蓋邀請成效表"
```

---

## 上線與操作順序（實作完成後）

依 spec §4.5，不可調換：

1. **部署本分支**（A / C 的程式碼同時生效；B / D 只是端點，尚未觸發）
2. **執行補發試算**：後台成長分頁按「試算（不發放）」，核對名單筆數與遮罩 email 是否合理
3. **執行補發**：按「執行補發」，確認回報的 `rewarded` 數與試算的 `scanned` 相符
4. **觀察數日**：新訂閱者透過歡迎信 CTA 產生的「信箱確認」KPI 是否開始成長
5. **最後才補寄**：邀請分頁按「補寄確認信」。放最後是因為此時歷史帳已清完，補寄湧入的確認都是乾淨的即時資料，不會與補發混在一起難以歸因
