# 工商時間提案系統 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讀者付點申請工商提案 → 管理員審核 → 編輯器插入電子報 → 寄送/發布時對帳保存關聯並扣配額 → 安全轉址記點擊 → 後台統計 CTR。

**Architecture:** 新 `world.springai.survey.promo` 套件承載提案／版位／點擊三個實體與服務；讀者頁面控制器放 `reader` 套件（`ReaderNav`、`HtmlTemplate` 為 package-private）；`CampaignService` 只加三個接線點（對帳、token 替換、配額歸還）。關聯真相在 `promo_placement`，markdown 只是對帳核對對象。

**Tech Stack:** Spring Boot + JPA + Flyway（PostgreSQL）、JUnit 5 + Mockito（單元）、本機 5433 PG 容器（整合）、admin.html 純 vanilla JS。

**Spec:** `docs/superpowers/specs/2026-08-02-promo-proposal-system-design.md`（實作前先通讀）

## Global Constraints

- mvn 一律先設 `$env:JAVA_HOME='D:\java\jdk-21'`（PowerShell）；否則 shell 預設 JDK 8 會編譯失敗且錯誤訊息誤導。
- 所有程式碼需函式級中文註解；重要變數也要註解（專案規範）。
- `reader.credits` 永不 read-modify-write：加點一律 `ReaderRepository.addCredits`、扣點一律 `deductCredits`（條件式 UPDATE）。`credit_txn` 帳本只增不改不刪，「餘額恆等於帳本總和」是核心不變式。
- `/api/admin/**` 端點一律 `@RequestHeader(value = "X-Admin-Key", required = false) String key` + `adminKeyGuard.verify(key)`（見 `AdminCampaignController`）。
- 讀者端導覽列一律呼叫 `ReaderNav.links()`；新讀者模板的 `<nav>` 內只能有 `<!--NAV_LINKS-->` 佔位符（`ReaderNavGuardTest` 機械化守衛）。
- Flyway 新版號 V19（開工時先 `ls survey-backend/src/main/resources/db/migration` 確認 V19 未被佔用；被佔用就順延並全案改號）。
- 分支：`agent/promo-proposal-system`；每個 Task 結尾 commit。
- 佔位符常數字面值 `__PROMO_RT__` 全案唯一來源是 `PromoRecipientTokenService.PLACEHOLDER`。

---

### Task 1: V19 migration ＋ CreditTxn 擴充

**Files:**
- Create: `survey-backend/src/main/resources/db/migration/V19__promo_proposal_system.sql`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/CreditTxn.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/CreditTxnRepository.java`

**Interfaces:**
- Produces: 資料表 `promo_proposal`／`promo_placement`／`promo_click`；`CreditTxn.REASON_PROMO_APPLY`、`CreditTxn.REASON_PROMO_REFUND`、建構子 `CreditTxn(Long readerId, int delta, String reason, Long campaignId, String note, Long promoProposalId)`、`CreditTxnRepository.existsByPromoProposalIdAndReason(Long, String)`

- [ ] **Step 1: 撰寫 V19 migration**

```sql
-- V19__promo_proposal_system.sql
-- 工商時間提案系統：提案、版位（電子報×提案關聯）、點擊紀錄，及帳本擴充。
-- 設計依據 docs/superpowers/specs/2026-08-02-promo-proposal-system-design.md §3

-- 工商提案：讀者提交、管理員審核；unit_cost 是申請當下單價快照（退點以此計算）
CREATE TABLE promo_proposal (
    id              BIGSERIAL PRIMARY KEY,
    reader_id       BIGINT NOT NULL REFERENCES reader(id),
    contact_name    VARCHAR(100) NOT NULL,
    contact_email   VARCHAR(255) NOT NULL,
    title           VARCHAR(150) NOT NULL,
    body_text       TEXT NOT NULL,
    link_text       VARCHAR(100) NOT NULL,
    link_url        VARCHAR(1000) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_note     TEXT,
    reviewed_at     TIMESTAMPTZ,
    placement_quota INT NOT NULL,
    placement_used  INT NOT NULL DEFAULT 0,
    unit_cost       INT NOT NULL,
    pricing_type    VARCHAR(20) NOT NULL DEFAULT 'FREE',
    payment_status  VARCHAR(20),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_promo_quota CHECK (placement_quota BETWEEN 1 AND 3),
    CONSTRAINT ck_promo_used CHECK (placement_used >= 0 AND placement_used <= placement_quota),
    CONSTRAINT ck_promo_link_https CHECK (link_url LIKE 'https://%')
);
CREATE INDEX idx_promo_proposal_reader ON promo_proposal(reader_id);
CREATE INDEX idx_promo_proposal_status ON promo_proposal(status);

-- 版位：campaign_id 建立時為 NULL（編輯器插入時 Campaign 列尚不存在），對帳時綁定
CREATE TABLE promo_placement (
    id           BIGSERIAL PRIMARY KEY,
    campaign_id  BIGINT REFERENCES campaign(id),
    proposal_id  BIGINT NOT NULL REFERENCES promo_proposal(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    committed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 同一期同提案最多一個版位；未綁定的 DRAFT 不受限
CREATE UNIQUE INDEX uq_promo_placement_campaign_proposal
    ON promo_placement(campaign_id, proposal_id) WHERE campaign_id IS NOT NULL;
CREATE INDEX idx_promo_placement_proposal ON promo_placement(proposal_id);

-- 點擊紀錄：append-only，彙總於查詢時計算
CREATE TABLE promo_click (
    id            BIGSERIAL PRIMARY KEY,
    placement_id  BIGINT NOT NULL REFERENCES promo_placement(id),
    channel       VARCHAR(10) NOT NULL,
    identity_type VARCHAR(10) NOT NULL,
    identity_key  VARCHAR(255),
    clicked_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_promo_click_placement ON promo_click(placement_id);

-- 帳本擴充：退點冪等判斷需要「這筆退點屬於哪個提案」
ALTER TABLE credit_txn ADD COLUMN promo_proposal_id BIGINT REFERENCES promo_proposal(id);
```

- [ ] **Step 2: 擴充 CreditTxn 實體**

在 `CreditTxn.java` 加入（照既有欄位與註解風格）：

```java
    /** 工商提案申請扣點（負向） */
    public static final String REASON_PROMO_APPLY = "PROMO_APPLY";
    /** 工商提案退點：被拒全退、封存退未投放餘額（正向） */
    public static final String REASON_PROMO_REFUND = "PROMO_REFUND";

    /** reason=PROMO_APPLY／PROMO_REFUND 時對應的提案；退點冪等判斷依據 */
    @Column(name = "promo_proposal_id")
    private Long promoProposalId;
```

既有五參數建構子保留原樣；新增六參數建構子並讓舊的委派：

```java
    /** 建立一筆點數交易（工商提案路徑，帶提案 id） */
    public CreditTxn(Long readerId, int delta, String reason, Long campaignId,
                     String note, Long promoProposalId) {
        this(readerId, delta, reason, campaignId, note);
        this.promoProposalId = promoProposalId;
    }

    public Long getPromoProposalId() { return promoProposalId; }
```

- [ ] **Step 3: CreditTxnRepository 加冪等查詢**

```java
    /** 該提案是否已有指定原因的交易——退點冪等防線（REJECTED→ARCHIVED 不重複退） */
    boolean existsByPromoProposalIdAndReason(Long promoProposalId, String reason);
```

- [ ] **Step 4: 編譯＋既有測試不壞**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; cd survey-backend; ./mvnw -q compile test -Dtest=MigrationSafetyTest`
Expected: BUILD SUCCESS（MigrationSafetyTest 會掃 migration 檔基本安全）

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/resources/db/migration/V19__promo_proposal_system.sql survey-backend/src/main/java/world/springai/survey/reader/CreditTxn.java survey-backend/src/main/java/world/springai/survey/reader/CreditTxnRepository.java
git commit -m "feat(promo): V19 migration 與 credit_txn 工商提案擴充"
```

---

### Task 2: promo 套件三實體與 repository

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/promo/PromoProposal.java`
- Create: `survey-backend/src/main/java/world/springai/survey/promo/PromoProposalRepository.java`
- Create: `survey-backend/src/main/java/world/springai/survey/promo/PromoPlacement.java`
- Create: `survey-backend/src/main/java/world/springai/survey/promo/PromoPlacementRepository.java`
- Create: `survey-backend/src/main/java/world/springai/survey/promo/PromoClick.java`
- Create: `survey-backend/src/main/java/world/springai/survey/promo/PromoClickRepository.java`

**Interfaces:**
- Consumes: Task 1 的資料表
- Produces（後續 Task 依賴的精確簽名）:
  - `PromoProposal`：常數 `STATUS_PENDING/STATUS_APPROVED/STATUS_REJECTED/STATUS_ARCHIVED`＝`"PENDING"…`；getter/setter 對應 §3.1 全欄位（`getPlacementQuota()`、`getPlacementUsed()`、`getUnitCost()`…）；建構子 `PromoProposal(Long readerId, String contactName, String contactEmail, String title, String bodyText, String linkText, String linkUrl, int placementQuota, int unitCost)`（status 預設 PENDING、pricingType 預設 "FREE"）
  - `PromoProposalRepository`：`int countByReaderIdAndStatus(Long readerId, String status)`、`List<PromoProposal> findByReaderIdOrderByCreatedAtDesc(Long readerId)`、`List<PromoProposal> findByStatusOrderByCreatedAtDesc(String status)`、`List<PromoProposal> findSelectable()`、`int consumeQuota(Long id)`、`int releaseQuota(Long id)`
  - `PromoPlacement`：常數 `STATUS_DRAFT/STATUS_COMMITTED/STATUS_REMOVED`；建構子 `PromoPlacement(Long proposalId)`（campaignId null、status DRAFT）；`getCampaignId()/setCampaignId()`、`getStatus()/setStatus()`、`setCommittedAt(OffsetDateTime)`
  - `PromoPlacementRepository`：`List<PromoPlacement> findByCampaignIdAndStatus(Long campaignId, String status)`
  - `PromoClick`：建構子 `PromoClick(Long placementId, String channel, String identityType, String identityKey)`；常數 `CHANNEL_EMAIL/CHANNEL_WEB`、`IDENTITY_RECIPIENT/IDENTITY_READER/IDENTITY_ANON`
  - `PromoClickRepository`：投影介面與統計查詢（下方）

- [ ] **Step 1: 實作實體與 repository**

實體照 `CreditTxn.java` 的風格（`@Entity`、`GenerationType.IDENTITY`、`created_at` 用 `insertable = false, updatable = false` 交給 DB 預設值；`PromoProposal.updated_at` 同理，更新時由服務層 `setUpdatedAt` 不必做——本案不依賴該欄位排序，維持 DB default 即可）。

`PromoProposalRepository` 的兩個條件式 UPDATE（照 `ReaderRepository.deductCredits` 的寫法與理由，含 `flushAutomatically`/`clearAutomatically` 註解）：

```java
    /** 可進編輯器選單的提案：已核准且配額未滿 */
    @Query("select p from PromoProposal p where p.status = 'APPROVED' "
        + "and p.placementUsed < p.placementQuota order by p.createdAt desc")
    List<PromoProposal> findSelectable();

    /**
     * 條件式扣配額：只有未滿額才扣。回傳 0 表示配額已滿（或提案不存在），
     * 呼叫端必須據此擋下寄送——正確性來自受影響筆數，不是先前的檢查。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update PromoProposal p set p.placementUsed = p.placementUsed + 1 "
        + "where p.id = :id and p.placementUsed < p.placementQuota")
    int consumeQuota(@Param("id") Long id);

    /** 歸還一次配額（重排移除版位／取消排程時；下限 0 由 SQL 條件保證） */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update PromoProposal p set p.placementUsed = p.placementUsed - 1 "
        + "where p.id = :id and p.placementUsed > 0")
    int releaseQuota(@Param("id") Long id);
```

`PromoClickRepository` 統計投影（PostgreSQL `FILTER` 語法，正式與測試環境皆為 PG）：

```java
    /** 每版位×通道的總點擊與唯一點擊（ANON 不進唯一）投影 */
    interface ChannelStat {
        Long getPlacementId();
        String getChannel();
        long getTotal();
        long getUniq();
    }

    /** 彙總指定版位的點擊統計；唯一點擊 = 非匿名身分去重 */
    @Query(value = "SELECT placement_id AS placementId, channel, COUNT(*) AS total, "
        + "COUNT(DISTINCT identity_type || ':' || identity_key) "
        + "FILTER (WHERE identity_type <> 'ANON') AS uniq "
        + "FROM promo_click WHERE placement_id IN (:ids) "
        + "GROUP BY placement_id, channel", nativeQuery = true)
    List<ChannelStat> statsForPlacements(@Param("ids") List<Long> ids);
```

- [ ] **Step 2: 編譯**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; cd survey-backend; ./mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/promo/
git commit -m "feat(promo): 提案/版位/點擊實體與 repository"
```

---

### Task 3: CreditPolicy.promoPlacementCost ＋ AppSetting key

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/AppSettingService.java`（常數區，比照 `CREDIT_PREMIUM_COST`）
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/CreditPolicy.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/CreditPolicyPromoTest.java`

**Interfaces:**
- Produces: `AppSettingService.CREDIT_PROMO_PLACEMENT_COST = "credit.promo_placement_cost"`；`CreditPolicy.promoPlacementCost()`（後備 100、夾 ≥ 0）；`CreditPolicy.DEFAULT_PROMO_PLACEMENT_COST = 100`

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import world.springai.survey.AppSettingService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** promoPlacementCost：後備 100、可設 0（免費投放合法）、負值夾 0 */
class CreditPolicyPromoTest {

    private final AppSettingService settings = mock(AppSettingService.class);
    private final CreditPolicy policy = new CreditPolicy(settings);

    @Test
    void 未設定時採後備值100() {
        when(settings.getInt(eq(AppSettingService.CREDIT_PROMO_PLACEMENT_COST), anyInt()))
            .thenAnswer(inv -> inv.getArgument(1));
        assertEquals(100, policy.promoPlacementCost());
    }

    @Test
    void 設0為合法的免費投放() {
        when(settings.getInt(eq(AppSettingService.CREDIT_PROMO_PLACEMENT_COST), anyInt()))
            .thenReturn(0);
        assertEquals(0, policy.promoPlacementCost());
    }

    @Test
    void 負值夾到0() {
        when(settings.getInt(eq(AppSettingService.CREDIT_PROMO_PLACEMENT_COST), anyInt()))
            .thenReturn(-50);
        assertEquals(0, policy.promoPlacementCost());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**（`promoPlacementCost` 不存在，編譯失敗即為失敗態）

Run: `$env:JAVA_HOME='D:\java\jdk-21'; cd survey-backend; ./mvnw -q test -Dtest=CreditPolicyPromoTest`

- [ ] **Step 3: 實作**

`AppSettingService` 常數區加：

```java
    /** 工商提案每次投放的點數單價 */
    public static final String CREDIT_PROMO_PLACEMENT_COST = "credit.promo_placement_cost";
```

`CreditPolicy` 加（放在 `referralReward()` 附近，註解說明為何夾 ≥ 0 而非 ≥ 1——0 是合法「免費投放」營運設定，比照 `signupGrant()` 的理由）：

```java
    /** 工商提案投放單價的後備值 */
    static final int DEFAULT_PROMO_PLACEMENT_COST = 100;

    /** 工商提案每次投放單價；0 為合法值（免費投放），負值夾到 0 */
    public int promoPlacementCost() {
        return Math.max(0, appSettingService.getInt(
            AppSettingService.CREDIT_PROMO_PLACEMENT_COST, DEFAULT_PROMO_PLACEMENT_COST));
    }
```

- [ ] **Step 4: 跑測試確認通過**，並跑既有 `CreditPolicy` 相關測試不壞

- [ ] **Step 5: Commit**

```bash
git add -A survey-backend/src/main survey-backend/src/test
git commit -m "feat(promo): CreditPolicy 工商投放單價（AppSetting 可調、後備 100）"
```

---

### Task 4: PromoRecipientTokenService（自包含收件人 token）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/promo/PromoRecipientTokenService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/promo/PromoRecipientTokenServiceTest.java`

**Interfaces:**
- Produces: `PromoRecipientTokenService.PLACEHOLDER = "__PROMO_RT__"`；`String issue(String email)`（回 `base64url(email) + "." + sig`）；`Optional<String> verify(String token)`（成功回正規化 email）
- Consumes: `@Value("${app.unsubscribe-secret}")`（沿用退訂 secret；簽名內容加 `"promo|"` 前綴做 domain separation）

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey.promo;

import org.junit.jupiter.api.Test;
import world.springai.survey.audience.UnsubscribeTokenService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** 自包含 token：簽發→驗證往返、竄改拒絕、與退訂 token 不可互換 */
class PromoRecipientTokenServiceTest {

    private final PromoRecipientTokenService service = new PromoRecipientTokenService("test-secret");

    @Test
    void 簽發後可驗回正規化email() {
        String token = service.issue("  Alice@Example.COM ");
        assertEquals(Optional.of("alice@example.com"), service.verify(token));
    }

    @Test
    void 竄改任一段即驗證失敗() {
        String token = service.issue("alice@example.com");
        String[] parts = token.split("\\.", 2);
        assertTrue(service.verify("x" + token).isEmpty());
        assertTrue(service.verify(parts[0] + ".AAAA").isEmpty());
        assertTrue(service.verify(parts[0]).isEmpty()); // 缺簽章段
    }

    @Test
    void null與空字串與佔位符一律失敗不拋例外() {
        assertTrue(service.verify(null).isEmpty());
        assertTrue(service.verify("").isEmpty());
        assertTrue(service.verify(PromoRecipientTokenService.PLACEHOLDER).isEmpty());
    }

    @Test
    void 與退訂token不可互換_domainSeparation() {
        // 同一把 secret 下，把退訂簽章拼進 promo token 必須驗不過
        UnsubscribeTokenService unsub = new UnsubscribeTokenService("test-secret");
        String email = "alice@example.com";
        String b64 = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(email.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(service.verify(b64 + "." + unsub.sign(email)).isEmpty());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; cd survey-backend; ./mvnw -q test -Dtest=PromoRecipientTokenServiceTest`

- [ ] **Step 3: 實作**（HMAC 細節照 `UnsubscribeTokenService`：HmacSHA256、Base64 URL-safe 無 padding、`MessageDigest.isEqual` 常數時間比對）

```java
package world.springai.survey.promo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/**
 * 工商轉址連結的收件人 token：{@code base64url(email) + "." + HMAC("promo|" + email)}。
 *
 * <p><b>為什麼要自包含 email</b>：HMAC 單向不可逆，轉址端點只拿得到 token，
 * 必須能從 token 本身取回 email 才能重算簽章驗證（spec §5）。</p>
 *
 * <p><b>domain separation</b>：沿用 {@code app.unsubscribe-secret} 同一把 secret，
 * 但簽名內容加 {@code "promo|"} 前綴——promo token 與退訂 token 不可互換冒用。</p>
 */
@Component
public class PromoRecipientTokenService {

    /** 寄送時每收件人替換的佔位符；全案唯一來源 */
    public static final String PLACEHOLDER = "__PROMO_RT__";

    /** HMAC 秘鑰（與退訂共用，簽名前綴不同） */
    private final String secret;

    /** 注入秘鑰（測試可直接以建構子傳入） */
    public PromoRecipientTokenService(@Value("${app.unsubscribe-secret}") String secret) {
        this.secret = secret;
    }

    /** 簽發：email 正規化（trim＋小寫）後編碼並簽章 */
    public String issue(String email) {
        String normalized = normalize(email);
        String b64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
        return b64 + "." + hmac(normalized);
    }

    /** 驗證：格式不符、解碼失敗、簽章不符一律回 empty，不拋例外 */
    public Optional<String> verify(String token) {
        if (!StringUtils.hasText(token)) return Optional.empty();
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) return Optional.empty();
        String email;
        try {
            email = new String(Base64.getUrlDecoder().decode(token.substring(0, dot)),
                StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String normalized = normalize(email);
        boolean ok = MessageDigest.isEqual(
            hmac(normalized).getBytes(StandardCharsets.UTF_8),
            token.substring(dot + 1).getBytes(StandardCharsets.UTF_8));
        return ok ? Optional.of(normalized) : Optional.empty();
    }

    /** HMAC-SHA256（含 promo| 前綴），Base64 URL-safe 無 padding */
    private String hmac(String normalizedEmail) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(("promo|" + normalizedEmail).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("無法簽發 promo token", e);
        }
    }

    /** email 正規化：與 UnsubscribeTokenService 同基準（trim＋小寫） */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

- [ ] **Step 5: Commit**

```bash
git add -A survey-backend/src
git commit -m "feat(promo): 自包含收件人 token（HMAC domain separation）"
```

---

### Task 5: PromoProposalService.apply（申請＋扣點）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/promo/PromoProposalService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/promo/PromoProposalServiceApplyTest.java`

**Interfaces:**
- Consumes: `ReaderRepository.deductCredits(Long, int)`／`findById`、`CreditTxnRepository.save`、`PromoProposalRepository`、`CreditPolicy.promoPlacementCost()`
- Produces:
  - `record ApplyRequest(String contactName, String contactEmail, String title, String bodyText, String linkText, String linkUrl, int placements)`
  - `record ApplyResult(long proposalId, int totalCost, int credits)`（credits＝扣款後權威餘額）
  - `ApplyResult apply(Long readerId, ApplyRequest req)`（`@Transactional`）
  - 例外：`PromoValidationException extends IllegalArgumentException`（驗證不過→400）、`InsufficientCreditsException extends IllegalStateException`（餘額不足→409）

- [ ] **Step 1: 寫失敗測試**（Mockito 風格同 `CampaignServiceTest`）

```java
package world.springai.survey.promo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import world.springai.survey.reader.CreditPolicy;
import world.springai.survey.reader.CreditTxn;
import world.springai.survey.reader.CreditTxnRepository;
import world.springai.survey.reader.Reader;
import world.springai.survey.reader.ReaderRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 申請＋扣點：驗證、餘額防線、扣款先於落單、帳本寫入 */
class PromoProposalServiceApplyTest {

    private final PromoProposalRepository proposalRepository = mock(PromoProposalRepository.class);
    private final ReaderRepository readerRepository = mock(ReaderRepository.class);
    private final CreditTxnRepository creditTxnRepository = mock(CreditTxnRepository.class);
    private final CreditPolicy creditPolicy = mock(CreditPolicy.class);
    private PromoProposalService service;

    /** 合法申請的基準樣本，各測試再局部改壞 */
    private PromoProposalService.ApplyRequest valid;

    @BeforeEach
    void setUp() {
        service = new PromoProposalService(
            proposalRepository, readerRepository, creditTxnRepository, creditPolicy);
        valid = new PromoProposalService.ApplyRequest(
            "王小明", "ming@example.com", "好課推薦", "這是一段純文字文案",
            "立即報名", "https://example.com/course", 2);
        when(creditPolicy.promoPlacementCost()).thenReturn(100);
        when(proposalRepository.countByReaderIdAndStatus(1L, PromoProposal.STATUS_PENDING))
            .thenReturn(0);
        Reader reader = mock(Reader.class);
        when(reader.getCredits()).thenReturn(500);
        when(readerRepository.findById(1L)).thenReturn(Optional.of(reader));
        when(readerRepository.deductCredits(1L, 200)).thenReturn(1);
        when(proposalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 成功申請_扣款先於落單且寫入帳本() {
        service.apply(1L, valid);
        InOrder inOrder = inOrder(readerRepository, proposalRepository, creditTxnRepository);
        inOrder.verify(readerRepository).deductCredits(1L, 200); // 100×2
        inOrder.verify(proposalRepository).save(any(PromoProposal.class));
        inOrder.verify(creditTxnRepository).save(argThat(txn ->
            txn.getDelta() == -200 && CreditTxn.REASON_PROMO_APPLY.equals(txn.getReason())));
    }

    @Test
    void 餘額不足擋下申請() {
        when(readerRepository.deductCredits(1L, 200)).thenReturn(0);
        assertThrows(PromoProposalService.InsufficientCreditsException.class,
            () -> service.apply(1L, valid));
        verify(proposalRepository, never()).save(any());
    }

    @Test
    void 非https網址拒絕() {
        var bad = new PromoProposalService.ApplyRequest(valid.contactName(), valid.contactEmail(),
            valid.title(), valid.bodyText(), valid.linkText(), "http://example.com", 1);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.apply(1L, bad));
    }

    @Test
    void 含小於號拒絕_禁HTML() {
        var bad = new PromoProposalService.ApplyRequest(valid.contactName(), valid.contactEmail(),
            valid.title(), "hello <b>bold</b>", valid.linkText(), valid.linkUrl(), 1);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.apply(1L, bad));
    }

    @Test
    void 含佔位符字面拒絕() {
        var bad = new PromoProposalService.ApplyRequest(valid.contactName(), valid.contactEmail(),
            valid.title(), "嵌入 __PROMO_RT__ 攻擊", valid.linkText(), valid.linkUrl(), 1);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.apply(1L, bad));
    }

    @Test
    void 投放次數超出1到3拒絕() {
        var bad = new PromoProposalService.ApplyRequest(valid.contactName(), valid.contactEmail(),
            valid.title(), valid.bodyText(), valid.linkText(), valid.linkUrl(), 4);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.apply(1L, bad));
    }

    @Test
    void 待審上限3件擋下() {
        when(proposalRepository.countByReaderIdAndStatus(1L, PromoProposal.STATUS_PENDING))
            .thenReturn(3);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.apply(1L, valid));
    }

    @Test
    void 單價0時免扣點也不寫帳本負項() {
        when(creditPolicy.promoPlacementCost()).thenReturn(0);
        service.apply(1L, valid);
        verify(readerRepository, never()).deductCredits(anyLong(), anyInt());
        verify(creditTxnRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

- [ ] **Step 3: 實作 apply**

```java
package world.springai.survey.promo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import world.springai.survey.reader.CreditPolicy;
import world.springai.survey.reader.CreditTxn;
import world.springai.survey.reader.CreditTxnRepository;
import world.springai.survey.reader.Reader;
import world.springai.survey.reader.ReaderRepository;

/**
 * 工商提案：申請（扣點）與審核（狀態機＋退點）。
 *
 * <p>扣點交易設計完全比照 {@code UnlockService}：條件式扣款
 * （{@code WHERE credits >= :cost}）是併發防線；扣款、落單、寫帳本
 * 同一交易，任一失敗整組回滾，維持「餘額恆等於帳本總和」不變式。</p>
 */
@Service
public class PromoProposalService {

    /** 待審中提案的每人上限（防濫用） */
    static final int MAX_PENDING_PER_READER = 3;
    /** 投放次數上下限（spec §2） */
    static final int MIN_PLACEMENTS = 1;
    static final int MAX_PLACEMENTS = 3;

    /** 驗證不過（欄位格式／上限），controller 轉 400 */
    public static class PromoValidationException extends IllegalArgumentException {
        public PromoValidationException(String message) { super(message); }
    }

    /** 餘額不足，controller 轉 409 */
    public static class InsufficientCreditsException extends IllegalStateException {
        public InsufficientCreditsException(String message) { super(message); }
    }

    /** 申請請求；placements 為投放次數（1–3） */
    public record ApplyRequest(String contactName, String contactEmail, String title,
                               String bodyText, String linkText, String linkUrl,
                               int placements) {}

    /** 申請結果；credits 為扣款後重新讀取的權威餘額 */
    public record ApplyResult(long proposalId, int totalCost, int credits) {}

    private final PromoProposalRepository proposalRepository;
    private final ReaderRepository readerRepository;
    private final CreditTxnRepository creditTxnRepository;
    private final CreditPolicy creditPolicy;

    /** 注入提案、讀者、帳本與點數參數 */
    public PromoProposalService(PromoProposalRepository proposalRepository,
                                ReaderRepository readerRepository,
                                CreditTxnRepository creditTxnRepository,
                                CreditPolicy creditPolicy) {
        this.proposalRepository = proposalRepository;
        this.readerRepository = readerRepository;
        this.creditTxnRepository = creditTxnRepository;
        this.creditPolicy = creditPolicy;
    }

    /** 送出申請：驗證 → 扣點（單價×次數）→ 落單 → 寫帳本，同一交易 */
    @Transactional
    public ApplyResult apply(Long readerId, ApplyRequest req) {
        validate(readerId, req);

        int unitCost = creditPolicy.promoPlacementCost();
        int totalCost = unitCost * req.placements();

        Reader reader = readerRepository.findById(readerId)
            .orElseThrow(() -> new PromoValidationException("讀者不存在：id=" + readerId));

        if (totalCost > 0) {
            // 條件式扣款是併發防線：回 0 列代表餘額不足（或期間被其他交易扣走）
            if (reader.getCredits() < totalCost
                || readerRepository.deductCredits(readerId, totalCost) == 0) {
                throw new InsufficientCreditsException(
                    "點數不足：需要 " + totalCost + " 點，目前 " + reader.getCredits() + " 點");
            }
        }

        PromoProposal proposal = proposalRepository.save(new PromoProposal(
            readerId, req.contactName().trim(), req.contactEmail().trim(),
            req.title().trim(), req.bodyText().trim(), req.linkText().trim(),
            req.linkUrl().trim(), req.placements(), unitCost));

        if (totalCost > 0) {
            creditTxnRepository.save(new CreditTxn(readerId, -totalCost,
                CreditTxn.REASON_PROMO_APPLY, null, proposal.getTitle(), proposal.getId()));
        }

        // 扣款後重新讀取權威餘額（理由同 UnlockService：不用記憶體算術）
        int remaining = readerRepository.findById(readerId)
            .map(Reader::getCredits)
            .orElseThrow(() -> new IllegalStateException("扣款後讀不到讀者：id=" + readerId));
        return new ApplyResult(proposal.getId(), totalCost, remaining);
    }

    /** 欄位與上限驗證；訊息面向讀者、可直接顯示 */
    private void validate(Long readerId, ApplyRequest req) {
        requireLen(req.contactName(), 100, "聯絡人");
        requireLen(req.contactEmail(), 255, "Email");
        requireLen(req.title(), 150, "提案名稱");
        requireLen(req.bodyText(), 2000, "文案");
        requireLen(req.linkText(), 100, "連結文字");
        requireLen(req.linkUrl(), 1000, "網址");
        if (!req.contactEmail().contains("@")) {
            throw new PromoValidationException("Email 格式不正確");
        }
        if (!req.linkUrl().trim().startsWith("https://")) {
            throw new PromoValidationException("網址僅接受 https:// 開頭");
        }
        // 禁 HTML／Script：任何欄位含 '<' 一律拒絕；同時拒絕佔位符字面，
        // 否則寄送時每收件人替換機制會把收件人 token 誤代入文案（spec §7.2）
        for (String field : new String[]{req.title(), req.bodyText(), req.linkText()}) {
            if (field.contains("<")) {
                throw new PromoValidationException("內容不可包含 HTML（偵測到 < 字元）");
            }
            if (field.contains(PromoRecipientTokenService.PLACEHOLDER)) {
                throw new PromoValidationException("內容含保留字 __PROMO_RT__，請移除");
            }
        }
        if (req.placements() < MIN_PLACEMENTS || req.placements() > MAX_PLACEMENTS) {
            throw new PromoValidationException("投放次數僅接受 1–3 次");
        }
        if (proposalRepository.countByReaderIdAndStatus(readerId, PromoProposal.STATUS_PENDING)
            >= MAX_PENDING_PER_READER) {
            throw new PromoValidationException("同時最多 " + MAX_PENDING_PER_READER + " 件待審提案");
        }
    }

    /** 必填＋長度上限檢查 */
    private void requireLen(String value, int max, String label) {
        if (!StringUtils.hasText(value)) {
            throw new PromoValidationException(label + " 為必填");
        }
        if (value.trim().length() > max) {
            throw new PromoValidationException(label + " 超過長度上限 " + max);
        }
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

- [ ] **Step 5: Commit**

```bash
git add -A survey-backend/src
git commit -m "feat(promo): 提案申請與扣點（條件式扣款＋帳本）"
```

---

### Task 6: 審核狀態機＋退點

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/promo/PromoProposalService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/promo/PromoProposalServiceReviewTest.java`

**Interfaces:**
- Produces: `PromoProposal approve(Long id)`、`PromoProposal reject(Long id, String note)`、`PromoProposal archive(Long id)`（皆 `@Transactional`；非法轉移拋 `PromoValidationException`）
- Consumes: `ReaderRepository.addCredits`、`CreditTxnRepository.existsByPromoProposalIdAndReason`

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey.promo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import world.springai.survey.reader.CreditPolicy;
import world.springai.survey.reader.CreditTxn;
import world.springai.survey.reader.CreditTxnRepository;
import world.springai.survey.reader.ReaderRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 狀態機轉移矩陣與退點金額／冪等 */
class PromoProposalServiceReviewTest {

    private final PromoProposalRepository proposalRepository = mock(PromoProposalRepository.class);
    private final ReaderRepository readerRepository = mock(ReaderRepository.class);
    private final CreditTxnRepository creditTxnRepository = mock(CreditTxnRepository.class);
    private PromoProposalService service;

    @BeforeEach
    void setUp() {
        service = new PromoProposalService(
            proposalRepository, readerRepository, creditTxnRepository, mock(CreditPolicy.class));
        when(readerRepository.addCredits(anyLong(), anyInt())).thenReturn(1);
        when(creditTxnRepository.existsByPromoProposalIdAndReason(anyLong(), anyString()))
            .thenReturn(false);
    }

    /** 建一筆指定狀態／配額的提案 mock（id=7, reader=1, quota=3, used 由參數給） */
    private PromoProposal proposal(String status, int used) {
        PromoProposal p = new PromoProposal(1L, "王小明", "ming@example.com", "好課",
            "文案", "報名", "https://example.com", 3, 100);
        p.setStatus(status);
        p.setPlacementUsed(used);
        // id 由 JPA 產生，測試用反射或 setter；實作時給 PromoProposal 一個測試可用的 setId 或改用 spy
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", 7L);
        when(proposalRepository.findById(7L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void 待審可核准() {
        proposal(PromoProposal.STATUS_PENDING, 0);
        assertEquals(PromoProposal.STATUS_APPROVED, service.approve(7L).getStatus());
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    @Test
    void 拒絕時全額退點並寫帳本() {
        proposal(PromoProposal.STATUS_PENDING, 0);
        service.reject(7L, "文案不符規範");
        verify(readerRepository).addCredits(1L, 300); // 3×100 全退
        verify(creditTxnRepository).save(argThat(txn ->
            txn.getDelta() == 300 && CreditTxn.REASON_PROMO_REFUND.equals(txn.getReason())));
    }

    @Test
    void 核准後封存退未用餘額() {
        proposal(PromoProposal.STATUS_APPROVED, 2);
        service.archive(7L);
        verify(readerRepository).addCredits(1L, 100); // (3-2)×100
    }

    @Test
    void 已拒絕再封存不重複退點() {
        proposal(PromoProposal.STATUS_REJECTED, 0);
        when(creditTxnRepository.existsByPromoProposalIdAndReason(
            7L, CreditTxn.REASON_PROMO_REFUND)).thenReturn(true);
        service.archive(7L);
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    @Test
    void 配額用罄時封存不產生退點交易() {
        proposal(PromoProposal.STATUS_APPROVED, 3);
        service.archive(7L);
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
        verify(creditTxnRepository, never()).save(any());
    }

    @Test
    void 非法轉移一律拒絕() {
        proposal(PromoProposal.STATUS_APPROVED, 0);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.approve(7L));   // APPROVED 不能再核准
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.reject(7L, "x")); // APPROVED 不能改拒絕
        proposal(PromoProposal.STATUS_ARCHIVED, 0);
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.archive(7L));   // 終態不能再封存
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

- [ ] **Step 3: 實作審核方法**（加進 `PromoProposalService`）

```java
    /** 核准：僅 PENDING 可核准 */
    @Transactional
    public PromoProposal approve(Long id) {
        PromoProposal p = requireStatus(id, PromoProposal.STATUS_PENDING, "核准");
        p.setStatus(PromoProposal.STATUS_APPROVED);
        p.setReviewedAt(java.time.OffsetDateTime.now());
        return proposalRepository.save(p);
    }

    /** 拒絕：僅 PENDING 可拒絕；全額退點（此時必未投放） */
    @Transactional
    public PromoProposal reject(Long id, String note) {
        PromoProposal p = requireStatus(id, PromoProposal.STATUS_PENDING, "拒絕");
        p.setStatus(PromoProposal.STATUS_REJECTED);
        p.setReviewNote(note);
        p.setReviewedAt(java.time.OffsetDateTime.now());
        refundRemaining(p);
        return proposalRepository.save(p);
    }

    /** 封存：APPROVED／REJECTED 皆可；退未投放餘額（冪等，已退過不重複） */
    @Transactional
    public PromoProposal archive(Long id) {
        PromoProposal p = proposalRepository.findById(id)
            .orElseThrow(() -> new PromoValidationException("提案不存在：id=" + id));
        if (!PromoProposal.STATUS_APPROVED.equals(p.getStatus())
            && !PromoProposal.STATUS_REJECTED.equals(p.getStatus())) {
            throw new PromoValidationException("狀態 " + p.getStatus() + " 不可封存");
        }
        p.setStatus(PromoProposal.STATUS_ARCHIVED);
        refundRemaining(p);
        return proposalRepository.save(p);
    }

    /**
     * 退還未投放餘額：(quota − used) × unit_cost。
     * 冪等防線：同一提案只會有一筆 PROMO_REFUND——REJECTED 時已退過的，
     * 之後 ARCHIVED 不重複退。
     */
    private void refundRemaining(PromoProposal p) {
        int amount = (p.getPlacementQuota() - p.getPlacementUsed()) * p.getUnitCost();
        if (amount <= 0) return;
        if (creditTxnRepository.existsByPromoProposalIdAndReason(
                p.getId(), CreditTxn.REASON_PROMO_REFUND)) {
            return;
        }
        readerRepository.addCredits(p.getReaderId(), amount);
        creditTxnRepository.save(new CreditTxn(p.getReaderId(), amount,
            CreditTxn.REASON_PROMO_REFUND, null, p.getTitle(), p.getId()));
    }

    /** 取出提案並要求目前狀態；不符拋驗證例外 */
    private PromoProposal requireStatus(Long id, String expected, String action) {
        PromoProposal p = proposalRepository.findById(id)
            .orElseThrow(() -> new PromoValidationException("提案不存在：id=" + id));
        if (!expected.equals(p.getStatus())) {
            throw new PromoValidationException(
                "狀態 " + p.getStatus() + " 不可" + action + "（僅 " + expected + " 可）");
        }
        return p;
    }
```

- [ ] **Step 4: 跑測試確認通過**（Task 5＋6 測試一起跑）

- [ ] **Step 5: Commit**

```bash
git add -A survey-backend/src
git commit -m "feat(promo): 審核狀態機與退點（冪等）"
```

---

### Task 7: 版位建立與 snippet（Markdown escape）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/promo/PromoPlacementService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/promo/PromoPlacementServiceSnippetTest.java`

**Interfaces:**
- Produces:
  - `record Snippet(long placementId, String markdown)`
  - `Snippet createPlacement(Long proposalId)`（提案須 APPROVED 且配額未滿，否則 `PromoProposalService.PromoValidationException`）
  - `static String escapeMarkdown(String text)`（跳脫 `\` `` ` `` `*` `_` `[` `]` `(` `)` `#` `!` `>` `|`）
  - `static java.util.List<Long> parsePlacementIds(String markdown)`（掃 `/promo/c/{id}` 出現的 id，去重保序）

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey.promo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** snippet 生成、Markdown escape、URL 解析 */
class PromoPlacementServiceSnippetTest {

    private final PromoPlacementRepository placementRepository = mock(PromoPlacementRepository.class);
    private final PromoProposalRepository proposalRepository = mock(PromoProposalRepository.class);
    private final PromoPlacementService service =
        new PromoPlacementService(placementRepository, proposalRepository);

    @Test
    void escape跳脫markdown特殊字元() {
        assertEquals("\\*粗體\\*與\\[連結\\]\\(x\\)",
            PromoPlacementService.escapeMarkdown("*粗體*與[連結](x)"));
    }

    @Test
    void 產生成對promo區塊snippet() {
        PromoProposal p = new PromoProposal(1L, "王", "a@b.c", "好課",
            "限時 *5* 折", "馬上看", "https://example.com", 2, 100);
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", 9L);
        p.setStatus(PromoProposal.STATUS_APPROVED);
        when(proposalRepository.findById(9L)).thenReturn(Optional.of(p));
        when(placementRepository.save(any())).thenAnswer(inv -> {
            PromoPlacement pl = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(pl, "id", 55L);
            return pl;
        });

        PromoPlacementService.Snippet s = service.createPlacement(9L);

        assertEquals(55L, s.placementId());
        String md = s.markdown();
        assertTrue(md.startsWith("<!--promo-->\n"));
        assertTrue(md.endsWith("<!--/promo-->\n"));
        assertTrue(md.contains("限時 \\*5\\* 折"));           // 文案已 escape
        assertTrue(md.contains("[馬上看](/promo/c/55?rt=__PROMO_RT__)"));
    }

    @Test
    void 非核准提案不可建版位() {
        PromoProposal p = new PromoProposal(1L, "王", "a@b.c", "好課",
            "文案", "看", "https://example.com", 2, 100);
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", 9L);
        when(proposalRepository.findById(9L)).thenReturn(Optional.of(p)); // 仍 PENDING
        assertThrows(PromoProposalService.PromoValidationException.class,
            () -> service.createPlacement(9L));
    }

    @Test
    void 解析markdown中的placementId_去重保序() {
        String md = "x [a](/promo/c/3?rt=__PROMO_RT__) y [b](/promo/c/12?rt=__PROMO_RT__)"
            + " 重複 [c](/promo/c/3?rt=__PROMO_RT__)";
        assertEquals(List.of(3L, 12L), PromoPlacementService.parsePlacementIds(md));
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

- [ ] **Step 3: 實作**

```java
package world.springai.survey.promo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 版位生命週期：建立（編輯器插入）、對帳（寄送／發布定案）、歸還（重排／取消）。
 * 關聯真相在 promo_placement；markdown 只是對帳的核對對象（spec §6）。
 */
@Service
public class PromoPlacementService {

    /** 轉址連結的確定性格式；對帳解析與 snippet 生成共用同一來源 */
    private static final Pattern PLACEMENT_URL = Pattern.compile("/promo/c/(\\d+)");

    private final PromoPlacementRepository placementRepository;
    private final PromoProposalRepository proposalRepository;

    /** 注入版位與提案 repository */
    public PromoPlacementService(PromoPlacementRepository placementRepository,
                                 PromoProposalRepository proposalRepository) {
        this.placementRepository = placementRepository;
        this.proposalRepository = proposalRepository;
    }

    /** 編輯器插入結果：版位 id 與可直接貼進內文的成對 promo 區塊 */
    public record Snippet(long placementId, String markdown) {}

    /** 建立 DRAFT 版位並生成文案快照 snippet；提案須 APPROVED 且配額未滿 */
    @Transactional
    public Snippet createPlacement(Long proposalId) {
        PromoProposal p = proposalRepository.findById(proposalId)
            .orElseThrow(() -> new PromoProposalService.PromoValidationException(
                "提案不存在：id=" + proposalId));
        if (!PromoProposal.STATUS_APPROVED.equals(p.getStatus())) {
            throw new PromoProposalService.PromoValidationException(
                "僅已核准提案可插入，目前狀態：" + p.getStatus());
        }
        if (p.getPlacementUsed() >= p.getPlacementQuota()) {
            throw new PromoProposalService.PromoValidationException(
                "提案投放次數已用罄（" + p.getPlacementUsed() + "/" + p.getPlacementQuota() + "）");
        }
        PromoPlacement placement = placementRepository.save(new PromoPlacement(p.getId()));
        // 文案快照：escape 後落地，審核內容即凍結；連結由欄位生成、不信任文案內語法
        String md = "<!--promo-->\n"
            + escapeMarkdown(p.getBodyText()) + "\n\n"
            + "[" + escapeMarkdown(p.getLinkText()) + "](/promo/c/" + placement.getId()
            + "?rt=" + PromoRecipientTokenService.PLACEHOLDER + ")\n"
            + "<!--/promo-->\n";
        return new Snippet(placement.getId(), md);
    }

    /** 跳脫 Markdown 特殊字元：提案文字是純文字，不得讓語法意外生效 */
    static String escapeMarkdown(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if ("\\`*_[]()#!>|".indexOf(c) >= 0) sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }

    /** 掃描內文中出現的版位 id（自己生成的確定性 URL），去重保序 */
    static List<Long> parsePlacementIds(String markdown) {
        Set<Long> ids = new LinkedHashSet<>();
        Matcher m = PLACEMENT_URL.matcher(markdown == null ? "" : markdown);
        while (m.find()) {
            ids.add(Long.parseLong(m.group(1)));
        }
        return new ArrayList<>(ids);
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

- [ ] **Step 5: Commit**

```bash
git add -A survey-backend/src
git commit -m "feat(promo): 版位建立與文案快照 snippet（Markdown escape）"
```

---

### Task 8: 對帳（reconcile）／預檢／配額歸還

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/promo/PromoPlacementService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/promo/PromoPlacementServiceReconcileTest.java`

**Interfaces:**
- Produces:
  - `void assertCommittable(String markdown)`——寄送前預檢（不寫入）：版位存在、未綁其他 campaign、提案配額足夠；不過拋 `IllegalStateException`（訊息含提案名稱，後台可直接顯示）
  - `void reconcile(Long campaignId, String markdown)`（`@Transactional`）——內文出現的 DRAFT → 綁 campaign＋COMMITTED＋`consumeQuota`（回 0 擋下）；已 COMMITTED 且綁同 campaign → 略過（冪等）；綁其他 campaign → 拋例外；本 campaign 已 COMMITTED 但內文消失 → REMOVED＋`releaseQuota`
  - `void releaseForCampaign(Long campaignId)`（`@Transactional`）——取消排程用：該 campaign 全部 COMMITTED → REMOVED＋`releaseQuota`

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey.promo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 對帳：COMMIT／擋下／REMOVED／冪等／配額歸還 */
class PromoPlacementServiceReconcileTest {

    private final PromoPlacementRepository placementRepository = mock(PromoPlacementRepository.class);
    private final PromoProposalRepository proposalRepository = mock(PromoProposalRepository.class);
    private PromoPlacementService service;

    @BeforeEach
    void setUp() {
        service = new PromoPlacementService(placementRepository, proposalRepository);
        when(placementRepository.findByCampaignIdAndStatus(anyLong(), anyString()))
            .thenReturn(List.of());
        when(placementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(proposalRepository.consumeQuota(anyLong())).thenReturn(1);
    }

    /** 建一個版位 mock：id、proposalId、目前狀態與綁定 campaign */
    private PromoPlacement placement(long id, long proposalId, String status, Long campaignId) {
        PromoPlacement pl = new PromoPlacement(proposalId);
        org.springframework.test.util.ReflectionTestUtils.setField(pl, "id", id);
        pl.setStatus(status);
        pl.setCampaignId(campaignId);
        when(placementRepository.findById(id)).thenReturn(Optional.of(pl));
        return pl;
    }

    @Test
    void 內文出現的DRAFT被綁定並COMMIT扣配額() {
        PromoPlacement pl = placement(55L, 9L, PromoPlacement.STATUS_DRAFT, null);
        service.reconcile(100L, "[看](/promo/c/55?rt=__PROMO_RT__)");
        assertEquals(PromoPlacement.STATUS_COMMITTED, pl.getStatus());
        assertEquals(100L, pl.getCampaignId());
        verify(proposalRepository).consumeQuota(9L);
    }

    @Test
    void 配額不足擋下並回滾() {
        placement(55L, 9L, PromoPlacement.STATUS_DRAFT, null);
        when(proposalRepository.consumeQuota(9L)).thenReturn(0);
        assertThrows(IllegalStateException.class,
            () -> service.reconcile(100L, "[看](/promo/c/55?rt=__PROMO_RT__)"));
    }

    @Test
    void 已綁其他campaign擋下_markdown複製誤用() {
        placement(55L, 9L, PromoPlacement.STATUS_COMMITTED, 999L);
        assertThrows(IllegalStateException.class,
            () -> service.reconcile(100L, "[看](/promo/c/55?rt=__PROMO_RT__)"));
    }

    @Test
    void 同campaign重複對帳冪等不重複扣() {
        placement(55L, 9L, PromoPlacement.STATUS_COMMITTED, 100L);
        service.reconcile(100L, "[看](/promo/c/55?rt=__PROMO_RT__)");
        verify(proposalRepository, never()).consumeQuota(anyLong());
    }

    @Test
    void 重排時消失的版位轉REMOVED並歸還配額() {
        PromoPlacement gone = placement(66L, 9L, PromoPlacement.STATUS_COMMITTED, 100L);
        when(placementRepository.findByCampaignIdAndStatus(100L, PromoPlacement.STATUS_COMMITTED))
            .thenReturn(List.of(gone));
        service.reconcile(100L, "內文已無任何工商連結");
        assertEquals(PromoPlacement.STATUS_REMOVED, gone.getStatus());
        verify(proposalRepository).releaseQuota(9L);
    }

    @Test
    void 取消排程全數歸還() {
        PromoPlacement pl = placement(55L, 9L, PromoPlacement.STATUS_COMMITTED, 100L);
        when(placementRepository.findByCampaignIdAndStatus(100L, PromoPlacement.STATUS_COMMITTED))
            .thenReturn(List.of(pl));
        service.releaseForCampaign(100L);
        assertEquals(PromoPlacement.STATUS_REMOVED, pl.getStatus());
        verify(proposalRepository).releaseQuota(9L);
    }

    @Test
    void 預檢不寫入任何狀態() {
        placement(55L, 9L, PromoPlacement.STATUS_DRAFT, null);
        PromoProposal p = new PromoProposal(1L, "王", "a@b.c", "好課",
            "文", "看", "https://example.com", 2, 100);
        p.setStatus(PromoProposal.STATUS_APPROVED);
        when(proposalRepository.findById(9L)).thenReturn(Optional.of(p));
        service.assertCommittable("[看](/promo/c/55?rt=__PROMO_RT__)");
        verify(placementRepository, never()).save(any());
        verify(proposalRepository, never()).consumeQuota(anyLong());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

- [ ] **Step 3: 實作三個方法**（加進 `PromoPlacementService`；`PromoPlacement` 實體需 `proposalId` getter）

```java
    /**
     * 寄送前預檢（不寫入）：讓「擋下」發生在 Campaign 列建立與任何寄信副作用之前。
     * CampaignService 刻意無交易（ZSend 副作用無法回滾），所以順序是
     * 預檢 → 建 Campaign → reconcile → 寄送；預檢失敗時什麼都還沒發生。
     */
    public void assertCommittable(String markdown) {
        for (Long id : parsePlacementIds(markdown)) {
            PromoPlacement pl = placementRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                    "工商版位不存在（id=" + id + "），請重新插入提案"));
            if (pl.getCampaignId() != null
                && PromoPlacement.STATUS_COMMITTED.equals(pl.getStatus())) {
                continue; // 同 campaign 重寄／重排的冪等情境，reconcile 再驗歸屬
            }
            PromoProposal p = proposalRepository.findById(pl.getProposalId())
                .orElseThrow(() -> new IllegalStateException(
                    "工商提案不存在（placement=" + id + "）"));
            if (p.getPlacementUsed() >= p.getPlacementQuota()) {
                throw new IllegalStateException(
                    "提案「" + p.getTitle() + "」投放次數已用罄，請移除該工商區塊");
            }
        }
    }

    /** 對帳定案：內文出現的 DRAFT → COMMIT＋扣配額；本期已 COMMIT 但消失 → REMOVED＋歸還 */
    @Transactional
    public void reconcile(Long campaignId, String markdown) {
        List<Long> present = parsePlacementIds(markdown);

        for (Long id : present) {
            PromoPlacement pl = placementRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                    "工商版位不存在（id=" + id + "），請重新插入提案"));
            if (PromoPlacement.STATUS_COMMITTED.equals(pl.getStatus())) {
                if (!campaignId.equals(pl.getCampaignId())) {
                    throw new IllegalStateException("工商版位 " + id
                        + " 已刊於其他電子報，請刪除該區塊並重新插入提案");
                }
                continue; // 冪等：同期重寄不重複扣
            }
            if (pl.getCampaignId() != null && !campaignId.equals(pl.getCampaignId())) {
                throw new IllegalStateException("工商版位 " + id
                    + " 屬於其他電子報，請刪除該區塊並重新插入提案");
            }
            // 條件式扣配額是唯一防線：回 0 即擋下，交易回滾已 COMMIT 的同批版位
            if (proposalRepository.consumeQuota(pl.getProposalId()) == 0) {
                PromoProposal p = proposalRepository.findById(pl.getProposalId()).orElse(null);
                throw new IllegalStateException("提案「"
                    + (p == null ? pl.getProposalId() : p.getTitle())
                    + "」投放次數已用罄，寄送已取消");
            }
            pl.setCampaignId(campaignId);
            pl.setStatus(PromoPlacement.STATUS_COMMITTED);
            pl.setCommittedAt(java.time.OffsetDateTime.now());
            placementRepository.save(pl);
        }

        // 重排情境：先前已綁本期、但新內文已無該連結 → 視為未刊登，歸還配額
        for (PromoPlacement pl : placementRepository.findByCampaignIdAndStatus(
                campaignId, PromoPlacement.STATUS_COMMITTED)) {
            if (!present.contains(pl.getId())) {
                pl.setStatus(PromoPlacement.STATUS_REMOVED);
                placementRepository.save(pl);
                proposalRepository.releaseQuota(pl.getProposalId());
            }
        }
    }

    /** 取消排程：該期全部 COMMITTED 版位歸還配額（信件未寄出，視為未刊登） */
    @Transactional
    public void releaseForCampaign(Long campaignId) {
        for (PromoPlacement pl : placementRepository.findByCampaignIdAndStatus(
                campaignId, PromoPlacement.STATUS_COMMITTED)) {
            pl.setStatus(PromoPlacement.STATUS_REMOVED);
            placementRepository.save(pl);
            proposalRepository.releaseQuota(pl.getProposalId());
        }
    }
```

- [ ] **Step 4: 跑測試確認通過**

- [ ] **Step 5: Commit**

```bash
git add -A survey-backend/src
git commit -m "feat(promo): 對帳 COMMIT/REMOVED 與配額 enforce/歸還"
```

---

### Task 9: CampaignService 接線（send／publish／reschedule／cancel／token 替換）

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/newsletter/CampaignService.java`
- Test: 既有 `CampaignServiceTest` 加案例（同檔 mock 風格）

**Interfaces:**
- Consumes: `PromoPlacementService.assertCommittable/reconcile/releaseForCampaign`、`PromoRecipientTokenService.issue/PLACEHOLDER`
- Produces: 無新公開介面；行為變更如下

接線點（實作前先重讀 `CampaignService` 對應區段確認行號漂移）：

1. 建構子注入 `PromoPlacementService promoPlacementService` 與 `PromoRecipientTokenService promoTokenService`（連同測試建構子一併補參數）。
2. `send(...)`（正式群發入口，約 L247 的主多載）：在建立 `Campaign` 實體**之前**呼叫 `promoPlacementService.assertCommittable(markdown)`；在 `campaignRepository.save(campaign)` 取得 id **之後、任何寄信提交之前**呼叫 `promoPlacementService.reconcile(campaignId, markdown)`。
3. `publish(...)`（約 L388，網頁發布）：campaign 儲存後同樣 `reconcile(campaignId, markdown)`。
4. reschedule 路徑（約 L690 重排渲染處）：重排完成前 `reconcile(campaignId, markdown)`（重排是重新對帳的唯一入口，消失的版位在此歸還）。
5. 取消排程路徑（`AdminCampaignController` 的 `DELETE /api/admin/campaigns/{id}/schedule` 對應的 service 方法）：取消成功後 `promoPlacementService.releaseForCampaign(campaignId)`。
6. `renderFor(...)`（L966）：第一行加 token 替換——

```java
        // 工商轉址連結的收件人 token：每收件人一枚，佔位符來源唯一（Task 4）
        bodyHtml = bodyHtml.replace(PromoRecipientTokenService.PLACEHOLDER,
            promoTokenService.issue(email));
```

（測試信路徑 L162 同樣經 `renderFor`，token 有效但版位仍 DRAFT，不會產生統計——spec §5 的「測試信不入統計」由此免費達成，不需要任何旗標。）

- [ ] **Step 1: 在 `CampaignServiceTest` 加失敗測試**（沿用該檔既有 mock 建構方式；下列斷言為必要案例）

```java
    @Test
    void 群發前先預檢並於儲存後對帳() {
        // arrange：照該檔既有 send 測試的樣板準備 recipients/mailSender mock
        service.send("主旨", "[看](/promo/c/55?rt=__PROMO_RT__)", null, null, ...);
        InOrder inOrder = inOrder(promoPlacementService, campaignRepository, mailSender);
        inOrder.verify(promoPlacementService).assertCommittable(contains("/promo/c/55"));
        inOrder.verify(campaignRepository).save(any());
        inOrder.verify(promoPlacementService).reconcile(anyLong(), contains("/promo/c/55"));
    }

    @Test
    void 對帳失敗時不寄出任何信() {
        doThrow(new IllegalStateException("投放次數已用罄"))
            .when(promoPlacementService).assertCommittable(anyString());
        assertThrows(IllegalStateException.class, () -> service.send(...));
        verify(mailSender, never()).send(any(), anyString(), anyString(), anyString());
    }

    @Test
    void renderFor替換佔位符為收件人token() {
        when(promoTokenService.issue("alice@example.com")).thenReturn("B64.SIG");
        // 經 preview/test 路徑觸發 renderFor 後斷言輸出含 rt=B64.SIG 且不含 __PROMO_RT__
    }
```

- [ ] **Step 2: 跑測試確認失敗**（既有 `CampaignServiceTest` 建構子參數不足會先編譯失敗——先補注入再跑）

- [ ] **Step 3: 依接線點 1–6 實作**（每處加中文註解說明「為何在這個位置」：預檢在副作用前、對帳在 id 誕生後寄信前）

- [ ] **Step 4: 跑 `CampaignServiceTest` 全數通過**（既有 45+ 案例不得變紅）

- [ ] **Step 5: Commit**

```bash
git add -A survey-backend/src
git commit -m "feat(promo): CampaignService 對帳接線與收件人 token 替換"
```

---

### Task 10: 轉址端點（PromoClickService ＋ PromoClickController）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/promo/PromoClickService.java`
- Create: `survey-backend/src/main/java/world/springai/survey/promo/PromoClickController.java`
- Test: `survey-backend/src/test/java/world/springai/survey/promo/PromoClickServiceTest.java`

**Interfaces:**
- Consumes: `PromoRecipientTokenService.verify`、`world.springai.survey.reader.ReaderSessionService.readReaderId(String, OffsetDateTime)`（回 `Optional<Long>`；先讀該類確認實際簽名）、repositories
- Produces:
  - `PromoClickService`：`Optional<String> resolveAndRecord(long placementId, String rt, String sessionCookie)`——empty＝404；有值＝302 目的地
  - `PromoClickController`：`GET /promo/c/{placementId}`，`@CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false)`

- [ ] **Step 1: 寫失敗測試**

```java
package world.springai.survey.promo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import world.springai.survey.reader.ReaderSessionService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 轉址與歸戶：RECIPIENT > READER > ANON；只有 COMMITTED 記點擊 */
class PromoClickServiceTest {

    private final PromoPlacementRepository placementRepository = mock(PromoPlacementRepository.class);
    private final PromoProposalRepository proposalRepository = mock(PromoProposalRepository.class);
    private final PromoClickRepository clickRepository = mock(PromoClickRepository.class);
    private final PromoRecipientTokenService tokenService = mock(PromoRecipientTokenService.class);
    private final ReaderSessionService sessionService = mock(ReaderSessionService.class);
    private PromoClickService service;

    @BeforeEach
    void setUp() {
        service = new PromoClickService(placementRepository, proposalRepository,
            clickRepository, tokenService, sessionService);
        when(tokenService.verify(any())).thenReturn(Optional.empty());
        when(sessionService.readReaderId(any(), any())).thenReturn(Optional.empty());
    }

    /** 準備版位＋提案：id=55、proposal=9、目的地 https://example.com */
    private void placement(String status) {
        PromoPlacement pl = new PromoPlacement(9L);
        org.springframework.test.util.ReflectionTestUtils.setField(pl, "id", 55L);
        pl.setStatus(status);
        when(placementRepository.findById(55L)).thenReturn(Optional.of(pl));
        PromoProposal p = new PromoProposal(1L, "王", "a@b.c", "好課",
            "文", "看", "https://example.com", 2, 100);
        when(proposalRepository.findById(9L)).thenReturn(Optional.of(p));
    }

    @Test
    void 有效token記EMAIL_RECIPIENT並轉址() {
        placement(PromoPlacement.STATUS_COMMITTED);
        when(tokenService.verify("tok")).thenReturn(Optional.of("alice@example.com"));
        assertEquals(Optional.of("https://example.com"),
            service.resolveAndRecord(55L, "tok", null));
        verify(clickRepository).save(argThat(c ->
            PromoClick.CHANNEL_EMAIL.equals(c.getChannel())
            && PromoClick.IDENTITY_RECIPIENT.equals(c.getIdentityType())
            && "alice@example.com".equals(c.getIdentityKey())));
    }

    @Test
    void 無token有session記WEB_READER() {
        placement(PromoPlacement.STATUS_COMMITTED);
        when(sessionService.readReaderId(eq("cookie"), any())).thenReturn(Optional.of(42L));
        service.resolveAndRecord(55L, null, "cookie");
        verify(clickRepository).save(argThat(c ->
            PromoClick.CHANNEL_WEB.equals(c.getChannel())
            && PromoClick.IDENTITY_READER.equals(c.getIdentityType())
            && "42".equals(c.getIdentityKey())));
    }

    @Test
    void 皆無記WEB_ANON_identityKey為null() {
        placement(PromoPlacement.STATUS_COMMITTED);
        service.resolveAndRecord(55L, null, null);
        verify(clickRepository).save(argThat(c ->
            PromoClick.IDENTITY_ANON.equals(c.getIdentityType()) && c.getIdentityKey() == null));
    }

    @Test
    void DRAFT版位照樣轉址但不記錄() {
        placement(PromoPlacement.STATUS_DRAFT);
        assertEquals(Optional.of("https://example.com"),
            service.resolveAndRecord(55L, null, null));
        verify(clickRepository, never()).save(any());
    }

    @Test
    void 版位不存在回empty() {
        when(placementRepository.findById(55L)).thenReturn(Optional.empty());
        assertTrue(service.resolveAndRecord(55L, null, null).isEmpty());
    }

    @Test
    void 記錄失敗不影響轉址() {
        placement(PromoPlacement.STATUS_COMMITTED);
        when(clickRepository.save(any())).thenThrow(new RuntimeException("db down"));
        assertEquals(Optional.of("https://example.com"),
            service.resolveAndRecord(55L, null, null)); // 讀者體驗優先，統計 best-effort
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

- [ ] **Step 3: 實作 service 與 controller**

```java
package world.springai.survey.promo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import world.springai.survey.reader.ReaderSessionService;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 工商連結點擊：解析目的地並 best-effort 記錄歸戶。
 *
 * <p>歸戶順序 RECIPIENT（token）→ READER（session）→ ANON；
 * 只有 COMMITTED 版位記錄——DRAFT 涵蓋測試信與預覽，天然不入統計（spec §5）。</p>
 *
 * <p><b>記錄失敗不擋轉址</b>：點擊統計是輔助數據，讀者到得了目的地是主體驗；
 * 寫入失敗記 log 讓監控看到即可。</p>
 */
@Service
public class PromoClickService {

    private static final Logger log = LoggerFactory.getLogger(PromoClickService.class);

    private final PromoPlacementRepository placementRepository;
    private final PromoProposalRepository proposalRepository;
    private final PromoClickRepository clickRepository;
    private final PromoRecipientTokenService tokenService;
    private final ReaderSessionService sessionService;

    /** 注入版位、提案、點擊、token 與 session 服務 */
    public PromoClickService(PromoPlacementRepository placementRepository,
                             PromoProposalRepository proposalRepository,
                             PromoClickRepository clickRepository,
                             PromoRecipientTokenService tokenService,
                             ReaderSessionService sessionService) {
        this.placementRepository = placementRepository;
        this.proposalRepository = proposalRepository;
        this.clickRepository = clickRepository;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
    }

    /** 查目的地並記錄點擊；empty＝版位或提案不存在（404） */
    public Optional<String> resolveAndRecord(long placementId, String rt, String sessionCookie) {
        Optional<PromoPlacement> placement = placementRepository.findById(placementId);
        if (placement.isEmpty()) return Optional.empty();
        Optional<PromoProposal> proposal = proposalRepository.findById(placement.get().getProposalId());
        if (proposal.isEmpty()) return Optional.empty();

        if (PromoPlacement.STATUS_COMMITTED.equals(placement.get().getStatus())) {
            try {
                clickRepository.save(buildClick(placementId, rt, sessionCookie));
            } catch (RuntimeException e) {
                log.warn("promo 點擊記錄失敗 placement={}，轉址照常", placementId, e);
            }
        }
        return Optional.of(proposal.get().getLinkUrl());
    }

    /** 依歸戶順序組出點擊列 */
    private PromoClick buildClick(long placementId, String rt, String sessionCookie) {
        Optional<String> email = tokenService.verify(rt);
        if (email.isPresent()) {
            return new PromoClick(placementId, PromoClick.CHANNEL_EMAIL,
                PromoClick.IDENTITY_RECIPIENT, email.get());
        }
        Optional<Long> readerId = sessionService.readReaderId(sessionCookie, OffsetDateTime.now());
        if (readerId.isPresent()) {
            return new PromoClick(placementId, PromoClick.CHANNEL_WEB,
                PromoClick.IDENTITY_READER, String.valueOf(readerId.get()));
        }
        return new PromoClick(placementId, PromoClick.CHANNEL_WEB,
            PromoClick.IDENTITY_ANON, null);
    }
}
```

```java
package world.springai.survey.promo;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.springai.survey.reader.ReaderSessionService;

/**
 * 工商連結安全轉址：目的地一律由 DB 依 placementId 查出，
 * 網址不進參數——無 open redirect 攻擊面（spec §5）。
 */
@RestController
public class PromoClickController {

    private final PromoClickService clickService;

    /** 注入點擊服務 */
    public PromoClickController(PromoClickService clickService) {
        this.clickService = clickService;
    }

    /** 302 轉址；版位不存在回 404 */
    @GetMapping("/promo/c/{placementId}")
    public ResponseEntity<Void> click(
            @PathVariable long placementId,
            @RequestParam(value = "rt", required = false) String rt,
            @CookieValue(value = ReaderSessionService.COOKIE_NAME, required = false) String sessionCookie) {
        return clickService.resolveAndRecord(placementId, rt, sessionCookie)
            .map(url -> ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url).<Void>build())
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

- [ ] **Step 5: Commit**

```bash
git add -A survey-backend/src
git commit -m "feat(promo): 安全轉址端點與點擊歸戶"
```

---

### Task 11: 統計服務＋Admin API

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/promo/PromoStatsService.java`
- Create: `survey-backend/src/main/java/world/springai/survey/promo/AdminPromoController.java`
- Test: `survey-backend/src/test/java/world/springai/survey/promo/PromoStatsServiceTest.java`

**Interfaces:**
- Consumes: `PromoClickRepository.statsForPlacements`、`CampaignRepository.findById`（取 `accepted_count`——先讀 `Campaign.java` 確認 getter 名稱，V12 之後應為 `getAcceptedCount()`）、`AdminKeyGuard.verify`
- Produces:
  - `record PlacementStats(long placementId, Long campaignId, String campaignSubject, long accepted, long emailTotal, long emailUnique, long webTotal, long webUnique, Double emailCtr)`（`emailCtr = accepted > 0 ? emailUnique/accepted : null`）
  - `record ProposalStats(long proposalId, String title, String status, int placementQuota, int placementUsed, List<PlacementStats> placements)`
  - `PromoStatsService.overview()` → `List<ProposalStats>`（全部提案，各自彙總版位明細）
  - Admin 端點（全部 `X-Admin-Key`）：
    - `GET  /api/admin/promo/proposals?status=`（省略 status＝全部）
    - `POST /api/admin/promo/proposals/{id}/approve`
    - `POST /api/admin/promo/proposals/{id}/reject`（body `{"note": "..."}`）
    - `POST /api/admin/promo/proposals/{id}/archive`
    - `GET  /api/admin/promo/selectable`（編輯器選單：APPROVED 且配額未滿）
    - `POST /api/admin/promo/placements`（body `{"proposalId": N}`→ 回 `{placementId, markdown}`）
    - `GET  /api/admin/promo/stats`（overview）

- [ ] **Step 1: 寫失敗測試**（統計組裝邏輯；repository 以 mock 餵 `ChannelStat`）

```java
package world.springai.survey.promo;

import org.junit.jupiter.api.Test;
import world.springai.survey.newsletter.Campaign;
import world.springai.survey.newsletter.CampaignRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 統計組裝：通道分列、CTR 分母為 accepted_count、無發送時 CTR 為 null */
class PromoStatsServiceTest {

    /** 手工 ChannelStat 樁 */
    private record Stat(Long placementId, String channel, long total, long uniq)
        implements PromoClickRepository.ChannelStat {
        public Long getPlacementId() { return placementId; }
        public String getChannel() { return channel; }
        public long getTotal() { return total; }
        public long getUniq() { return uniq; }
    }

    @Test
    void 通道分列且CTR以accepted為分母() {
        var proposalRepository = mock(PromoProposalRepository.class);
        var placementRepository = mock(PromoPlacementRepository.class);
        var clickRepository = mock(PromoClickRepository.class);
        var campaignRepository = mock(CampaignRepository.class);
        var service = new PromoStatsService(proposalRepository, placementRepository,
            clickRepository, campaignRepository);

        PromoProposal p = new PromoProposal(1L, "王", "a@b.c", "好課",
            "文", "看", "https://example.com", 3, 100);
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", 9L);
        when(proposalRepository.findAll()).thenReturn(List.of(p));

        PromoPlacement pl = new PromoPlacement(9L);
        org.springframework.test.util.ReflectionTestUtils.setField(pl, "id", 55L);
        pl.setCampaignId(100L);
        pl.setStatus(PromoPlacement.STATUS_COMMITTED);
        when(placementRepository.findByProposalId(9L)).thenReturn(List.of(pl));

        when(clickRepository.statsForPlacements(List.of(55L))).thenReturn(List.of(
            new Stat(55L, "EMAIL", 30, 20),
            new Stat(55L, "WEB", 10, 4)));

        Campaign campaign = mock(Campaign.class);
        when(campaign.getSubject()).thenReturn("第 12 期");
        when(campaign.getAcceptedCount()).thenReturn(200);
        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));

        var stats = service.overview();
        var row = stats.get(0).placements().get(0);
        assertEquals(20, row.emailUnique());
        assertEquals(10, row.webTotal());
        assertEquals(0.10, row.emailCtr(), 1e-9); // 20/200
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

- [ ] **Step 3: 實作 `PromoStatsService`**（依測試組裝；`emailCtr` 在 `accepted == 0` 時回 `null`，COMMITTED 以外版位不列）與 `AdminPromoController`（每個端點：`adminKeyGuard.verify(key)` → 呼叫對應 service → 回 DTO；`PromoValidationException` 以 `@ExceptionHandler` 或 try/catch 轉 `ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage())`——先看 `AdminCampaignController` 既有錯誤處理慣例並照做）

- [ ] **Step 4: 跑測試確認通過**

- [ ] **Step 5: Commit**

```bash
git add -A survey-backend/src
git commit -m "feat(promo): 後台審核/插入/統計 API"
```

---

### Task 12: 讀者端 /r/promo 頁＋ReaderNav 入口

**Files:**
- Create: `survey-backend/src/main/resources/templates/reader/promo.html`
- Create: `survey-backend/src/main/java/world/springai/survey/reader/PromoPortalController.java`（必須在 `reader` 套件——`ReaderNav`、`HtmlTemplate` 為 package-private）
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/ReaderNav.java`
- Modify: `survey-backend/src/test/java/world/springai/survey/reader/ReaderNavGuardTest.java`（守衛清單加 `/r/promo`）
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReaderNavTest.java`（若已存在則擴充）

**Interfaces:**
- Consumes: `ReaderContext.resolve`、`PromoProposalService.apply`、`PromoProposalRepository.findByReaderIdOrderByCreatedAtDesc`、`CreditPolicy.promoPlacementCost()`、訂閱數（照 `CampaignService` 的來源 `RecipientService.subscriberCount()`；若該 bean 不在 reader 套件可見範圍，先確認其套件與可見性再注入）
- Produces: `GET /r/promo`（登入才可見，未登入導登入頁）、`POST /r/promo/apply`（JSON）

- [ ] **Step 1: ReaderNav 失敗測試**——登入分支包含工商合作、未登入不含：

```java
    @Test
    void 登入導覽含工商合作_未登入不含() {
        assertTrue(ReaderNav.links(true).contains("<a href=\"/r/promo\">工商合作</a>"));
        assertFalse(ReaderNav.links(false).contains("/r/promo"));
    }
```

- [ ] **Step 2: 修改 `ReaderNav`**——加常數與登入分支（javadoc 補一段「工商合作僅登入可見」的理由；順序放在「我的帳戶」之後、「遊戲規則」之前）：

```java
    /** 工商合作：申請與我的提案；僅登入可見（申請需綁定 reader 並扣點） */
    private static final String PROMO = "<a href=\"/r/promo\">工商合作</a>";

    static String links(boolean loggedIn) {
        if (loggedIn) {
            return HOME + ARCHIVE + ME + PROMO + RULES;
        }
        return HOME + ARCHIVE + LOGIN + RULES;
    }
```

同步把 `ReaderNavGuardTest` 的逐字禁止清單加上 `<a href="/r/promo"`（保護新連結不被 inline）。

- [ ] **Step 3: 建 `promo.html` 模板**——結構抄 `me.html` 的骨架（先讀該檔確認佔位符命名慣例與 `<nav>` 寫法），`<nav>` 內只放 `<!--NAV_LINKS-->`。內容區塊：

```html
<!-- 申請表單：單價與試算由後端算好帶入，前端不重算（顯示與扣點同源原則） -->
<section class="card">
  <h2>工商時間申請</h2>
  <p>目前訂閱規模：<!--SUBSCRIBER_COUNT--> 人；每次投放 <!--UNIT_COST--> 點，投放次數 1–3 次。</p>
  <form id="promo-form">
    <label>聯絡人 <input name="contactName" maxlength="100" required></label>
    <label>Email <input name="contactEmail" type="email" maxlength="255" required></label>
    <label>提案名稱 <input name="title" maxlength="150" required></label>
    <label>純文字文案 <textarea name="bodyText" maxlength="2000" required></textarea></label>
    <label>連結文字 <input name="linkText" maxlength="100" required></label>
    <label>連結網址（https） <input name="linkUrl" type="url" pattern="https://.*" maxlength="1000" required></label>
    <label>投放次數
      <select name="placements"><option>1</option><option>2</option><option>3</option></select>
    </label>
    <p>總費用：<span id="promo-cost">—</span> 點（目前餘額 <!--CREDITS--> 點）</p>
    <button type="submit">送出申請</button>
    <p id="promo-msg" role="status"></p>
  </form>
</section>
<!-- 我的提案列表：後端 server-render 成 <tr>，佔位符整段替換 -->
<section class="card">
  <h2>我的提案</h2>
  <table><thead><tr><th>名稱</th><th>狀態</th><th>投放</th><th>送出時間</th></tr></thead>
  <tbody><!--PROPOSAL_ROWS--></tbody></table>
</section>
<script>
// 試算：次數 × 單價；單價由後端帶入，避免前後端各說一套
(function () {
  var unit = parseInt('<!--UNIT_COST-->', 10) || 0;
  var form = document.getElementById('promo-form');
  var sel = form.querySelector('[name=placements]');
  function calc() { document.getElementById('promo-cost').textContent = unit * parseInt(sel.value, 10); }
  sel.addEventListener('change', calc); calc();
  form.addEventListener('submit', async function (e) {
    e.preventDefault();
    var msg = document.getElementById('promo-msg');
    var data = Object.fromEntries(new FormData(form).entries());
    data.placements = parseInt(data.placements, 10);
    try {
      var res = await fetch('/r/promo/apply', { method: 'POST',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
      var body = await res.json();
      if (!res.ok) { msg.textContent = body.message || '申請失敗'; return; }
      location.reload(); // 成功後重載，列表與餘額都以伺服器為準
    } catch (err) { msg.textContent = '連線失敗，請稍後再試'; }
  });
})();
</script>
```

（注意：server 端把 `PROPOSAL_ROWS` 的每個欄位值先過 `HtmlTemplate.escapeHtml`——提案名稱是使用者可控字串，`HtmlTemplate.render` 不做跳脫。）

- [ ] **Step 4: 實作 `PromoPortalController`**——`GET /r/promo` 照 `ReaderPortalController.me()` 的骨架（session 解析、`redirectToLogin("/r/promo")`、`htmlTemplate` 渲染，先讀該檔確認 render 呼叫的實際簽名）；`POST /r/promo/apply` 未登入回 401 JSON，登入呼叫 `promoProposalService.apply(reader.getId(), request)`，`PromoValidationException` → 400 `{"message": ...}`、`InsufficientCreditsException` → 409 `{"message": ...}`、成功回 `{"proposalId":…,"totalCost":…,"credits":…}`。狀態顯示中文對照：PENDING=待審核、APPROVED=已核准、REJECTED=已拒絕（顯示 `review_note`）、ARCHIVED=已封存。

- [ ] **Step 5: 跑 reader 套件測試**

Run: `$env:JAVA_HOME='D:\java\jdk-21'; cd survey-backend; ./mvnw -q test -Dtest='ReaderNav*'`
Expected: PASS（guard test 含新禁止字串仍全綠）

- [ ] **Step 6: Commit**

```bash
git add -A survey-backend/src
git commit -m "feat(promo): 讀者端工商合作頁與導覽入口"
```

---

### Task 13: admin.html（審核分頁＋編輯器插入＋統計）＋全案驗證

**Files:**
- Modify: `survey-backend/src/main/resources/static/admin.html`

**Interfaces:**
- Consumes: Task 11 的全部 admin 端點；既有 `api(path, opts)` helper（L612，自動帶 `X-Admin-Key`）；編輯器 textarea `#markdown`（L206）

- [ ] **Step 1: 加「工商提案」分頁**——照既有分頁的 nav 按鈕＋section 標記慣例（先看檔內任一分頁如「名單中心」的 markup 再複製結構）。內容三塊：

1. 提案清單（狀態下拉篩選＋表格：名稱／聯絡人／狀態／投放 已用/總數／操作按鈕）
2. 統計表格（提案 × 版位明細：期別／發送數／信件點擊 總/唯一／信件 CTR／網頁點擊 唯一/總）＋固定註腳「唯一點擊以提案版位×收件人去重；郵件安全掃描器可能造成少量誤差」
3. 操作 JS（全部走 `api()`，DOM 寫入一律 `textContent`，不用 innerHTML——admin.html 有既有 XSS 慣例）：

```javascript
  // 工商提案：清單載入與審核操作
  async function loadPromoProposals(){
    const status=$('#promo-status-filter').value;
    const rows=await api('/api/admin/promo/proposals'+(status?'?status='+status:''));
    const body=$('#promo-list tbody');body.textContent='';
    rows.forEach(p=>{
      const tr=document.createElement('tr');
      tr.append(cell(p.title),cell(p.contactName),cell(p.status),
        cell(p.placementUsed+'/'+p.placementQuota));
      const td=document.createElement('td');
      if(p.status==='PENDING'){
        td.append(btn('核准',()=>reviewPromo(p.id,'approve')),
                  btn('拒絕',()=>{const note=prompt('拒絕理由');if(note!=null)reviewPromo(p.id,'reject',note);}));
      }else if(p.status==='APPROVED'||p.status==='REJECTED'){
        td.append(btn('封存',()=>reviewPromo(p.id,'archive')));
      }
      tr.append(td);body.append(tr);
    });
  }
  async function reviewPromo(id,action,note){
    try{await api('/api/admin/promo/proposals/'+id+'/'+action,
      {method:'POST',body:JSON.stringify(note?{note}:{})});loadPromoProposals();}
    catch(e){if(e.message!=='401')alert('操作失敗：'+e.message);}
  }
```

（`cell`／`btn` 若檔內已有同名 helper 直接沿用；沒有 `btn` 就地補一個三行 helper。）

- [ ] **Step 2: 編輯器「插入工商提案」**——在 `#markdown` 編輯器工具區加一顆按鈕與下拉：

```javascript
  // 編輯器插入：取可選提案 → 建版位 → snippet 插入游標處
  async function insertPromo(){
    try{
      const list=await api('/api/admin/promo/selectable');
      if(!list.length){alert('目前沒有可插入的已核准提案');return;}
      const pick=prompt('輸入要插入的提案編號：\n'
        +list.map(p=>p.id+'：'+p.title+'（剩 '+(p.placementQuota-p.placementUsed)+' 次）').join('\n'));
      if(!pick)return;
      const r=await api('/api/admin/promo/placements',
        {method:'POST',body:JSON.stringify({proposalId:parseInt(pick,10)})});
      const ta=$('#markdown'),pos=ta.selectionStart;
      // 插入游標處並前後補空行，避免 promo 標記黏住其他段落
      ta.value=ta.value.slice(0,pos)+'\n'+r.markdown+'\n'+ta.value.slice(ta.selectionEnd);
      ta.focus();
    }catch(e){if(e.message!=='401')alert('插入失敗：'+e.message);}
  }
```

- [ ] **Step 3: 統計載入**——`loadPromoStats()` 呼叫 `/api/admin/promo/stats`，CTR 顯示為百分比一位小數、`null` 顯示「—」（純網頁發布無發送數）。

- [ ] **Step 4: 全案驗證**

```powershell
$env:JAVA_HOME='D:\java\jdk-21'; cd survey-backend
./mvnw test        # 全套測試（含既有 45+ CampaignServiceTest 與 PG 整合測試）
node scripts/verify-admin.mjs   # 既有 admin 頁自動驗證（若腳本涵蓋分頁結構）
```

Expected: 全綠；任何既有測試變紅都必須修到綠才算完成（免 TDD ≠ 免驗證）。

- [ ] **Step 5: 手動煙霧流程**（本地 `SPRING_PROFILES_ACTIVE=postgres` 起服務）：讀者登入 → `/r/promo` 申請（看餘額扣減）→ admin 核准 → 編輯器插入 → 預覽確認 promo 卡片 → 測試信 → 點測試信連結（統計應為 0，版位仍 DRAFT）→ 正式發布 → 點擊 → 後台統計出現數字。

- [ ] **Step 6: Commit**

```bash
git add survey-backend/src/main/resources/static/admin.html
git commit -m "feat(promo): 後台工商提案分頁、編輯器插入與統計呈現"
```

---

## Self-Review 紀錄

- **Spec 覆蓋**：§3 資料模型→Task 1–2；§3.4 單價→Task 3；§5 token／轉址→Task 4、10；§4.1 狀態機／退點→Task 6；§6 插入／對帳／重排／取消→Task 7–9；§7 讀者端→Task 5、12；§8 後台→Task 11、13；§9 測試→各 Task 內建＋Task 13 全案驗證；§10 非目標未實作（正確）。
- **型別一致性**：`ApplyRequest/ApplyResult`、`Snippet`、`PromoValidationException` 等跨 Task 簽名已逐一核對；`PromoRecipientTokenService.PLACEHOLDER` 為佔位符唯一來源。
- **已知的實作時確認點**（不是佔位符，是防行號漂移）：`CampaignService` 接線行號、`HtmlTemplate.render` 實際簽名、`RecipientService` 可見性、`Campaign.getAcceptedCount()` getter 名稱——各 Task 已標明「先讀該檔再照做」。
