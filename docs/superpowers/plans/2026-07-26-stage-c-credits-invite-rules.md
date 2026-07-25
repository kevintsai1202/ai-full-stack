# 階段 C：點數消耗 + 邀請 + 規則頁 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把階段 B 留下的 PARTIAL 死路接成可走完的閉環——讀者能用點數解鎖進階文章、能靠邀請賺點數、能在規則頁看懂整套機制，而站方能在後台授予 VIP 與加點。

**Architecture:** 扣點是唯一需要交易保證的寫入路徑（`article_access` UNIQUE + 條件式 `UPDATE`），集中在 `reader/UnlockService`；點數的所有對外數字集中在 `reader/CreditPolicy` 這唯一來源；邀請獎勵跨越 `audience → reader` 的禁止方向，改以 Spring 事件解耦。

**Tech Stack:** Spring Boot 3.5.0 / Java 21 / PostgreSQL / Flyway / Maven 3.9.11、vanilla HTML+JS（無建置步驟）

## Global Constraints

- **既有資料不可清除**（spec §4.0）：禁止 `DROP TABLE` / `DROP COLUMN` / `TRUNCATE`、禁止無 WHERE 的 `DELETE`、禁止對正式 DB 執行 `flyway clean`。
- **本階段不含任何 migration。** V7 已建好 `reader` / `credit_txn` / `article_access` / `login_token` / `app_setting`（含 8 筆參數種子），V8 已擴充 `campaign` 與 `last_engaged_at`。邀請歸因用既有的 `survey_response.answers` jsonb，不加欄位。**任何任務若覺得需要新 migration，先停下來回報——那代表計畫有誤，不要自行新增。**
- `ddl-auto` 必須維持 `validate`；`spring.flyway.clean-disabled: true` 不得改動。
- **不新增任何 Maven 依賴**（測試或生產皆然）。沿用 `spring-boot-starter-test`。
- **`mvn` 必須明確指定 JDK 21**：`JAVA_HOME=/d/java/jdk-21 mvn -B clean test`。shell 預設 `JAVA_HOME` 是 JDK 8，會失敗在 `TrackingController.java` 第 55 行的 text block，錯誤訊息（`unclosed string literal`）完全不提 JDK 版本，極易誤判為編碼問題。
- **`audience` / `mail` / `media` 不得 import `reader` / `newsletter` / `form`**；`newsletter` 不得 import `reader`。由 `PackageDependencyTest` 守衛。
- **PARTIAL 的回應不得包含受限區的任何字串**（spec §5.3）。不是 CSS 隱藏、不是前端過濾。
- **所有對讀者顯示的點數數字一律取自 `CreditPolicy`**（spec §5.11 硬要求）。規則頁、`/r/me`、gate 區塊三處若各自寫死或各自讀 `app_setting`，調整參數後就會出現「顯示的代價與實際扣的不一致」。
- 既有 confirm / unsubscribe 的 URL 路徑（`/api/survey/confirm`、`/api/survey/unsubscribe`）**絕對不可變更**——已寄出的信件內含這些網址，改路徑等於讓所有在途的確認信與退訂連結失效。
- 所有程式碼需有中文註解；函式級別註解必要，重要變數與物件也要註解。

### 關於測試數的說明（與階段 B 的做法刻意不同）

各任務只寫「本任務新增約 N 個測試」，**不維護跨任務的累計數字鏈**。階段 B 的計畫維護了 `72→…→204` 的精確鏈，結果因審查追加測試而被改了十幾次，每次都要回頭改後面所有任務——這個鏈本身沒有守住任何東西。

**驗收標準是「全綠且 Skipped: 0」，不是某個特定數字。** 實際數字記在 `.superpowers/sdd/progress.md`。

---

## File Structure

**新增（生產）**

| 檔案 | 責任 |
|---|---|
| `reader/CreditPolicy.java` | 點數相關參數的唯一來源；封裝各參數的下限保護 |
| `reader/UnlockService.java` | 扣點解鎖的交易邊界（唯一會扣點的地方） |
| `reader/UnlockController.java` | `POST /api/reader/unlock/{slug}` |
| `reader/ReferralService.java` | 邀請歸因查詢與獎勵發放（冪等） |
| `reader/ReferralRewardListener.java` | 監聽 `SubscriptionConfirmedEvent` 觸發獎勵 |
| `reader/ReaderPortalController.java` | `/r/me`、`/r/invite` 頁面與個人資料更新 API |
| `reader/RulesPageController.java` | `/r/rules` 頁面（數字動態注入） |
| `reader/AdminReaderController.java` | 後台：VIP 授予、手動／批次加點、帳本查詢 |
| `audience/SubscriptionController.java` | confirm / unsubscribe 端點（自 `form` 搬入，路徑不變） |
| `audience/SubscriptionLinkBuilder.java` | confirm / unsubscribe 連結組裝的唯一擁有者 |
| `audience/SubscriptionConfirmedEvent.java` | 確認訂閱成功事件 |
| `AdminSettingController.java` | 後台：參數讀寫（跨領域，故置於根 package） |

**新增（靜態頁）**：`static/reader/me.html`、`static/reader/invite.html`、`static/reader/rules.html`

**修改**

| 檔案 | 變更 |
|---|---|
| `reader/AccessDecisionService.java` | 新增 `CAN_UNLOCK` 判定；成本計算改委派 `CreditPolicy` |
| `reader/ReaderAccountService.java` | 贈點金額改用 `CreditPolicy`；建帳戶時寫入 `referred_by` |
| `reader/ReaderPageController.java` | gate 區塊改為可操作的解鎖按鈕 |
| `reader/ReaderRepository.java` | 新增條件式扣點與加點 |
| `reader/CreditTxnRepository.java` | 新增 REFERRAL 冪等檢查與 email 查帳本 |
| `form/SurveyRequest.java` | 新增 `ref` 欄位 |
| `form/SurveyController.java` | 寫入 `answers._ref`；移除 confirm/unsubscribe；`stats()` 排除底線鍵 |
| `audience/WelcomeMailService.java`、`newsletter/InviteService.java` | 連結組裝改用 `SubscriptionLinkBuilder` |
| `newsletter/CampaignService.java` | 群發前檢查保留額度 |
| `mail/MailQuotaService.java` | `Quota` 加入保留額度與行銷可用量 |
| `static/reader/index.html` | 規則頁連結；`?ref=` 送進後端 |
| `static/reader/reader.css` | 新頁面所需樣式 |
| `static/admin.html` | 新增「讀者管理」與「參數設定」分頁、補上文章發布欄位 |
| `application.yml` | 無變更（`transactional-reserve` 已存在） |

---

## Task 1: `CreditPolicy` — 點數數字的唯一來源

**背景**：目前 `AccessDecisionService.DEFAULT_PREMIUM_COST = 10` 與 `ReaderAccountService.DEFAULT_SIGNUP_GRANT = 300` 各自持有後備值，各自呼叫 `AppSettingService`。本階段要再加三個讀取點（規則頁、`/r/me`、gate 區塊），若沿用這個模式就會有六處各自解讀參數。spec §5.11 明訂三處數字必須同源，所以先把來源收斂。

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/reader/CreditPolicy.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/AccessDecisionService.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/ReaderAccountService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/CreditPolicyTest.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/AccessDecisionServiceTest.java`（既有，改建構子呼叫）
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReaderAccountServiceTest.java`（既有，改建構子呼叫）

**Interfaces:**
- Consumes: `AppSettingService.getInt(String key, int defaultValue)`；常數 `AppSettingService.CREDIT_SIGNUP_GRANT` / `CREDIT_PREMIUM_COST` / `CREDIT_REFERRAL_REWARD` / `VIP_DEFAULT_DAYS`
- Produces:
  - `CreditPolicy.signupGrant() → int`（≥ 0）
  - `CreditPolicy.premiumCost() → int`（**≥ 1**）
  - `CreditPolicy.referralReward() → int`（≥ 0）
  - `CreditPolicy.vipDefaultDays() → int`（**≥ 1**）
  - `CreditPolicy.costOf(Campaign campaign) → int`（**≥ 1**）

- [ ] **Step 1: 寫失敗測試 `CreditPolicyTest`**

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import world.springai.survey.AppSettingService;
import world.springai.survey.newsletter.Campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CreditPolicy 的測試重點不是「讀得到設定值」，而是**下限保護的方向**。
 *
 * <p>每個參數的下限不同，而且理由不同：premiumCost 與 vipDefaultDays 若為 0
 * 會造成權限外洩（所有 PREMIUM 免費／VIP 立即失效），signupGrant 與
 * referralReward 為 0 只是「不送點」，是合法的營運設定。把四者都夾成 ≥ 1
 * 會讓後台無法關閉贈點；都不夾則會外洩。所以逐一驗證。</p>
 */
class CreditPolicyTest {

    /**
     * 建一個「指定鍵回傳指定值、其餘鍵回傳呼叫端預設值」的 AppSettingService 假物件。
     *
     * <p>第一個 stub 讓未指定的鍵回傳第 2 個引數（呼叫端的 defaultValue），
     * 忠實模擬 {@link AppSettingService#getInt} 查無此鍵時的真實行為。
     * 沒有這一行的話，Mockito 對未 stub 的 int 方法一律回 <b>0</b>——那會讓
     * 所有參數看起來都是 0，測試變成在驗證 Mockito 的預設值而不是 CreditPolicy。</p>
     */
    private AppSettingService settingsReturning(String key, int value) {
        AppSettingService settings = mock(AppSettingService.class);
        when(settings.getInt(anyString(), anyInt()))
            .thenAnswer(invocation -> invocation.getArgument(1, Integer.class));
        when(settings.getInt(eq(key), anyInt())).thenReturn(value);
        return settings;
    }

    /** 建一篇指定 tier 與成本的文章；沿用 AccessDecisionServiceTest 的建立方式 */
    private Campaign article(String tier, int cost) {
        Campaign c = new Campaign("主旨", "# 內容", "<h1>內容</h1>", null, null, "now", null, 0, "sent");
        c.setTier(tier);
        c.setCreditCost(cost);
        return c;
    }

    /** 正常設定值應原樣回傳 */
    @Test
    void readsConfiguredValues() {
        AppSettingService settings = mock(AppSettingService.class);
        when(settings.getInt(eq(AppSettingService.CREDIT_SIGNUP_GRANT), anyInt())).thenReturn(300);
        when(settings.getInt(eq(AppSettingService.CREDIT_PREMIUM_COST), anyInt())).thenReturn(10);
        when(settings.getInt(eq(AppSettingService.CREDIT_REFERRAL_REWARD), anyInt())).thenReturn(100);
        when(settings.getInt(eq(AppSettingService.VIP_DEFAULT_DAYS), anyInt())).thenReturn(365);

        CreditPolicy policy = new CreditPolicy(settings);

        assertEquals(300, policy.signupGrant());
        assertEquals(10, policy.premiumCost());
        assertEquals(100, policy.referralReward());
        assertEquals(365, policy.vipDefaultDays());
    }

    /** premiumCost 設成 0 必須被夾到 1：否則所有 PREMIUM 文章變免費 */
    @Test
    void premiumCostIsClampedToAtLeastOne() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.CREDIT_PREMIUM_COST, 0));
        assertEquals(1, policy.premiumCost());
    }

    /** premiumCost 設成負數同樣夾到 1 */
    @Test
    void negativePremiumCostIsClampedToAtLeastOne() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.CREDIT_PREMIUM_COST, -5));
        assertEquals(1, policy.premiumCost());
    }

    /** vipDefaultDays 設成 0 必須被夾到 1：0 天等於授予後立刻失效 */
    @Test
    void vipDaysIsClampedToAtLeastOne() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.VIP_DEFAULT_DAYS, 0));
        assertEquals(1, policy.vipDefaultDays());
    }

    /** signupGrant 為 0 是合法設定（關閉贈點），不可被夾成 1 */
    @Test
    void signupGrantZeroIsAllowed() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.CREDIT_SIGNUP_GRANT, 0));
        assertEquals(0, policy.signupGrant());
    }

    /** signupGrant 為負數則夾到 0：負的贈點會讓新讀者一開始就是負餘額 */
    @Test
    void negativeSignupGrantIsClampedToZero() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.CREDIT_SIGNUP_GRANT, -100));
        assertEquals(0, policy.signupGrant());
    }

    /** referralReward 為 0 是合法設定（關閉邀請獎勵） */
    @Test
    void referralRewardZeroIsAllowed() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.CREDIT_REFERRAL_REWARD, 0));
        assertEquals(0, policy.referralReward());
    }

    /** 文章自訂成本優先於全域預設 */
    @Test
    void perArticleCostWinsOverGlobalDefault() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.CREDIT_PREMIUM_COST, 10));
        assertEquals(50, policy.costOf(article(Campaign.TIER_PREMIUM, 50)));
    }

    /** 文章成本為 0 時退回全域預設，且仍受 ≥ 1 保護 */
    @Test
    void zeroArticleCostFallsBackToGlobalDefaultWithFloor() {
        CreditPolicy policy = new CreditPolicy(settingsReturning(AppSettingService.CREDIT_PREMIUM_COST, 0));
        assertEquals(1, policy.costOf(article(Campaign.TIER_PREMIUM, 0)));
    }

    /** 查無設定時採用內建後備值（此路徑不 stub 該鍵，讓 getInt 回傳 defaultValue） */
    @Test
    void fallsBackToBuiltInDefaultsWhenSettingAbsent() {
        AppSettingService settings = mock(AppSettingService.class);
        // 全部鍵都回傳呼叫端給的 defaultValue，模擬 app_setting 內查無此鍵
        when(settings.getInt(anyString(), anyInt()))
            .thenAnswer(invocation -> invocation.getArgument(1, Integer.class));

        CreditPolicy policy = new CreditPolicy(settings);

        assertEquals(CreditPolicy.DEFAULT_SIGNUP_GRANT, policy.signupGrant());
        assertEquals(CreditPolicy.DEFAULT_PREMIUM_COST, policy.premiumCost());
        assertEquals(CreditPolicy.DEFAULT_REFERRAL_REWARD, policy.referralReward());
        assertEquals(CreditPolicy.DEFAULT_VIP_DAYS, policy.vipDefaultDays());
    }
}
```

> **實作者注意**：上方 `settingsReturning` 的第一行 `when(settings.getInt(anyInt() == 0 ? null : null, anyInt()))` 是無效寫法（會 NPE），請直接刪掉那一行——`mock` 的 `getInt` 未 stub 時回 `0`，而各測試都明確 stub 了自己要的鍵，不需要那行。此處刻意保留這個錯誤的原始寫法給你看到，是因為 Mockito 對「未 stub 的方法回 0」這個預設行為常被誤解成「會回傳呼叫端的 defaultValue」——**不會**。因此 `settingsReturning` 只能用於「該鍵有明確 stub」的測試，不可拿來測後備值路徑。

- [ ] **Step 2: 執行測試確認失敗**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=CreditPolicyTest`
Expected: 編譯失敗，`cannot find symbol: class CreditPolicy`

- [ ] **Step 3: 實作 `CreditPolicy`**

```java
package world.springai.survey.reader;

import org.springframework.stereotype.Component;
import world.springai.survey.AppSettingService;
import world.springai.survey.newsletter.Campaign;

/**
 * 點數相關參數的唯一來源。
 *
 * <p><b>為什麼需要這一層</b>：spec §5.11 要求規則頁、{@code /r/me} 與 paywall
 * 提示區塊顯示的點數數字必須與實際扣點一致。若每個呼叫點各自呼叫
 * {@link AppSettingService} 並各自帶後備值，調整參數後就會出現「頁面說 10 點、
 * 實際扣 50 點」這類最傷信任的落差。把讀取集中在此，順帶把「下限保護」也集中，
 * 不讓每個呼叫點各自記得要夾。</p>
 *
 * <p><b>各參數的下限刻意不同</b>：{@link #premiumCost()} 與
 * {@link #vipDefaultDays()} 若為 0 會造成權限外洩（所有 PREMIUM 免費／VIP
 * 授予後立即失效），故夾到 1；{@link #signupGrant()} 與
 * {@link #referralReward()} 為 0 只代表「關閉贈點」，是合法營運設定，
 * 只夾掉負值。把四者一律夾成 ≥ 1 會讓後台無法關閉贈點。</p>
 */
@Component
public class CreditPolicy {

    /** 初始贈點的後備值（查不到設定時採用） */
    static final int DEFAULT_SIGNUP_GRANT = 300;
    /** PREMIUM 單篇解鎖點數的後備值 */
    static final int DEFAULT_PREMIUM_COST = 10;
    /** 邀請成功獎勵的後備值 */
    static final int DEFAULT_REFERRAL_REWARD = 100;
    /** VIP 預設效期天數的後備值 */
    static final int DEFAULT_VIP_DAYS = 365;

    private final AppSettingService appSettingService;

    /** 注入參數讀寫服務 */
    public CreditPolicy(AppSettingService appSettingService) {
        this.appSettingService = appSettingService;
    }

    /** 首次登入的初始贈點；0 為合法值（關閉贈點），負值夾到 0 */
    public int signupGrant() {
        return Math.max(0, appSettingService.getInt(
            AppSettingService.CREDIT_SIGNUP_GRANT, DEFAULT_SIGNUP_GRANT));
    }

    /**
     * PREMIUM 文章的全域預設解鎖點數。
     *
     * <p>永遠 ≥ 1：0 或負數會讓 {@code credits >= cost} 恆真，等於所有進階內容免費。
     * 這個下限是 paywall 的最後一道防線，不把正確性寄望在後台設定上。</p>
     */
    public int premiumCost() {
        return Math.max(1, appSettingService.getInt(
            AppSettingService.CREDIT_PREMIUM_COST, DEFAULT_PREMIUM_COST));
    }

    /** 邀請成功的獎勵點數；0 為合法值（關閉邀請獎勵），負值夾到 0 */
    public int referralReward() {
        return Math.max(0, appSettingService.getInt(
            AppSettingService.CREDIT_REFERRAL_REWARD, DEFAULT_REFERRAL_REWARD));
    }

    /** VIP 預設效期天數；永遠 ≥ 1，否則後台授予 VIP 後會立即過期 */
    public int vipDefaultDays() {
        return Math.max(1, appSettingService.getInt(
            AppSettingService.VIP_DEFAULT_DAYS, DEFAULT_VIP_DAYS));
    }

    /**
     * 取得該文章的解鎖成本：文章自訂值優先，未設定（0）時退回全域預設。
     *
     * <p>結果永遠 ≥ 1。PREMIUM 卻成本為 0 理論上已被資料庫的
     * {@code ck_campaign_premium_cost} 擋掉，但該 CHECK 只檢查
     * {@code tier <> 'PREMIUM' OR credit_cost > 0}，遇到 {@code tier} 大小寫
     * 不符時會整條放行，所以這裡不能假設資料庫已經把關。</p>
     */
    public int costOf(Campaign campaign) {
        if (campaign.getCreditCost() > 0) {
            return campaign.getCreditCost();
        }
        return premiumCost();
    }
}
```

- [ ] **Step 4: 執行測試確認通過**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=CreditPolicyTest`
Expected: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: 把 `AccessDecisionService` 的成本計算改委派 `CreditPolicy`**

`AccessDecisionService` 目前有 `DEFAULT_PREMIUM_COST` 常數與 `resolveCost(Campaign)`。改法：

1. 刪除 `private static final int DEFAULT_PREMIUM_COST = 10;`
2. 建構子的 `AppSettingService appSettingService` 參數換成 `CreditPolicy creditPolicy`，欄位同步更換。
3. `resolveCost` 改為委派，並保留公開簽章（`ReaderPageController` 仍在呼叫）：

```java
    /**
     * 取得該文章的解鎖成本。
     *
     * <p>實際計算與下限保護已收斂到 {@link CreditPolicy#costOf(Campaign)}；
     * 本方法保留為既有呼叫端的入口，不再自行讀取 app_setting。</p>
     */
    public int resolveCost(Campaign campaign) {
        return creditPolicy.costOf(campaign);
    }
```

4. 移除不再使用的 `import world.springai.survey.AppSettingService;`

- [ ] **Step 6: 把 `ReaderAccountService` 的贈點金額改用 `CreditPolicy`**

1. 刪除 `private static final int DEFAULT_SIGNUP_GRANT = 300;`
2. 建構子的 `AppSettingService appSettingService` 參數換成 `CreditPolicy creditPolicy`，欄位同步更換。
3. `createWithSignupGrant` 內：

```java
        int grant = creditPolicy.signupGrant();
```

4. **贈點為 0 時不要寫 credit_txn**——寫一筆 delta=0 的帳本紀錄只會讓對帳畫面出現無意義的列：

```java
    /** 建立新帳戶並發放初始贈點；餘額與帳本在同一交易內同步 */
    private Reader createWithSignupGrant(String email, OffsetDateTime now) {
        Reader reader = new Reader(email, generateUniqueReferralCode());
        reader = readerRepository.save(reader);

        int grant = creditPolicy.signupGrant();
        // 後台可把贈點調成 0（關閉贈點）；此時不寫帳本，避免留下 delta=0 的無意義紀錄
        if (grant > 0) {
            creditTxnRepository.save(new CreditTxn(
                reader.getId(), grant, CreditTxn.REASON_SIGNUP_GRANT, null, "首次登入初始贈點"));
            reader.setCredits(grant);
        }

        log.info("建立讀者帳戶 {} 並發放初始贈點 {} 點", email, grant);
        return reader;
    }
```

5. 移除不再使用的 `import world.springai.survey.AppSettingService;`

- [ ] **Step 7: 修正兩個既有測試檔的建構子呼叫**

`AccessDecisionServiceTest` 與 `ReaderAccountServiceTest` 目前 mock `AppSettingService` 並 stub `getInt(...)`。改為 mock `CreditPolicy` 並 stub 對應方法：

- `AccessDecisionServiceTest`：把 `AppSettingService settings = mock(AppSettingService.class);` 改為 `CreditPolicy creditPolicy = mock(CreditPolicy.class);`，把 `when(settings.getInt(eq(AppSettingService.CREDIT_PREMIUM_COST), anyInt())).thenReturn(N)` 改為 `when(creditPolicy.costOf(any())).thenReturn(N)`，建構子改傳 `creditPolicy`。
- `ReaderAccountServiceTest`：同理，`when(settings.getInt(eq(AppSettingService.CREDIT_SIGNUP_GRANT), anyInt())).thenReturn(N)` 改為 `when(creditPolicy.signupGrant()).thenReturn(N)`。
- **若原本有「app_setting 查不到時採用後備值」的測試**：那個行為現在屬於 `CreditPolicy`，已由 `CreditPolicyTest` 覆蓋。把該測試從這兩個檔案刪除，不要改成 mock 後備值——mock 後的「後備值」是測試自己設的，那種測試什麼都證明不了。

- [ ] **Step 8: 跑全套測試確認無迴歸**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B clean test`
Expected: `BUILD SUCCESS`、`Failures: 0, Errors: 0, Skipped: 0`

`PackageDependencyTest` 必須仍綠：`CreditPolicy` 在 `reader` 且 import `newsletter.Campaign`（reader → newsletter 是授權方向）與根 package 的 `AppSettingService`，皆合法。

- [ ] **Step 9: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/reader/CreditPolicy.java \
        survey-backend/src/main/java/world/springai/survey/reader/AccessDecisionService.java \
        survey-backend/src/main/java/world/springai/survey/reader/ReaderAccountService.java \
        survey-backend/src/test/java/world/springai/survey/reader/CreditPolicyTest.java \
        survey-backend/src/test/java/world/springai/survey/reader/AccessDecisionServiceTest.java \
        survey-backend/src/test/java/world/springai/survey/reader/ReaderAccountServiceTest.java
git commit -m "refactor(reader): 點數參數收斂為 CreditPolicy 唯一來源"
```

---

## Task 2: 邀請歸因寫入（`answers._ref`）

**背景**：`static/reader/index.html` 目前**已經**送出 `body.ref = ref`，但 `SurveyRequest` 沒有 `ref` 欄位，Jackson 預設忽略未知屬性，所以推薦碼一直被靜默丟棄。本任務把它接起來。

歸因必須存在名單中心側而非 `reader` 側：confirm 發生時被邀者可能還沒有 `reader` 列（`reader` 只在首次登入才建立）。spec §5.4 指定放進 `survey_response.answers` jsonb 的 `_ref` 鍵——既有欄位，不需 migration。

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/form/SurveyRequest.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/form/SurveyController.java`
- Test: `survey-backend/src/test/java/world/springai/survey/form/SurveyControllerTest.java`（既有）

**Interfaces:**
- Produces:
  - `SurveyRequest.getRef() → String` / `setRef(String)`
  - `answers` 內的系統鍵前綴約定：`_` 開頭為系統欄位，不是問卷答案
  - 常數 `SurveyController.REF_KEY = "_ref"`（package-private，供測試引用）

- [ ] **Step 1: 寫失敗測試**

加到 `SurveyControllerTest`：

```java
    /** 帶 ref 的訂閱請求應把推薦碼寫進 answers 的 _ref 鍵 */
    @Test
    void refIsStoredIntoAnswersUnderscoreRef() throws Exception {
        mvc.perform(post("/api/survey")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"invitee@example.com","consent":true,"source":"newsletter","ref":"ABCD2345"}
                    """))
           .andExpect(status().isCreated());

        ArgumentCaptor<SurveyResponse> captor = ArgumentCaptor.forClass(SurveyResponse.class);
        verify(repository).save(captor.capture());
        assertEquals("ABCD2345", captor.getValue().getAnswers().get("_ref"));
    }

    /**
     * answers 原本為 null 時也要能放進 _ref。
     *
     * <p>這是實際會走到的路徑：/r/ 訂閱表單只送 email、consent、source、ref，
     * 完全沒有問卷答案，所以 answers 是 null。若實作直接對 null 呼叫 put()
     * 會 NPE，而這條路徑正是邀請功能的主線。</p>
     */
    @Test
    void refIsStoredEvenWhenAnswersAbsent() throws Exception {
        mvc.perform(post("/api/survey")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"invitee2@example.com","consent":true,"source":"newsletter","ref":"WXYZ6789"}
                    """))
           .andExpect(status().isCreated());

        ArgumentCaptor<SurveyResponse> captor = ArgumentCaptor.forClass(SurveyResponse.class);
        verify(repository).save(captor.capture());
        assertNotNull(captor.getValue().getAnswers());
        assertEquals("WXYZ6789", captor.getValue().getAnswers().get("_ref"));
    }

    /** 沒有 ref 時不可留下空的 _ref 鍵，否則後續「有沒有推薦人」的判斷要多處理空字串 */
    @Test
    void absentRefLeavesNoUnderscoreRefKey() throws Exception {
        mvc.perform(post("/api/survey")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"plain@example.com","consent":true,"source":"newsletter"}
                    """))
           .andExpect(status().isCreated());

        ArgumentCaptor<SurveyResponse> captor = ArgumentCaptor.forClass(SurveyResponse.class);
        verify(repository).save(captor.capture());
        Map<String, Object> answers = captor.getValue().getAnswers();
        assertTrue(answers == null || !answers.containsKey("_ref"));
    }

    /** 空白字串的 ref 視同沒有 ref */
    @Test
    void blankRefIsIgnored() throws Exception {
        mvc.perform(post("/api/survey")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"blank@example.com","consent":true,"source":"newsletter","ref":"   "}
                    """))
           .andExpect(status().isCreated());

        ArgumentCaptor<SurveyResponse> captor = ArgumentCaptor.forClass(SurveyResponse.class);
        verify(repository).save(captor.capture());
        Map<String, Object> answers = captor.getValue().getAnswers();
        assertTrue(answers == null || !answers.containsKey("_ref"));
    }

    /**
     * 公開統計必須排除底線開頭的系統鍵。
     *
     * <p>沒有這道過濾，`_ref` 會被當成一道問卷答案出現在 /api/survey/stats
     * 對外公開的圖表裡——那不只是難看，而是把讀者的邀請碼關係公開了。</p>
     */
    @Test
    void statsExcludeUnderscorePrefixedSystemKeys() {
        SurveyResponse withRef = new SurveyResponse();
        withRef.setSource("survey_form");
        withRef.setAnswers(Map.of("status", "在職", "_ref", "ABCD2345"));
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(withRef));

        SurveyStats stats = controller.stats();

        // status 應被統計；_ref 不得出現在任何一組 bucket 的標籤中
        assertTrue(stats.status().stream().anyMatch(b -> "在職".equals(b.label())));
        assertTrue(stats.status().stream().noneMatch(b -> b.label().startsWith("_")));
    }
```

> 需要的 import：`org.mockito.ArgumentCaptor`、`java.util.Map`、`static org.junit.jupiter.api.Assertions.assertNotNull`、`assertTrue`、`assertEquals`。若 `SurveyControllerTest` 尚未有 `controller` 這個直接實例（僅用 MockMvc），請沿用該檔案既有的建立方式，不要新建一套。

- [ ] **Step 2: 執行測試確認失敗**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=SurveyControllerTest`
Expected: 新增的測試失敗，`_ref` 為 null（`ref` 尚未被讀取）

- [ ] **Step 3: `SurveyRequest` 新增 `ref` 欄位**

```java
    /**
     * 推薦碼（邀請連結的 ?ref= 值）。
     *
     * <p>不做格式驗證：推薦碼是否存在由 confirm 時查 reader 表決定，
     * 這裡收到亂碼只會導致查不到推薦人而不發獎，沒有安全影響。
     * 在此加 @Pattern 反而會讓亂改連結的人收到 400，訂閱直接失敗。</p>
     */
    private String ref;
```

以及對應的 getter / setter：

```java
    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
```

- [ ] **Step 4: `SurveyController.submit` 寫入 `_ref`**

在 `entity.setAnswers(req.getAnswers());` 之後、`repository.save(entity)` 之前插入：

```java
        // 邀請歸因：把推薦碼放進 answers 的系統鍵 _ref。
        // 為什麼存在名單中心而不是 reader 表：confirm 發生時被邀者可能還沒有
        // reader 列（reader 只在首次登入才建立），歸因必須先存得下來。
        if (StringUtils.hasText(req.getRef())) {
            // answers 對「只訂閱不填問卷」與匯入名單皆為 null，必須先初始化。
            // 用可變 Map：req.getAnswers() 來自 Jackson 反序列化，雖然通常可變，
            // 但不該依賴這點——複製一份最安全。
            Map<String, Object> answers = entity.getAnswers() == null
                ? new HashMap<>()
                : new HashMap<>(entity.getAnswers());
            answers.put(REF_KEY, req.getRef().trim());
            entity.setAnswers(answers);
        }
```

在類別常數區加入：

```java
    /**
     * 邀請歸因在 answers 內的鍵名。
     *
     * <p>底線前綴用於區別「系統欄位」與問卷答案——{@link #stats()} 會排除
     * 所有底線開頭的鍵，否則推薦碼會出現在對外公開的統計圖表裡。</p>
     */
    static final String REF_KEY = "_ref";
```

需要新增 import：`java.util.HashMap`。

- [ ] **Step 5: `stats()` 排除底線開頭的系統鍵**

目前 `status` 的取值是 `r.getAnswers().get("status")`，本身不受影響。但 spec §5.4 明訂「問卷統計須排除底線開頭的鍵」，而未來若有人把 `answers` 攤平統計就會外洩。做法是在取值處建立單一的過濾入口：

```java
    /**
     * 取出某一題的答案值；底線開頭的系統鍵一律視為不存在。
     *
     * <p>統計是對外公開、無需金鑰的端點，而 answers 內混有系統欄位
     * （目前是邀請歸因 {@link #REF_KEY}）。集中在這個方法過濾，讓日後
     * 新增統計題目不必各自記得排除——忘記一次就是把讀者的邀請關係公開。</p>
     */
    private static Object answerOf(SurveyResponse r, String key) {
        if (key.startsWith("_") || r.getAnswers() == null) {
            return null;
        }
        return r.getAnswers().get(key);
    }
```

並把 `stats()` 內的：

```java
        Stream<String> status = all.stream()
            .map(r -> r.getAnswers() == null ? null : r.getAnswers().get("status"))
```

改為：

```java
        Stream<String> status = all.stream()
            .map(r -> answerOf(r, "status"))
```

- [ ] **Step 6: 執行測試確認通過**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=SurveyControllerTest`
Expected: 全部通過，`Failures: 0, Errors: 0`

- [ ] **Step 7: 讓 `/r/` 訂閱表單的 `ref` 真的送達**

`static/reader/index.html` 已經有 `if (ref) { body.ref = ref; }`，行為現在才真正生效。把該處的過期註解改掉：

```javascript
  // 訂閱表單：沿用既有 /api/survey 端點（consent=true 表示同意接收）
  // 邀請碼 ?ref= 隨訂閱一起送出，後端寫入 answers._ref；
  // 獎勵在被邀者點確認信後才發（見 /r/rules）
```

- [ ] **Step 8: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/form/SurveyRequest.java \
        survey-backend/src/main/java/world/springai/survey/form/SurveyController.java \
        survey-backend/src/main/resources/static/reader/index.html \
        survey-backend/src/test/java/world/springai/survey/form/SurveyControllerTest.java
git commit -m "feat(form): 訂閱時寫入邀請歸因 answers._ref，公開統計排除系統鍵"
```

---

## Task 3: confirm / unsubscribe 搬進 `audience` + 連結組裝收斂 + 確認事件

**背景**：這是階段 B 改期過來的搬遷（spec §3）。`confirm` / `unsubscribe` 本質是**名單同意管理**，不是問卷表單功能，卻長在 `form/SurveyController`；同時有四處各自用字串拼出這兩個路由。結果是「package 拆解線」目前只是名義上的——`audience` 與 `newsletter` 仍以字串形式依賴 `form` 的路由，而 `PackageDependencyTest` 抓不到字串依賴。

本任務同時為 Task 4 鋪路：`confirm` 成功要發推薦獎勵，而獎勵屬 `reader`。**`audience` 不得 import `reader`**，所以只能發布事件讓 `reader` 監聽。

> **URL 路徑絕對不可變更。** 已寄出的邀請信與行銷信內含 `/api/survey/confirm` 與 `/api/survey/unsubscribe`，改路徑等於讓所有在途連結失效——那是不可回復的名單損失。本任務只搬程式碼，不搬網址。

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/audience/SubscriptionController.java`
- Create: `survey-backend/src/main/java/world/springai/survey/audience/SubscriptionLinkBuilder.java`
- Create: `survey-backend/src/main/java/world/springai/survey/audience/SubscriptionConfirmedEvent.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/form/SurveyController.java`（移除兩個端點與兩個 HTML 常數）
- Modify: `survey-backend/src/main/java/world/springai/survey/audience/WelcomeMailService.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/newsletter/CampaignService.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/newsletter/InviteService.java`
- Create: `survey-backend/src/test/java/world/springai/survey/audience/SubscriptionControllerTest.java`
- Create: `survey-backend/src/test/java/world/springai/survey/audience/SubscriptionLinkBuilderTest.java`
- Modify: `survey-backend/src/test/java/world/springai/survey/form/SurveyControllerTest.java`（移除已搬走的測試）

**Interfaces:**
- Consumes: `UnsubscribeTokenService.sign(String email) → String`、`verify(String email, String token) → boolean`；`SurveyResponseRepository.confirmByEmail(String) → int`、`unsubscribeByEmail(String) → int`、`touchEngagement(String, OffsetDateTime) → int`
- Produces:
  - `SubscriptionLinkBuilder.CONFIRM_PATH = "/api/survey/confirm"`、`UNSUBSCRIBE_PATH = "/api/survey/unsubscribe"`（public 常數）
  - `SubscriptionLinkBuilder.confirmLink(String email) → String`
  - `SubscriptionLinkBuilder.unsubscribeLink(String email) → String`
  - `SubscriptionLinkBuilder.previewUnsubscribeLink() → String`
  - `SubscriptionConfirmedEvent`（record，欄位 `String email`——**已正規化為小寫**）
  - 端點 `GET /api/survey/confirm`、`GET /api/survey/unsubscribe`（路徑與原本完全相同）

- [ ] **Step 1: 寫 `SubscriptionLinkBuilderTest`（失敗測試）**

```java
package world.springai.survey.audience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 連結組裝的唯一擁有者。
 *
 * <p>測試重點是「路徑字串與 URL 編碼」——這兩者一錯，寄出去的信裡就是死連結，
 * 而且信已經送到讀者信箱，無法回收。</p>
 */
class SubscriptionLinkBuilderTest {

    /** 固定回傳假簽章的 token 服務 */
    private final UnsubscribeTokenService tokenService = mock(UnsubscribeTokenService.class);

    /** 建一個綁定固定對外網址的 builder */
    private SubscriptionLinkBuilder builder() {
        when(tokenService.sign(anyString())).thenReturn("SIG");
        return new SubscriptionLinkBuilder(tokenService, "https://survey.example.com");
    }

    /**
     * 確認連結的路徑必須完全是 /api/survey/confirm。
     *
     * <p>已寄出的邀請信內含這個路徑，改動等於讓在途連結全部失效，
     * 所以這裡用<b>字面值</b>斷言而不是引用 CONFIRM_PATH 常數——
     * 用常數比對的話，改常數會讓測試跟著改而永遠不會變紅。</p>
     */
    @Test
    void confirmLinkUsesTheExactLegacyPath() {
        assertEquals("https://survey.example.com/api/survey/confirm?email=a%40b.com&t=SIG",
            builder().confirmLink("a@b.com"));
    }

    /** 退訂連結的路徑必須完全是 /api/survey/unsubscribe，同上理由 */
    @Test
    void unsubscribeLinkUsesTheExactLegacyPath() {
        assertEquals("https://survey.example.com/api/survey/unsubscribe?email=a%40b.com&t=SIG",
            builder().unsubscribeLink("a@b.com"));
    }

    /** email 內的加號必須編碼成 %2B，否則收件端會解讀成空白而查不到名單 */
    @Test
    void encodesPlusSignInEmail() {
        assertTrue(builder().unsubscribeLink("a+tag@b.com").contains("email=a%2Btag%40b.com"));
    }

    /** 預覽用連結不得帶真實簽章：預覽內容會顯示在後台，不該外流可用的退訂 token */
    @Test
    void previewLinkCarriesNoRealSignature() {
        String link = builder().previewUnsubscribeLink();
        assertTrue(link.contains("t=preview"));
        assertTrue(link.contains("preview%40example.com"));
    }
}
```

- [ ] **Step 2: 執行確認失敗**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=SubscriptionLinkBuilderTest`
Expected: 編譯失敗，`cannot find symbol: class SubscriptionLinkBuilder`

- [ ] **Step 3: 實作 `SubscriptionLinkBuilder`**

```java
package world.springai.survey.audience;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * confirm / unsubscribe 連結組裝的唯一擁有者。
 *
 * <p><b>為什麼要收斂</b>：這兩個路由原本被四處各自用字串拼出
 * （{@code WelcomeMailService}、{@code CampaignService} 兩處、{@code InviteService}），
 * 其中三處在 {@code newsletter} 與 {@code audience}，卻依賴當時長在 {@code form}
 * 的端點。那是一條 {@code PackageDependencyTest} 抓不到的隱形反向依賴——
 * 字串不是 import。收斂到本類後，路由只有一個擁有者，拆解線才真的可拆。</p>
 *
 * <p><b>路徑刻意保留 {@code /api/survey/} 前綴</b>：已寄出的信件內含這些網址，
 * 改路徑會讓所有在途的確認信與退訂連結失效。網址是對外契約，
 * 程式碼搬家不該改契約。</p>
 */
@Component
public class SubscriptionLinkBuilder {

    /** 確認訂閱端點路徑（對外契約，不可變更） */
    public static final String CONFIRM_PATH = "/api/survey/confirm";
    /** 退訂端點路徑（對外契約，不可變更） */
    public static final String UNSUBSCRIBE_PATH = "/api/survey/unsubscribe";

    private final UnsubscribeTokenService tokenService;
    /** 對外網址，用於組出完整連結 */
    private final String publicBaseUrl;

    /** 注入 HMAC 簽章服務與對外網址 */
    public SubscriptionLinkBuilder(UnsubscribeTokenService tokenService,
                                   @Value("${app.public-base-url}") String publicBaseUrl) {
        this.tokenService = tokenService;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** 組確認訂閱連結（含該 email 的個人化 HMAC 簽章） */
    public String confirmLink(String email) {
        return link(CONFIRM_PATH, email, tokenService.sign(email));
    }

    /** 組退訂連結（含該 email 的個人化 HMAC 簽章） */
    public String unsubscribeLink(String email) {
        return link(UNSUBSCRIBE_PATH, email, tokenService.sign(email));
    }

    /**
     * 後台預覽用的退訂連結：假 email、假簽章。
     *
     * <p>刻意不帶真實簽章——預覽內容會顯示在後台頁面與測試信中，
     * 不該讓一個可用的退訂 token 隨預覽外流。</p>
     */
    public String previewUnsubscribeLink() {
        return link(UNSUBSCRIBE_PATH, "preview@example.com", "preview");
    }

    /** 組出 {base}{path}?email={urlencoded}&t={token} */
    private String link(String path, String email, String token) {
        return publicBaseUrl + path
            + "?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
            + "&t=" + token;
    }
}
```

- [ ] **Step 4: 執行確認通過**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=SubscriptionLinkBuilderTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: 建立 `SubscriptionConfirmedEvent`**

```java
package world.springai.survey.audience;

/**
 * 確認訂閱成功事件。
 *
 * <p><b>為什麼需要事件而不是直接呼叫</b>：確認訂閱成功時要發放推薦獎勵，
 * 而獎勵屬於 {@code reader}（點數帳本）。但 spec §3 規定 {@code audience}
 * 是下層，不得依賴 {@code reader}——直接呼叫會讓 {@code PackageDependencyTest}
 * 變紅，而那條規則存在的理由（拆服務時的拆解線）是真的。
 * 事件讓 {@code audience} 只宣告「發生了什麼」，不需要知道有誰在乎。</p>
 *
 * @param email 已確認訂閱者的 email，<b>已正規化為小寫並去除前後空白</b>。
 *              正規化在發布端完成，訂閱端不必各自處理——否則每個監聽器
 *              都要記得正規化，忘記一次就是查不到人而靜默不發獎。
 */
public record SubscriptionConfirmedEvent(String email) {
}
```

- [ ] **Step 6: 寫 `SubscriptionControllerTest`（失敗測試）**

```java
package world.springai.survey.audience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * confirm / unsubscribe 端點：自 form.SurveyController 搬入。
 *
 * <p>原有的四項安全性質必須完整保留：防偽（HMAC）、冪等、不洩漏名單
 * （不論結果一律相同回應）、回應頁不回顯使用者輸入。</p>
 */
class SubscriptionControllerTest {

    private SurveyResponseRepository repository;
    private UnsubscribeTokenService tokenService;
    private ApplicationEventPublisher publisher;
    private SubscriptionController controller;

    @BeforeEach
    void setUp() {
        repository = mock(SurveyResponseRepository.class);
        tokenService = mock(UnsubscribeTokenService.class);
        publisher = mock(ApplicationEventPublisher.class);
        controller = new SubscriptionController(repository, tokenService, publisher);
    }

    /** 簽章正確時確認訂閱，並發布事件供 reader 發放推薦獎勵 */
    @Test
    void validConfirmUpdatesConsentAndPublishesEvent() {
        when(tokenService.verify(eq("a@b.com"), eq("SIG"))).thenReturn(true);
        when(repository.confirmByEmail("a@b.com")).thenReturn(1);

        ResponseEntity<String> response = controller.confirm("a@b.com", "SIG");

        assertEquals(200, response.getStatusCode().value());
        verify(repository).confirmByEmail("a@b.com");
        verify(publisher).publishEvent(new SubscriptionConfirmedEvent("a@b.com"));
    }

    /** 事件的 email 必須是正規化後的小寫，否則監聽端查不到推薦關係 */
    @Test
    void publishedEventCarriesNormalizedEmail() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(true);
        when(repository.confirmByEmail(anyString())).thenReturn(1);

        controller.confirm("  MixedCase@B.COM  ", "SIG");

        verify(publisher).publishEvent(new SubscriptionConfirmedEvent("mixedcase@b.com"));
    }

    /**
     * 簽章不符時不得寫入、不得發事件，但回應必須與成功時完全相同。
     *
     * <p>回應相同是刻意的：若失敗回不同訊息，任何人都能用這個端點
     * 逐一測試「某個 email 在不在名單裡」。</p>
     */
    @Test
    void invalidSignatureChangesNothingButLooksIdentical() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(false);

        ResponseEntity<String> response = controller.confirm("a@b.com", "BAD");

        assertEquals(200, response.getStatusCode().value());
        verify(repository, never()).confirmByEmail(anyString());
        verify(publisher, never()).publishEvent(any(SubscriptionConfirmedEvent.class));
    }

    /**
     * 名單中查無此 email（confirmByEmail 回 0）時不得發事件。
     *
     * <p>沒有這道檢查，任何持有合法簽章的 email 每次點擊都會觸發一次獎勵計算。
     * 雖然 Task 4 的冪等檢查會擋掉重複發獎，但「名單裡沒有這筆卻發出
     * 確認事件」本身就是錯的狀態，不該靠下游擋。</p>
     */
    @Test
    void confirmingUnknownEmailPublishesNoEvent() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(true);
        when(repository.confirmByEmail(anyString())).thenReturn(0);

        controller.confirm("ghost@b.com", "SIG");

        verify(publisher, never()).publishEvent(any(SubscriptionConfirmedEvent.class));
    }

    /** 確認訂閱是高可靠的參與度訊號，必須更新 last_engaged_at */
    @Test
    void validConfirmTouchesEngagement() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(true);
        when(repository.confirmByEmail(anyString())).thenReturn(1);

        controller.confirm("a@b.com", "SIG");

        verify(repository).touchEngagement(eq("a@b.com"), any());
    }

    /** 簽章正確時退訂 */
    @Test
    void validUnsubscribeMarksUnsubscribed() {
        when(tokenService.verify(eq("a@b.com"), eq("SIG"))).thenReturn(true);

        ResponseEntity<String> response = controller.unsubscribe("a@b.com", "SIG");

        assertEquals(200, response.getStatusCode().value());
        verify(repository).unsubscribeByEmail("a@b.com");
    }

    /** 退訂簽章不符時不得寫入，回應仍相同 */
    @Test
    void invalidUnsubscribeSignatureChangesNothing() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(false);

        controller.unsubscribe("a@b.com", "BAD");

        verify(repository, never()).unsubscribeByEmail(anyString());
    }

    /** email 缺漏時兩個端點都不得寫入（也不能 NPE） */
    @Test
    void missingEmailIsHandledSafely() {
        controller.confirm(null, "SIG");
        controller.unsubscribe(null, "SIG");

        verify(repository, never()).confirmByEmail(anyString());
        verify(repository, never()).unsubscribeByEmail(anyString());
    }

    /**
     * 回應頁不得回顯使用者輸入。
     *
     * <p>用一段 XSS 載荷當 email，斷言它不出現在回應中——不是斷言
     * 「有做跳脫」，而是斷言「根本不回顯」。固定字串頁面沒有回顯管道，
     * 這個測試守的是「日後有人為了友善提示而把 email 印出來」。</p>
     */
    @Test
    void responseNeverReflectsUserInput() {
        when(tokenService.verify(anyString(), anyString())).thenReturn(true);
        when(repository.confirmByEmail(anyString())).thenReturn(1);
        String payload = "<script>alert(1)</script>@b.com";

        assertFalse(controller.confirm(payload, "SIG").getBody().contains("script>alert"));
        assertFalse(controller.unsubscribe(payload, "SIG").getBody().contains("script>alert"));
    }
}
```

- [ ] **Step 7: 實作 `SubscriptionController`**

```java
package world.springai.survey.audience;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * 名單同意管理的公開端點：確認訂閱與退訂。
 *
 * <p>自 {@code form.SurveyController} 搬入——這兩個端點本質是名單中心的職責，
 * 不是問卷表單功能。<b>URL 路徑刻意保持不變</b>（見
 * {@link SubscriptionLinkBuilder}）：已寄出的信件內含這些網址。</p>
 *
 * <p>四項必須保留的安全性質：
 * ① 防偽——僅當 HMAC 簽章正確才執行；
 * ② 冪等——重複點擊、名單查無此人都回相同成功頁；
 * ③ 不洩漏名單——不論結果（含簽章不符）一律相同回應與 200，
 *    否則此端點會變成「某個 email 在不在名單裡」的查詢工具；
 * ④ 回應頁為固定字串、不回顯使用者輸入。</p>
 */
@RestController
public class SubscriptionController {

    private final SurveyResponseRepository repository;
    private final UnsubscribeTokenService tokenService;
    /** 確認成功後發布事件；讓 reader 能在不被 audience 依賴的前提下發放推薦獎勵 */
    private final ApplicationEventPublisher eventPublisher;

    /** 注入名單資料層、HMAC 驗證與事件發布器 */
    public SubscriptionController(SurveyResponseRepository repository,
                                  UnsubscribeTokenService tokenService,
                                  ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.tokenService = tokenService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 公開確認訂閱端點：使用者從邀請信點擊確認連結（GET）後以瀏覽器開啟，故回 HTML。
     *
     * <p>只有在「簽章正確」<b>且</b>「名單中確實有這筆」時才發布事件——
     * confirmByEmail 回 0 代表查無此 email，此時發事件會讓下游對一個
     * 不存在的訂閱者計算獎勵。</p>
     */
    @GetMapping(value = SubscriptionLinkBuilder.CONFIRM_PATH, produces = "text/html; charset=UTF-8")
    public ResponseEntity<String> confirm(@RequestParam(value = "email", required = false) String email,
                                          @RequestParam(value = "t", required = false) String token) {
        if (StringUtils.hasText(email) && tokenService.verify(email, token)) {
            String normalized = email.trim().toLowerCase();
            int affected = repository.confirmByEmail(normalized);
            if (affected > 0) {
                // 確認訂閱是高可靠的參與度訊號（spec §5.10）
                repository.touchEngagement(normalized, OffsetDateTime.now());
                eventPublisher.publishEvent(new SubscriptionConfirmedEvent(normalized));
            }
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
            .body(CONFIRM_HTML);
    }

    /**
     * 公開退訂端點：使用者從行銷信件點擊退訂連結（GET）後以瀏覽器開啟，故回 HTML。
     * 設計與確認端點一致：防偽、冪等、不洩漏名單、固定回應頁。
     */
    @GetMapping(value = SubscriptionLinkBuilder.UNSUBSCRIBE_PATH, produces = "text/html; charset=UTF-8")
    public ResponseEntity<String> unsubscribe(@RequestParam(value = "email", required = false) String email,
                                              @RequestParam(value = "t", required = false) String token) {
        if (StringUtils.hasText(email) && tokenService.verify(email, token)) {
            repository.unsubscribeByEmail(email.trim().toLowerCase());
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
            .body(UNSUBSCRIBE_HTML);
    }

    // 以下兩個常數自 form/SurveyController 逐字搬入，見 Step 7 的說明
}
```

接著把 `form/SurveyController` 內的 `CONFIRM_HTML` 與 `UNSUBSCRIBE_HTML` 兩個 `private static final String` 常數**原封不動**搬進本類（連 Javadoc 一起），取代上方那行註解。

> **不要改文案。** 這兩頁讀者已經看過，而且既有測試可能斷言其內容；搬移任務同時改文案會讓「搬移是否無損」變得無法驗證。

- [ ] **Step 8: 從 `form/SurveyController` 移除已搬走的部分**

刪除：`unsubscribe` 方法、`confirm` 方法、`CONFIRM_HTML`、`UNSUBSCRIBE_HTML` 常數，以及不再使用的 `UnsubscribeTokenService tokenService` 欄位與建構子參數、`import world.springai.survey.audience.UnsubscribeTokenService;`。

搬移後 `SurveyController` 只剩 `submit` / `list` / `stats` 與 CSV 輔助方法。**不要留下未使用的欄位或 import**——編譯器不會報錯，但那是搬移沒做完的痕跡。

- [ ] **Step 9: 四處連結組裝改用 `SubscriptionLinkBuilder`**

1. `audience/WelcomeMailService`：刪掉自己組連結的私有方法（含 `URLEncoder` 那段），建構子改注入 `SubscriptionLinkBuilder`，取代原本的 `UnsubscribeTokenService` + `publicBaseUrl` 兩個依賴；呼叫處改 `linkBuilder.unsubscribeLink(email)`。
2. `newsletter/CampaignService` 的 `preview`（原第 69 行）：改成 `emailTemplate.wrap(body, linkBuilder.previewUnsubscribeLink())`。
3. `newsletter/CampaignService.renderFor`（原第 353–355 行）：改成 `linkBuilder.unsubscribeLink(email)`。
4. `newsletter/InviteService.buildConfirmLink`：刪除該私有方法，兩處呼叫改為 `linkBuilder.confirmLink(r.getEmail())`。

三個類的建構子都要加 `SubscriptionLinkBuilder linkBuilder` 參數；若某個類因此不再需要 `UnsubscribeTokenService` 或 `publicBaseUrl`，一併移除。

依賴方向檢查：`newsletter` → `audience` 是授權方向，`PackageDependencyTest` 會通過。

- [ ] **Step 10: 修正受影響的既有測試**

- `SurveyControllerTest`：移除 confirm / unsubscribe 相關測試（已搬到 `SubscriptionControllerTest`），修正建構子呼叫。
- `WelcomeMailServiceTest`、`CampaignServiceTest`、`InviteServiceTest`：建構子多了 `SubscriptionLinkBuilder`，改為傳入 mock 並 stub 對應方法。**若原測試斷言了完整連結字串**，改成 stub builder 回傳固定值（如 `"https://x/confirm?t=SIG"`）再斷言該值出現在信件內文——連結格式的正確性已由 `SubscriptionLinkBuilderTest` 負責，不要在三個地方重複斷言同一件事。

- [ ] **Step 11: 跑全套測試**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B clean test`
Expected: `BUILD SUCCESS`、`Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 12: 用真實啟動驗證路由沒有重複或消失**

搬移端點最容易犯的錯是「兩個 controller 同時宣告同一個路徑」——那會在**啟動時**才炸（`Ambiguous mapping`），單元測試抓不到。

Run:

```bash
cd survey-backend && JAVA_HOME=/d/java/jdk-21 timeout 90 mvn -B spring-boot:run 2>&1 | grep -E "Started SurveyApplication|Ambiguous mapping|APPLICATION FAILED"
```

Expected: 出現 `Started SurveyApplication`，且**沒有** `Ambiguous mapping`

- [ ] **Step 13: Commit（注意 git index 陷阱）**

> **本分支的前身已經三次踩到同一個陷阱**：搬移檔案 + 修改內容時，`git add` 若引用了已不存在的舊路徑，整個 staging 會靜默中止，commit 捕捉到的是舊內容，而 `mvn test` **抓不到**（它編譯工作樹，不是 git index）。
>
> 因此：commit 後必須執行 `git diff HEAD --stat` 比對工作樹，**無輸出才算成功**。

```bash
git add -A survey-backend/src/main/java/world/springai/survey/ \
           survey-backend/src/test/java/world/springai/survey/
git commit -m "refactor(audience): confirm/unsubscribe 端點搬入 audience 並收斂連結組裝"
git diff HEAD --stat -- survey-backend/   # 必須無輸出
```

---

## Task 4: 邀請獎勵發放（冪等，事件驅動）

**背景**：spec §5.4 要求「confirm 成功的同一交易內：推薦人 +100 點」。實作上**刻意不用同一交易**，理由見 Step 5 的設計說明——這是本階段唯一偏離 spec 的地方，Step 10 會同步回寫 spec。

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/reader/ReferralService.java`
- Create: `survey-backend/src/main/java/world/springai/survey/reader/ReferralRewardListener.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/ReaderRepository.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/CreditTxnRepository.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/ReaderAccountService.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/audience/SurveyResponseRepository.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReferralServiceTest.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReaderAccountServiceTest.java`（既有）

**Interfaces:**
- Consumes: `SubscriptionConfirmedEvent`、`CreditPolicy.referralReward()`
- Produces:
  - `ReferralService.RewardOutcome`（enum：`REWARDED` / `NO_REFERRER` / `ALREADY_REWARDED` / `SELF_INVITE` / `REFERRER_NOT_FOUND`）
  - `ReferralService.rewardFor(String inviteeEmail) → RewardOutcome`
  - `ReferralService.referralCodeOf(SurveyResponse) → Optional<String>`（static）
  - `ReferralService.stats(Long referrerId) → ReferralStats`（record：`int invitedCount, int earnedCredits`）
  - `ReaderRepository.addCredits(Long id, int delta) → int`
  - `CreditTxnRepository.existsByReasonAndNote(String reason, String note) → boolean`
  - `CreditTxnRepository.findByReaderIdAndReasonOrderByCreatedAtDesc(Long readerId, String reason) → List<CreditTxn>`
  - `SurveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(String email) → Optional<SurveyResponse>`

- [ ] **Step 1: 寫 `ReferralServiceTest`（失敗測試）**

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 邀請獎勵：發放條件與冪等 */
class ReferralServiceTest {

    private static final long REFERRER_ID = 7L;

    private SurveyResponseRepository surveyResponseRepository;
    private ReaderRepository readerRepository;
    private CreditTxnRepository creditTxnRepository;
    private CreditPolicy creditPolicy;
    private ReferralService service;

    @BeforeEach
    void setUp() {
        surveyResponseRepository = mock(SurveyResponseRepository.class);
        readerRepository = mock(ReaderRepository.class);
        creditTxnRepository = mock(CreditTxnRepository.class);
        creditPolicy = mock(CreditPolicy.class);
        when(creditPolicy.referralReward()).thenReturn(100);
        service = new ReferralService(surveyResponseRepository, readerRepository,
            creditTxnRepository, creditPolicy);
    }

    /** 建一筆帶（或不帶）推薦碼的名單資料 */
    private SurveyResponse invitee(String email, String refCode) {
        SurveyResponse r = new SurveyResponse();
        r.setEmail(email);
        if (refCode != null) {
            r.setAnswers(Map.of("_ref", refCode));
        }
        return r;
    }

    /** 建一個帶 id 的推薦人 */
    private Reader referrer(String email, String code) {
        Reader reader = new Reader(email, code);
        ReflectionTestUtils.setField(reader, "id", REFERRER_ID);
        return reader;
    }

    /** 讓「查得到被邀者且帶推薦碼、查得到推薦人」成立 */
    private void givenReferralChain(String inviteeEmail, String referrerEmail, String code) {
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(invitee(inviteeEmail, code)));
        when(readerRepository.findByReferralCode(code))
            .thenReturn(Optional.of(referrer(referrerEmail, code)));
    }

    /** 正常路徑：發放獎勵、寫帳本、加餘額 */
    @Test
    void rewardsReferrerOnFirstConfirm() {
        givenReferralChain("invitee@b.com", "host@b.com", "CODE1234");
        when(creditTxnRepository.existsByReasonAndNote(CreditTxn.REASON_REFERRAL, "invitee@b.com"))
            .thenReturn(false);
        when(readerRepository.addCredits(REFERRER_ID, 100)).thenReturn(1);

        assertEquals(ReferralService.RewardOutcome.REWARDED, service.rewardFor("invitee@b.com"));

        verify(readerRepository).addCredits(REFERRER_ID, 100);
        verify(creditTxnRepository).save(any(CreditTxn.class));
    }

    /**
     * 帳本的 note 必須恰好是被邀者 email。
     *
     * <p>note 同時是冪等檢查的鍵（{@code existsByReasonAndNote}）。若實作寫成
     * 「邀請 invitee@b.com」這種帶前綴的可讀字串，而冪等檢查用的是裸 email，
     * 檢查就永遠回 false——重複點確認信會重複發獎。兩者必須是同一個值。</p>
     */
    @Test
    void ledgerNoteIsExactlyTheInviteeEmail() {
        givenReferralChain("invitee@b.com", "host@b.com", "CODE1234");
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(1);

        service.rewardFor("invitee@b.com");

        ArgumentCaptor<CreditTxn> captor = ArgumentCaptor.forClass(CreditTxn.class);
        verify(creditTxnRepository).save(captor.capture());
        assertEquals("invitee@b.com", captor.getValue().getNote());
        assertEquals(CreditTxn.REASON_REFERRAL, captor.getValue().getReason());
        assertEquals(100, captor.getValue().getDelta());
        assertEquals(REFERRER_ID, captor.getValue().getReaderId());
    }

    /** 重複 confirm 不重複發獎 */
    @Test
    void alreadyRewardedIsIdempotent() {
        givenReferralChain("invitee@b.com", "host@b.com", "CODE1234");
        when(creditTxnRepository.existsByReasonAndNote(CreditTxn.REASON_REFERRAL, "invitee@b.com"))
            .thenReturn(true);

        assertEquals(ReferralService.RewardOutcome.ALREADY_REWARDED, service.rewardFor("invitee@b.com"));

        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
        verify(creditTxnRepository, never()).save(any(CreditTxn.class));
    }

    /** 沒有推薦碼就沒有獎勵（絕大多數訂閱者走這條） */
    @Test
    void noReferralCodeMeansNoReward() {
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(invitee("plain@b.com", null)));

        assertEquals(ReferralService.RewardOutcome.NO_REFERRER, service.rewardFor("plain@b.com"));
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    /** 推薦碼查不到對應讀者（亂改連結）時不發獎，也不可拋例外 */
    @Test
    void unknownReferralCodeMeansNoReward() {
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(invitee("invitee@b.com", "GARBAGE1")));
        when(readerRepository.findByReferralCode("GARBAGE1")).thenReturn(Optional.empty());

        assertEquals(ReferralService.RewardOutcome.REFERRER_NOT_FOUND, service.rewardFor("invitee@b.com"));
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    /**
     * 自我邀請不得發獎。
     *
     * <p>用自己的邀請碼訂閱自己的 email：冪等鍵（被邀者 email）雖然會擋掉
     * 第二次，但第一次仍會發獎，所以必須明確拒絕。</p>
     */
    @Test
    void selfInviteIsRejected() {
        givenReferralChain("host@b.com", "host@b.com", "CODE1234");

        assertEquals(ReferralService.RewardOutcome.SELF_INVITE, service.rewardFor("host@b.com"));
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    /** 自我邀請的大小寫變體同樣要擋掉 */
    @Test
    void selfInviteWithDifferentCaseIsRejected() {
        givenReferralChain("HOST@B.com", "host@b.com", "CODE1234");

        assertEquals(ReferralService.RewardOutcome.SELF_INVITE, service.rewardFor("HOST@B.com"));
    }

    /** 名單中查無此 email 時不發獎（理論上不會發生，因為事件只在 affected > 0 時發） */
    @Test
    void unknownInviteeMeansNoReward() {
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.empty());

        assertEquals(ReferralService.RewardOutcome.NO_REFERRER, service.rewardFor("ghost@b.com"));
    }

    /**
     * 獎勵設為 0 時不寫帳本、不佔用冪等鍵。
     *
     * <p>後台可把邀請獎勵調成 0（關閉此機制）。若此時仍寫一筆 delta=0 的帳本，
     * 冪等鍵就被佔用了——日後把獎勵調回 100，這位推薦人再也拿不到
     * 這位被邀者的獎勵。</p>
     */
    @Test
    void zeroRewardWritesNoLedgerEntry() {
        when(creditPolicy.referralReward()).thenReturn(0);
        givenReferralChain("invitee@b.com", "host@b.com", "CODE1234");

        service.rewardFor("invitee@b.com");

        verify(creditTxnRepository, never()).save(any(CreditTxn.class));
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    /**
     * 加點的受影響筆數為 0 時必須拋例外，不可回報成功。
     *
     * <p>addCredits 回 0 代表推薦人那一列不存在。若靜默回 REWARDED，帳本會出現
     * 一筆沒有對應餘額變動的紀錄，而 reader.credits 是 credit_txn 的物化總和
     * ——「餘額永遠可由帳本重算稽核」這個不變式就破了。</p>
     */
    @Test
    void failedCreditUpdateThrows() {
        givenReferralChain("invitee@b.com", "host@b.com", "CODE1234");
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.rewardFor("invitee@b.com"));
    }

    /** 邀請成效統計：筆數與點數總和 */
    @Test
    void statsSumsRewardTransactions() {
        when(creditTxnRepository.findByReaderIdAndReasonOrderByCreatedAtDesc(
                REFERRER_ID, CreditTxn.REASON_REFERRAL))
            .thenReturn(java.util.List.of(
                new CreditTxn(REFERRER_ID, 100, CreditTxn.REASON_REFERRAL, null, "a@b.com"),
                new CreditTxn(REFERRER_ID, 100, CreditTxn.REASON_REFERRAL, null, "c@b.com")));

        ReferralService.ReferralStats stats = service.stats(REFERRER_ID);

        assertEquals(2, stats.invitedCount());
        assertEquals(200, stats.earnedCredits());
    }
}
```

- [ ] **Step 2: 執行確認失敗**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=ReferralServiceTest`
Expected: 編譯失敗，`cannot find symbol: class ReferralService`

- [ ] **Step 3: 補上三個 repository 方法**

`reader/ReaderRepository.java`（檔頭需加對應 import）：

```java
    /**
     * 加點（正數）。回傳受影響筆數，0 表示該讀者不存在。
     *
     * <p>用條件式 UPDATE 而不是「讀出來改再存回」：後者在併發下會覆蓋
     * 另一筆交易剛寫入的餘額（讀到舊值 → 加 → 寫回，另一筆的變動就消失了）。
     * 這裡直接讓資料庫算 {@code credits = credits + :delta}。</p>
     */
    @Modifying
    @Transactional
    @Query("update Reader r set r.credits = r.credits + :delta where r.id = :id")
    int addCredits(@Param("id") Long id, @Param("delta") int delta);
```

`reader/CreditTxnRepository.java`：

```java
    /**
     * 是否已有這筆原因與註記的交易——邀請獎勵的冪等鍵。
     *
     * <p>note 存的是被邀者 email，所以「同一個被邀者只發一次獎」由此保證。
     * 重複點擊確認信、退訂後再確認，都不會重複發獎。</p>
     */
    boolean existsByReasonAndNote(String reason, String note);

    /** 某讀者某類交易的明細（新到舊）；邀請成效統計使用 */
    List<CreditTxn> findByReaderIdAndReasonOrderByCreatedAtDesc(Long readerId, String reason);
```

`audience/SurveyResponseRepository.java`：

```java
    /**
     * 依 email 取最新一筆名單資料（不分大小寫）。
     *
     * <p>刻意用 findFirst + OrderBy 而非 findByEmail：同一個 email 可能有多筆
     * （已在正式資料中實測到有人相隔一個月填了兩次問卷）。若寫成回傳
     * Optional 的 findByEmailIgnoreCase，遇到多筆時 Spring Data 會拋
     * IncorrectResultSizeDataAccessException，讓確認訂閱整個失敗。</p>
     */
    Optional<SurveyResponse> findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(String email);
```

> `SurveyResponseRepository` 目前沒有 import `Optional`，請補 `import java.util.Optional;`。

- [ ] **Step 4: 實作 `ReferralService`**

```java
package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import java.util.List;
import java.util.Optional;

/**
 * 邀請歸因與獎勵發放。
 *
 * <p>獎勵只在「被邀者真的點了自己信箱裡的確認信」時發放（spec §5.4），
 * 因此本服務由 {@link ReferralRewardListener} 在確認訂閱事件後呼叫，
 * 而不是在訂閱時就發——填假 email 拿不到點數。這也是第一版不設邀請人數
 * 上限的理由：濫用面很窄。</p>
 */
@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);

    /**
     * 邀請歸因在 survey_response.answers 內的鍵名。
     *
     * <p><b>與 {@code form.SurveyController.REF_KEY} 必須永遠一致。</b>
     * 刻意不 import 那個常數：{@code reader} 依賴 {@code form} 會讓上層
     * package 互相糾纏（spec §3 只授權 reader → audience/mail/newsletter）。
     * 這是「重複一個字串常數」與「多一條跨 package 依賴」的取捨，選擇前者，
     * 並以本註解與兩處各自的測試守住一致性。</p>
     */
    static final String REF_KEY = "_ref";

    /** 發放結果 */
    public enum RewardOutcome {
        /** 已發放獎勵 */
        REWARDED,
        /** 沒有推薦人（無 _ref、名單查無此人，或獎勵設為 0）——絕大多數訂閱者走這條 */
        NO_REFERRER,
        /** 這位被邀者的獎勵已發過 */
        ALREADY_REWARDED,
        /** 推薦碼指向自己 */
        SELF_INVITE,
        /** 推薦碼查不到對應讀者（亂改連結） */
        REFERRER_NOT_FOUND
    }

    /** 邀請成效：成功邀請人數與累計獲得點數 */
    public record ReferralStats(int invitedCount, int earnedCredits) {}

    private final SurveyResponseRepository surveyResponseRepository;
    private final ReaderRepository readerRepository;
    private final CreditTxnRepository creditTxnRepository;
    private final CreditPolicy creditPolicy;

    /** 注入名單中心、讀者、帳本與點數參數 */
    public ReferralService(SurveyResponseRepository surveyResponseRepository,
                           ReaderRepository readerRepository,
                           CreditTxnRepository creditTxnRepository,
                           CreditPolicy creditPolicy) {
        this.surveyResponseRepository = surveyResponseRepository;
        this.readerRepository = readerRepository;
        this.creditTxnRepository = creditTxnRepository;
        this.creditPolicy = creditPolicy;
    }

    /**
     * 為某位「剛完成確認訂閱」的被邀者發放推薦獎勵。
     *
     * <p>冪等：以 {@code (reason='REFERRAL', note=被邀者 email)} 判斷是否已發過，
     * 所以重複點確認信、退訂後再確認，都不會重複發獎。</p>
     *
     * @param inviteeEmail 被邀者 email（呼叫端已正規化，此處仍再正規化一次以防直接呼叫）
     */
    @Transactional
    public RewardOutcome rewardFor(String inviteeEmail) {
        String invitee = normalize(inviteeEmail);

        Optional<String> code = surveyResponseRepository
            .findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(invitee)
            .flatMap(ReferralService::referralCodeOf);
        if (code.isEmpty()) {
            return RewardOutcome.NO_REFERRER;
        }

        Optional<Reader> found = readerRepository.findByReferralCode(code.get());
        if (found.isEmpty()) {
            log.info("推薦碼 {} 查不到對應讀者，不發獎", code.get());
            return RewardOutcome.REFERRER_NOT_FOUND;
        }
        Reader referrer = found.get();

        // 自我邀請：用自己的碼訂閱自己的 email。冪等鍵雖然會擋住第二次，
        // 但第一次仍會發獎，所以必須明確拒絕。
        if (normalize(referrer.getEmail()).equals(invitee)) {
            log.info("推薦碼 {} 指向自己，不發獎", code.get());
            return RewardOutcome.SELF_INVITE;
        }

        // 冪等檢查：note 存的正是被邀者 email
        if (creditTxnRepository.existsByReasonAndNote(CreditTxn.REASON_REFERRAL, invitee)) {
            return RewardOutcome.ALREADY_REWARDED;
        }

        int reward = creditPolicy.referralReward();
        // 後台可把獎勵調成 0（關閉此機制）。此時不寫帳本也不佔用冪等鍵——
        // 否則日後把獎勵調回 100，這位被邀者的獎勵就永遠拿不到了。
        if (reward <= 0) {
            log.info("邀請獎勵設定為 {}，不發獎", reward);
            return RewardOutcome.NO_REFERRER;
        }

        int updated = readerRepository.addCredits(referrer.getId(), reward);
        if (updated == 0) {
            // 推薦人那一列不存在。若靜默成功，帳本會多一筆沒有對應餘額變動的紀錄，
            // 而 reader.credits 是 credit_txn 的物化總和——不變式會破。
            throw new IllegalStateException(
                "加點失敗：推薦人 id=" + referrer.getId() + " 不存在");
        }
        creditTxnRepository.save(new CreditTxn(
            referrer.getId(), reward, CreditTxn.REASON_REFERRAL, null, invitee));

        log.info("邀請獎勵已發放：推薦人 id={} +{} 點（被邀者 {}）",
            referrer.getId(), reward, invitee);
        return RewardOutcome.REWARDED;
    }

    /** 從名單資料取出推薦碼；無 answers、無 _ref 或為空白時回 empty */
    public static Optional<String> referralCodeOf(SurveyResponse response) {
        if (response.getAnswers() == null) {
            return Optional.empty();
        }
        Object raw = response.getAnswers().get(REF_KEY);
        if (raw == null) {
            return Optional.empty();
        }
        String code = String.valueOf(raw).trim();
        return code.isEmpty() ? Optional.empty() : Optional.of(code);
    }

    /** 某位推薦人的邀請成效：成功邀請人數與累計獲得點數 */
    public ReferralStats stats(Long referrerId) {
        List<CreditTxn> rewards = creditTxnRepository
            .findByReaderIdAndReasonOrderByCreatedAtDesc(referrerId, CreditTxn.REASON_REFERRAL);
        int earned = rewards.stream().mapToInt(CreditTxn::getDelta).sum();
        return new ReferralStats(rewards.size(), earned);
    }

    /** email 正規化：去前後空白並轉小寫 */
    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
```

- [ ] **Step 5: 實作 `ReferralRewardListener`**

```java
package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import world.springai.survey.audience.SubscriptionConfirmedEvent;

/**
 * 確認訂閱後發放推薦獎勵。
 *
 * <p><b>為什麼是監聽器而不是直接呼叫</b>：確認訂閱的端點在 {@code audience}，
 * 而 spec §3 規定 {@code audience} 不得依賴 {@code reader}。事件讓依賴方向
 * 保持 {@code reader → audience}（本類 import audience 的事件型別），
 * 拆解線不被破壞。</p>
 *
 * <p><b>為什麼用 AFTER_COMMIT + REQUIRES_NEW，而非 spec §5.4 寫的「同一交易內」</b>：
 * 確認訂閱是<b>不可重建的同意紀錄</b>——讀者親手點了信裡的連結，這件事一旦
 * 沒記下來，就只能重新徵求同意。推薦獎勵則是可補救的（後台能手動加點）。
 * 若兩者同交易，發獎時任何錯誤都會連帶回滾確認訂閱，等於用「可補救的失敗」
 * 換掉「不可補救的資產」，方向是錯的。</p>
 *
 * <p>此處也不能只是「在監聽器內 try/catch 但仍同交易」：一旦內層寫入失敗，
 * Spring 會把交易標記為 rollback-only，外層即使吞掉例外，commit 仍會以
 * {@code UnexpectedRollbackException} 收場。要真正隔離就必須分開交易。</p>
 *
 * <p>代價是「確認成功但發獎失敗」會靜默損失一次獎勵，因此失敗以 ERROR 記錄，
 * 並在 spec §5.4 記載此偏離與補救方式。</p>
 */
@Component
public class ReferralRewardListener {

    private static final Logger log = LoggerFactory.getLogger(ReferralRewardListener.class);

    private final ReferralService referralService;

    /** 注入獎勵發放服務 */
    public ReferralRewardListener(ReferralService referralService) {
        this.referralService = referralService;
    }

    /**
     * 在確認訂閱的交易提交後，於獨立交易中發放獎勵。
     *
     * <p>例外一律在此吞掉並記為 ERROR：此時確認訂閱已經提交，讓例外往上拋
     * 只會出現在無人查看的事件發布堆疊裡，而讀者早已看到「訂閱確認成功」的頁面。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubscriptionConfirmed(SubscriptionConfirmedEvent event) {
        try {
            ReferralService.RewardOutcome outcome = referralService.rewardFor(event.email());
            // NO_REFERRER 是絕大多數訂閱者的情形，不值得每次都寫一行 log
            if (outcome != ReferralService.RewardOutcome.NO_REFERRER) {
                log.info("確認訂閱後的邀請獎勵處理結果：{}（{}）", outcome, event.email());
            }
        } catch (Exception e) {
            log.error("邀請獎勵發放失敗（確認訂閱已完成，可於後台手動加點）：{}",
                event.email(), e);
        }
    }
}
```

- [ ] **Step 6: 執行測試確認通過**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=ReferralServiceTest`
Expected: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 7: 首次登入時把 `_ref` 搬到 `reader.referred_by`**

spec §5.4 要求建帳戶時記錄推薦人。在 `ReaderAccountService.createWithSignupGrant` 內、第一次 `readerRepository.save(reader)` **之前**插入：

```java
        // 把名單中心的推薦歸因搬到讀者帳戶（spec §5.4）。
        // 這是「誰邀請了我」的長期紀錄；獎勵發放不看這個欄位，而是看
        // credit_txn 的冪等鍵——兩者職責不同，不要合併。
        surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email)
            .flatMap(ReferralService::referralCodeOf)
            .flatMap(readerRepository::findByReferralCode)
            // 自我邀請不記錄，與 ReferralService 的判定保持一致
            .filter(referrer -> !referrer.getEmail().equalsIgnoreCase(email))
            .ifPresent(referrer -> reader.setReferredBy(referrer.getId()));
```

> 順序很重要：`reader` 必須先有 `referredBy` 才 `save`，否則要多一次 UPDATE。

- [ ] **Step 8: 在 `ReaderAccountServiceTest` 補兩個案例**

該檔案的 `setUp()` 已建好 `readerRepository` / `creditTxnRepository` / `surveyResponseRepository` mock，且 `readerRepository.save(...)` 會補上 id=1L。沿用它們（`Reader` 有 `setId`，不需 `ReflectionTestUtils`）：

```java
    /** 首次登入時應把名單中心的推薦碼轉成 reader.referred_by */
    @Test
    void firstLoginRecordsReferrer() {
        when(readerRepository.findByEmailIgnoreCase("newbie@example.com")).thenReturn(Optional.empty());

        // 名單中心有這筆訂閱，且帶著推薦碼
        world.springai.survey.audience.SurveyResponse row =
            new world.springai.survey.audience.SurveyResponse();
        row.setEmail("newbie@example.com");
        row.setAnswers(java.util.Map.of("_ref", "HOSTCODE"));
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("newbie@example.com"))
            .thenReturn(Optional.of(row));

        // 推薦碼對應的推薦人
        Reader referrer = new Reader("host@example.com", "HOSTCODE");
        referrer.setId(7L);
        when(readerRepository.findByReferralCode("HOSTCODE")).thenReturn(Optional.of(referrer));

        service.findOrCreate("newbie@example.com", NOW);

        ArgumentCaptor<Reader> saved = ArgumentCaptor.forClass(Reader.class);
        verify(readerRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertEquals(7L, saved.getValue().getReferredBy());
    }

    /**
     * 沒有推薦碼時 referred_by 必須為 null。
     *
     * <p>絕大多數訂閱者走這條路徑。寫入 0 或空值會讓「有沒有推薦人」的判斷
     * 在日後每個使用點都要多處理一種情況。</p>
     */
    @Test
    void firstLoginWithoutReferrerLeavesReferredByNull() {
        when(readerRepository.findByEmailIgnoreCase("plain@example.com")).thenReturn(Optional.empty());
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.empty());

        Reader reader = service.findOrCreate("plain@example.com", NOW);

        org.junit.jupiter.api.Assertions.assertNull(reader.getReferredBy());
    }

    /** 自我邀請不記錄 referred_by（與 ReferralService 的判定保持一致） */
    @Test
    void selfReferralIsNotRecorded() {
        when(readerRepository.findByEmailIgnoreCase("host@example.com")).thenReturn(Optional.empty());

        world.springai.survey.audience.SurveyResponse row =
            new world.springai.survey.audience.SurveyResponse();
        row.setEmail("host@example.com");
        row.setAnswers(java.util.Map.of("_ref", "HOSTCODE"));
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.of(row));

        Reader self = new Reader("host@example.com", "HOSTCODE");
        self.setId(7L);
        when(readerRepository.findByReferralCode("HOSTCODE")).thenReturn(Optional.of(self));

        Reader reader = service.findOrCreate("host@example.com", NOW);

        org.junit.jupiter.api.Assertions.assertNull(reader.getReferredBy());
    }
```

> `setUp()` 內的 `new ReaderAccountService(...)` 第 4 個引數要從 `appSettingService` 改成 `creditPolicy` mock（Task 1 已改建構子），並把 `when(appSettingService.getInt(...))` 改成 `when(creditPolicy.signupGrant()).thenReturn(300)`。

- [ ] **Step 9: 跑全套測試**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B clean test`
Expected: `BUILD SUCCESS`、`Failures: 0, Errors: 0, Skipped: 0`

`PackageDependencyTest` 必須綠：`reader` import `audience.SubscriptionConfirmedEvent` 是授權方向；`audience` 沒有任何 `reader` 的 import。

- [ ] **Step 10: 以真實啟動驗證衍生查詢方法名合法**

`findFirstByEmailIgnoreCaseOrderByCreatedAtDesc`、`existsByReasonAndNote`、`findByReaderIdAndReasonOrderByCreatedAtDesc` 都是 Spring Data 衍生查詢——**方法名拼錯只會在啟動時炸**（`PropertyReferenceException`），而測試把 repository mock 掉了，完全抓不到。

Run:

```bash
cd survey-backend && JAVA_HOME=/d/java/jdk-21 timeout 90 mvn -B spring-boot:run 2>&1 | grep -E "Started SurveyApplication|PropertyReferenceException|APPLICATION FAILED"
```

Expected: 出現 `Started SurveyApplication`，且**沒有** `PropertyReferenceException`

- [ ] **Step 11: 同步 spec 的偏離記錄**

在 spec `### 5.4 邀請歸因與獎勵` 的「實作注意」清單之後，補一段引用區塊，說明：

1. 實作用 `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW`，**不是**「同一交易內」。
2. 理由：確認訂閱是不可重建的同意紀錄，推薦獎勵可由後台手動加點補救；同交易會讓可補救的失敗回滾掉不可補救的資產。
3. 也不能只在監聽器內 try/catch 而仍同交易——rollback-only 標記會讓 commit 以 `UnexpectedRollbackException` 收場。
4. 代價與補救：發獎失敗會靜默損失一次獎勵，以 ERROR 記錄，後台手動加點。
5. 依賴方向：事件是為了不讓 `audience` 依賴 `reader`（spec §3）。

- [ ] **Step 12: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/reader/ \
        survey-backend/src/main/java/world/springai/survey/audience/SurveyResponseRepository.java \
        survey-backend/src/test/java/world/springai/survey/reader/ \
        docs/superpowers/specs/2026-07-25-reader-newsletter-platform-design.md
git commit -m "feat(reader): 邀請獎勵以確認訂閱事件驅動，冪等且與確認訂閱交易隔離"
git diff HEAD --stat -- survey-backend/ docs/   # 必須無輸出
```

---

## Task 5: 扣點解鎖的交易邊界（`UnlockService`）

**背景**：這是本階段唯一會扣點的地方，也是唯一需要交易保證的寫入路徑。spec §5.2 明訂與 `CampaignService` 相反的處理——那裡有無法回滾的 ZSend 副作用所以刻意不加 `@Transactional`，這裡純本地狀態，所以必須是交易性的。

**三個併發防線**（缺一不可）：

1. `article_access` 的 `UNIQUE (reader_id, campaign_id)`——同一篇不可能扣兩次。
2. 條件式 `UPDATE ... WHERE credits >= :cost`——回 0 列代表併發下餘額已被扣走。
3. **扣款先於插入紀錄**——若插入撞 UNIQUE，扣款隨交易一起回滾。反過來寫（先插入後扣款）在插入成功、扣款失敗時，會留下「有解鎖紀錄但沒扣點」的免費解鎖。

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/reader/UnlockService.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/ReaderRepository.java`（新增條件式扣點）
- Test: `survey-backend/src/test/java/world/springai/survey/reader/UnlockServiceTest.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/UnlockConstraintTest.java`（連真實 PostgreSQL 驗證兩道資料庫層防線）

**Interfaces:**
- Consumes: `CreditPolicy.costOf(Campaign)`、`ArticleAccessRepository.existsByReaderIdAndCampaignId`、`CreditTxnRepository.save`、`SurveyResponseRepository.touchEngagement`
- Produces:
  - `UnlockService.Outcome`（enum：`UNLOCKED` / `ALREADY_UNLOCKED` / `INSUFFICIENT_CREDITS`）
  - `UnlockService.Result`（record：`Outcome outcome, int cost, int credits`——`credits` 為操作後餘額）
  - `UnlockService.unlock(Long readerId, Campaign campaign, OffsetDateTime now) → Result`
  - `ReaderRepository.deductCredits(Long id, int cost) → int`

- [ ] **Step 1: 寫 `UnlockServiceTest`（失敗測試）**

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import world.springai.survey.audience.SurveyResponseRepository;
import world.springai.survey.newsletter.Campaign;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 扣點解鎖：正常路徑、冪等、餘額不足、併發 */
class UnlockServiceTest {

    private static final long READER_ID = 3L;
    private static final long CAMPAIGN_ID = 42L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-01T10:00:00Z");

    private ReaderRepository readerRepository;
    private ArticleAccessRepository articleAccessRepository;
    private CreditTxnRepository creditTxnRepository;
    private SurveyResponseRepository surveyResponseRepository;
    private CreditPolicy creditPolicy;
    private UnlockService service;

    @BeforeEach
    void setUp() {
        readerRepository = mock(ReaderRepository.class);
        articleAccessRepository = mock(ArticleAccessRepository.class);
        creditTxnRepository = mock(CreditTxnRepository.class);
        surveyResponseRepository = mock(SurveyResponseRepository.class);
        creditPolicy = mock(CreditPolicy.class);
        when(creditPolicy.costOf(any())).thenReturn(10);
        service = new UnlockService(readerRepository, articleAccessRepository,
            creditTxnRepository, surveyResponseRepository, creditPolicy);
    }

    /** 建一篇已發布的 PREMIUM 文章 */
    private Campaign article() {
        Campaign c = new Campaign("主旨", "# 內容", "<h1>內容</h1>", null, null, "now", null, 0, "sent");
        c.setTier(Campaign.TIER_PREMIUM);
        c.setCreditCost(10);
        c.setPublishedAt(NOW.minusDays(1));
        ReflectionTestUtils.setField(c, "id", CAMPAIGN_ID);
        return c;
    }

    /** 讓資料庫中的讀者有指定餘額 */
    private void givenReaderWithCredits(int credits) {
        Reader reader = new Reader("r@b.com", "CODE1234");
        ReflectionTestUtils.setField(reader, "id", READER_ID);
        reader.setCredits(credits);
        when(readerRepository.findById(READER_ID)).thenReturn(Optional.of(reader));
    }

    /** 正常路徑：扣點、寫帳本、寫解鎖紀錄、更新參與度 */
    @Test
    void unlocksAndDeductsCredits() {
        givenReaderWithCredits(300);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(READER_ID, CAMPAIGN_ID)).thenReturn(false);
        when(readerRepository.deductCredits(READER_ID, 10)).thenReturn(1);

        UnlockService.Result result = service.unlock(READER_ID, article(), NOW);

        assertEquals(UnlockService.Outcome.UNLOCKED, result.outcome());
        assertEquals(10, result.cost());
        assertEquals(290, result.credits());
        verify(readerRepository).deductCredits(READER_ID, 10);
        verify(articleAccessRepository).saveAndFlush(any(ArticleAccess.class));
        verify(creditTxnRepository).save(any(CreditTxn.class));
        verify(surveyResponseRepository).touchEngagement(anyString(), any());
    }

    /**
     * 扣款必須發生在寫入解鎖紀錄之前。
     *
     * <p>反過來的順序（先插入 article_access 再扣款）在扣款失敗時，
     * 會留下「有解鎖紀錄但沒扣點」的永久免費解鎖——而 article_access
     * 同時是 ALREADY_UNLOCKED 的判斷來源，這個狀態無法自我修復。</p>
     */
    @Test
    void deductsBeforeWritingAccessRecord() {
        givenReaderWithCredits(300);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), anyLong())).thenReturn(false);
        when(readerRepository.deductCredits(anyLong(), anyInt())).thenReturn(1);

        service.unlock(READER_ID, article(), NOW);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(readerRepository, articleAccessRepository);
        inOrder.verify(readerRepository).deductCredits(READER_ID, 10);
        inOrder.verify(articleAccessRepository).saveAndFlush(any(ArticleAccess.class));
    }

    /** 帳本的 delta 必須是負數，且帶上 campaign_id 供對帳 */
    @Test
    void ledgerRecordsNegativeDeltaWithCampaignId() {
        givenReaderWithCredits(300);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), anyLong())).thenReturn(false);
        when(readerRepository.deductCredits(anyLong(), anyInt())).thenReturn(1);

        service.unlock(READER_ID, article(), NOW);

        ArgumentCaptor<CreditTxn> captor = ArgumentCaptor.forClass(CreditTxn.class);
        verify(creditTxnRepository).save(captor.capture());
        assertEquals(-10, captor.getValue().getDelta());
        assertEquals(CreditTxn.REASON_READ, captor.getValue().getReason());
        assertEquals(CAMPAIGN_ID, captor.getValue().getCampaignId());
    }

    /** 解鎖紀錄要記下當時實扣點數，日後調參數不影響已解鎖的歷史成本 */
    @Test
    void accessRecordStoresActualCost() {
        givenReaderWithCredits(300);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), anyLong())).thenReturn(false);
        when(readerRepository.deductCredits(anyLong(), anyInt())).thenReturn(1);

        service.unlock(READER_ID, article(), NOW);

        ArgumentCaptor<ArticleAccess> captor = ArgumentCaptor.forClass(ArticleAccess.class);
        verify(articleAccessRepository).saveAndFlush(captor.capture());
        assertEquals(10, captor.getValue().getCost());
        assertEquals(CAMPAIGN_ID, captor.getValue().getCampaignId());
    }

    /** 已解鎖過就不再扣點，且完全不寫入任何東西 */
    @Test
    void alreadyUnlockedDeductsNothing() {
        givenReaderWithCredits(300);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(READER_ID, CAMPAIGN_ID)).thenReturn(true);

        UnlockService.Result result = service.unlock(READER_ID, article(), NOW);

        assertEquals(UnlockService.Outcome.ALREADY_UNLOCKED, result.outcome());
        assertEquals(300, result.credits());
        verify(readerRepository, never()).deductCredits(anyLong(), anyInt());
        verify(articleAccessRepository, never()).saveAndFlush(any());
        verify(creditTxnRepository, never()).save(any());
    }

    /** 餘額不足時不寫入任何東西，並回報還差幾點所需的目前餘額 */
    @Test
    void insufficientCreditsWritesNothing() {
        givenReaderWithCredits(3);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), anyLong())).thenReturn(false);

        UnlockService.Result result = service.unlock(READER_ID, article(), NOW);

        assertEquals(UnlockService.Outcome.INSUFFICIENT_CREDITS, result.outcome());
        assertEquals(3, result.credits());
        assertEquals(10, result.cost());
        verify(readerRepository, never()).deductCredits(anyLong(), anyInt());
        verify(creditTxnRepository, never()).save(any());
    }

    /**
     * 餘額判斷必須用資料庫的即時值，不可信任呼叫端傳入的物件。
     *
     * <p>呼叫端的 Reader 來自 session cookie 解析，可能是幾分鐘前的快照。
     * 若用它判斷餘額，讀者在另一個分頁扣過點之後，這裡會用舊餘額放行，
     * 最後靠條件式 UPDATE 才擋下——那條防線應該留給真正的併發，
     * 而不是被當成常態的餘額檢查。這個測試以「傳入 id 而非 Reader」
     * 的簽章從介面層面保證這件事。</p>
     */
    @Test
    void readsCreditsFromDatabaseNotFromCaller() {
        givenReaderWithCredits(5);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), anyLong())).thenReturn(false);

        UnlockService.Result result = service.unlock(READER_ID, article(), NOW);

        assertEquals(UnlockService.Outcome.INSUFFICIENT_CREDITS, result.outcome());
        verify(readerRepository).findById(READER_ID);
    }

    /**
     * 條件式扣款回 0 列（併發下餘額已被扣走）必須拋例外讓交易回滾。
     *
     * <p>不可回報 INSUFFICIENT_CREDITS 了事：此時餘額檢查已經通過，
     * 回 0 列代表在檢查與扣款之間有另一筆交易扣走了點數。若靜默處理，
     * 呼叫端會以為是普通的餘額不足，而真正的問題（同一讀者的併發解鎖）
     * 就被藏起來了。</p>
     */
    @Test
    void concurrentDeductionFailureThrows() {
        givenReaderWithCredits(300);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), anyLong())).thenReturn(false);
        when(readerRepository.deductCredits(anyLong(), anyInt())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.unlock(READER_ID, article(), NOW));
        verify(creditTxnRepository, never()).save(any());
    }

    /**
     * UNIQUE 撞擊必須往外拋，不可在本方法內吞掉。
     *
     * <p>這是 Spring 交易語意的陷阱：`saveAndFlush` 觸發
     * DataIntegrityViolationException 時，交易已被標記為 rollback-only。
     * 若在 @Transactional 方法內捕捉並正常回傳 ALREADY_UNLOCKED，
     * commit 階段會改拋 UnexpectedRollbackException——呼叫端收到的是
     * 一個看起來毫無關聯的錯誤。正確做法是讓它往外拋，由交易邊界
     * <b>之外</b>的 controller 判讀（見 Task 7）。</p>
     */
    @Test
    void uniqueViolationPropagatesInsteadOfBeingSwallowed() {
        givenReaderWithCredits(300);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), anyLong())).thenReturn(false);
        when(readerRepository.deductCredits(anyLong(), anyInt())).thenReturn(1);
        when(articleAccessRepository.saveAndFlush(any()))
            .thenThrow(new DataIntegrityViolationException("uq_article_access"));

        assertThrows(DataIntegrityViolationException.class,
            () -> service.unlock(READER_ID, article(), NOW));
    }

    /** 讀者不存在時拋例外（session 有效但帳戶被刪，屬異常狀態） */
    @Test
    void unknownReaderThrows() {
        when(readerRepository.findById(READER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.unlock(READER_ID, article(), NOW));
    }

    /**
     * 未發布的文章不可解鎖。
     *
     * <p>授權判斷在 AccessDecisionService，但扣點是不可逆的寫入，
     * 不該完全信任呼叫端已經判斷過——草稿被解鎖會讓讀者付了點數卻
     * 看到未完成的內容，而點數已經扣掉了。</p>
     */
    @Test
    void unpublishedArticleCannotBeUnlocked() {
        givenReaderWithCredits(300);
        Campaign draft = article();
        draft.setPublishedAt(null);

        assertThrows(IllegalStateException.class, () -> service.unlock(READER_ID, draft, NOW));
        verify(readerRepository, never()).deductCredits(anyLong(), anyInt());
    }

    /**
     * BASIC 文章不可解鎖。
     *
     * <p>BASIC 對訂閱者本來就免費，對它扣點是純粹的損失。這也是
     * fail-closed 的方向：只有精確等於 PREMIUM 才允許扣點解鎖，
     * tier 打錯字時寧可拒絕解鎖（讀者看得到免費區、可回報問題），
     * 也不要對一個判斷不明的文章扣點。</p>
     */
    @Test
    void basicArticleCannotBeUnlocked() {
        givenReaderWithCredits(300);
        Campaign basic = article();
        basic.setTier(Campaign.TIER_BASIC);

        assertThrows(IllegalStateException.class, () -> service.unlock(READER_ID, basic, NOW));
        verify(readerRepository, never()).deductCredits(anyLong(), anyInt());
    }
}
```

- [ ] **Step 2: 執行確認失敗**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=UnlockServiceTest`
Expected: 編譯失敗，`cannot find symbol: class UnlockService`

- [ ] **Step 3: 在 `ReaderRepository` 新增條件式扣點**

```java
    /**
     * 條件式扣點：只有餘額足夠時才扣。回傳受影響筆數，0 表示餘額不足或讀者不存在。
     *
     * <p><b>{@code WHERE credits >= :cost} 是併發防線</b>，不是重複檢查。
     * 呼叫端已經讀過餘額並判斷足夠，但在「讀取」與「扣款」之間，
     * 同一讀者的另一個請求可能已經扣走點數。把條件放進 SQL 讓資料庫
     * 以單一原子操作決定成敗——正確性來自受影響筆數，不是來自先前的檢查。</p>
     */
    @Modifying
    @Transactional
    @Query("update Reader r set r.credits = r.credits - :cost where r.id = :id and r.credits >= :cost")
    int deductCredits(@Param("id") Long id, @Param("cost") int cost);
```

- [ ] **Step 4: 實作 `UnlockService`**

```java
package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.springai.survey.audience.SurveyResponseRepository;
import world.springai.survey.newsletter.Campaign;

import java.time.OffsetDateTime;

/**
 * 扣點解鎖：本系統唯一會扣除讀者點數的地方。
 *
 * <p><b>為什麼必須是交易性的</b>（與 {@code CampaignService} 相反）：
 * 那裡有無法回滾的 ZSend 寄信副作用，所以刻意不加 {@code @Transactional}；
 * 這裡三個寫入（扣餘額、寫帳本、寫解鎖紀錄）全是本地狀態，
 * 任一失敗都必須整組回滾，否則 {@code reader.credits} 與
 * {@code credit_txn} 的總和會不一致——而「餘額永遠可由帳本重算稽核」
 * 是本系統的核心不變式。</p>
 *
 * <p><b>三個併發防線</b>：
 * ① {@code article_access} 的 UNIQUE(reader_id, campaign_id)；
 * ② {@link ReaderRepository#deductCredits} 的 {@code WHERE credits >= :cost}；
 * ③ <b>扣款先於寫入解鎖紀錄</b>——若插入撞 UNIQUE，扣款隨交易一起回滾。
 * 反過來的順序在插入成功、扣款失敗時會留下「有解鎖紀錄但沒扣點」的
 * 永久免費解鎖，而該紀錄同時是 ALREADY_UNLOCKED 的判斷來源，無法自我修復。</p>
 */
@Service
public class UnlockService {

    private static final Logger log = LoggerFactory.getLogger(UnlockService.class);

    /** 解鎖結果 */
    public enum Outcome {
        /** 本次成功解鎖並扣點 */
        UNLOCKED,
        /** 先前已解鎖，未扣點 */
        ALREADY_UNLOCKED,
        /** 餘額不足，未扣點 */
        INSUFFICIENT_CREDITS
    }

    /**
     * 解鎖結果。
     *
     * @param outcome 結果
     * @param cost    該文章的解鎖成本
     * @param credits <b>操作後</b>的餘額（未扣點時即為目前餘額）
     */
    public record Result(Outcome outcome, int cost, int credits) {}

    private final ReaderRepository readerRepository;
    private final ArticleAccessRepository articleAccessRepository;
    private final CreditTxnRepository creditTxnRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final CreditPolicy creditPolicy;

    /** 注入讀者、解鎖紀錄、帳本、名單中心與點數參數 */
    public UnlockService(ReaderRepository readerRepository,
                         ArticleAccessRepository articleAccessRepository,
                         CreditTxnRepository creditTxnRepository,
                         SurveyResponseRepository surveyResponseRepository,
                         CreditPolicy creditPolicy) {
        this.readerRepository = readerRepository;
        this.articleAccessRepository = articleAccessRepository;
        this.creditTxnRepository = creditTxnRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.creditPolicy = creditPolicy;
    }

    /**
     * 以點數解鎖一篇進階文章。
     *
     * <p><b>參數刻意收 {@code readerId} 而非 {@code Reader}</b>：呼叫端的 Reader
     * 來自 session cookie 解析，餘額可能是幾分鐘前的快照。餘額判斷必須用
     * 交易內的即時值，用簽章保證呼叫端無法把舊餘額餵進來。</p>
     *
     * <p><b>UNIQUE 撞擊不在此捕捉</b>，而是往外拋給 controller 處理。
     * 原因是 Spring 的交易語意：一旦 {@code saveAndFlush} 觸發
     * {@code DataIntegrityViolationException}，交易已被標記 rollback-only，
     * 在本方法內捕捉並正常回傳會讓 commit 改拋
     * {@code UnexpectedRollbackException}——呼叫端收到一個看起來毫無關聯的錯誤。
     * 捕捉必須發生在交易邊界<b>之外</b>。</p>
     *
     * @throws IllegalStateException           讀者不存在、文章未發布或非 PREMIUM、併發扣款失敗
     * @throws org.springframework.dao.DataIntegrityViolationException 併發解鎖撞上 UNIQUE
     */
    @Transactional
    public Result unlock(Long readerId, Campaign campaign, OffsetDateTime now) {
        // 扣點是不可逆的寫入，不完全信任呼叫端已做過授權判斷。
        // 草稿被解鎖 → 讀者付了點數卻看到未完成的內容，而點數已經扣掉。
        if (!campaign.isPublished()) {
            throw new IllegalStateException("文章尚未發布，不可解鎖：id=" + campaign.getId());
        }
        // 只有精確等於 PREMIUM 才允許扣點。fail-closed 方向：tier 打錯字時
        // 寧可拒絕解鎖（讀者仍看得到免費區、能回報問題），也不要對一篇
        // 判斷不明的文章扣點。BASIC 對訂閱者本來就免費，扣點是純粹的損失。
        if (!Campaign.TIER_PREMIUM.equals(campaign.getTier())) {
            throw new IllegalStateException(
                "只有 PREMIUM 文章需要解鎖，tier=" + campaign.getTier());
        }

        Reader reader = readerRepository.findById(readerId)
            .orElseThrow(() -> new IllegalStateException("讀者不存在：id=" + readerId));

        int cost = creditPolicy.costOf(campaign);

        // 已解鎖：不寫入任何東西就回傳，交易內無任何變動，可安全正常返回
        if (articleAccessRepository.existsByReaderIdAndCampaignId(readerId, campaign.getId())) {
            return new Result(Outcome.ALREADY_UNLOCKED, cost, reader.getCredits());
        }

        // 餘額不足：同樣沒有寫入，可安全正常返回
        if (reader.getCredits() < cost) {
            return new Result(Outcome.INSUFFICIENT_CREDITS, cost, reader.getCredits());
        }

        // 防線②：條件式扣款。回 0 列代表檢查與扣款之間有另一筆交易扣走了點數。
        int deducted = readerRepository.deductCredits(readerId, cost);
        if (deducted == 0) {
            // 不可回報成 INSUFFICIENT_CREDITS：餘額檢查方才已通過，
            // 這是真正的併發衝突，靜默處理會把問題藏起來。
            throw new IllegalStateException(
                "扣點失敗（併發衝突）：reader=" + readerId + " cost=" + cost);
        }

        // 防線①③：扣款成功後才寫解鎖紀錄；撞 UNIQUE 時扣款隨交易回滾
        articleAccessRepository.saveAndFlush(new ArticleAccess(readerId, campaign.getId(), cost));
        creditTxnRepository.save(new CreditTxn(
            readerId, -cost, CreditTxn.REASON_READ, campaign.getId(), campaign.getSubject()));

        // 解鎖是高可靠的參與度訊號（spec §5.10）
        surveyResponseRepository.touchEngagement(reader.getEmail(), now);

        log.info("讀者 id={} 以 {} 點解鎖文章 id={}", readerId, cost, campaign.getId());
        return new Result(Outcome.UNLOCKED, cost, reader.getCredits() - cost);
    }
}
```

- [ ] **Step 5: 執行測試確認通過**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=UnlockServiceTest`
Expected: `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 6: 用真實資料庫驗證條件式扣點與 UNIQUE 防線**

單元測試把 repository mock 掉了，所以 `deductCredits` 的 SQL 條件與 `uq_article_access` 都**沒有真的被驗證過**。這兩者是防線本體，必須實測。

在 `survey-backend/src/test/java/world/springai/survey/reader/UnlockConstraintTest.java` 建立測試。連線常數與 `MigrationSafetyTest` 相同（同一個 `survey-test-db`，port 5433，可用 `MIGRATION_TEST_DB_HOST` 等環境變數覆寫），但用**獨立的資料庫名稱**避免兩個測試互相干擾，且套用**全部** migration（要用到 V7 建的表）：

```java
package world.springai.survey.reader;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 以真實 PostgreSQL 驗證扣點的兩道資料庫層防線。
 *
 * <p><b>為什麼一定要真實資料庫</b>：{@code UnlockServiceTest} 把 repository
 * 全部 mock 掉了，所以 {@code deductCredits} 的 {@code WHERE credits >= :cost}
 * 條件與 {@code uq_article_access} 這兩道防線<b>從未被真的執行過</b>。
 * 它們是併發正確性的本體，不能只靠 mock 回傳值來「驗證」。</p>
 *
 * <p>連線資訊與 {@code MigrationSafetyTest} 相同（同一個專用測試容器），
 * 但使用獨立資料庫名稱避免兩者互相干擾。連不上時以明確中文訊息失敗，
 * <b>不靜默跳過</b>——寧可紅燈也不要假綠燈。</p>
 */
class UnlockConstraintTest {

    /** 取得環境變數，未設定或空字串時退回預設值 */
    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    private static final String DB_HOST = env("MIGRATION_TEST_DB_HOST", "127.0.0.1");
    private static final String DB_PORT = env("MIGRATION_TEST_DB_PORT", "5433");
    private static final String USER = env("MIGRATION_TEST_DB_USER", "postgres");
    private static final String PASS = env("MIGRATION_TEST_DB_PASSWORD", "password");
    private static final String ADMIN_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    /** 獨立的資料庫名稱，不與 MigrationSafetyTest 共用 */
    private static final String TEST_DB = "survey_unlock_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    /** 重建乾淨資料庫並套用全部 migration（需要 V7 建立的 reader/article_access） */
    @BeforeAll
    static void prepare() throws SQLException {
        requireTestDatabase();
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB);
            st.execute("CREATE DATABASE " + TEST_DB);
        }
        Flyway.configure().dataSource(TEST_URL, USER, PASS).load().migrate();
    }

    /** 連不上專用測試容器時以明確訊息失敗，並附上啟動指令 */
    private static void requireTestDatabase() {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 連得上即可
        } catch (SQLException e) {
            fail("""
                連不到專用測試容器（%s）。本測試驗證的是資料庫層的併發防線，
                無法用 mock 取代，因此不能靜默跳過。請先啟動容器：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                連線資訊可用 MIGRATION_TEST_DB_HOST／PORT／USER／PASSWORD 覆寫。
                """.formatted(ADMIN_URL));
        }
    }

    /** 建一位指定餘額的讀者，回傳其 id */
    private long insertReader(Connection c, String email, int credits) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("INSERT INTO reader (email, credits, referral_code) VALUES ('"
                + email + "', " + credits + ", '" + email.substring(0, 6).toUpperCase() + "')");
            try (ResultSet rs = st.executeQuery(
                    "SELECT id FROM reader WHERE email = '" + email + "'")) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** 讀取某讀者目前餘額 */
    private int creditsOf(Connection c, long readerId) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT credits FROM reader WHERE id = " + readerId)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * 餘額不足時條件式扣點必須回 0 列，且餘額完全不變。
     *
     * <p>這是防線本體：若 WHERE 條件寫錯（或被「簡化」掉），餘額會變成負數，
     * 而負餘額會讓 {@code credits >= cost} 永遠為假——讀者連 0 點的提示
     * 都看不對，且已經被扣掉的點數再也拿不回來。</p>
     */
    @Test
    void conditionalDeductRejectsInsufficientBalance() throws SQLException {
        try (Connection c = DriverManager.getConnection(TEST_URL, USER, PASS)) {
            long id = insertReader(c, "poor@example.com", 5);

            int affected;
            try (Statement st = c.createStatement()) {
                affected = st.executeUpdate(
                    "UPDATE reader SET credits = credits - 10 WHERE id = " + id + " AND credits >= 10");
            }

            assertEquals(0, affected, "餘額不足時不該有任何一列被更新");
            assertEquals(5, creditsOf(c, id), "餘額必須完全不變");
        }
    }

    /** 餘額剛好等於成本時應扣款成功並歸零 */
    @Test
    void conditionalDeductAllowsExactBalance() throws SQLException {
        try (Connection c = DriverManager.getConnection(TEST_URL, USER, PASS)) {
            long id = insertReader(c, "exact@example.com", 10);

            int affected;
            try (Statement st = c.createStatement()) {
                affected = st.executeUpdate(
                    "UPDATE reader SET credits = credits - 10 WHERE id = " + id + " AND credits >= 10");
            }

            assertEquals(1, affected);
            assertEquals(0, creditsOf(c, id), "餘額應歸零而非變負");
        }
    }

    /**
     * 同一讀者同一文章不可有第二筆解鎖紀錄。
     *
     * <p>{@code uq_article_access} 是「同一篇不重複扣點」的最終保證。
     * 若這個約束不存在（或被 migration 漏掉），併發解鎖會扣兩次點。</p>
     */
    @Test
    void uniqueConstraintPreventsDuplicateUnlock() throws SQLException {
        try (Connection c = DriverManager.getConnection(TEST_URL, USER, PASS)) {
            long id = insertReader(c, "dup@example.com", 300);

            try (Statement st = c.createStatement()) {
                st.execute("INSERT INTO article_access (reader_id, campaign_id, cost) VALUES ("
                    + id + ", 999, 10)");
            }

            assertThrows(SQLException.class, () -> {
                try (Statement st = c.createStatement()) {
                    st.execute("INSERT INTO article_access (reader_id, campaign_id, cost) VALUES ("
                        + id + ", 999, 10)");
                }
            }, "第二筆相同 (reader_id, campaign_id) 必須被 uq_article_access 擋下");
        }
    }

    /** 不同文章可以各自解鎖（確認 UNIQUE 是複合鍵而非只看 reader_id） */
    @Test
    void differentArticlesCanBothBeUnlocked() throws SQLException {
        try (Connection c = DriverManager.getConnection(TEST_URL, USER, PASS)) {
            long id = insertReader(c, "multi@example.com", 300);

            try (Statement st = c.createStatement()) {
                st.execute("INSERT INTO article_access (reader_id, campaign_id, cost) VALUES ("
                    + id + ", 1001, 10)");
                st.execute("INSERT INTO article_access (reader_id, campaign_id, cost) VALUES ("
                    + id + ", 1002, 10)");
            }

            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT count(*) FROM article_access WHERE reader_id = " + id)) {
                rs.next();
                assertEquals(2, rs.getInt(1));
            }
        }
    }
}
```

> **驗收前先破壞一次**：把 `WHERE ... AND credits >= 10` 的條件拿掉，確認
> `conditionalDeductRejectsInsufficientBalance` 變紅。若拿掉條件測試仍綠，
> 那這個測試沒有守住任何東西。

- [ ] **Step 7: 跑全套測試**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B clean test`
Expected: `BUILD SUCCESS`、`Failures: 0, Errors: 0, Skipped: 0`

> 若 `UnlockConstraintTest` 因容器未啟動而失敗，先啟動容器再跑，**不要**把測試改成可跳過：
> `docker start survey-test-db`

- [ ] **Step 8: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/reader/UnlockService.java \
        survey-backend/src/main/java/world/springai/survey/reader/ReaderRepository.java \
        survey-backend/src/test/java/world/springai/survey/reader/
git commit -m "feat(reader): 扣點解鎖的交易邊界與三道併發防線"
git diff HEAD --stat -- survey-backend/   # 必須無輸出
```

---

## Task 6: 授權判斷加入「可解鎖」狀態

**背景**：階段 B 的 `AccessDecisionService` 對「PREMIUM + 非 VIP + 未解鎖」一律回 `NEEDS_CREDITS`，不分餘額夠不夠。本階段要區分兩者，因為 gate 區塊要顯示的東西完全不同：餘額夠 → 顯示解鎖按鈕；不夠 → 顯示還差幾點與邀請連結。

**刻意偏離 spec §5.2**：spec 的規則表寫「`credits >= campaign.credit_cost` → **FULL + 扣點**」，即點開文章就自動扣點。本階段改為 **PARTIAL + 顯示解鎖按鈕**，讀者按下按鈕才扣。理由：讀者從電子報連結點進來就被無感扣點，會被感受為未經同意的收費，而 spec §5.11 整節都在講點數機制的可信度。Step 5 會同步回寫 spec。

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/AccessDecisionService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/AccessDecisionServiceTest.java`

**Interfaces:**
- Produces: `AccessDecisionService.Reason.CAN_UNLOCK`（新增的 enum 值）
- 不變：`decide` 仍是純函式；`recordAccess` 仍只在 `VIP` 時寫入

- [ ] **Step 1: 寫失敗測試**

加到 `AccessDecisionServiceTest`：

```java
    /** 餘額剛好等於成本 → 可解鎖（不是直接 FULL） */
    @Test
    void enoughCreditsYieldsCanUnlockNotFull() {
        Reader reader = subscribedReader();
        reader.setCredits(10);

        AccessDecisionService.Decision decision =
            service.decide(reader, true, article(Campaign.TIER_PREMIUM, 10), NOW);

        // 關鍵：仍是 PARTIAL。受限區在讀者按下解鎖前不得進入回應。
        assertEquals(AccessDecisionService.Access.PARTIAL, decision.access());
        assertEquals(AccessDecisionService.Reason.CAN_UNLOCK, decision.reason());
        assertEquals(0, decision.shortfall());
    }

    /** 餘額超過成本同樣是可解鎖 */
    @Test
    void moreThanEnoughCreditsYieldsCanUnlock() {
        Reader reader = subscribedReader();
        reader.setCredits(300);

        AccessDecisionService.Decision decision =
            service.decide(reader, true, article(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Reason.CAN_UNLOCK, decision.reason());
    }

    /** 餘額少 1 點 → 需要更多點數，並回報差額 */
    @Test
    void oneCreditShortYieldsNeedsCreditsWithShortfall() {
        Reader reader = subscribedReader();
        reader.setCredits(9);

        AccessDecisionService.Decision decision =
            service.decide(reader, true, article(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Access.PARTIAL, decision.access());
        assertEquals(AccessDecisionService.Reason.NEEDS_CREDITS, decision.reason());
        assertEquals(1, decision.shortfall());
    }

    /** 零餘額的差額等於全額成本 */
    @Test
    void zeroCreditsShortfallEqualsFullCost() {
        Reader reader = subscribedReader();
        reader.setCredits(0);

        AccessDecisionService.Decision decision =
            service.decide(reader, true, article(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(10, decision.shortfall());
    }

    /**
     * 已解鎖優先於可解鎖：不可讓已付過點的讀者再看到解鎖按鈕。
     *
     * <p>順序錯了不會扣兩次點（UnlockService 的 exists 檢查會擋），
     * 但讀者會看到一個「再花 10 點解鎖」的按鈕，那是嚴重的信任問題。</p>
     */
    @Test
    void alreadyUnlockedWinsOverCanUnlock() {
        Reader reader = subscribedReader();
        reader.setCredits(300);
        when(articleAccessRepository.existsByReaderIdAndCampaignId(anyLong(), anyLong())).thenReturn(true);

        AccessDecisionService.Decision decision =
            service.decide(reader, true, article(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Access.FULL, decision.access());
        assertEquals(AccessDecisionService.Reason.ALREADY_UNLOCKED, decision.reason());
    }

    /** VIP 優先於可解鎖：VIP 不該被要求付點 */
    @Test
    void vipWinsOverCanUnlock() {
        Reader vip = subscribedReader();
        vip.setCredits(300);
        vip.setTier(Reader.TIER_VIP);
        vip.setVipExpiresAt(NOW.plusDays(30));

        AccessDecisionService.Decision decision =
            service.decide(vip, true, article(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Reason.VIP, decision.reason());
    }

    /** 未確認訂閱者即使餘額充足也不可解鎖 */
    @Test
    void unsubscribedReaderCannotUnlockEvenWithCredits() {
        Reader reader = subscribedReader();
        reader.setCredits(300);

        AccessDecisionService.Decision decision =
            service.decide(reader, false, article(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Reason.NOT_SUBSCRIBED, decision.reason());
    }

    /** 未發布的文章即使餘額充足也不可解鎖 */
    @Test
    void unpublishedArticleNeverOffersUnlock() {
        Reader reader = subscribedReader();
        reader.setCredits(300);

        AccessDecisionService.Decision decision =
            service.decide(reader, true, unpublishedArticle(Campaign.TIER_PREMIUM, 10), NOW);

        assertEquals(AccessDecisionService.Reason.NOT_PUBLISHED, decision.reason());
    }

    /** CAN_UNLOCK 不得寫入 article_access——那會變成沒扣點的免費解鎖 */
    @Test
    void recordAccessIgnoresCanUnlock() {
        Reader reader = subscribedReader();
        AccessDecisionService.Decision canUnlock = new AccessDecisionService.Decision(
            AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.CAN_UNLOCK, 0);

        service.recordAccess(reader, article(Campaign.TIER_PREMIUM, 10), canUnlock);

        verify(articleAccessRepository, never()).save(any());
    }
```

> `subscribedReader()` 是本檔案需要的輔助方法；若既有檔案已有等效的建立方式（例如直接 `new Reader(...)`），沿用它，不要新增重複的 helper。

- [ ] **Step 2: 執行確認失敗**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=AccessDecisionServiceTest`
Expected: 編譯失敗，`cannot find symbol: CAN_UNLOCK`

- [ ] **Step 3: 新增 `CAN_UNLOCK` 並修改判定**

在 `Reason` enum 內，`NEEDS_CREDITS` 之前加入：

```java
        /** 餘額足夠，等待讀者確認是否要花點數解鎖 */
        CAN_UNLOCK,
```

把 `decide` 結尾的階段 B 註解與回傳改為：

```java
        int cost = resolveCost(campaign);
        // 餘額足夠時仍回 PARTIAL——受限區在讀者按下解鎖前不得進入回應。
        //
        // 這裡刻意偏離 spec §5.2 規則表的「credits >= cost → FULL + 扣點」：
        // 讀者從電子報連結點進來就被無感扣點，會被感受為未經同意的收費，
        // 而整套點數機制的可信度是 spec §5.11 的核心訴求。改為「顯示成本 →
        // 讀者按下按鈕 → 扣點」，誤點成本為 0。實際扣點在 UnlockService。
        if (reader.getCredits() >= cost) {
            return new Decision(Access.PARTIAL, Reason.CAN_UNLOCK, 0);
        }
        return new Decision(Access.PARTIAL, Reason.NEEDS_CREDITS, cost - reader.getCredits());
```

> 原本的 `int shortfall = Math.max(0, cost - reader.getCredits());` 可以刪掉——走到最後一行時 `cost > credits` 必然成立，`Math.max` 是多餘的保護，留著反而讓人以為差額可能為 0。

同時更新類別 Javadoc：把「**階段 B 範圍**：扣點路徑尚未接上」那段改成描述階段 C 的實際行為（`CAN_UNLOCK` 代表可解鎖但尚未扣點，扣點由 `UnlockService` 負責）。

`recordAccess` **不需修改**——它只對 `VIP` 寫入，`CAN_UNLOCK` 自然被排除；解鎖時的寫入由 `UnlockService` 負責。但請在其 Javadoc 把「階段 C 接上付費解鎖後，會再加上『本次扣點解鎖』的寫入情況」這句改成「付費解鎖的寫入由 `UnlockService` 負責，本方法仍只處理 VIP」。

- [ ] **Step 4: 執行測試確認通過**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=AccessDecisionServiceTest`
Expected: 全部通過

- [ ] **Step 5: 同步 spec §5.2 的規則表**

把規則表中的：

```
credits >= campaign.credit_cost           → FULL + 扣點
```

改為：

```
credits >= campaign.credit_cost           → PARTIAL + CAN_UNLOCK（顯示解鎖按鈕，尚未扣點）
```

並在「四條在實作階段由審查補上的規則」之後新增第 5 條：

> 5. **餘額足夠不等於直接放行。** spec 原本寫「`credits >= cost` → FULL + 扣點」，實作改為「PARTIAL + 顯示解鎖按鈕，讀者確認才扣」。理由：讀者從電子報連結點進來就被無感扣點，會被感受為未經同意的收費，而 §5.11 整節的訴求正是點數機制的可信度；誤點的成本從「10 點」降為「0」。代價是多一次互動，但這次互動本身就是讓讀者理解機制的時機（gate 區塊同時放規則頁連結）。實際扣點集中在 `UnlockService`，`decide()` 維持純函式。

- [ ] **Step 6: 跑全套測試並 Commit**

```bash
cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B clean test
cd .. && git add survey-backend/src/main/java/world/springai/survey/reader/AccessDecisionService.java \
        survey-backend/src/test/java/world/springai/survey/reader/AccessDecisionServiceTest.java \
        docs/superpowers/specs/2026-07-25-reader-newsletter-platform-design.md
git commit -m "feat(reader): 授權判斷區分可解鎖與點數不足"
git diff HEAD --stat -- survey-backend/ docs/   # 必須無輸出
```

---

## Task 7: 解鎖端點與可操作的 gate 區塊

**背景**：把 Task 5 的 `UnlockService` 與 Task 6 的 `CAN_UNLOCK` 接成讀者實際可用的流程。這是讀者第一次遇到點數的時刻，也是唯一會認真讀規則的時刻（spec §5.11），所以 gate 區塊必須同時放規則頁連結。

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/reader/UnlockController.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/ReaderPageController.java`（改寫 `renderGate`）
- Modify: `survey-backend/src/main/resources/static/reader/reader.css`（gate 區塊樣式）
- Test: `survey-backend/src/test/java/world/springai/survey/reader/UnlockControllerTest.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReaderPageControllerTest.java`（既有）

**Interfaces:**
- Consumes: `UnlockService.unlock(Long, Campaign, OffsetDateTime)`、`ReaderContext.resolve(String)`、`CampaignRepository.findBySlug(String)`
- Produces: 端點 `POST /api/reader/unlock/{slug}`，回 JSON `{"outcome":"UNLOCKED","cost":10,"credits":290}`

- [ ] **Step 1: 寫 `UnlockControllerTest`（失敗測試）**

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import world.springai.survey.ApiExceptionHandler;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 解鎖端點：登入檢查、授權檢查、結果回報與併發處理 */
class UnlockControllerTest {

    private static final long READER_ID = 3L;
    private static final long CAMPAIGN_ID = 42L;

    private CampaignRepository campaignRepository;
    private ReaderContext readerContext;
    private UnlockService unlockService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        readerContext = mock(ReaderContext.class);
        unlockService = mock(UnlockService.class);
        mvc = MockMvcBuilders
            .standaloneSetup(new UnlockController(campaignRepository, readerContext, unlockService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    /** 建一篇已發布的 PREMIUM 文章 */
    private Campaign article() {
        Campaign c = new Campaign("主旨", "# 內容", "<h1>內容</h1>", null, null, "now", null, 0, "sent");
        c.setTier(Campaign.TIER_PREMIUM);
        c.setCreditCost(10);
        c.setSlug("my-post");
        c.setPublishedAt(OffsetDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(c, "id", CAMPAIGN_ID);
        return c;
    }

    /** 讓 readerContext 回一個已確認訂閱的登入讀者 */
    private void givenLoggedInSubscriber() {
        Reader reader = new Reader("r@b.com", "CODE1234");
        ReflectionTestUtils.setField(reader, "id", READER_ID);
        when(readerContext.resolve(anyString()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader, true)));
    }

    /** 成功解鎖回 200 與結果明細 */
    @Test
    void successfulUnlockReturnsOutcomeAndBalance() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(article()));
        when(unlockService.unlock(anyLong(), any(), any()))
            .thenReturn(new UnlockService.Result(UnlockService.Outcome.UNLOCKED, 10, 290));

        mvc.perform(post("/api/reader/unlock/my-post").cookie(
                new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.outcome").value("UNLOCKED"))
           .andExpect(jsonPath("$.cost").value(10))
           .andExpect(jsonPath("$.credits").value(290));
    }

    /** 未登入回 401，且絕不呼叫解鎖 */
    @Test
    void anonymousRequestIsRejected() throws Exception {
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        mvc.perform(post("/api/reader/unlock/my-post"))
           .andExpect(status().isUnauthorized());

        verify(unlockService, never()).unlock(anyLong(), any(), any());
    }

    /**
     * 未確認訂閱者不可解鎖。
     *
     * <p>沒有這道檢查，一個登入但未確認訂閱的人可以直接 POST 這個端點
     * 繞過 AccessDecisionService 的 NOT_SUBSCRIBED 判定——頁面上看不到
     * 解鎖按鈕不等於端點不能被呼叫。</p>
     */
    @Test
    void loggedInButUnsubscribedIsRejected() throws Exception {
        Reader reader = new Reader("r@b.com", "CODE1234");
        ReflectionTestUtils.setField(reader, "id", READER_ID);
        when(readerContext.resolve(anyString()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader, false)));
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(article()));

        mvc.perform(post("/api/reader/unlock/my-post").cookie(
                new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
           .andExpect(status().isForbidden());

        verify(unlockService, never()).unlock(anyLong(), any(), any());
    }

    /** 找不到文章回 404 */
    @Test
    void unknownSlugReturnsNotFound() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("ghost")).thenReturn(Optional.empty());

        mvc.perform(post("/api/reader/unlock/ghost").cookie(
                new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
           .andExpect(status().isNotFound());
    }

    /** 未發布的文章回 404（與 /r/news/{slug} 的行為一致，不洩漏草稿存在） */
    @Test
    void unpublishedArticleReturnsNotFound() throws Exception {
        givenLoggedInSubscriber();
        Campaign draft = article();
        draft.setPublishedAt(null);
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(draft));

        mvc.perform(post("/api/reader/unlock/my-post").cookie(
                new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
           .andExpect(status().isNotFound());

        verify(unlockService, never()).unlock(anyLong(), any(), any());
    }

    /** 餘額不足回 200 與 INSUFFICIENT_CREDITS（不是錯誤，是正常的業務結果） */
    @Test
    void insufficientCreditsIsReportedNotThrown() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(article()));
        when(unlockService.unlock(anyLong(), any(), any()))
            .thenReturn(new UnlockService.Result(UnlockService.Outcome.INSUFFICIENT_CREDITS, 10, 3));

        mvc.perform(post("/api/reader/unlock/my-post").cookie(
                new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.outcome").value("INSUFFICIENT_CREDITS"))
           .andExpect(jsonPath("$.credits").value(3));
    }

    /**
     * 併發撞上 UNIQUE 時要轉譯成 ALREADY_UNLOCKED，不可讓 500 外洩。
     *
     * <p>這個轉譯必須在 controller（交易邊界之外）做——UnlockService 內
     * 捕捉會因 rollback-only 標記而在 commit 時改拋
     * UnexpectedRollbackException。讀者的實際情境是「開了兩個分頁各按一次
     * 解鎖」，正確結果是「已解鎖」而不是伺服器錯誤。</p>
     */
    @Test
    void concurrentUniqueViolationIsTranslatedToAlreadyUnlocked() throws Exception {
        givenLoggedInSubscriber();
        when(campaignRepository.findBySlug("my-post")).thenReturn(Optional.of(article()));
        when(unlockService.unlock(anyLong(), any(), any()))
            .thenThrow(new DataIntegrityViolationException("uq_article_access"));

        mvc.perform(post("/api/reader/unlock/my-post").cookie(
                new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.outcome").value("ALREADY_UNLOCKED"));
    }

    /**
     * 解鎖必須是 POST，不可用 GET。
     *
     * <p>GET 會被瀏覽器預抓、被 email 客戶端的連結掃描器觸發、被搜尋引擎爬——
     * 任何一個都會在讀者不知情的狀況下扣掉點數。這與 magic link 遇到的
     * Outlook Safe Links 問題同源。</p>
     */
    @Test
    void unlockIsNotReachableByGet() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/reader/unlock/my-post"))
           .andExpect(status().isMethodNotAllowed());
    }
}
```

- [ ] **Step 2: 執行確認失敗**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=UnlockControllerTest`
Expected: 編譯失敗，`cannot find symbol: class UnlockController`

- [ ] **Step 3: 實作 `UnlockController`**

```java
package world.springai.survey.reader;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 以點數解鎖文章的端點。
 *
 * <p><b>必須是 POST</b>：GET 會被瀏覽器預抓、被 email 客戶端的連結掃描器觸發、
 * 被搜尋引擎爬，任何一個都會在讀者不知情的狀況下扣掉點數。這與 magic link
 * 遇到 Outlook Safe Links 的問題同源。</p>
 *
 * <p><b>授權在此重新檢查，不依賴頁面上有沒有顯示按鈕</b>：看不到按鈕不代表
 * 端點不能被直接呼叫。</p>
 */
@RestController
public class UnlockController {

    private final CampaignRepository campaignRepository;
    private final ReaderContext readerContext;
    private final UnlockService unlockService;

    /** 注入文章查詢、讀者身分解析與解鎖服務 */
    public UnlockController(CampaignRepository campaignRepository,
                           ReaderContext readerContext,
                           UnlockService unlockService) {
        this.campaignRepository = campaignRepository;
        this.readerContext = readerContext;
        this.unlockService = unlockService;
    }

    /**
     * 解鎖指定文章。
     *
     * <p>回傳 {@code outcome} / {@code cost} / {@code credits}，讓前端能直接
     * 更新餘額顯示並決定是否重新載入頁面。餘額不足回 200 而非錯誤碼——
     * 那是正常的業務結果，不是失敗。</p>
     */
    @PostMapping("/api/reader/unlock/{slug}")
    public ResponseEntity<Map<String, Object>> unlock(
            @PathVariable String slug,
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {

        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        if (current.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // 未確認訂閱者不可解鎖：頁面上看不到按鈕不等於端點不能被呼叫
        if (!current.get().subscribed()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 與 /r/news/{slug} 行為一致：未發布一律 404，不洩漏草稿存在
        Campaign campaign = campaignRepository.findBySlug(slug)
            .filter(Campaign::isPublished)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這篇文章"));

        UnlockService.Result result;
        try {
            result = unlockService.unlock(current.get().reader().getId(), campaign, OffsetDateTime.now());
        } catch (DataIntegrityViolationException e) {
            // 併發：另一個請求（多半是讀者自己的另一個分頁）已經解鎖。
            // 這個捕捉必須在交易邊界之外——在 UnlockService 內捕捉會因為
            // rollback-only 標記而讓 commit 改拋 UnexpectedRollbackException。
            result = new UnlockService.Result(UnlockService.Outcome.ALREADY_UNLOCKED, 0, 0);
        }

        return ResponseEntity.ok(Map.of(
            "outcome", result.outcome().name(),
            "cost", result.cost(),
            "credits", result.credits()));
    }
}
```

- [ ] **Step 4: 執行測試確認通過**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=UnlockControllerTest`
Expected: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: 改寫 `ReaderPageController.renderGate`**

`renderGate` 目前對 `NEEDS_CREDITS` 只顯示「先看其他內容」。改為區分 `CAN_UNLOCK` 與 `NEEDS_CREDITS`，並在兩者都放規則頁連結（spec §5.11 的三個曝光位置之一，也是最重要的一處）：

```java
    /**
     * 渲染 paywall 提示區塊。
     *
     * <p>這是讀者第一次遇到點數的地方，也是唯一會認真讀規則的時刻
     * （spec §5.11），因此點數相關的兩種狀態都必須附上規則頁連結。</p>
     *
     * <p>所有點數數字都取自 {@link AccessDecisionService#resolveCost}，
     * 不在此重算——頁面顯示的代價與實際扣的必須是同一個來源。</p>
     */
    private String renderGate(AccessDecisionService.Decision decision, Campaign campaign, String slug) {
        String encodedRedirect = "/r/news/" + slug;
        int cost = accessDecisionService.resolveCost(campaign);
        return switch (decision.reason()) {
            case NOT_LOGGED_IN -> gateHtml("接下來的內容需要登入",
                "用訂閱時的 email 登入就能繼續看，不需要密碼。",
                "<a class=\"btn\" href=\"/r/login?redirect=" + HtmlTemplate.escapeHtml(encodedRedirect) + "\">登入繼續閱讀</a>");
            case NOT_SUBSCRIBED -> gateHtml("這個 email 尚未完成訂閱確認",
                "看完整內容需要先完成訂閱確認，可以直接在訂閱頁重新訂閱一次。",
                "<a class=\"btn\" href=\"/r/\">重新訂閱</a>");
            case CAN_UNLOCK -> gateHtml("這是進階內容",
                "解鎖需要 " + cost + " 點，<strong>一次解鎖永久可讀</strong>。",
                "<button class=\"btn\" id=\"unlock-btn\" data-slug=\"" + HtmlTemplate.escapeHtml(slug)
                    + "\" data-cost=\"" + cost + "\">用 " + cost + " 點解鎖</button>"
                    + "<div class=\"msg\" id=\"unlock-msg\"></div>"
                    + rulesHint());
            case NEEDS_CREDITS -> gateHtml("這是進階內容",
                "解鎖需要 " + cost + " 點，你還差 " + decision.shortfall() + " 點。"
                    + "邀請朋友訂閱可以獲得點數。",
                "<a class=\"btn\" href=\"/r/invite\">看我的邀請連結</a>" + rulesHint());
            default -> "";
        };
    }

    /** 規則頁連結：點數機制的可信度來源，兩種點數狀態都必須附上（spec §5.11） */
    private String rulesHint() {
        return "<p class=\"gate-hint\">不清楚點數怎麼運作？<a href=\"/r/rules\">看遊戲規則</a></p>";
    }
```

同時在 `article` 方法回傳的 `vars` 中加入解鎖用的 JavaScript 佔位符：

```java
        // CAN_UNLOCK 時才需要解鎖腳本，其餘情況輸出空字串——
        // 不讓不需要的頁面帶著一段用不到的 JS
        vars.put("<!--UNLOCK_SCRIPT-->",
            decision.reason() == AccessDecisionService.Reason.CAN_UNLOCK ? UNLOCK_SCRIPT : "");
```

並在類別內加入腳本常數：

```java
    /**
     * 解鎖按鈕的前端腳本。
     *
     * <p>解鎖成功後以 {@code location.reload()} 重新載入，讓受限區由 server
     * 重新渲染進來——不是用 JS 把內容插進頁面。受限區必須始終由 server 端
     * 依授權結果決定是否輸出（spec §5.3），前端插入等於受限區曾經出現在
     * 某個 API 回應中，paywall 就形同虛設。</p>
     */
    private static final String UNLOCK_SCRIPT = """
        <script>
          const unlockBtn = document.getElementById('unlock-btn');
          const unlockMsg = document.getElementById('unlock-msg');
          unlockBtn.addEventListener('click', async () => {
            unlockBtn.disabled = true;
            unlockMsg.textContent = '處理中…';
            unlockMsg.className = 'msg show';
            try {
              const res = await fetch('/api/reader/unlock/' + encodeURIComponent(unlockBtn.dataset.slug),
                { method: 'POST' });
              if (res.status === 401) {
                unlockMsg.textContent = '登入已過期，請重新登入。';
                unlockMsg.className = 'msg show err';
                unlockBtn.disabled = false;
                return;
              }
              if (!res.ok) { throw new Error('unlock failed'); }
              const data = await res.json();
              if (data.outcome === 'UNLOCKED' || data.outcome === 'ALREADY_UNLOCKED') {
                // 重新載入讓 server 重新渲染受限區，不由前端插入內容
                location.reload();
                return;
              }
              if (data.outcome === 'INSUFFICIENT_CREDITS') {
                unlockMsg.textContent = '點數不足，目前有 ' + data.credits + ' 點。';
                unlockMsg.className = 'msg show err';
              }
            } catch (e) {
              unlockMsg.textContent = '解鎖失敗，請稍後再試。';
              unlockMsg.className = 'msg show err';
            } finally {
              unlockBtn.disabled = false;
            }
          });
        </script>
        """;
```

在 `static/reader/article.html` 的 `</div>` 之後、`</body>` 之前加入佔位符：

```html
<!--UNLOCK_SCRIPT-->
```

- [ ] **Step 6: 在 `ReaderPageControllerTest` 補 gate 測試**

沿用該檔案既有的 `gatedArticle(tier, cost)`、`reader(tier, credits)`、`stubDecision(...)` 輔助方法與 `SENTINEL` / `FREE_MARKER` 哨兵常數：

```java
    /**
     * CAN_UNLOCK 時要有解鎖按鈕與成本數字，且**仍不含受限區內容**。
     *
     * <p>最後那個斷言是 paywall 的驗收條件本身：只檢查「有解鎖按鈕」
     * 不能證明受限內容沒被送到瀏覽器。</p>
     */
    @Test
    void canUnlockRendersUnlockButtonWithoutGatedContent() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_PREMIUM, 10)));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 300), true)));
        when(accessDecisionService.resolveCost(any())).thenReturn(10);
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.CAN_UNLOCK, 0);

        String html = mvc.perform(get("/r/news/test-article")
                .cookie(new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("用 10 點解鎖"), "應顯示成本與解鎖按鈕文字");
        assertTrue(html.contains("id=\"unlock-btn\""), "應有解鎖按鈕");
        assertTrue(html.contains("/r/rules"), "gate 區塊必須附規則頁連結（spec §5.11）");
        assertTrue(html.contains(FREE_MARKER), "免費區必須看得到");
        assertFalse(html.contains(SENTINEL), "受限區絕不可出現在 PARTIAL 回應中");
    }

    /** NEEDS_CREDITS 時顯示差額與邀請連結，不得有解鎖按鈕，也不得含受限區 */
    @Test
    void needsCreditsRendersShortfallAndInviteLink() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_PREMIUM, 10)));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 3), true)));
        when(accessDecisionService.resolveCost(any())).thenReturn(10);
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NEEDS_CREDITS, 7);

        String html = mvc.perform(get("/r/news/test-article")
                .cookie(new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
            .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("還差 7 點"), "應顯示差額");
        assertTrue(html.contains("/r/invite"), "應引導去邀請頁賺點數");
        assertTrue(html.contains("/r/rules"), "gate 區塊必須附規則頁連結");
        assertFalse(html.contains("id=\"unlock-btn\""), "餘額不足時不可出現解鎖按鈕");
        assertFalse(html.contains(SENTINEL), "受限區絕不可出現在 PARTIAL 回應中");
    }

    /**
     * 解鎖腳本只在 CAN_UNLOCK 時輸出。
     *
     * <p>不是效能考量——未登入者頁面帶著一段解鎖腳本，會讓「這篇要付費」
     * 的訊息在錯誤的時機出現，而該讀者要做的是登入。</p>
     */
    @Test
    void unlockScriptOnlyAppearsForCanUnlock() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_PREMIUM, 10)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        when(accessDecisionService.resolveCost(any())).thenReturn(10);
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);

        String html = mvc.perform(get("/r/news/test-article"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(html.contains("unlock-btn"), "未登入時不該有解鎖腳本或按鈕");
        assertTrue(html.contains("/r/login"), "應引導登入");
        assertFalse(html.contains(SENTINEL));
    }

    /** FULL 時受限區必須出現，且不該有 gate 區塊 */
    @Test
    void fullAccessRendersGatedContentAndNoGate() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_PREMIUM, 10)));
        when(readerContext.resolve(any()))
            .thenReturn(Optional.of(new ReaderContext.Current(reader(Reader.TIER_FREE, 290), true)));
        stubDecision(AccessDecisionService.Access.FULL, AccessDecisionService.Reason.ALREADY_UNLOCKED, 0);

        String html = mvc.perform(get("/r/news/test-article")
                .cookie(new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT")))
            .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains(SENTINEL), "已解鎖者必須看得到受限區");
        assertFalse(html.contains("id=\"unlock-btn\""), "已解鎖不該再顯示解鎖按鈕");
    }
```

> **最關鍵的斷言是 `assertFalse(html.contains(SENTINEL))`**——這是 paywall 的驗收條件本身，每個 PARTIAL 案例都要驗。上面四個測試已涵蓋 CAN_UNLOCK / NEEDS_CREDITS / NOT_LOGGED_IN / FULL 四種情況。
>
> 若既有的 `stubDecision` 尚未涵蓋 `resolveCost`，本任務新增的 `when(accessDecisionService.resolveCost(any()))` 是必要的——沒有它，mock 會回 0，gate 文案會變成「用 0 點解鎖」而測試的字串斷言會失敗（這正是我們想要的失敗，不是要繞過它）。

- [ ] **Step 7: 補 `reader.css` 的 gate 樣式**

在 `static/reader/reader.css` 末尾加入（沿用檔案內既有的 CSS 變數命名）：

```css
/* paywall 提示區塊內的規則頁連結 */
.gate-hint {
  margin-top: 14px;
  font-size: .88rem;
  color: var(--muted);
}

/* 解鎖按鈕為 <button>（其餘 .btn 多為 <a>），需補齊按鈕預設樣式差異 */
button.btn {
  border: none;
  cursor: pointer;
  font: inherit;
}

button.btn:disabled {
  opacity: .6;
  cursor: default;
}
```

- [ ] **Step 8: 跑全套測試**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B clean test`
Expected: `BUILD SUCCESS`、`Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 9: 手動驗證解鎖流程（真實瀏覽器）**

依 CLAUDE.md 的強制規範，瀏覽器操作要寫成可重跑的腳本。在 `survey-backend/scripts/verify-unlock-flow.mjs` 建立腳本（比照既有的 `verify-reader-flow.mjs` 寫法與中文註解），流程：

1. 以 admin API 發布一篇 `tier=PREMIUM`、`credit_cost=10`、含 `<!--paywall-->` 的文章。
2. 用測試 email 訂閱並登入（沿用 `verify-reader-flow.mjs` 既有的登入取得 cookie 做法）。
3. 開 `/r/news/{slug}`，斷言：頁面**不含**受限區文字、含「用 10 點解鎖」。
4. `POST /api/reader/unlock/{slug}`，斷言回 `UNLOCKED`、`credits` 為 290。
5. 重新開 `/r/news/{slug}`，斷言**含**受限區文字。
6. 再次 `POST` 同一端點，斷言回 `ALREADY_UNLOCKED` 且 `credits` 未再減少。

Expected: 六步全部通過。

- [ ] **Step 10: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/reader/ \
        survey-backend/src/main/resources/static/reader/ \
        survey-backend/src/test/java/world/springai/survey/reader/ \
        survey-backend/scripts/verify-unlock-flow.mjs
git commit -m "feat(reader): 解鎖端點與可操作的 paywall 提示區塊"
git diff HEAD --stat -- survey-backend/   # 必須無輸出
```

---

## Task 8: 遊戲規則頁（`/r/rules`）

**背景**：spec §5.11 明訂這頁是點數機制的可信度來源——機制不透明時，扣點會被感受為不當收費。**所有數字動態注入，不寫死**，因為 §9.2 明訂第一版參數就是要靠上線後數據校準；規則頁寫死「10 點」而後台已調成 50 點，是最傷信任的一類落差。

**刻意不做 CMS**（YAGNI）：頁面骨架與文案寫在靜態 HTML，只有數字動態注入。文案大改需要部署一次，但這頻率遠低於參數調整。

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/reader/RulesPageController.java`
- Create: `survey-backend/src/main/resources/static/reader/rules.html`
- Modify: `survey-backend/src/main/resources/static/reader/index.html`（訂閱表單下方加規則頁連結）
- Test: `survey-backend/src/test/java/world/springai/survey/reader/RulesPageControllerTest.java`

**Interfaces:**
- Consumes: `CreditPolicy.signupGrant()` / `premiumCost()` / `referralReward()` / `vipDefaultDays()`、`HtmlTemplate.render`、`ReaderContext.resolve`
- Produces: 端點 `GET /r/rules`（公開，不需登入）

- [ ] **Step 1: 寫 `RulesPageControllerTest`（失敗測試）**

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 規則頁：數字必須全部來自 CreditPolicy。
 *
 * <p>測試策略刻意用「非典型數值」（77 / 33 / 55 / 111）而非真實的
 * 300 / 10 / 100 / 365。用真實值的話，即使實作把數字寫死在 HTML 裡，
 * 測試也會通過——那種測試什麼都證明不了。</p>
 */
class RulesPageControllerTest {

    private CreditPolicy creditPolicy;
    private ReaderContext readerContext;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        creditPolicy = mock(CreditPolicy.class);
        readerContext = mock(ReaderContext.class);
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        when(creditPolicy.signupGrant()).thenReturn(77);
        when(creditPolicy.premiumCost()).thenReturn(33);
        when(creditPolicy.referralReward()).thenReturn(55);
        when(creditPolicy.vipDefaultDays()).thenReturn(111);
        mvc = MockMvcBuilders
            .standaloneSetup(new RulesPageController(new HtmlTemplate(), creditPolicy, readerContext))
            .build();
    }

    /** 頁面可公開存取（不需登入） */
    @Test
    void rulesPageIsPublic() throws Exception {
        mvc.perform(get("/r/rules")).andExpect(status().isOk());
    }

    /** 初始贈點必須來自 CreditPolicy */
    @Test
    void injectsSignupGrant() throws Exception {
        mvc.perform(get("/r/rules")).andExpect(content().string(org.hamcrest.Matchers.containsString("77")));
    }

    /** PREMIUM 單篇點數必須來自 CreditPolicy */
    @Test
    void injectsPremiumCost() throws Exception {
        mvc.perform(get("/r/rules")).andExpect(content().string(org.hamcrest.Matchers.containsString("33")));
    }

    /** 邀請獎勵必須來自 CreditPolicy */
    @Test
    void injectsReferralReward() throws Exception {
        mvc.perform(get("/r/rules")).andExpect(content().string(org.hamcrest.Matchers.containsString("55")));
    }

    /** VIP 效期必須來自 CreditPolicy */
    @Test
    void injectsVipDays() throws Exception {
        mvc.perform(get("/r/rules")).andExpect(content().string(org.hamcrest.Matchers.containsString("111")));
    }

    /**
     * 真實預設值不得出現在頁面中。
     *
     * <p>這是上一組測試的反面守衛：若實作把 300 / 10 / 100 / 365 寫死在
     * HTML 裡，同時又注入了 mock 的值，上面四個測試會全部通過而頁面
     * 卻同時顯示兩組數字。斷言「舊值不存在」才真的守住了單一來源。</p>
     */
    @Test
    void hardcodedDefaultsAreAbsent() throws Exception {
        String html = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("300 點"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("10 點"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("100 點"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("365 天"));
    }

    /**
     * VIP 段落必須逐字採用 spec §5.11 已定案的措辭。
     *
     * <p>「尚未開放付費訂閱」這句是刻意的：VIP 若寫得模糊會顯得不透明，
     * 反而傷害整頁的可信度。這個測試守的是日後有人「順手改順一點」。</p>
     */
    @Test
    void vipWordingIsExactlyAsSpecified() throws Exception {
        mvc.perform(get("/r/rules"))
           .andExpect(content().string(org.hamcrest.Matchers.containsString(
               "VIP 目前由站方主動授予給課程學員與合作夥伴，尚未開放付費訂閱")));
    }

    /** 必須載明「點數不過期」與「規則調整不扣減既有餘額」兩項承諾 */
    @Test
    void containsBothStandingPromises() throws Exception {
        String html = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("不會過期"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("不會扣減"));
    }

    /** 必須有最後更新日期（規則涉及權益） */
    @Test
    void containsLastUpdatedDate() throws Exception {
        mvc.perform(get("/r/rules"))
           .andExpect(content().string(org.hamcrest.Matchers.containsString("最後更新")));
    }

    /** 登入者的導覽列要顯示「我的帳戶」，未登入則顯示「登入」 */
    @Test
    void navReflectsLoginState() throws Exception {
        String anonymous = mvc.perform(get("/r/rules")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(anonymous.contains("/r/login"));
    }
}
```

- [ ] **Step 2: 執行確認失敗**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=RulesPageControllerTest`
Expected: 編譯失敗，`cannot find symbol: class RulesPageController`

- [ ] **Step 3: 建立 `static/reader/rules.html`**

以讀者會問的問題組織，非條文式（spec §5.11）。**所有數字都是佔位符**：

```html
<!doctype html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>遊戲規則｜凱文大叔的電子報</title>
<meta name="description" content="點數怎麼來、怎麼用，以及為什麼有些文章需要點數。">
<link rel="stylesheet" href="/r/reader.css">
</head>
<body>
<header class="site-head">
  <div class="site-head-inner">
    <a class="brand" href="/r/">凱文大叔的電子報</a>
    <nav><!--NAV_LINKS--></nav>
  </div>
</header>

<div class="wrap">
  <h1 style="font-size:1.5rem">遊戲規則</h1>
  <p style="color:var(--muted)">這頁說明點數怎麼來、怎麼用。看完你應該不會有任何意外。</p>

  <div class="card">
    <h2>訂閱能拿到什麼？</h2>
    <p>確認訂閱之後，<strong>基本內容全文免費</strong>——不是只給開頭幾段的誘餌，是整篇。每期電子報會直接寄到你的信箱，也能在<a href="/r/archive">歷史內容</a>翻閱。</p>
    <p>只有少數<strong>進階內容</strong>需要點數，那些是花比較多時間做的深度題目。</p>
  </div>

  <div class="card">
    <h2>點數怎麼來？</h2>
    <ul>
      <li><strong>首次登入送 <!--SIGNUP_GRANT--> 點</strong>：訂閱後用 email 登入一次就會拿到。</li>
      <li><strong>邀請朋友訂閱，每位 +<!--REFERRAL_REWARD--> 點</strong>：對方點了自己信箱裡的確認信才算成功（詳見下方）。</li>
      <li><strong>活動贈點</strong>：課程學員與特定活動參加者會由站方直接加點。</li>
    </ul>
  </div>

  <div class="card">
    <h2>點數怎麼用？</h2>
    <p>進階文章每篇 <!--PREMIUM_COST--> 點。在文章頁按下解鎖按鈕才會扣點，<strong>不會因為你點進來就自動扣</strong>。</p>
    <p><strong>一次解鎖，永久可讀。</strong>同一篇文章不會重複扣點，之後隨時回來看都不用再付。</p>
  </div>

  <div class="card">
    <h2>點數會不會過期？</h2>
    <p><strong>不會過期。</strong>沒有回收機制，也沒有使用期限。</p>
    <p>另外，<strong>點數規則調整不會扣減你既有的點數餘額</strong>。若日後調整每篇的點數，只影響之後的解鎖，已經解鎖的內容也不會被收回。</p>
  </div>

  <div class="card">
    <h2>為什麼有些文章要點數？</h2>
    <p>有些題目需要花好幾天做實驗、踩完坑再整理，跟隨手寫的觀察筆記不是同一件事。點數是讓這類內容能繼續做下去的方式，而不是把好東西鎖起來——<!--SIGNUP_GRANT--> 點的初始贈點就是希望你先看幾篇再決定值不值得。</p>
  </div>

  <div class="card">
    <h2>VIP 是什麼？</h2>
    <p>VIP 目前由站方主動授予給課程學員與合作夥伴，尚未開放付費訂閱。VIP 期間所有進階內容不需點數。</p>
    <p style="color:var(--muted);font-size:.92rem">授予的效期預設為 <!--VIP_DAYS--> 天。</p>
  </div>

  <div class="card">
    <h2>邀請怎麼算成功？</h2>
    <p>你在<a href="/r/invite">我的邀請</a>頁會拿到一個專屬連結。朋友用那個連結訂閱之後，<strong>還要點開自己信箱裡的確認信</strong>，才算一次成功邀請，你才會拿到 <!--REFERRAL_REWARD--> 點。</p>
    <p style="color:var(--muted);font-size:.92rem">這樣設計是為了避免有人填假 email 刷點數——確認信只有真正的信箱主人收得到。</p>
  </div>

  <div class="foot">
    <p>最後更新：<!--LAST_UPDATED--></p>
  </div>
</div>
</body>
</html>
```

> **關於「未來付費機制」的版位**：spec §5.11 已定案「頁面結構預留位置，但第一版不顯示、不預告時程、不開放候補名單」。上方的 VIP 段落即為該版位——**不要**新增「敬請期待」之類的文字。預告未定案的收費會提前引發疑慮，時程一延就成為信任負債。

- [ ] **Step 4: 實作 `RulesPageController`**

```java
package world.springai.survey.reader;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 遊戲規則頁。
 *
 * <p><b>所有數字動態注入，不寫死</b>（spec §5.11 硬要求）：§9.2 明訂第一版
 * 參數就是要靠上線後數據校準。若頁面寫死「一篇 10 點」而後台已調成 50 點，
 * 讀者看到的代價與實際扣的不一致——那是最傷信任的一類落差。數字一律取自
 * {@link CreditPolicy}，與 paywall 提示區塊、{@code /r/me} 同源。</p>
 *
 * <p><b>刻意不做 CMS</b>（YAGNI）：文案寫在靜態 HTML，只有數字動態注入。
 * 文案大改需要部署一次，但這頻率遠低於參數調整。不為此建
 * {@code static_page} 表或後台編輯器——{@code mail_template} 那種入庫模式
 * 是因為信件範本需要頻繁微調，規則頁沒有同等需求。</p>
 */
@RestController
public class RulesPageController {

    /**
     * 規則最後更新日期。
     *
     * <p>規則涉及讀者權益，必須有日期。刻意寫成常數而非 {@code LocalDate.now()}：
     * 顯示「今天」會讓讀者以為規則天天在改，反而降低可信度。<b>修改本頁文案時
     * 請一併更新這個日期</b>。</p>
     */
    private static final String LAST_UPDATED = "2026-07-26";

    private final HtmlTemplate htmlTemplate;
    private final CreditPolicy creditPolicy;
    private final ReaderContext readerContext;

    /** 注入頁面渲染、點數參數與讀者身分解析 */
    public RulesPageController(HtmlTemplate htmlTemplate,
                              CreditPolicy creditPolicy,
                              ReaderContext readerContext) {
        this.htmlTemplate = htmlTemplate;
        this.creditPolicy = creditPolicy;
        this.readerContext = readerContext;
    }

    /** 規則頁：公開，不需登入 */
    @GetMapping(value = "/r/rules", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> rules(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        boolean loggedIn = readerContext.resolve(sessionCookie).isPresent();

        Map<String, String> vars = new HashMap<>();
        vars.put("<!--NAV_LINKS-->", navLinks(loggedIn));
        vars.put("<!--SIGNUP_GRANT-->", String.valueOf(creditPolicy.signupGrant()));
        vars.put("<!--PREMIUM_COST-->", String.valueOf(creditPolicy.premiumCost()));
        vars.put("<!--REFERRAL_REWARD-->", String.valueOf(creditPolicy.referralReward()));
        vars.put("<!--VIP_DAYS-->", String.valueOf(creditPolicy.vipDefaultDays()));
        vars.put("<!--LAST_UPDATED-->", LAST_UPDATED);

        // 導覽列會因登入狀態而異，故不可被共享快取；規則本身則允許讀者端瀏覽器快取，
        // 但參數改動要立即生效，所以一律 no-store。
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
            .body(htmlTemplate.render("static/reader/rules.html", vars));
    }

    /** 依登入狀態顯示不同的導覽連結 */
    private String navLinks(boolean loggedIn) {
        if (loggedIn) {
            return "<a href=\"/r/archive\">歷史內容</a><a href=\"/r/me\">我的帳戶</a>";
        }
        return "<a href=\"/r/archive\">歷史內容</a><a href=\"/r/login\">登入</a>";
    }
}
```

- [ ] **Step 5: 執行測試確認通過**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=RulesPageControllerTest`
Expected: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 6: 在 `/r/` 訂閱頁加規則頁連結**

spec §5.11 的三個曝光位置之一。在 `static/reader/index.html` 訂閱表單的 `</div>` 之後（「已經訂閱過了？」那段之前）插入：

```html
  <p style="margin-top:18px;color:var(--muted);font-size:.92rem">
    訂閱後首次登入會拿到初始點數，進階內容可用點數解鎖。<a href="/r/rules">看遊戲規則</a>
  </p>
```

> 這裡刻意**不寫具體點數**——靜態 HTML 沒有動態注入管道，寫死就會與後台設定漂移。想知道數字的人點進規則頁即可。

- [ ] **Step 7: 加入規則頁的樣式（若需要）**

檢查 `reader.css` 是否已有 `.card h2` 的樣式。若規則頁的多張 card 排版過於緊密，在檔案末尾補：

```css
/* 規則頁的連續卡片：拉開間距讓每個問題成為視覺單位 */
.wrap .card + .card {
  margin-top: 16px;
}

.card h2 {
  font-size: 1.05rem;
  margin: 0 0 10px;
}

.card ul {
  margin: 10px 0 0;
  padding-left: 1.3em;
  line-height: 1.9;
}
```

- [ ] **Step 8: 跑全套測試並 Commit**

```bash
cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B clean test
cd .. && git add survey-backend/src/main/java/world/springai/survey/reader/RulesPageController.java \
        survey-backend/src/main/resources/static/reader/ \
        survey-backend/src/test/java/world/springai/survey/reader/RulesPageControllerTest.java
git commit -m "feat(reader): 遊戲規則頁，數字全部由 CreditPolicy 動態注入"
git diff HEAD --stat -- survey-backend/   # 必須無輸出
```

---

## Task 9: 我的帳戶（`/r/me`）

**背景**：spec §8 要求顯示餘額、交易明細、VIP 狀態與個人資料編輯；§5.11 要求餘額區塊旁放規則頁連結（三個曝光位置之三）。

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/reader/ReaderPortalController.java`
- Create: `survey-backend/src/main/resources/static/reader/me.html`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReaderPortalControllerTest.java`

**Interfaces:**
- Consumes: `ReaderContext.resolve`、`CreditTxnRepository.findByReaderIdOrderByCreatedAtDesc`、`SurveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc` / `touchEngagement`、`CreditPolicy`
- Produces:
  - 端點 `GET /r/me`（需登入，未登入導向 `/r/login?redirect=/r/me`）
  - 端點 `POST /api/reader/profile`（更新顯示名稱）
  - `ReaderPortalController` 之後在 Task 10 追加 `/r/invite`

- [ ] **Step 1: 寫 `ReaderPortalControllerTest`（失敗測試）**

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 我的帳戶頁：登入要求、餘額與明細顯示、個人資料更新 */
class ReaderPortalControllerTest {

    private static final long READER_ID = 3L;

    private ReaderContext readerContext;
    private CreditTxnRepository creditTxnRepository;
    private SurveyResponseRepository surveyResponseRepository;
    private ReferralService referralService;
    private CreditPolicy creditPolicy;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        readerContext = mock(ReaderContext.class);
        creditTxnRepository = mock(CreditTxnRepository.class);
        surveyResponseRepository = mock(SurveyResponseRepository.class);
        referralService = mock(ReferralService.class);
        creditPolicy = mock(CreditPolicy.class);
        when(creditPolicy.premiumCost()).thenReturn(33);
        when(creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.empty());
        when(referralService.stats(anyLong()))
            .thenReturn(new ReferralService.ReferralStats(0, 0));
        mvc = MockMvcBuilders.standaloneSetup(new ReaderPortalController(
                new HtmlTemplate(), readerContext, creditTxnRepository,
                surveyResponseRepository, referralService, creditPolicy))
            .build();
    }

    /** 建一個帶 id 與餘額的登入讀者 */
    private Reader reader(int credits) {
        Reader r = new Reader("reader@example.com", "CODE1234");
        ReflectionTestUtils.setField(r, "id", READER_ID);
        r.setCredits(credits);
        return r;
    }

    /** 讓 readerContext 回一個登入且已確認訂閱的讀者 */
    private void givenLoggedIn(Reader r) {
        when(readerContext.resolve(anyString()))
            .thenReturn(Optional.of(new ReaderContext.Current(r, true)));
    }

    /** 建一個帶 session cookie 的請求 */
    private jakarta.servlet.http.Cookie cookie() {
        return new jakarta.servlet.http.Cookie(ReaderSessionService.COOKIE_NAME, "JWT");
    }

    /**
     * 未登入必須導向登入頁並帶 redirect，而不是回 401。
     *
     * <p>這是頁面而非 API：讀者在瀏覽器裡看到 401 空白頁是死路，
     * 導向登入頁並在登入後回到這裡才是可走完的流程。</p>
     */
    @Test
    void anonymousIsRedirectedToLoginWithRedirectBack() throws Exception {
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        mvc.perform(get("/r/me"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/login?redirect=/r/me"));
    }

    /** 已登入顯示餘額 */
    @Test
    void showsCreditBalance() throws Exception {
        givenLoggedIn(reader(287));

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("287")));
    }

    /** 顯示 email */
    @Test
    void showsEmail() throws Exception {
        givenLoggedIn(reader(287));

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(content().string(containsString("reader@example.com")));
    }

    /** 餘額區塊旁必須有規則頁連結（spec §5.11 的三個曝光位置之三） */
    @Test
    void linksToRulesPage() throws Exception {
        givenLoggedIn(reader(287));

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(content().string(containsString("/r/rules")));
    }

    /** 交易明細逐筆顯示，正負號要能分辨 */
    @Test
    void listsCreditTransactions() throws Exception {
        givenLoggedIn(reader(287));
        when(creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(READER_ID)).thenReturn(List.of(
            new CreditTxn(READER_ID, -13, CreditTxn.REASON_READ, 42L, "某篇進階文章"),
            new CreditTxn(READER_ID, 300, CreditTxn.REASON_SIGNUP_GRANT, null, "首次登入初始贈點")));

        String html = mvc.perform(get("/r/me").cookie(cookie()))
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("-13"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("+300"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("某篇進階文章"));
    }

    /**
     * 帳本內容必須經過 HTML 跳脫。
     *
     * <p>note 欄位存的是文章主旨（由後台輸入）與被邀者 email。主旨含
     * {@code <script>} 時若不跳脫，就是一個儲存型 XSS——而且是打在
     * 讀者自己的帳戶頁上。</p>
     */
    @Test
    void escapesTransactionNotes() throws Exception {
        givenLoggedIn(reader(287));
        when(creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(READER_ID)).thenReturn(List.of(
            new CreditTxn(READER_ID, -13, CreditTxn.REASON_READ, 42L, "<script>alert(1)</script>")));

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
           .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    /** 沒有交易時顯示空狀態，不是空白區塊 */
    @Test
    void showsEmptyStateWhenNoTransactions() throws Exception {
        givenLoggedIn(reader(300));

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(content().string(containsString("還沒有交易紀錄")));
    }

    /** VIP 且未到期顯示到期日 */
    @Test
    void showsVipStatusWithExpiry() throws Exception {
        Reader vip = reader(0);
        vip.setTier(Reader.TIER_VIP);
        vip.setVipExpiresAt(OffsetDateTime.parse("2027-01-31T00:00:00Z"));
        givenLoggedIn(vip);

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(content().string(containsString("VIP")))
           .andExpect(content().string(containsString("2027-01-31")));
    }

    /**
     * tier 為 VIP 但已到期時不得顯示成有效 VIP。
     *
     * <p>系統刻意不做自動降級排程（spec §13.5），所以資料庫裡會存在
     * 「tier=VIP 但 vip_expires_at 已過」的列。頁面若照 tier 顯示，
     * 讀者會以為自己還是 VIP，然後在解鎖時發現要扣點。</p>
     */
    @Test
    void expiredVipIsNotShownAsActive() throws Exception {
        Reader expired = reader(50);
        expired.setTier(Reader.TIER_VIP);
        expired.setVipExpiresAt(OffsetDateTime.now().minusDays(1));
        givenLoggedIn(expired);

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(content().string(containsString("已到期")));
    }

    /** 更新顯示名稱成功並更新參與度時間戳 */
    @Test
    void updatesDisplayNameAndTouchesEngagement() throws Exception {
        givenLoggedIn(reader(300));
        SurveyResponse row = new SurveyResponse();
        row.setEmail("reader@example.com");
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("reader@example.com"))
            .thenReturn(Optional.of(row));

        mvc.perform(post("/api/reader/profile").cookie(cookie())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"凱文\"}"))
           .andExpect(status().isOk());

        verify(surveyResponseRepository).save(row);
        verify(surveyResponseRepository).touchEngagement(anyString(), any());
        org.junit.jupiter.api.Assertions.assertEquals("凱文", row.getName());
    }

    /** 未登入不可更新個人資料 */
    @Test
    void anonymousCannotUpdateProfile() throws Exception {
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        mvc.perform(post("/api/reader/profile")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"凱文\"}"))
           .andExpect(status().isUnauthorized());
    }

    /**
     * 個人資料只能改自己的：名單中查無此 email 時回 404 而不是建新列。
     *
     * <p>建新列會讓「讀者自行維護個人資訊」變成「讀者可以往名單中心插資料」，
     * 而名單中心的每一列都代表一份同意紀錄。</p>
     */
    @Test
    void profileUpdateDoesNotCreateAudienceRow() throws Exception {
        givenLoggedIn(reader(300));
        when(surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.empty());

        mvc.perform(post("/api/reader/profile").cookie(cookie())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"凱文\"}"))
           .andExpect(status().isNotFound());

        verify(surveyResponseRepository, org.mockito.Mockito.never()).save(any());
    }

    /** 帳戶頁不可被共享快取（含餘額等個人資料） */
    @Test
    void isNeverSharedCached() throws Exception {
        givenLoggedIn(reader(300));

        mvc.perform(get("/r/me").cookie(cookie()))
           .andExpect(header().string("Cache-Control", "private, no-store"))
           .andExpect(header().string("Vary", "Cookie"));
    }
}
```

- [ ] **Step 2: 執行確認失敗**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=ReaderPortalControllerTest`
Expected: 編譯失敗，`cannot find symbol: class ReaderPortalController`

- [ ] **Step 3: 建立 `static/reader/me.html`**

```html
<!doctype html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>我的帳戶｜凱文大叔的電子報</title>
<meta name="robots" content="noindex">
<link rel="stylesheet" href="/r/reader.css">
</head>
<body>
<header class="site-head">
  <div class="site-head-inner">
    <a class="brand" href="/r/">凱文大叔的電子報</a>
    <nav><!--NAV_LINKS--></nav>
  </div>
</header>

<div class="wrap">
  <h1 style="font-size:1.5rem">我的帳戶</h1>

  <div class="card">
    <h2>點數餘額</h2>
    <p class="balance"><!--CREDITS--> 點</p>
    <p style="color:var(--muted);font-size:.92rem">
      進階內容每篇 <!--PREMIUM_COST--> 點，一次解鎖永久可讀。<a href="/r/rules">看遊戲規則</a>
    </p>
    <p style="margin-top:12px"><a class="btn ghost" href="/r/invite">邀請朋友賺點數</a></p>
  </div>

  <div class="card">
    <h2>帳戶資訊</h2>
    <p style="color:var(--muted);font-size:.92rem">Email：<!--EMAIL--></p>
    <p style="color:var(--muted);font-size:.92rem">方案：<!--TIER_STATUS--></p>
    <label for="display-name">顯示名稱</label>
    <div class="form-row">
      <input type="text" id="display-name" value="<!--DISPLAY_NAME-->" maxlength="40" placeholder="想讓我怎麼稱呼你？">
      <button class="btn" id="save-profile">儲存</button>
    </div>
    <div class="msg" id="profile-msg"></div>
  </div>

  <div class="card">
    <h2>交易明細</h2>
    <!--TXN_LIST-->
  </div>

  <div class="foot">
    <p><button class="btn ghost" id="logout-btn">登出</button></p>
  </div>
</div>

<script>
  // 儲存顯示名稱：同時會更新名單中心的最後互動時間（參與度訊號）
  const saveBtn = document.getElementById('save-profile');
  const profileMsg = document.getElementById('profile-msg');

  saveBtn.addEventListener('click', async () => {
    saveBtn.disabled = true;
    try {
      const res = await fetch('/api/reader/profile', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: document.getElementById('display-name').value })
      });
      if (res.ok) {
        profileMsg.textContent = '已儲存。';
        profileMsg.className = 'msg show ok';
      } else if (res.status === 401) {
        profileMsg.textContent = '登入已過期，請重新登入。';
        profileMsg.className = 'msg show err';
      } else {
        profileMsg.textContent = '儲存失敗，請稍後再試。';
        profileMsg.className = 'msg show err';
      }
    } catch (e) {
      profileMsg.textContent = '連線失敗，請稍後再試。';
      profileMsg.className = 'msg show err';
    } finally {
      saveBtn.disabled = false;
    }
  });

  // 登出後回訂閱首頁
  document.getElementById('logout-btn').addEventListener('click', async () => {
    await fetch('/api/reader/logout', { method: 'POST' });
    location.href = '/r/';
  });
</script>
</body>
</html>
```

- [ ] **Step 4: 實作 `ReaderPortalController`**

```java
package world.springai.survey.reader;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 讀者自助頁面：我的帳戶與我的邀請。
 *
 * <p>與 {@link ReaderPageController}（內容頁）、{@link ReaderAuthController}
 * （登入流程）分開：這裡處理的是「讀者對自己帳戶的操作」，依賴組合完全不同。</p>
 *
 * <p>頁面而非 API：未登入時<b>導向登入頁並帶 redirect</b>，而不是回 401——
 * 讀者在瀏覽器看到空白的 401 是死路。</p>
 */
@RestController
public class ReaderPortalController {

    /** 日期顯示格式 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HtmlTemplate htmlTemplate;
    private final ReaderContext readerContext;
    private final CreditTxnRepository creditTxnRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final ReferralService referralService;
    private final CreditPolicy creditPolicy;

    /** 注入渲染、身分解析、帳本、名單中心、邀請統計與點數參數 */
    public ReaderPortalController(HtmlTemplate htmlTemplate,
                                 ReaderContext readerContext,
                                 CreditTxnRepository creditTxnRepository,
                                 SurveyResponseRepository surveyResponseRepository,
                                 ReferralService referralService,
                                 CreditPolicy creditPolicy) {
        this.htmlTemplate = htmlTemplate;
        this.readerContext = readerContext;
        this.creditTxnRepository = creditTxnRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.referralService = referralService;
        this.creditPolicy = creditPolicy;
    }

    /** 個人資料更新請求；目前只開放顯示名稱 */
    public record ProfileRequest(String name) {}

    /** 我的帳戶：餘額、方案、交易明細、顯示名稱編輯 */
    @GetMapping(value = "/r/me", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> me(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        if (current.isEmpty()) {
            return redirectToLogin("/r/me");
        }
        Reader reader = current.get().reader();

        Map<String, String> vars = new HashMap<>();
        vars.put("<!--NAV_LINKS-->", "<a href=\"/r/archive\">歷史內容</a><a href=\"/r/invite\">我的邀請</a>");
        vars.put("<!--CREDITS-->", String.valueOf(reader.getCredits()));
        vars.put("<!--PREMIUM_COST-->", String.valueOf(creditPolicy.premiumCost()));
        vars.put("<!--EMAIL-->", HtmlTemplate.escapeHtml(reader.getEmail()));
        vars.put("<!--TIER_STATUS-->", renderTierStatus(reader));
        vars.put("<!--DISPLAY_NAME-->", HtmlTemplate.escapeHtml(displayNameOf(reader.getEmail())));
        vars.put("<!--TXN_LIST-->", renderTransactions(
            creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(reader.getId())));

        return privatePage(htmlTemplate.render("static/reader/me.html", vars));
    }

    /**
     * 更新顯示名稱。
     *
     * <p>名單中查無此 email 時回 404 而<b>不建新列</b>：建列會讓「讀者維護
     * 個人資訊」變成「讀者可往名單中心插資料」，而名單中心的每一列都代表
     * 一份同意紀錄。</p>
     */
    @PostMapping("/api/reader/profile")
    public ResponseEntity<Void> updateProfile(
            @RequestBody ProfileRequest request,
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        if (current.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String email = current.get().reader().getEmail();

        Optional<SurveyResponse> row =
            surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email);
        if (row.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 只截斷不拒絕：使用者輸入超長名稱時默默存前 40 字比回 400 友善，
        // 而顯示名稱沒有任何正確性要求
        String name = request.name() == null ? "" : request.name().trim();
        row.get().setName(name.length() > 40 ? name.substring(0, 40) : name);
        surveyResponseRepository.save(row.get());
        // 更新個人資料是高可靠的參與度訊號（spec §5.10）
        surveyResponseRepository.touchEngagement(email, OffsetDateTime.now());

        return ResponseEntity.ok().build();
    }

    /** 導向登入頁並帶回跳目標 */
    private ResponseEntity<String> redirectToLogin(String target) {
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, "/r/login?redirect=" + target)
            .build();
    }

    /**
     * 個人頁面的回應標頭。
     *
     * <p>內容含餘額與交易明細，絕不可被共享快取（CDN、app-gateway 反向代理）
     * 拿去餵給別的讀者。</p>
     */
    private ResponseEntity<String> privatePage(String html) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
            .body(html);
    }

    /**
     * 描述方案狀態。
     *
     * <p>本系統刻意不做 VIP 自動降級排程（spec §13.5），所以資料庫裡會有
     * 「tier=VIP 但已過期」的列。這裡以 {@link Reader#isActiveVip} 判斷而非
     * 直接看 tier——否則讀者會以為自己還是 VIP，然後在解鎖時發現要扣點。</p>
     */
    private String renderTierStatus(Reader reader) {
        OffsetDateTime now = OffsetDateTime.now();
        if (reader.isActiveVip(now)) {
            return reader.getVipExpiresAt() == null
                ? "VIP（無到期日）"
                : "VIP（有效至 " + reader.getVipExpiresAt().format(DATE_FORMAT) + "）";
        }
        if (Reader.TIER_VIP.equals(reader.getTier())) {
            return "VIP 已到期，目前為一般訂閱者";
        }
        return "一般訂閱者";
    }

    /** 從名單中心取顯示名稱；查無資料回空字串 */
    private String displayNameOf(String email) {
        return surveyResponseRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email)
            .map(SurveyResponse::getName)
            .orElse("");
    }

    /** 渲染交易明細；note 一律跳脫（存的是後台輸入的文章主旨與 email） */
    private String renderTransactions(List<CreditTxn> transactions) {
        if (transactions.isEmpty()) {
            return "<p class=\"empty\">還沒有交易紀錄。</p>";
        }
        StringBuilder sb = new StringBuilder("<ul class=\"txn-list\">");
        for (CreditTxn txn : transactions) {
            String sign = txn.getDelta() >= 0 ? "+" : "";
            String cls = txn.getDelta() >= 0 ? "gain" : "spend";
            sb.append("<li class=\"txn-item\">")
              .append("<span class=\"txn-delta ").append(cls).append("\">")
              .append(sign).append(txn.getDelta()).append("</span>")
              .append("<span class=\"txn-reason\">").append(reasonLabel(txn.getReason())).append("</span>")
              .append("<span class=\"txn-note\">").append(HtmlTemplate.escapeHtml(txn.getNote())).append("</span>")
              .append("<span class=\"txn-date\">")
              .append(txn.getCreatedAt() == null ? "" : txn.getCreatedAt().format(DATE_FORMAT))
              .append("</span></li>");
        }
        return sb.append("</ul>").toString();
    }

    /** 把交易原因代碼轉成讀者看得懂的中文 */
    private String reasonLabel(String reason) {
        return switch (reason) {
            case CreditTxn.REASON_SIGNUP_GRANT -> "初始贈點";
            case CreditTxn.REASON_REFERRAL -> "邀請獎勵";
            case CreditTxn.REASON_READ -> "解鎖文章";
            case CreditTxn.REASON_ADMIN_GRANT -> "站方贈點";
            // 未知代碼原樣顯示：新增 reason 時忘記加對應中文，
            // 顯示代碼比顯示空字串好——讀者看得到「有這筆」而非憑空消失
            default -> reason;
        };
    }
}
```

- [ ] **Step 5: 執行測試確認通過**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=ReaderPortalControllerTest`
Expected: 全部通過（14 個測試）

- [ ] **Step 6: 補 `reader.css` 的帳戶頁樣式**

```css
/* 餘額數字：帳戶頁的視覺焦點 */
.balance {
  font-size: 2rem;
  font-weight: 700;
  margin: 6px 0 4px;
  color: var(--accent, #0d9488);
}

/* 交易明細列表 */
.txn-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.txn-item {
  display: grid;
  grid-template-columns: 4.5rem 6rem 1fr auto;
  gap: 10px;
  align-items: baseline;
  padding: 10px 0;
  border-bottom: 1px solid var(--line, #eee);
  font-size: .94rem;
}

.txn-delta {
  font-weight: 700;
  text-align: right;
}

.txn-delta.gain { color: #0d9488; }
.txn-delta.spend { color: #b45309; }

.txn-note {
  color: var(--muted);
  overflow-wrap: anywhere;
}

.txn-date {
  color: var(--muted);
  font-size: .85rem;
}

/* 窄螢幕改為兩行，避免四欄擠壓 */
@media (max-width: 560px) {
  .txn-item {
    grid-template-columns: 4.5rem 1fr;
  }
  .txn-date { grid-column: 2; }
}
```

- [ ] **Step 7: 讓其他頁面的導覽列連到 `/r/me`**

`ReaderPageController.navLinks(boolean)` 目前登入時只顯示「歷史內容」。改為：

```java
    /** 依登入狀態顯示不同的導覽連結 */
    private String navLinks(boolean loggedIn) {
        if (loggedIn) {
            return "<a href=\"/r/archive\">歷史內容</a><a href=\"/r/me\">我的帳戶</a>";
        }
        return "<a href=\"/r/archive\">歷史內容</a><a href=\"/r/login\">登入</a>";
    }
```

> 這個改動會讓 `ReaderPageControllerTest` 中斷言導覽列內容的測試失敗（若有）。修正它們——這是預期的行為變更，不是迴歸。

- [ ] **Step 8: 跑全套測試並 Commit**

```bash
cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B clean test
cd .. && git add survey-backend/src/main/java/world/springai/survey/reader/ \
        survey-backend/src/main/resources/static/reader/ \
        survey-backend/src/test/java/world/springai/survey/reader/
git commit -m "feat(reader): 我的帳戶頁（餘額、方案、交易明細、顯示名稱）"
git diff HEAD --stat -- survey-backend/   # 必須無輸出
```

---

## Task 10: 我的邀請（`/r/invite`）

**背景**：spec §8 要求顯示邀請碼與連結、已成功邀請人數與獲得點數。

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/ReaderPortalController.java`（新增 `/r/invite`）
- Create: `survey-backend/src/main/resources/static/reader/invite.html`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReaderPortalControllerTest.java`（既有）

**Interfaces:**
- Consumes: `ReferralService.stats(Long)`、`CreditPolicy.referralReward()`
- Produces: 端點 `GET /r/invite`（需登入）

- [ ] **Step 1: 寫失敗測試**

加到 `ReaderPortalControllerTest`：

```java
    /** 未登入導向登入頁並帶回跳目標 */
    @Test
    void anonymousInviteRedirectsToLogin() throws Exception {
        when(readerContext.resolve(any())).thenReturn(Optional.empty());

        mvc.perform(get("/r/invite"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "/r/login?redirect=/r/invite"));
    }

    /** 顯示完整的邀請連結（含 ?ref= 與讀者自己的邀請碼） */
    @Test
    void showsFullInviteLinkWithReferralCode() throws Exception {
        givenLoggedIn(reader(300));

        mvc.perform(get("/r/invite").cookie(cookie()))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("/r/?ref=CODE1234")));
    }

    /** 顯示邀請成效：人數與累計點數 */
    @Test
    void showsReferralStats() throws Exception {
        givenLoggedIn(reader(300));
        when(referralService.stats(READER_ID))
            .thenReturn(new ReferralService.ReferralStats(3, 300));

        String html = mvc.perform(get("/r/invite").cookie(cookie()))
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("3"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("300"));
    }

    /** 每位獎勵點數必須來自 CreditPolicy，不可寫死 */
    @Test
    void rewardPerInviteComesFromPolicy() throws Exception {
        givenLoggedIn(reader(300));
        when(creditPolicy.referralReward()).thenReturn(55);

        mvc.perform(get("/r/invite").cookie(cookie()))
           .andExpect(content().string(containsString("55")));
    }

    /** 尚無成功邀請時顯示鼓勵性空狀態，而不是「0 人」的冷數字 */
    @Test
    void showsEmptyStateWithNoInvites() throws Exception {
        givenLoggedIn(reader(300));
        when(referralService.stats(READER_ID))
            .thenReturn(new ReferralService.ReferralStats(0, 0));

        mvc.perform(get("/r/invite").cookie(cookie()))
           .andExpect(content().string(containsString("還沒有人")));
    }

    /**
     * 必須說明「被邀者點確認信才算成功」。
     *
     * <p>spec §5.4 明訂先講清楚以避免爭議：讀者分享了連結、朋友也訂閱了，
     * 但點數沒進來——若頁面沒事先說明，那就是一次客訴。</p>
     */
    @Test
    void explainsConfirmationRequirement() throws Exception {
        givenLoggedIn(reader(300));

        mvc.perform(get("/r/invite").cookie(cookie()))
           .andExpect(content().string(containsString("確認信")));
    }

    /** 邀請頁同樣不可被共享快取（含個人邀請碼） */
    @Test
    void invitePageIsNeverSharedCached() throws Exception {
        givenLoggedIn(reader(300));

        mvc.perform(get("/r/invite").cookie(cookie()))
           .andExpect(header().string("Cache-Control", "private, no-store"))
           .andExpect(header().string("Vary", "Cookie"));
    }
```

- [ ] **Step 2: 執行確認失敗**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=ReaderPortalControllerTest`
Expected: 新增測試失敗（`/r/invite` 回 404）

- [ ] **Step 3: 建立 `static/reader/invite.html`**

```html
<!doctype html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>我的邀請｜凱文大叔的電子報</title>
<meta name="robots" content="noindex">
<link rel="stylesheet" href="/r/reader.css">
</head>
<body>
<header class="site-head">
  <div class="site-head-inner">
    <a class="brand" href="/r/">凱文大叔的電子報</a>
    <nav><!--NAV_LINKS--></nav>
  </div>
</header>

<div class="wrap">
  <h1 style="font-size:1.5rem">我的邀請</h1>
  <p style="color:var(--muted)">把連結分享給可能有興趣的人。對方確認訂閱後，你會拿到 <!--REWARD--> 點。</p>

  <div class="card">
    <h2>你的邀請連結</h2>
    <div class="form-row">
      <input type="text" id="invite-link" value="<!--INVITE_LINK-->" readonly>
      <button class="btn" id="copy-btn">複製</button>
    </div>
    <div class="msg" id="copy-msg"></div>
    <p style="color:var(--muted);font-size:.9rem;margin-top:12px">
      邀請碼：<strong><!--REFERRAL_CODE--></strong>
    </p>
  </div>

  <div class="card">
    <h2>邀請成效</h2>
    <!--STATS_BLOCK-->
  </div>

  <div class="card">
    <h2>怎麼算成功？</h2>
    <p>對方用你的連結填了 email 之後，<strong>還要點開自己信箱裡的確認信</strong>，才算一次成功邀請。</p>
    <p style="color:var(--muted);font-size:.92rem">這樣設計是為了避免有人填假 email 刷點數。若朋友說已經訂閱但你的點數沒增加，通常是確認信還沒點——請他找一下信箱（有時會在促銷或垃圾郵件匣）。</p>
    <p style="margin-top:12px"><a href="/r/rules">看完整遊戲規則</a></p>
  </div>
</div>

<script>
  // 複製邀請連結；navigator.clipboard 在非 HTTPS 環境不可用，故保留 select 退路
  const copyBtn = document.getElementById('copy-btn');
  const linkInput = document.getElementById('invite-link');
  const copyMsg = document.getElementById('copy-msg');

  copyBtn.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(linkInput.value);
      copyMsg.textContent = '已複製到剪貼簿。';
      copyMsg.className = 'msg show ok';
    } catch (e) {
      // 退路：選取起來讓使用者自己按 Ctrl+C
      linkInput.select();
      copyMsg.textContent = '請按 Ctrl+C 複製。';
      copyMsg.className = 'msg show';
    }
  });
</script>
</body>
</html>
```

- [ ] **Step 4: 在 `ReaderPortalController` 新增 `/r/invite`**

建構子需增加 `@Value("${app.public-base-url}") String publicBaseUrl` 參數與同名欄位（邀請連結必須是完整網址，讀者要貼給別人）。

```java
    /** 我的邀請：邀請連結、邀請碼與成效 */
    @GetMapping(value = "/r/invite", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> invite(
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        Optional<ReaderContext.Current> current = readerContext.resolve(sessionCookie);
        if (current.isEmpty()) {
            return redirectToLogin("/r/invite");
        }
        Reader reader = current.get().reader();
        ReferralService.ReferralStats stats = referralService.stats(reader.getId());

        Map<String, String> vars = new HashMap<>();
        vars.put("<!--NAV_LINKS-->", "<a href=\"/r/archive\">歷史內容</a><a href=\"/r/me\">我的帳戶</a>");
        vars.put("<!--REWARD-->", String.valueOf(creditPolicy.referralReward()));
        // 完整網址：讀者要把它貼給別人，相對路徑沒有用
        vars.put("<!--INVITE_LINK-->",
            HtmlTemplate.escapeHtml(publicBaseUrl + "/r/?ref=" + reader.getReferralCode()));
        vars.put("<!--REFERRAL_CODE-->", HtmlTemplate.escapeHtml(reader.getReferralCode()));
        vars.put("<!--STATS_BLOCK-->", renderStats(stats));

        return privatePage(htmlTemplate.render("static/reader/invite.html", vars));
    }

    /**
     * 渲染邀請成效。
     *
     * <p>零邀請時給鼓勵性的空狀態而非「0 人 / 0 點」——冷數字讀起來像
     * 失敗提示，而這頁的目的是讓人想去分享。</p>
     */
    private String renderStats(ReferralService.ReferralStats stats) {
        if (stats.invitedCount() == 0) {
            return "<p class=\"empty\">還沒有人透過你的連結完成訂閱。分享出去試試看？</p>";
        }
        return """
            <p class="balance">%d 人</p>
            <p style="color:var(--muted);font-size:.92rem">累計獲得 %d 點</p>
            """.formatted(stats.invitedCount(), stats.earnedCredits());
    }
```

- [ ] **Step 5: 執行測試確認通過**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=ReaderPortalControllerTest`
Expected: 全部通過（21 個測試）

> 注意：建構子多了一個參數，`setUp()` 內的 `new ReaderPortalController(...)` 要補上第 7 個引數（例如 `"https://survey.example.com"`），否則整個測試檔編譯失敗。

- [ ] **Step 6: 跑全套測試並 Commit**

```bash
cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B clean test
cd .. && git add survey-backend/src/main/java/world/springai/survey/reader/ReaderPortalController.java \
        survey-backend/src/main/resources/static/reader/invite.html \
        survey-backend/src/test/java/world/springai/survey/reader/ReaderPortalControllerTest.java
git commit -m "feat(reader): 我的邀請頁（邀請連結、邀請碼、成效）"
git diff HEAD --stat -- survey-backend/   # 必須無輸出
```

---

## Task 11: 後台 API — VIP 授予、手動／批次加點、帳本查詢

**背景**：spec §7 的四項新增功能。VIP 一律由後台手動授予（spec §2 非目標：不做任何金流）。

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/reader/AdminReaderController.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/ReaderRepository.java`（依 email 前綴搜尋）
- Test: `survey-backend/src/test/java/world/springai/survey/reader/AdminReaderControllerTest.java`

**Interfaces:**
- Consumes: `AdminKeyGuard.verify(String key)`、`ReaderRepository`、`CreditTxnRepository`、`CreditPolicy.vipDefaultDays()`、`ReaderAccountService.findOrCreate(String, OffsetDateTime)`
- Produces:
  - `GET /api/admin/readers?q={email 片段}` → 讀者清單（含餘額、方案、到期日）
  - `POST /api/admin/readers/vip` body `{"email":"...","days":365}` → 授予／延長 VIP
  - `DELETE /api/admin/readers/vip?email=...` → 取消 VIP
  - `POST /api/admin/readers/credits` body `{"emails":["a@b.com"],"delta":100,"note":"2026 春季班"}` → 批次加點（單筆即長度 1 的陣列）
  - `GET /api/admin/readers/ledger?email=...` → 該讀者的交易明細

- [ ] **Step 1: 寫 `AdminReaderControllerTest`（失敗測試）**

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.AdminKeyGuard;
import world.springai.survey.ApiExceptionHandler;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 後台讀者管理：金鑰保護、VIP 授予、加點、帳本查詢 */
class AdminReaderControllerTest {

    private static final long READER_ID = 3L;
    private static final String KEY = "X-Admin-Key";

    private AdminKeyGuard guard;
    private ReaderRepository readerRepository;
    private CreditTxnRepository creditTxnRepository;
    private ReaderAccountService readerAccountService;
    private CreditPolicy creditPolicy;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        guard = mock(AdminKeyGuard.class);
        readerRepository = mock(ReaderRepository.class);
        creditTxnRepository = mock(CreditTxnRepository.class);
        readerAccountService = mock(ReaderAccountService.class);
        creditPolicy = mock(CreditPolicy.class);
        when(creditPolicy.vipDefaultDays()).thenReturn(365);
        mvc = MockMvcBuilders.standaloneSetup(new AdminReaderController(
                guard, readerRepository, creditTxnRepository, readerAccountService, creditPolicy))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    /** 建一個帶 id 的讀者 */
    private Reader reader(String email) {
        Reader r = new Reader(email, "CODE1234");
        ReflectionTestUtils.setField(r, "id", READER_ID);
        r.setCredits(300);
        return r;
    }

    /**
     * 每一個端點都必須經過金鑰驗證。
     *
     * <p>逐一驗證而不是只測一個：漏掉任何一個端點的 verify，就是一個
     * 讓任何人都能授予自己 VIP 或無限加點的洞。這種漏洞不會在功能測試中
     * 出現——功能測試都會帶金鑰。</p>
     */
    @Test
    void everyEndpointRequiresAdminKey() throws Exception {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED))
            .when(guard).verify(any());

        mvc.perform(get("/api/admin/readers").param("q", "a"))
           .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/readers/vip").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\"}"))
           .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/readers/vip").param("email", "a@b.com"))
           .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/readers/credits").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\"],\"delta\":10,\"note\":\"x\"}"))
           .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/readers/ledger").param("email", "a@b.com"))
           .andExpect(status().isUnauthorized());

        // 沒有任何一個端點在金鑰不符時還做了寫入
        verify(readerRepository, never()).save(any());
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
        verify(creditTxnRepository, never()).save(any());
    }

    /** 依 email 片段搜尋讀者 */
    @Test
    void searchesReadersByEmailFragment() throws Exception {
        when(readerRepository.findByEmailContainingIgnoreCaseOrderByEmailAsc("kevin"))
            .thenReturn(List.of(reader("kevin@example.com")));

        mvc.perform(get("/api/admin/readers").param("q", "kevin").header(KEY, "ok"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].email").value("kevin@example.com"))
           .andExpect(jsonPath("$[0].credits").value(300));
    }

    /** 授予 VIP：未指定天數時採用 CreditPolicy 的預設效期 */
    @Test
    void grantsVipWithDefaultDurationFromPolicy() throws Exception {
        Reader r = reader("a@b.com");
        when(readerRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(r));
        when(readerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\"}"))
           .andExpect(status().isOk());

        ArgumentCaptor<Reader> captor = ArgumentCaptor.forClass(Reader.class);
        verify(readerRepository).save(captor.capture());
        assertEquals(Reader.TIER_VIP, captor.getValue().getTier());
        assertNotNull(captor.getValue().getVipExpiresAt());
        // 預設 365 天：允許 1 天誤差以避免測試在午夜前後失敗
        long days = java.time.Duration.between(
            OffsetDateTime.now(), captor.getValue().getVipExpiresAt()).toDays();
        org.junit.jupiter.api.Assertions.assertTrue(days >= 363 && days <= 366, "實際天數 " + days);
    }

    /** 可指定自訂天數 */
    @Test
    void grantsVipWithExplicitDuration() throws Exception {
        Reader r = reader("a@b.com");
        when(readerRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(r));
        when(readerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"days\":30}"))
           .andExpect(status().isOk());

        ArgumentCaptor<Reader> captor = ArgumentCaptor.forClass(Reader.class);
        verify(readerRepository).save(captor.capture());
        long days = java.time.Duration.between(
            OffsetDateTime.now(), captor.getValue().getVipExpiresAt()).toDays();
        org.junit.jupiter.api.Assertions.assertTrue(days >= 29 && days <= 31, "實際天數 " + days);
    }

    /**
     * 對還沒有 reader 帳戶的 email 授予 VIP 時，要先建立帳戶。
     *
     * <p>這是實際會遇到的情境：課程學員名單匯入後尚未登入過，站方要先把
     * VIP 設好。若直接回 404，站方就得請學員先登入一次再回來設定——
     * 而這正是最容易漏掉的一步。</p>
     */
    @Test
    void grantingVipToUnknownEmailCreatesAccountFirst() throws Exception {
        when(readerRepository.findByEmailIgnoreCase("new@b.com")).thenReturn(Optional.empty());
        when(readerAccountService.findOrCreate(anyString(), any())).thenReturn(reader("new@b.com"));
        when(readerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@b.com\"}"))
           .andExpect(status().isOk());

        verify(readerAccountService).findOrCreate(anyString(), any());
    }

    /** 天數為 0 或負數必須回 400，不可產生立即過期的 VIP */
    @Test
    void nonPositiveDaysIsRejected() throws Exception {
        mvc.perform(post("/api/admin/readers/vip").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"days\":0}"))
           .andExpect(status().isBadRequest());

        verify(readerRepository, never()).save(any());
    }

    /** 取消 VIP：tier 回 FREE 且清掉到期日 */
    @Test
    void revokesVip() throws Exception {
        Reader r = reader("a@b.com");
        r.setTier(Reader.TIER_VIP);
        r.setVipExpiresAt(OffsetDateTime.now().plusDays(30));
        when(readerRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(r));
        when(readerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(delete("/api/admin/readers/vip").param("email", "a@b.com").header(KEY, "ok"))
           .andExpect(status().isOk());

        ArgumentCaptor<Reader> captor = ArgumentCaptor.forClass(Reader.class);
        verify(readerRepository).save(captor.capture());
        assertEquals(Reader.TIER_FREE, captor.getValue().getTier());
        // 到期日必須一併清掉：留著會讓日後重新授予時看到舊日期而誤判
        assertNull(captor.getValue().getVipExpiresAt());
    }

    /** 批次加點：每個 email 各寫一筆帳本 */
    @Test
    void grantsCreditsToMultipleReaders() throws Exception {
        when(readerRepository.findByEmailIgnoreCase(anyString()))
            .thenReturn(Optional.of(reader("a@b.com")));
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(1);

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\",\"c@b.com\"],\"delta\":100,\"note\":\"2026 春季班\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.granted").value(2));

        verify(readerRepository, times(2)).addCredits(anyLong(), anyInt());
        verify(creditTxnRepository, times(2)).save(any(CreditTxn.class));
    }

    /** 加點的帳本要記 ADMIN_GRANT 與說明文字（客訴對帳靠這個） */
    @Test
    void adminGrantRecordsReasonAndNote() throws Exception {
        when(readerRepository.findByEmailIgnoreCase(anyString()))
            .thenReturn(Optional.of(reader("a@b.com")));
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(1);

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\"],\"delta\":100,\"note\":\"2026 春季班\"}"))
           .andExpect(status().isOk());

        ArgumentCaptor<CreditTxn> captor = ArgumentCaptor.forClass(CreditTxn.class);
        verify(creditTxnRepository).save(captor.capture());
        assertEquals(CreditTxn.REASON_ADMIN_GRANT, captor.getValue().getReason());
        assertEquals("2026 春季班", captor.getValue().getNote());
        assertEquals(100, captor.getValue().getDelta());
    }

    /**
     * 說明文字必填。
     *
     * <p>ADMIN_GRANT 沒有說明就無法對帳——半年後看到「某人 +500 點」
     * 卻不知道為什麼，這筆就變成永遠的疑問。帳本是只增不改的，
     * 事後補不了說明。</p>
     */
    @Test
    void adminGrantRequiresNote() throws Exception {
        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\"],\"delta\":100,\"note\":\"  \"}"))
           .andExpect(status().isBadRequest());

        verify(creditTxnRepository, never()).save(any());
    }

    /** delta 為 0 必須回 400（沒有意義的操作，只會污染帳本） */
    @Test
    void zeroDeltaIsRejected() throws Exception {
        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\"],\"delta\":0,\"note\":\"x\"}"))
           .andExpect(status().isBadRequest());
    }

    /**
     * 允許扣點（負 delta），但不可讓餘額變負。
     *
     * <p>後台需要扣點的能力（誤加後修正）。用 deductCredits 的條件式 UPDATE
     * 而非 addCredits 負值：後者會讓餘額變成負數，而負餘額會讓
     * {@code credits >= cost} 永遠為假，讀者連 0 點狀態的提示都看不對。</p>
     */
    @Test
    void negativeDeltaCannotDriveBalanceBelowZero() throws Exception {
        when(readerRepository.findByEmailIgnoreCase(anyString()))
            .thenReturn(Optional.of(reader("a@b.com")));
        // 餘額 300，要扣 500 → 條件式扣款回 0 列
        when(readerRepository.deductCredits(anyLong(), anyInt())).thenReturn(0);

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"a@b.com\"],\"delta\":-500,\"note\":\"修正誤加\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.granted").value(0))
           .andExpect(jsonPath("$.failed").value(1));

        // 扣款失敗就不該寫帳本，否則餘額與帳本總和會不一致
        verify(creditTxnRepository, never()).save(any());
    }

    /** 查不到的 email 計入 failed 而不中斷整批 */
    @Test
    void unknownEmailsAreReportedNotFatal() throws Exception {
        when(readerRepository.findByEmailIgnoreCase("known@b.com"))
            .thenReturn(Optional.of(reader("known@b.com")));
        when(readerRepository.findByEmailIgnoreCase("ghost@b.com")).thenReturn(Optional.empty());
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(1);

        mvc.perform(post("/api/admin/readers/credits").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"known@b.com\",\"ghost@b.com\"],\"delta\":50,\"note\":\"x\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.granted").value(1))
           .andExpect(jsonPath("$.failed").value(1));
    }

    /** 帳本查詢回該讀者的全部交易 */
    @Test
    void returnsLedgerForReader() throws Exception {
        when(readerRepository.findByEmailIgnoreCase("a@b.com"))
            .thenReturn(Optional.of(reader("a@b.com")));
        when(creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(READER_ID))
            .thenReturn(List.of(new CreditTxn(READER_ID, -10, CreditTxn.REASON_READ, 42L, "某文章")));

        mvc.perform(get("/api/admin/readers/ledger").param("email", "a@b.com").header(KEY, "ok"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].delta").value(-10))
           .andExpect(jsonPath("$[0].reason").value("READ"));
    }

    /** 查不到讀者時帳本回 404 */
    @Test
    void ledgerForUnknownReaderIsNotFound() throws Exception {
        when(readerRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        mvc.perform(get("/api/admin/readers/ledger").param("email", "ghost@b.com").header(KEY, "ok"))
           .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: 執行確認失敗**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=AdminReaderControllerTest`
Expected: 編譯失敗，`cannot find symbol: class AdminReaderController`

- [ ] **Step 3: 在 `ReaderRepository` 新增搜尋方法**

```java
    /** 依 email 片段搜尋（不分大小寫），後台讀者管理使用 */
    List<Reader> findByEmailContainingIgnoreCaseOrderByEmailAsc(String fragment);
```

> 需補 `import java.util.List;`。

- [ ] **Step 4: 實作 `AdminReaderController`**

```java
package world.springai.survey.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import world.springai.survey.AdminKeyGuard;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 後台讀者管理：VIP 授予、手動／批次加點、帳本查詢（spec §7）。
 *
 * <p>VIP 一律由站方手動授予——本系統刻意不做任何金流（spec §2 非目標）。</p>
 *
 * <p><b>每個端點都必須先過 {@link AdminKeyGuard#verify}。</b>漏掉任何一個
 * 就是「任何人都能授予自己 VIP 或無限加點」的洞，而這種漏洞不會在功能測試中
 * 出現——功能測試都會帶金鑰。</p>
 */
@RestController
public class AdminReaderController {

    private static final Logger log = LoggerFactory.getLogger(AdminReaderController.class);

    /** 顯示名稱最長長度上限，避免後台清單被超長 note 撐爛 */
    private static final int MAX_NOTE_LENGTH = 200;

    private final AdminKeyGuard guard;
    private final ReaderRepository readerRepository;
    private final CreditTxnRepository creditTxnRepository;
    private final ReaderAccountService readerAccountService;
    private final CreditPolicy creditPolicy;

    /** 注入金鑰守衛、讀者、帳本、帳戶建立與點數參數 */
    public AdminReaderController(AdminKeyGuard guard,
                                ReaderRepository readerRepository,
                                CreditTxnRepository creditTxnRepository,
                                ReaderAccountService readerAccountService,
                                CreditPolicy creditPolicy) {
        this.guard = guard;
        this.readerRepository = readerRepository;
        this.creditTxnRepository = creditTxnRepository;
        this.readerAccountService = readerAccountService;
        this.creditPolicy = creditPolicy;
    }

    /** VIP 授予請求；days 為 null 時採用 CreditPolicy 的預設效期 */
    public record VipRequest(String email, Integer days) {}

    /** 加點請求；delta 可為負（修正誤加），note 必填供對帳 */
    public record CreditGrantRequest(List<String> emails, Integer delta, String note) {}

    /** 批次加點結果 */
    public record GrantResult(int granted, int failed, List<String> failedEmails) {}

    /** 依 email 片段搜尋讀者 */
    @GetMapping("/api/admin/readers")
    public List<Map<String, Object>> search(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestParam("q") String query) {
        guard.verify(key);
        return readerRepository.findByEmailContainingIgnoreCaseOrderByEmailAsc(query.trim())
            .stream()
            .map(this::toSummary)
            .toList();
    }

    /**
     * 授予或延長 VIP。
     *
     * <p>對還沒有 reader 帳戶的 email 會先建立帳戶——這是實際情境：
     * 課程學員名單匯入後尚未登入過，站方要先把 VIP 設好。若回 404，
     * 站方得請學員先登入一次再回來設定，而那正是最容易漏掉的一步。</p>
     */
    @PostMapping("/api/admin/readers/vip")
    public Map<String, Object> grantVip(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody VipRequest request) {
        guard.verify(key);

        int days = request.days() == null ? creditPolicy.vipDefaultDays() : request.days();
        // 0 或負數會產生「授予後立即過期」的 VIP，那不是任何人想要的結果
        if (days <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VIP 天數必須大於 0");
        }

        OffsetDateTime now = OffsetDateTime.now();
        Reader reader = readerRepository.findByEmailIgnoreCase(normalize(request.email()))
            .orElseGet(() -> readerAccountService.findOrCreate(normalize(request.email()), now));

        reader.setTier(Reader.TIER_VIP);
        reader.setVipExpiresAt(now.plusDays(days));
        Reader saved = readerRepository.save(reader);

        log.info("已授予 VIP：{} 至 {}", saved.getEmail(), saved.getVipExpiresAt());
        return toSummary(saved);
    }

    /**
     * 取消 VIP。
     *
     * <p>到期日必須一併清掉：留著會讓日後重新授予時在後台看到舊日期而誤判
     * 「這人還是 VIP」。</p>
     */
    @DeleteMapping("/api/admin/readers/vip")
    public Map<String, Object> revokeVip(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestParam("email") String email) {
        guard.verify(key);

        Reader reader = readerRepository.findByEmailIgnoreCase(normalize(email))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查無此讀者"));
        reader.setTier(Reader.TIER_FREE);
        reader.setVipExpiresAt(null);

        log.info("已取消 VIP：{}", reader.getEmail());
        return toSummary(readerRepository.save(reader));
    }

    /**
     * 批次加點（單筆即長度 1 的陣列）。
     *
     * <p>單筆失敗不中斷整批，回報 granted / failed 與失敗清單——貼一整班
     * 學員的名單時，其中一個打錯字不該讓其他人都拿不到點數。</p>
     */
    @PostMapping("/api/admin/readers/credits")
    public GrantResult grantCredits(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody CreditGrantRequest request) {
        guard.verify(key);

        int delta = request.delta() == null ? 0 : request.delta();
        if (delta == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "點數變動不得為 0");
        }
        // ADMIN_GRANT 沒有說明就無法對帳：帳本只增不改，事後補不了說明
        if (request.note() == null || request.note().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請填寫加點說明（供日後對帳）");
        }
        String note = request.note().trim();
        if (note.length() > MAX_NOTE_LENGTH) {
            note = note.substring(0, MAX_NOTE_LENGTH);
        }
        if (request.emails() == null || request.emails().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請至少提供一個 email");
        }

        int granted = 0;
        List<String> failed = new ArrayList<>();
        for (String raw : request.emails()) {
            String email = normalize(raw);
            if (email.isEmpty()) {
                continue;
            }
            var found = readerRepository.findByEmailIgnoreCase(email);
            if (found.isEmpty()) {
                failed.add(email);
                continue;
            }
            // 扣點走條件式 UPDATE，避免餘額變負——負餘額會讓
            // credits >= cost 永遠為假，讀者連 0 點的提示都看不對
            int affected = delta > 0
                ? readerRepository.addCredits(found.get().getId(), delta)
                : readerRepository.deductCredits(found.get().getId(), -delta);
            if (affected == 0) {
                failed.add(email);
                continue;
            }
            creditTxnRepository.save(new CreditTxn(
                found.get().getId(), delta, CreditTxn.REASON_ADMIN_GRANT, null, note));
            granted++;
        }

        log.info("後台加點 {} 點：成功 {} 筆、失敗 {} 筆（{}）", delta, granted, failed.size(), note);
        return new GrantResult(granted, failed.size(), failed);
    }

    /** 某讀者的交易明細（客訴對帳用） */
    @GetMapping("/api/admin/readers/ledger")
    public List<CreditTxn> ledger(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestParam("email") String email) {
        guard.verify(key);

        Reader reader = readerRepository.findByEmailIgnoreCase(normalize(email))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "查無此讀者"));
        return creditTxnRepository.findByReaderIdOrderByCreatedAtDesc(reader.getId());
    }

    /**
     * 讀者摘要。
     *
     * <p>`vipActive` 以 {@link Reader#isActiveVip} 計算而非直接看 tier：
     * 系統不做自動降級（spec §13.5），資料庫裡會有「tier=VIP 但已過期」的列，
     * 後台若照 tier 顯示會誤判。</p>
     */
    private Map<String, Object> toSummary(Reader reader) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("email", reader.getEmail());
        map.put("tier", reader.getTier());
        map.put("vipActive", reader.isActiveVip(OffsetDateTime.now()));
        map.put("vipExpiresAt", reader.getVipExpiresAt());
        map.put("credits", reader.getCredits());
        map.put("referralCode", reader.getReferralCode());
        map.put("lastLoginAt", reader.getLastLoginAt());
        return map;
    }

    /** email 正規化：去前後空白並轉小寫 */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
```

- [ ] **Step 5: 執行測試並以真實啟動驗證衍生查詢**

```bash
cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=AdminReaderControllerTest
JAVA_HOME=/d/java/jdk-21 timeout 90 mvn -B spring-boot:run 2>&1 | grep -E "Started SurveyApplication|PropertyReferenceException|Ambiguous mapping"
```

Expected: 測試全綠；啟動出現 `Started SurveyApplication`，無 `PropertyReferenceException`（`findByEmailContainingIgnoreCaseOrderByEmailAsc` 是衍生查詢，拼錯只在啟動時炸）

- [ ] **Step 6: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/reader/ \
        survey-backend/src/test/java/world/springai/survey/reader/AdminReaderControllerTest.java
git commit -m "feat(reader): 後台讀者管理 API（VIP 授予、批次加點、帳本查詢）"
git diff HEAD --stat -- survey-backend/   # 必須無輸出
```

---

## Task 12: 後台 API — 參數設定

**背景**：spec §7 與 §9.1。參數存 DB 就是為了「改完立即生效、不必重新部署」，但目前沒有任何端點可以改。

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/AdminSettingController.java`
- Test: `survey-backend/src/test/java/world/springai/survey/AdminSettingControllerTest.java`

**Interfaces:**
- Consumes: `AdminKeyGuard.verify`、`AppSettingService.get` / `set` / `getInt`
- Produces:
  - `GET /api/admin/settings` → 全部可調參數的目前值
  - `PUT /api/admin/settings` body `{"credit.premium_cost":"20"}` → 更新（可一次多筆）

> **為什麼放在根 package 而非 `reader`**：這些參數跨越多個領域（點數給 `reader`、參與度門檻給 `audience`），不屬於任何單一 package。`AppSettingService` 本身也在根 package，理由相同。

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 後台參數設定：金鑰保護、白名單、型別驗證 */
class AdminSettingControllerTest {

    private static final String KEY = "X-Admin-Key";

    private AdminKeyGuard guard;
    private AppSettingService settings;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        guard = mock(AdminKeyGuard.class);
        settings = mock(AppSettingService.class);
        when(settings.getInt(anyString(), anyInt()))
            .thenAnswer(inv -> inv.getArgument(1, Integer.class));
        mvc = MockMvcBuilders.standaloneSetup(new AdminSettingController(guard, settings))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    /** 兩個端點都要金鑰 */
    @Test
    void bothEndpointsRequireAdminKey() throws Exception {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED))
            .when(guard).verify(any());

        mvc.perform(get("/api/admin/settings")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":\"20\"}"))
           .andExpect(status().isUnauthorized());

        verify(settings, never()).set(anyString(), anyString());
    }

    /** 讀取回全部可調參數 */
    @Test
    void listsAllAdjustableSettings() throws Exception {
        mvc.perform(get("/api/admin/settings").header(KEY, "ok"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$['credit.premium_cost']").exists())
           .andExpect(jsonPath("$['credit.signup_grant']").exists())
           .andExpect(jsonPath("$['credit.referral_reward']").exists())
           .andExpect(jsonPath("$['vip.default_days']").exists());
    }

    /** 更新單筆參數 */
    @Test
    void updatesSetting() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":\"20\"}"))
           .andExpect(status().isOk());

        verify(settings).set(eq(AppSettingService.CREDIT_PREMIUM_COST), eq("20"));
    }

    /** 可一次更新多筆 */
    @Test
    void updatesMultipleSettings() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":\"20\",\"credit.referral_reward\":\"150\"}"))
           .andExpect(status().isOk());

        verify(settings).set(eq(AppSettingService.CREDIT_PREMIUM_COST), eq("20"));
        verify(settings).set(eq(AppSettingService.CREDIT_REFERRAL_REWARD), eq("150"));
    }

    /**
     * 不在白名單的鍵必須回 400。
     *
     * <p>沒有白名單，這個端點就變成「往 app_setting 寫任意鍵值」的通用寫入口。
     * 那不只是資料髒污——若日後有任何功能改讀 app_setting 的某個鍵，
     * 這個洞就成了行為注入點。</p>
     */
    @Test
    void unknownKeyIsRejected() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"evil.key\":\"boom\"}"))
           .andExpect(status().isBadRequest());

        verify(settings, never()).set(anyString(), anyString());
    }

    /** 非整數值必須回 400（全部可調參數都是整數） */
    @Test
    void nonIntegerValueIsRejected() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":\"abc\"}"))
           .andExpect(status().isBadRequest());

        verify(settings, never()).set(anyString(), anyString());
    }

    /**
     * 一筆無效就整批不寫入。
     *
     * <p>部分成功會讓後台顯示「已儲存」卻只改了一半，而使用者無從得知
     * 哪一筆沒進去。先全部驗證再全部寫入。</p>
     */
    @Test
    void invalidEntryRejectsWholeBatch() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":\"20\",\"credit.referral_reward\":\"abc\"}"))
           .andExpect(status().isBadRequest());

        // 有效的那筆也不可以被寫入
        verify(settings, never()).set(anyString(), anyString());
    }

    /**
     * premium_cost 設為 0 或負數必須回 400。
     *
     * <p>CreditPolicy 會把它夾成 1，所以不會外洩內容——但後台顯示 0
     * 而實際是 1 的落差同樣會誤導營運判斷。在入口就擋掉比較誠實。</p>
     */
    @Test
    void nonPositivePremiumCostIsRejected() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.premium_cost\":\"0\"}"))
           .andExpect(status().isBadRequest());
    }

    /** 贈點與邀請獎勵允許 0（關閉該機制），但不允許負數 */
    @Test
    void zeroGrantIsAllowedButNegativeIsNot() throws Exception {
        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.signup_grant\":\"0\"}"))
           .andExpect(status().isOk());

        mvc.perform(put("/api/admin/settings").header(KEY, "ok")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credit.signup_grant\":\"-1\"}"))
           .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: 執行確認失敗**

Run: `cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=AdminSettingControllerTest`
Expected: 編譯失敗，`cannot find symbol: class AdminSettingController`

- [ ] **Step 3: 實作 `AdminSettingController`**

```java
package world.springai.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 後台參數設定（spec §7、§9.1）。
 *
 * <p>參數存 DB 的唯一理由就是「改完立即生效、不必重新部署」，
 * 而在此之前沒有任何端點可以改——設定在資料庫裡卻改不動，等於還是寫死的。</p>
 *
 * <p><b>置於根 package 的理由</b>：這些參數跨越多個領域（點數給
 * {@code reader}、參與度門檻給 {@code audience}），不屬於任何單一 package。
 * {@link AppSettingService} 在根 package 也是同一個理由。</p>
 */
@RestController
public class AdminSettingController {

    private static final Logger log = LoggerFactory.getLogger(AdminSettingController.class);

    /**
     * 可調參數白名單：鍵 → 允許的最小值。
     *
     * <p><b>白名單是必要的</b>：沒有它，這個端點就變成「往 app_setting 寫
     * 任意鍵值」的通用寫入口。那不只是資料髒污——若日後有任何功能改讀
     * app_setting 的某個鍵，這個洞就成了行為注入點。</p>
     *
     * <p><b>最小值各不相同</b>（與 {@code CreditPolicy} 的下限保護對應）：
     * premium_cost 與 vip.default_days 為 0 會造成權限外洩，故最小 1；
     * signup_grant 與 referral_reward 為 0 是合法的「關閉贈點」設定，故最小 0。</p>
     */
    private static final Map<String, Integer> ADJUSTABLE = Map.of(
        AppSettingService.CREDIT_SIGNUP_GRANT, 0,
        AppSettingService.CREDIT_PREMIUM_COST, 1,
        AppSettingService.CREDIT_REFERRAL_REWARD, 0,
        AppSettingService.VIP_DEFAULT_DAYS, 1);

    /** 各參數在查無設定時顯示的預設值（與 CreditPolicy 的後備值一致） */
    private static final Map<String, Integer> DISPLAY_DEFAULTS = Map.of(
        AppSettingService.CREDIT_SIGNUP_GRANT, 300,
        AppSettingService.CREDIT_PREMIUM_COST, 10,
        AppSettingService.CREDIT_REFERRAL_REWARD, 100,
        AppSettingService.VIP_DEFAULT_DAYS, 365);

    private final AdminKeyGuard guard;
    private final AppSettingService settings;

    /** 注入金鑰守衛與參數服務 */
    public AdminSettingController(AdminKeyGuard guard, AppSettingService settings) {
        this.guard = guard;
        this.settings = settings;
    }

    /** 讀取全部可調參數的目前值 */
    @GetMapping("/api/admin/settings")
    public Map<String, Integer> list(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        guard.verify(key);

        // LinkedHashMap 保持固定順序，讓後台欄位不會每次重新載入就跳動
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String settingKey : ordered()) {
            result.put(settingKey, settings.getInt(settingKey, DISPLAY_DEFAULTS.get(settingKey)));
        }
        return result;
    }

    /**
     * 更新參數（可一次多筆）。
     *
     * <p><b>先全部驗證再全部寫入</b>：部分成功會讓後台顯示「已儲存」卻只改了
     * 一半，而使用者無從得知哪一筆沒進去。</p>
     */
    @PutMapping("/api/admin/settings")
    public Map<String, Integer> update(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody Map<String, String> updates) {
        guard.verify(key);

        // 第一遍：全部驗證
        Map<String, Integer> validated = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            Integer min = ADJUSTABLE.get(entry.getKey());
            if (min == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不是可調參數：" + entry.getKey());
            }
            int value;
            try {
                value = Integer.parseInt(entry.getValue().trim());
            } catch (NumberFormatException | NullPointerException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    entry.getKey() + " 必須是整數，收到：" + entry.getValue());
            }
            if (value < min) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    entry.getKey() + " 不得小於 " + min + "，收到：" + value);
            }
            validated.put(entry.getKey(), value);
        }

        // 第二遍：全部寫入（set 會清除該鍵的快取，做到立即生效）
        validated.forEach((k, v) -> settings.set(k, String.valueOf(v)));
        log.info("後台更新參數：{}", validated);

        return list(key);
    }

    /** 固定的參數顯示順序 */
    private java.util.List<String> ordered() {
        return java.util.List.of(
            AppSettingService.CREDIT_SIGNUP_GRANT,
            AppSettingService.CREDIT_PREMIUM_COST,
            AppSettingService.CREDIT_REFERRAL_REWARD,
            AppSettingService.VIP_DEFAULT_DAYS);
    }
}
```

- [ ] **Step 4: 執行測試確認通過並 Commit**

```bash
cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B test -Dtest=AdminSettingControllerTest
cd .. && git add survey-backend/src/main/java/world/springai/survey/AdminSettingController.java \
        survey-backend/src/test/java/world/springai/survey/AdminSettingControllerTest.java
git commit -m "feat(admin): 參數設定 API（白名單 + 全驗證後才寫入）"
git diff HEAD --stat -- survey-backend/   # 必須無輸出
```

---

## Task 13: 交易信保留額度

**背景**：spec §6 的第 2 項。目前設定鍵 `app.mail.transactional-reserve` 存在但**沒有任何程式讀取它**——「群發吃光額度導致讀者無法登入」的風險從階段 B 上線就成立。本任務把它接上（依使用者決定提前至階段 C）。

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/mail/MailQuotaService.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/newsletter/CampaignService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/mail/MailQuotaServiceTest.java`（既有）
- Test: `survey-backend/src/test/java/world/springai/survey/newsletter/CampaignServiceTest.java`（既有）

**Interfaces:**
- Produces:
  - `MailQuotaService.Quota` 新增三個欄位：`long reserve, long marketingRemaining, long marketingBatchMax`
  - `MailQuotaService.reserve() → long`（供後台顯示原因）
  - `CampaignService.SendResult` 新增欄位 `int skippedForQuota`

- [ ] **Step 1: 寫失敗測試（`MailQuotaServiceTest`）**

沿用該檔案既有的 `MockRestServiceServer` 做法。**注意建構子多了第 4 個參數 `reserve`**，既有的三處 `new MailQuotaService(builder, ..., 100)` 都要補上（既有測試傳 `0` 以維持原斷言不變，新測試才傳非零值）：

```java
    /** 月額度剩 120 封的回應，用於保留額度的計算測試 */
    private static final String LOW_QUOTA_RESPONSE = """
        {"data":{"getZSendUserStatus":{"status":"healthy",\
        "dailyQuota":999999999,"dailySent":0,"quotaResetAt":"2026-07-26T00:00:00Z",\
        "monthlyQuota":50000,"monthlySent":49880,"monthlyResetAt":"2026-07-28T16:18:35Z",\
        "quotaType":"both","overageBillingEnabled":false}}}""";

    /**
     * 行銷可用量 = 剩餘額度 - 保留額度。
     *
     * <p>保留額度是給登入信、確認信、歡迎信的。若群發把額度用到 0，
     * 讀者就收不到 magic link——那不是「信少寄一封」，而是整個讀者端
     * 登不進去（spec §6）。</p>
     */
    @Test
    void marketingRemainingSubtractsReserve() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "sk-test", 100, 50);

        server.expect(requestTo("https://api.zeabur.com/graphql"))
              .andRespond(withSuccess(LOW_QUOTA_RESPONSE, APPLICATION_JSON));

        MailQuotaService.Quota q = service.current();

        assertEquals(120, q.remaining());
        assertEquals(50, q.reserve());
        assertEquals(70, q.marketingRemaining());
        server.verify();
    }

    /** 剩餘額度低於保留額度時，行銷可用量為 0 而非負數 */
    @Test
    void marketingRemainingNeverGoesNegative() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // fallback 額度 30、保留 50 → 行銷可用量應為 0，不可是 -20
        MailQuotaService service = new MailQuotaService(builder, "", 30, 50);

        MailQuotaService.Quota q = service.current();

        assertEquals(0, q.marketingRemaining());
        assertEquals(0, q.marketingBatchMax());
    }

    /** 行銷單批上限同時受 BATCH_CAP 與行銷可用量限制 */
    @Test
    void marketingBatchMaxRespectsBothCaps() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "sk-test", 100, 50);

        server.expect(requestTo("https://api.zeabur.com/graphql"))
              .andRespond(withSuccess(PRO_RESPONSE, APPLICATION_JSON));

        MailQuotaService.Quota q = service.current();

        // 剩餘 48800 − 保留 50 = 48750，但單批仍收斂到 BATCH_CAP
        assertEquals(48750, q.marketingRemaining());
        assertEquals(MailQuotaService.BATCH_CAP, q.marketingBatchMax());
        server.verify();
    }

    /** fallback 路徑也要扣除保留額度（不能只在成功偵測時才保留） */
    @Test
    void fallbackQuotaAlsoReservesTransactional() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "", 100, 50);

        MailQuotaService.Quota q = service.current();

        assertEquals("fallback", q.source());
        assertEquals(50, q.marketingRemaining());
        server.verify();
    }

    /** 保留額度設為 0 時，行銷可用量等於剩餘額度（等同關閉此機制） */
    @Test
    void zeroReserveMeansNoRestriction() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailQuotaService service = new MailQuotaService(builder, "", 100, 0);

        MailQuotaService.Quota q = service.current();

        assertEquals(q.remaining(), q.marketingRemaining());
    }
```

- [ ] **Step 2: 修改 `MailQuotaService`**

1. 建構子**在最後**新增第 4 個參數（順序很重要——測試以位置引數呼叫）：

```java
    /** 注入 HTTP 客戶端建構器、Zeabur 帳號 token、偵測失敗時的保守額度與交易信保留額度 */
    public MailQuotaService(RestClient.Builder builder,
                            @Value("${app.mail.zeabur-token:}") String zeaburToken,
                            @Value("${app.mail.fallback-quota:100}") long fallbackQuota,
                            @Value("${app.mail.transactional-reserve:50}") long transactionalReserve) {
```

並加欄位與註解：

```java
    /**
     * 保留給交易信的額度（封）。
     *
     * <p>登入信、確認信、歡迎信不受此限制——它們正是 reserve 的使用者。
     * 這個數字存在的理由：群發把額度用到 0 時，讀者就收不到 magic link，
     * 那不是「信少寄一封」，而是整個讀者端登不進去（spec §6）。</p>
     */
    private final long transactionalReserve;
```

2. `Quota` record 新增三個欄位：

```java
     * @param reserve            保留給交易信的額度
     * @param marketingRemaining 可用於行銷信的量 = max(0, remaining - reserve)
     * @param marketingBatchMax  行銷信單批上限 = min(marketingRemaining, BATCH_CAP)
```

```java
    public record Quota(String source, String status,
                        long dailyQuota, long dailySent, long dailyRemaining,
                        long monthlyQuota, long monthlySent, long monthlyRemaining,
                        long remaining, long batchMax,
                        long reserve, long marketingRemaining, long marketingBatchMax,
                        boolean overageBillingEnabled,
                        String quotaResetAt, String monthlyResetAt) {}
```

3. `fetch()` 與 `fallback()` 都要計算新欄位。抽一個私有方法避免兩處各算一次：

```java
    /** 依剩餘額度算出行銷可用量與單批上限（扣除交易信保留額度） */
    private long[] marketingLimits(long remaining) {
        long marketingRemaining = Math.max(0, remaining - transactionalReserve);
        return new long[] { marketingRemaining, Math.min(marketingRemaining, BATCH_CAP) };
    }
```

4. 新增 `public long reserve() { return transactionalReserve; }` 供後台顯示。

- [ ] **Step 3: 讓 `CampaignService` 在群發前檢查**

1. 建構子注入 `MailQuotaService mailQuotaService`。

> 依賴方向：`newsletter` → `mail` 是授權方向，`PackageDependencyTest` 通過。

2. `SendResult` 新增欄位：

```java
    /**
     * 發送結果。
     *
     * @param skippedForQuota 因保留交易信額度而未寄出的人數；> 0 時後台必須顯示原因
     */
    public record SendResult(Long campaignId, int recipientCount, int accepted, int failed,
                             int skippedForQuota) {}
```

3. 在 `send(...)` 取得 recipients 之後、建立 campaign 之前插入：

```java
        // 保留交易信額度（spec §6）：群發不得吃掉登入信與確認信的可用量，
        // 否則讀者收不到 magic link 就整個登不進讀者端。
        MailQuotaService.Quota quota = mailQuotaService.current();
        int skippedForQuota = 0;
        if (quota.marketingRemaining() <= 0) {
            // 完全沒有行銷可用量時直接拒絕，而不是寄 0 封後回報成功——
            // 後者會讓後台顯示「已發送」而實際上沒人收到。
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "行銷可用額度為 0（剩餘 " + quota.remaining() + " 封已全數保留給登入信等交易信）。"
                    + "請等額度重置後再發送。");
        }
        if (recipients.size() > quota.marketingRemaining()) {
            // 縮減批量而非全部拒絕：先寄一部分，剩下的下次再寄。
            // 縮減量必須回報，否則「已發送」會被誤解成全部寄出。
            skippedForQuota = (int) (recipients.size() - quota.marketingRemaining());
            recipients = recipients.subList(0, (int) quota.marketingRemaining());
            log.warn("群發縮減批量：原 {} 人，因保留 {} 封交易信額度而只寄 {} 人",
                recipients.size() + skippedForQuota, quota.reserve(), recipients.size());
        }
```

> **注意 `recipients` 的宣告**：原本是 `List<String> recipients = recipientService.recipients(role, interest);`。若它是 `final` 或後續有 `recipients.size()` 的使用假設，請確認縮減後所有引用都用縮減後的清單。**特別檢查 `campaign.setRecipientCount(...)`——它必須記錄實際寄送人數，不是原始人數**，否則補寄（階段 E）算差集時會把被縮減的人算成「已寄失敗」。

4. 所有 `new SendResult(...)` 的呼叫點都要補第 5 個引數。`reschedule` 走排程路徑，同樣要檢查額度（排程也會實際寄出）。

- [ ] **Step 4: 更新既有測試**

`CampaignServiceTest` 需要：
- 建構子補 `MailQuotaService` mock，並 stub `current()` 回一個 `marketingRemaining` 充足的 `Quota`（否則所有既有的發送測試都會撞到額度檢查）。
- 新增三個案例：

先在該檔案加一個建立 `Quota` 的輔助方法（`Quota` 有 15 個欄位，每個測試各寫一次會難以閱讀）：

```java
    /** 建一個只關心 marketingRemaining 的 Quota；其餘欄位給合理但無關的值 */
    private MailQuotaService.Quota quotaWithMarketing(long marketingRemaining) {
        long remaining = marketingRemaining + 50;
        return new MailQuotaService.Quota("zeabur", "healthy",
            999999999L, 0, 999999999L,
            50000, 0, remaining,
            remaining, Math.min(remaining, 500),
            50, marketingRemaining, Math.min(marketingRemaining, 500),
            false, null, null);
    }
```

再加三個測試（`recipientService.recipients(...)` 的 stub 方式沿用該檔案既有寫法）：

```java
    /**
     * 行銷可用量為 0 時拒絕發送並回 409。
     *
     * <p>不可寄 0 封後回報成功——那會讓後台顯示「已發送」而實際上沒人收到。</p>
     */
    @Test
    void sendIsRejectedWhenNoMarketingQuota() {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@b.com", "c@b.com"));
        when(mailQuotaService.current()).thenReturn(quotaWithMarketing(0));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.send("主旨", "# 內容", null, null, "now", null));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        // 一封都不能寄出
        verify(mailSender, never()).send(anyString(), anyString(), anyString());
    }

    /**
     * 收件人多於行銷可用量時縮減批量並回報縮減數。
     *
     * <p>`recipientCount` 必須是**實際寄送人數**而非原始人數：階段 E 的補寄
     * 會用它算差集，記成原始人數會讓被縮減的人被判定為「已寄但失敗」。</p>
     */
    @Test
    void sendTruncatesToMarketingQuotaAndReportsSkipped() {
        when(recipientService.recipients(null, null))
            .thenReturn(List.of("a@b.com", "b@b.com", "c@b.com", "d@b.com", "e@b.com"));
        when(mailQuotaService.current()).thenReturn(quotaWithMarketing(2));

        CampaignService.SendResult result = service.send("主旨", "# 內容", null, null, "now", null);

        assertEquals(2, result.recipientCount(), "只應寄送額度允許的人數");
        assertEquals(3, result.skippedForQuota(), "縮減數必須回報，否則會被誤解為全部寄出");

        ArgumentCaptor<Campaign> saved = ArgumentCaptor.forClass(Campaign.class);
        verify(campaignRepository, atLeastOnce()).save(saved.capture());
        assertEquals(2, saved.getValue().getRecipientCount(), "應記錄實際寄送人數，供補寄算差集");
    }

    /** 額度充足時行為與原本完全相同，skippedForQuota 為 0 */
    @Test
    void sendIsUnchangedWhenQuotaIsAmple() {
        when(recipientService.recipients(null, null)).thenReturn(List.of("a@b.com", "c@b.com"));
        when(mailQuotaService.current()).thenReturn(quotaWithMarketing(1000));

        CampaignService.SendResult result = service.send("主旨", "# 內容", null, null, "now", null);

        assertEquals(2, result.recipientCount());
        assertEquals(0, result.skippedForQuota());
    }
```

> **重要**：`setUp()` 內既有的 `new CampaignService(...)` 要補上 `mailQuotaService` mock，並且**必須 stub `current()` 回一個 `marketingRemaining` 充足的 Quota**——否則 Mockito 對未 stub 的方法回 `null`，所有既有的發送測試都會在額度檢查處 NPE。建議直接在 `setUp()` 加：
>
> ```java
> when(mailQuotaService.current()).thenReturn(quotaWithMarketing(10000));
> ```
>
> `send(...)` 的實際簽章請以該檔案既有呼叫為準（有 6 參數與 10 參數兩個 overload）；上面用的是既有測試最常用的那個。

`AdminCampaignControllerTest` 也會因 `SendResult` 多一欄而需要調整建構 `SendResult` 的地方。

- [ ] **Step 5: 更新後台額度顯示**

`admin.html` 的 `#quota-info` 目前顯示 `remaining`。改為同時顯示行銷可用量與保留額度（讓「為什麼只能寄這麼多」一眼可見）。在 Task 14 一併處理即可，此處只需確認 `GET /api/admin/mail-quota` 回傳的 JSON 已含新欄位（`Quota` record 加欄位後自動包含）。

- [ ] **Step 6: 跑全套測試並 Commit**

```bash
cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B clean test
cd .. && git add survey-backend/src/main/java/world/springai/survey/mail/MailQuotaService.java \
        survey-backend/src/main/java/world/springai/survey/newsletter/CampaignService.java \
        survey-backend/src/test/java/world/springai/survey/
git commit -m "feat(mail): 群發保留交易信額度，避免讀者收不到登入信"
git diff HEAD --stat -- survey-backend/   # 必須無輸出
```

- [ ] **Step 7: 同步 spec §6 的實作狀態**

spec §6 目前有一段「階段 B 的實際狀態（誠實記錄）」寫著第 2 項未實作、風險從階段 B 上線就成立。把它改為記錄「已於階段 C 實作」，並描述實際行為（可用量為 0 時回 409、不足時縮減批量並回報 `skippedForQuota`）。

---

## Task 14: 後台操作介面（`admin.html`）

**背景**：Task 11–13 的能力若沒有 UI，實務上等於不可用——授予 VIP 要手打 curl 帶金鑰。同時補上階段 B 遺留的「文章發布」欄位（spec §7 的「文章設定」，階段 B 只做了 API）。

**Files:**
- Modify: `survey-backend/src/main/resources/static/admin.html`
- Create: `survey-backend/scripts/verify-admin-reader.mjs`（可重跑的驗證腳本）

**Interfaces:**
- Consumes: Task 11 與 12 的全部端點、`GET /api/admin/mail-quota`、既有的 `POST /api/admin/campaign/send`

- [ ] **Step 1: 新增「讀者管理」分頁**

在 `admin.html` 的 `<nav class="tabs">` 內，`tab-campaign` 之後加入：

```html
    <button class="tab" id="tab-readers" data-view="readers">讀者管理</button>
    <button class="tab" id="tab-settings" data-view="settings">參數設定</button>
```

在 `campaign-view` 的 `</section>` 之後加入兩個新 view：

```html
  <section class="view" id="readers-view" hidden>
    <div class="card">
      <h2>搜尋讀者</h2>
      <div class="form-row">
        <input type="text" id="reader-q" placeholder="輸入 email 片段">
        <button class="btn" id="reader-search-btn">搜尋</button>
      </div>
      <div class="table-wrap">
        <table id="reader-table">
          <thead><tr><th>Email</th><th>方案</th><th>VIP 到期</th><th>點數</th><th>最後登入</th><th></th></tr></thead>
          <tbody></tbody>
        </table>
      </div>
      <div class="msg" id="reader-msg"></div>
    </div>

    <div class="card">
      <h2>授予 VIP</h2>
      <div class="row">
        <div><label for="vip-email">Email</label><input type="email" id="vip-email" placeholder="reader@example.com"></div>
        <div><label for="vip-days">效期（天）</label><input type="number" id="vip-days" min="1" placeholder="留空用預設"></div>
      </div>
      <button class="btn" id="vip-grant-btn" style="margin-top:12px">授予 VIP</button>
      <p class="hint">尚未登入過的 email 也可以先設定，系統會自動建立帳戶。</p>
      <div class="msg" id="vip-msg"></div>
    </div>

    <div class="card">
      <h2>加點（可批次）</h2>
      <label for="grant-emails">Email 清單（一行一筆）</label>
      <textarea id="grant-emails" rows="5" placeholder="student1@example.com&#10;student2@example.com"></textarea>
      <div class="row">
        <div><label for="grant-delta">點數（負數為扣點）</label><input type="number" id="grant-delta" value="100"></div>
        <div><label for="grant-note">說明（必填，供對帳）</label><input type="text" id="grant-note" placeholder="2026 春季班學員"></div>
      </div>
      <button class="btn" id="grant-btn" style="margin-top:12px">送出加點</button>
      <div class="msg" id="grant-msg"></div>
    </div>

    <div class="card">
      <h2>點數帳本查詢</h2>
      <div class="form-row">
        <input type="email" id="ledger-email" placeholder="reader@example.com">
        <button class="btn ghost" id="ledger-btn">查詢</button>
      </div>
      <div class="table-wrap">
        <table id="ledger-table">
          <thead><tr><th>時間</th><th>變動</th><th>原因</th><th>說明</th></tr></thead>
          <tbody></tbody>
        </table>
      </div>
      <div class="msg" id="ledger-msg"></div>
    </div>
  </section>

  <section class="view" id="settings-view" hidden>
    <div class="card">
      <h2>可調參數</h2>
      <p class="hint">改完立即生效，不需重新部署。讀者端的規則頁、我的帳戶與解鎖提示都會同步顯示新數字。</p>
      <div id="settings-fields"></div>
      <button class="btn" id="settings-save-btn" style="margin-top:14px">儲存</button>
      <div class="msg" id="settings-msg"></div>
    </div>
  </section>
```

- [ ] **Step 2: 補上對應的 JavaScript**

在 `admin.html` 既有 `<script>` 內加入（沿用該檔案既有的 `api()` / `esc()` / 金鑰帶入方式——**不要新寫一套 fetch 包裝**）：

```javascript
  // ===== 讀者管理 =====

  /** 搜尋讀者並渲染表格 */
  async function searchReaders() {
    const q = document.getElementById('reader-q').value.trim();
    if (!q) { return; }
    try {
      const rows = await api('/api/admin/readers?q=' + encodeURIComponent(q));
      const tbody = document.querySelector('#reader-table tbody');
      tbody.innerHTML = rows.map(r => `<tr>
        <td>${esc(r.email)}</td>
        <td>${r.vipActive ? 'VIP' : '一般'}</td>
        <td>${r.vipExpiresAt ? esc(r.vipExpiresAt.slice(0, 10)) : '—'}</td>
        <td>${r.credits}</td>
        <td>${r.lastLoginAt ? esc(r.lastLoginAt.slice(0, 10)) : '未登入'}</td>
        <td>${r.vipActive ? `<button class="btn ghost revoke-btn" data-email="${esc(r.email)}">取消 VIP</button>` : ''}</td>
      </tr>`).join('');
      // 取消 VIP 的按鈕是動態產生的，故用事件委派而非逐一綁定
      tbody.querySelectorAll('.revoke-btn').forEach(btn => {
        btn.addEventListener('click', () => revokeVip(btn.dataset.email));
      });
      showMsg('reader-msg', `找到 ${rows.length} 位讀者。`, true);
    } catch (e) {
      showMsg('reader-msg', '搜尋失敗：' + e.message, false);
    }
  }

  /** 取消某位讀者的 VIP */
  async function revokeVip(email) {
    if (!confirm(`確定要取消 ${email} 的 VIP？`)) { return; }
    try {
      await api('/api/admin/readers/vip?email=' + encodeURIComponent(email), { method: 'DELETE' });
      showMsg('reader-msg', '已取消 VIP。', true);
      await searchReaders();
    } catch (e) {
      showMsg('reader-msg', '取消失敗：' + e.message, false);
    }
  }

  /** 授予 VIP */
  async function grantVip() {
    const email = document.getElementById('vip-email').value.trim();
    const days = document.getElementById('vip-days').value.trim();
    if (!email) { showMsg('vip-msg', '請輸入 email。', false); return; }
    try {
      const body = { email };
      if (days) { body.days = Number(days); }
      const r = await api('/api/admin/readers/vip', { method: 'POST', body: JSON.stringify(body) });
      showMsg('vip-msg', `已授予 VIP，到期日 ${r.vipExpiresAt ? r.vipExpiresAt.slice(0, 10) : '無'}。`, true);
    } catch (e) {
      showMsg('vip-msg', '授予失敗：' + e.message, false);
    }
  }

  /** 批次加點 */
  async function grantCredits() {
    const emails = document.getElementById('grant-emails').value
      .split('\n').map(s => s.trim()).filter(Boolean);
    const delta = Number(document.getElementById('grant-delta').value);
    const note = document.getElementById('grant-note').value.trim();
    if (!emails.length) { showMsg('grant-msg', '請至少輸入一個 email。', false); return; }
    if (!note) { showMsg('grant-msg', '請填寫說明（供日後對帳）。', false); return; }
    if (!confirm(`確定要對 ${emails.length} 位讀者${delta >= 0 ? '加' : '扣'} ${Math.abs(delta)} 點？`)) { return; }
    try {
      const r = await api('/api/admin/readers/credits', {
        method: 'POST', body: JSON.stringify({ emails, delta, note })
      });
      const failedNote = r.failed > 0 ? `，失敗 ${r.failed} 筆：${r.failedEmails.join(', ')}` : '';
      showMsg('grant-msg', `成功 ${r.granted} 筆${failedNote}`, r.failed === 0);
    } catch (e) {
      showMsg('grant-msg', '加點失敗：' + e.message, false);
    }
  }

  /** 查詢某位讀者的點數帳本 */
  async function loadLedger() {
    const email = document.getElementById('ledger-email').value.trim();
    if (!email) { return; }
    try {
      const rows = await api('/api/admin/readers/ledger?email=' + encodeURIComponent(email));
      document.querySelector('#ledger-table tbody').innerHTML = rows.map(t => `<tr>
        <td>${t.createdAt ? esc(t.createdAt.slice(0, 16).replace('T', ' ')) : ''}</td>
        <td>${t.delta >= 0 ? '+' : ''}${t.delta}</td>
        <td>${esc(t.reason)}</td>
        <td>${esc(t.note || '')}</td>
      </tr>`).join('');
      showMsg('ledger-msg', `共 ${rows.length} 筆交易。`, true);
    } catch (e) {
      showMsg('ledger-msg', '查詢失敗：' + e.message, false);
    }
  }

  // ===== 參數設定 =====

  /** 各參數的中文標籤與說明 */
  const SETTING_LABELS = {
    'credit.signup_grant': ['初始贈點', '首次登入時發放；設 0 可關閉贈點'],
    'credit.premium_cost': ['進階文章單篇點數', '文章可個別覆寫；此為預設值，最小 1'],
    'credit.referral_reward': ['邀請成功獎勵', '被邀者點確認信後發放；設 0 可關閉'],
    'vip.default_days': ['VIP 預設效期（天）', '授予 VIP 時未指定天數就用這個值']
  };

  /** 載入參數並產生輸入欄位 */
  async function loadSettings() {
    try {
      const values = await api('/api/admin/settings');
      document.getElementById('settings-fields').innerHTML = Object.entries(values).map(([k, v]) => {
        const [label, hint] = SETTING_LABELS[k] || [k, ''];
        return `<div style="margin-bottom:14px">
          <label for="set-${esc(k)}">${esc(label)}</label>
          <input type="number" id="set-${esc(k)}" data-key="${esc(k)}" value="${v}">
          <p class="hint">${esc(hint)}</p>
        </div>`;
      }).join('');
    } catch (e) {
      showMsg('settings-msg', '載入失敗：' + e.message, false);
    }
  }

  /** 儲存參數（一次送出全部欄位） */
  async function saveSettings() {
    const payload = {};
    document.querySelectorAll('#settings-fields input[data-key]').forEach(input => {
      payload[input.dataset.key] = input.value;
    });
    try {
      await api('/api/admin/settings', { method: 'PUT', body: JSON.stringify(payload) });
      showMsg('settings-msg', '已儲存，立即生效。', true);
    } catch (e) {
      showMsg('settings-msg', '儲存失敗：' + e.message, false);
    }
  }

  document.getElementById('reader-search-btn').addEventListener('click', searchReaders);
  document.getElementById('vip-grant-btn').addEventListener('click', grantVip);
  document.getElementById('grant-btn').addEventListener('click', grantCredits);
  document.getElementById('ledger-btn').addEventListener('click', loadLedger);
  document.getElementById('settings-save-btn').addEventListener('click', saveSettings);
```

> **實作者注意**：`api()`、`esc()`、`showMsg()` 這三個輔助函式是否已存在於 `admin.html`？先確認名稱與簽章，**沿用既有的**。若既有的訊息顯示函式簽章不同（例如是 `showMsg(el, text, ok)` 而非 id 字串），請改寫上方呼叫以符合現況，不要新增第二套。分頁切換邏輯同理——找到既有的 `data-view` 切換程式碼並確認新分頁會被涵蓋，通常不需改動。

- [ ] **Step 3: 在「發送對象」卡片顯示保留額度**

把 `#quota-info` 的更新邏輯改為同時顯示行銷可用量與保留額度：

```javascript
      // 讓「為什麼只能寄這麼多」一眼可見，而不是等到發送失敗才知道
      quotaInfo.textContent = `剩餘額度 ${q.remaining} 封`
        + `（保留 ${q.reserve} 封給登入信等交易信，行銷可用 ${q.marketingRemaining} 封）`;
```

- [ ] **Step 4: 補上文章發布欄位（階段 B 遺留）**

spec §7 的「文章設定」在階段 B 只做了 API。在「⑤ 發送」卡片內，`mode` 那組 radio 之後加入：

```html
      <div class="row" style="margin-top:14px">
        <div><label for="art-slug">網址代稱 slug（留空則不發布到網頁）</label>
          <input type="text" id="art-slug" placeholder="my-first-post"></div>
        <div><label for="art-tier">分級</label>
          <select id="art-tier"><option value="BASIC">BASIC（訂閱者免費）</option><option value="PREMIUM">PREMIUM（需點數）</option></select></div>
        <div><label for="art-cost">解鎖點數（PREMIUM 用，留空用預設）</label>
          <input type="number" id="art-cost" min="1" placeholder="10"></div>
      </div>
      <p class="hint" id="tier-hint">設了 slug 才會出現在 /r/archive 與單篇頁面。</p>
```

並在送出 `send` 請求的地方帶上這三個欄位（沿用既有 body 組裝方式）。

> **重要**：階段 B 有一道「非 BASIC 拒絕寄送」的守門（因為信件端仍渲染整份 markdown，尚未實作折疊）。所以選 PREMIUM 送出會收到 400。**在 UI 上先擋住並說明**，而不是讓使用者撞到後端錯誤：

```javascript
  // 階段 D 才會實作「信件內只寄免費區」的折疊。在那之前，PREMIUM 文章
  // 若寄出會把受限區完整寄進信箱，paywall 形同虛設，故後端有守門。
  // 這裡先在 UI 說明，避免使用者選了 PREMIUM 才撞到 400。
  document.getElementById('art-tier').addEventListener('change', (e) => {
    const hint = document.getElementById('tier-hint');
    if (e.target.value === 'PREMIUM') {
      hint.textContent = 'PREMIUM 目前只能發布到網頁、不能寄送（信件折疊功能於階段 D 實作）。'
        + '請先以 BASIC 寄送，或設好 slug 後只發布不寄送。';
      hint.className = 'warn';
    } else {
      hint.textContent = '設了 slug 才會出現在 /r/archive 與單篇頁面。';
      hint.className = 'hint';
    }
  });
```

- [ ] **Step 5: 寫可重跑的驗證腳本**

依 CLAUDE.md 的強制規範，建立 `survey-backend/scripts/verify-admin-reader.mjs`（比照既有 `verify-admin.mjs` 的寫法與中文註解），驗證：

1. 搜尋讀者回 200 且結構正確。
2. 授予 VIP → 搜尋結果顯示 `vipActive: true`。
3. 取消 VIP → 顯示 `vipActive: false` 且 `vipExpiresAt` 為 null。
4. 批次加點 2 筆 → `granted: 2`，帳本查詢看得到兩筆 `ADMIN_GRANT`。
5. 加點缺 note → 回 400。
6. 參數設定：把 `credit.premium_cost` 改為 20 → 讀回為 20 → **開 `/r/rules` 斷言頁面出現 20** → 改回 10。

> 第 6 步是整個 §5.11「數字動態注入」要求的端到端驗收——單元測試用的是 mock，只有這裡能證明「後台改參數，讀者頁面真的跟著變」。

- [ ] **Step 6: 跑全套測試、執行腳本並 Commit**

```bash
cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B clean test
# 另一個終端啟動服務後執行：
node scripts/verify-admin-reader.mjs
cd .. && git add survey-backend/src/main/resources/static/admin.html \
        survey-backend/scripts/verify-admin-reader.mjs
git commit -m "feat(admin): 讀者管理與參數設定介面，補上文章發布欄位"
git diff HEAD --stat -- survey-backend/   # 必須無輸出
```

---

## Task 15: 端到端驗收與交接

**Files:**
- Create: `survey-backend/scripts/verify-stage-c.mjs`
- Modify: `docs/superpowers/specs/2026-07-25-reader-newsletter-platform-design.md`

- [ ] **Step 1: 寫完整流程的驗證腳本**

`survey-backend/scripts/verify-stage-c.mjs`，涵蓋一條真實讀者路徑（沿用 `verify-reader-flow.mjs` 的登入取 cookie 做法）：

1. 讀者 A 訂閱 → 首次登入 → 餘額為初始贈點、`credit_txn` 有 `SIGNUP_GRANT`。
2. 讀者 A 開 `/r/invite` → 取得邀請連結。
3. 讀者 B 用該連結訂閱（`/r/?ref=CODE`）→ 檢查 `survey_response.answers._ref` 已寫入。
4. **B 尚未點確認信時，A 的餘額不變**（這是防刷的核心）。
5. B 點確認信（用 admin 取得的 HMAC 連結或直接呼叫 confirm 端點）→ A 的餘額增加邀請獎勵，`/r/invite` 顯示 1 人。
6. **重複點同一確認連結 → A 的餘額不再增加**（冪等）。
7. 發布一篇 PREMIUM 文章 → B 開文章頁：不含受限區、有解鎖按鈕。
8. B 解鎖 → 餘額減少、頁面含受限區。
9. B 再次解鎖 → `ALREADY_UNLOCKED`，餘額不變。
10. 後台把 `credit.premium_cost` 改為 20 → `/r/rules` 顯示 20。
11. 後台授予 B VIP → B 開另一篇未解鎖的 PREMIUM 文章：直接看到全文、餘額不變。

Expected: 11 步全部通過。

> **第 4、6、9 步是本階段最重要的驗收**——它們驗的是「不該發生的事沒有發生」，而這類性質最容易在實作正確、測試卻只檢查正向路徑時被漏掉。

- [ ] **Step 2: 更新 spec 的階段狀態**

在 spec §11 的「階段 C」段落末尾補一行說明實際交付內容與偏離項（扣點改為需確認、事件相位、額度保留提前實作）。

- [ ] **Step 3: 全分支審查前的自我檢查**

```bash
cd survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B clean test
cd .. && git log --oneline main..HEAD
git diff main --stat | tail -3
```

確認：
- 測試全綠且 `Skipped: 0`
- 每個 commit 都可獨立編譯（若有搬移類的 commit，用 worktree 抽驗：`git worktree add --detach <tmp> <sha> && cd <tmp>/survey-backend && JAVA_HOME=/d/java/jdk-21 mvn -B -q test-compile`）
- **沒有任何新的 migration 檔**（`ls survey-backend/src/main/resources/db/migration/` 應仍只有 V1–V8）

---

## 完成標準

- [ ] `mvn clean test` 全綠且 `Skipped: 0`（用 `JAVA_HOME=/d/java/jdk-21`）
- [ ] `PackageDependencyTest` 三項全綠——特別是 `audience` 沒有任何 `reader` 的 import
- [ ] **沒有新增任何 Flyway migration**（仍為 V1–V8）
- [ ] **沒有新增任何 Maven 依賴**
- [ ] 讀者能以點數解鎖 PREMIUM 文章，且**必須按下按鈕才扣點**
- [ ] 同一篇文章不會被重複扣點（`article_access` UNIQUE 實測驗證過）
- [ ] 邀請獎勵只在被邀者點確認信後發放，且重複確認不重複發
- [ ] 自我邀請不發獎
- [ ] `/r/rules`、`/r/me`、gate 區塊三處的點數數字**全部來自 `CreditPolicy`**，改參數後三處同步變動（由 `verify-admin-reader.mjs` 第 6 步端到端驗證）
- [ ] 後台可搜尋讀者、授予／取消 VIP、批次加點、查帳本、改參數——**全部有 UI**
- [ ] 群發會保留交易信額度，可用量為 0 時拒絕發送而非靜默寄 0 封
- [ ] PARTIAL 回應**不含受限區任何字串**（每個 PARTIAL 案例都有此斷言）
- [ ] confirm / unsubscribe 的 URL 路徑**完全未變**（`/api/survey/confirm`、`/api/survey/unsubscribe`）
- [ ] 每個 commit 後都執行過 `git diff HEAD --stat` 且無輸出
- [ ] spec 已同步三處偏離：§5.2（扣點需確認）、§5.4（事件相位）、§6（額度保留已實作）

## 交接給階段 D 的已知事項

- **`CampaignService.send()` 仍以整份 markdown 渲染信件**，所以「非 BASIC 拒絕寄送」的守門仍然必要。階段 D 實作折疊（`vip_full_in_mail`）後才能解除。
- **`campaign` 同時是「發送批次」與「文章」**，所以「只發布到網頁不寄信」目前沒有獨立路徑——實務做法是設 slug 但不按發送。若這成為常態需求，階段 D 應考慮拆開。
- **`ReferralService.REF_KEY` 與 `SurveyController.REF_KEY` 是兩份同值常數**（刻意，避免 `reader → form` 依賴）。任一處改動都會靜默破壞邀請歸因——改動時必須同時改兩處。
- **`reader.referred_by` 只在首次登入時寫入。** 若讀者在被邀請前就已登入過，這個欄位會是 null，但獎勵仍會正常發放（獎勵看的是 `credit_txn` 的冪等鍵，不看這個欄位）。兩者職責不同，不要為了「一致」而合併。
