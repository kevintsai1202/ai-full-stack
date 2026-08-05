# 讀者頁側欄與投票發點 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 文章頁加上相關文章與分類側欄；電子報編輯器可直接建立投票問卷；一鍵投票即發點並在卡片上明示點數。

**Architecture:** 三項改動彼此獨立。側欄以 server 端渲染字串替換（沿用 `HtmlTemplate` 佔位符機制）加入，資料由新的唯讀查詢服務 `PublicRelatedArticleService` 提供。快建問卷在單一交易內串接 `FormSchemaService` 既有的四個方法，不新增資料表。投票發點新增一個 reason 與一條 partial unique index，發點服務比照既有 `NewsletterSubmissionService.grantRewardIfEligible` 的形狀；點數提示因架構守衛禁止 `newsletter → reader`，改以根套件介面做依賴反轉。

**Tech Stack:** Java 21、Spring Boot 3（`@RestController` + `JdbcTemplate` + Spring Data JPA）、Flyway、PostgreSQL 18、JUnit 5 + Mockito、原生 HTML/CSS/JS（無前端框架、無建置步驟）。

## Global Constraints

- **設計來源**：`docs/superpowers/specs/2026-08-05-reader-sidebar-and-vote-reward-design.md`（下稱 spec）。任何與 spec 衝突的實作都是錯的。
- **註解語言**：所有新增的類別、方法、重要變數都要有**中文**註解（專案 CLAUDE.md 強制）。
- **架構守衛**：`newsletter` 套件**不得** import `world.springai.survey.reader.*`（`PackageDependencyTest.newsletterMustNotDependOnReader`）。`reader → newsletter` 是授權方向。
- **測試指令**：`JAVA_HOME=/d/java/jdk-21 mvn -q test`。**預設 shell 的 `JAVA_HOME` 是 JDK 8，會把錯誤誤報成編碼／檔案損壞問題。**
- **整合測試需要資料庫**：走真實 PG 的測試連 `127.0.0.1:5433`。若容器不存在先建：`docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password -p 5433:5432 pgvector/pgvector:pg18`；已存在則 `docker start survey-test-db`。
- **點數數字唯一來源**：任何顯示點數的地方都必須經 `CreditPolicy`，不可自行呼叫 `AppSettingService` 另帶後備值。
- **付費牆不可退化**：`ReaderPageController` 既有的「PARTIAL 時受限區完全不進入回應」與 `Cache-Control: private, no-store` + `Vary: Cookie` 行為不得改變。
- **工作目錄**：所有指令都在 `survey-backend/` 下執行。

## File Structure

| 檔案 | 責任 |
|---|---|
| `src/main/java/.../newsletter/PublicRelatedArticleService.java`（新增） | 側欄用的相關文章唯讀查詢（同標籤優先、不足補最新） |
| `src/main/java/.../reader/ReaderPageController.java`（修改） | 新增 `<!--ARTICLE_SIDEBAR-->` 渲染 |
| `src/main/resources/templates/reader/article.html`（修改） | 兩欄結構與側欄佔位符 |
| `src/main/resources/static/reader/reader.css`（修改） | `.article-wrap` / `.article-layout` / `.article-side` 版面 |
| `src/main/java/.../form/FormSchemaService.java`（修改） | `createQuickVoteForm()`：單交易串接建立→加欄→發布→綁定 |
| `src/main/java/.../form/FormSchemaController.java`（修改） | `POST /api/admin/forms/quick-vote` |
| `src/main/resources/static/admin.html`（修改） | 快建投票面板、現有問卷下拉、新設定鍵標籤 |
| `src/main/resources/db/migration/V23__survey_vote_reward.sql`（新增） | 投票發點的 partial unique index |
| `src/main/java/.../SurveyVoteRewardView.java`（新增，根套件） | 讓 `newsletter` 取得投票獎勵點數的介面（依賴反轉） |
| `src/main/java/.../reader/CreditPolicy.java`（修改） | 實作 `SurveyVoteRewardView`、新增 `surveyVoteReward()` |
| `src/main/java/.../reader/CreditTxn.java`（修改） | `REASON_SURVEY_VOTE_REWARD` 常數 |
| `src/main/java/.../AppSettingService.java`（修改） | `credit.survey_vote_reward` 鍵 |
| `src/main/java/.../AdminSettingController.java`（修改） | 新鍵進 `ADJUSTABLE` / `DISPLAY_DEFAULTS` / `ordered()` |
| `src/main/java/.../form/SurveyVoteRewardService.java`（新增） | 投票發點（身分對映、冪等、帳本與餘額同交易） |
| `src/main/java/.../form/SurveyVoteService.java`（修改） | 落票成功後呼叫發點 |
| `src/main/java/.../newsletter/SurveyBlockRenderer.java`（修改） | 卡片題目上方的點數提示（四種通道／狀態） |
| `src/main/java/.../newsletter/MailBodyRenderer.java`（不改） | 已經呼叫 `expandForEmail`，簽章不變 |
| `src/main/java/.../form/SurveyPortalController.java`（修改） | 「已投票」橫幅改實查帳本 |

---

### Task 1: 相關文章查詢服務

**Files:**
- Create: `src/main/java/world/springai/survey/newsletter/PublicRelatedArticleService.java`
- Test: `src/test/java/world/springai/survey/newsletter/PublicRelatedArticleServiceTest.java`

**Interfaces:**
- Consumes: 既有 `campaign`、`campaign_tag`、`content_tag` 資料表；`JdbcTemplate`。
- Produces: `PublicRelatedArticleService.relatedTo(long campaignId, int limit)` 回 `List<RelatedArticle>`；`record RelatedArticle(String slug, String subject, OffsetDateTime publishedAt, Long coverMediaId, String coverEmoji)`。Task 2 依賴這兩個名稱。

**背景**：`campaign` 的相關欄位是 `slug`、`subject`、`published_at`、`cover_media_id`、`cover_emoji`；「已發布」的判斷全庫一致為 `slug IS NOT NULL AND published_at IS NOT NULL`（見 `CampaignRepository.findBySlugIsNotNullAndPublishedAtIsNotNullOrderByPublishedAtDesc`）。標籤要濾 `content_tag.active = TRUE`，與 `PublicCampaignTagService` 一致。

- [ ] **Step 1: 寫失敗測試**

本測試走真實 PG（`FormSchemaService` 家族的既有模式：`JdbcTemplate` 直接下 SQL，沒有 entity 可 mock）。整份 fixture 用 SQL 建，不依賴其他服務。

建立 `src/test/java/world/springai/survey/newsletter/PublicRelatedArticleServiceTest.java`：

```java
package world.springai.survey.newsletter;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link PublicRelatedArticleService} 整合測試：同標籤優先排序、不足補最新、
 * 排除本篇與未發布文章。走真實 5433 PG（服務全走 JdbcTemplate，無 entity 可 mock），
 * 模式比照 {@code FormSchemaServiceCreateFormTest}。
 */
@SpringBootTest(properties = "spring.flyway.enabled=false")
class PublicRelatedArticleServiceTest {

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    private static final String DB_HOST = env("MIGRATION_TEST_DB_HOST", "127.0.0.1");
    private static final String DB_PORT = env("MIGRATION_TEST_DB_PORT", "5433");
    private static final String USER = env("MIGRATION_TEST_DB_USER", "postgres");
    private static final String PASS = env("MIGRATION_TEST_DB_PASSWORD", "password");
    private static final String ADMIN_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    private static final String TEST_DB = "related_article_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    @Autowired PublicRelatedArticleService service;

    /** 重建乾淨資料庫並套用全部 migration */
    @BeforeAll
    static void prepare() throws SQLException {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 連得上即可
        } catch (SQLException e) {
            fail("""
                連不到專用測試容器（%s）。請先啟動：
                  docker start survey-test-db
                若容器不存在：
                  docker run -d --name survey-test-db -e POSTGRES_PASSWORD=password \\
                    -p 5433:5432 pgvector/pgvector:pg18
                """.formatted(ADMIN_URL));
        }
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + TEST_DB);
        }
        Flyway.configure().dataSource(TEST_URL, USER, PASS).load().migrate();
    }

    /** 每個測試前清空文章與標籤關聯，避免 migration 內建資料與前一個測試互相干擾 */
    @BeforeEach
    void clean() throws SQLException {
        exec("DELETE FROM campaign_tag");
        exec("DELETE FROM campaign");
    }

    /** 執行一段 SQL */
    private void exec(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(TEST_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /**
     * 插入一篇文章並回傳 id。publishedAt 為 null 代表未發布。
     * campaign 的 NOT NULL 欄位（markdown/mode/status/tier 等）都給最小合法值。
     */
    private long insertCampaign(String slug, String subject, String publishedAt) throws SQLException {
        String published = publishedAt == null ? "NULL" : "'" + publishedAt + "'";
        String slugValue = slug == null ? "NULL" : "'" + slug + "'";
        try (Connection c = DriverManager.getConnection(TEST_URL, USER, PASS);
             Statement st = c.createStatement();
             var rs = st.executeQuery("""
                 INSERT INTO campaign (subject, markdown, mode, status, recipient_count,
                                       accepted_count, failed_count, tier, credit_cost,
                                       slug, published_at, cover_emoji)
                 VALUES ('%s', 'body', 'now', 'sent', 0, 0, 0, 'BASIC', 0, %s, %s, '🚀')
                 RETURNING id
                 """.formatted(subject, slugValue, published))) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** 把文章綁到指定 slug 的標籤上（標籤由 V12 預設資料提供） */
    private void tag(long campaignId, String tagSlug) throws SQLException {
        exec("""
            INSERT INTO campaign_tag (campaign_id, tag_id)
            SELECT %d, id FROM content_tag WHERE slug = '%s'
            """.formatted(campaignId, tagSlug));
    }

    /** 共同標籤數多的排前面；完全無共同標籤者不進第一段 */
    @Test
    void 同標籤交集多者優先() throws Exception {
        long base = insertCampaign("base", "本篇", "2026-07-01T10:00:00+08:00");
        tag(base, "ai");
        tag(base, "rag");

        long two = insertCampaign("two-shared", "共兩個標籤", "2026-06-01T10:00:00+08:00");
        tag(two, "ai");
        tag(two, "rag");
        long one = insertCampaign("one-shared", "共一個標籤", "2026-06-20T10:00:00+08:00");
        tag(one, "ai");

        List<PublicRelatedArticleService.RelatedArticle> result = service.relatedTo(base, 5);

        assertEquals("two-shared", result.get(0).slug(),
            "共同標籤較多者必須排在前面，即使發布日較舊");
        assertEquals("one-shared", result.get(1).slug());
        assertFalse(result.stream().anyMatch(a -> a.slug().equals("base")), "不得列出本篇");
    }

    /** 同標籤不足 limit 時，用最新已發布文章補齊，且不重複已入選者 */
    @Test
    void 不足時補最新且不重複() throws Exception {
        long base = insertCampaign("base", "本篇", "2026-07-01T10:00:00+08:00");
        tag(base, "ai");
        long shared = insertCampaign("shared", "同標籤", "2026-05-01T10:00:00+08:00");
        tag(shared, "ai");
        insertCampaign("fresh", "無標籤但最新", "2026-06-30T10:00:00+08:00");

        List<PublicRelatedArticleService.RelatedArticle> result = service.relatedTo(base, 2);

        assertEquals(2, result.size());
        assertEquals("shared", result.get(0).slug(), "同標籤者永遠排在補齊者之前");
        assertEquals("fresh", result.get(1).slug());
        assertEquals(1L, result.stream().filter(a -> a.slug().equals("shared")).count(),
            "補齊時不得重複列出第一段已入選的文章");
    }

    /** 未發布（slug 或 published_at 為 null）一律不出現在任何一段 */
    @Test
    void 未發布文章不列入() throws Exception {
        long base = insertCampaign("base", "本篇", "2026-07-01T10:00:00+08:00");
        tag(base, "ai");
        long draft = insertCampaign("draft", "同標籤但未發布", null);
        tag(draft, "ai");
        insertCampaign(null, "沒有 slug", "2026-06-01T10:00:00+08:00");

        List<PublicRelatedArticleService.RelatedArticle> result = service.relatedTo(base, 5);

        assertTrue(result.isEmpty(), "只有未發布的候選時應回空清單：" + result);
    }

    /** 本篇沒有任何標籤時，全部由最新補齊（不需特例分支，但行為必須被釘住） */
    @Test
    void 本篇無標籤時全走補齊() throws Exception {
        long base = insertCampaign("base", "本篇", "2026-07-01T10:00:00+08:00");
        insertCampaign("older", "較舊", "2026-05-01T10:00:00+08:00");
        insertCampaign("newer", "較新", "2026-06-01T10:00:00+08:00");

        List<PublicRelatedArticleService.RelatedArticle> result = service.relatedTo(base, 5);

        assertEquals(List.of("newer", "older"), result.stream()
            .map(PublicRelatedArticleService.RelatedArticle::slug).toList(),
            "無標籤時應依發布日新到舊回傳");
    }

    /** limit 為 0 或負數時直接回空清單，不下任何 SQL */
    @Test
    void limit非正數回空清單() throws Exception {
        long base = insertCampaign("base", "本篇", "2026-07-01T10:00:00+08:00");
        insertCampaign("other", "其他", "2026-06-01T10:00:00+08:00");

        assertTrue(service.relatedTo(base, 0).isEmpty());
        assertTrue(service.relatedTo(base, -1).isEmpty());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

```bash
docker start survey-test-db
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest=PublicRelatedArticleServiceTest
```

預期：編譯失敗，`cannot find symbol: class PublicRelatedArticleService`。

- [ ] **Step 3: 寫最小實作**

建立 `src/main/java/world/springai/survey/newsletter/PublicRelatedArticleService.java`：

```java
package world.springai.survey.newsletter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文章頁側欄用的「相關文章」唯讀查詢服務。
 *
 * <p>放在 newsletter 套件、與 {@link PublicCampaignTagService} 同層：兩者都是
 * 「公開文章的唯讀查詢」，而 {@code reader → newsletter} 是架構守衛授權的方向
 * （反向會形成上層循環，見 {@code PackageDependencyTest}）。</p>
 *
 * <p><b>刻意不回傳摘要</b>：摘要必須經 {@code ContentSplitter} 只取免費區，
 * 多一條可能把受限區帶出來的路徑；側欄寬度也放不下摘要。因此本服務只回
 * 標題、日期與封面這些本來就公開的欄位。</p>
 */
@Service
public class PublicRelatedArticleService {

    /** 側欄用的精簡文章描述；coverMediaId 為 null 時由呼叫端退回 coverEmoji */
    public record RelatedArticle(String slug, String subject, OffsetDateTime publishedAt,
                                 Long coverMediaId, String coverEmoji) {}

    private final JdbcTemplate jdbc;

    /** 注入資料庫查詢工具 */
    public PublicRelatedArticleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 取得與指定文章相關的已發布文章，最多 limit 篇。
     *
     * <p>兩段式：先取共同 hashtag 最多者，不足時以最新已發布文章補齊
     * （排除本篇與第一段已入選者）。本篇沒有標籤時第一段自然回空，全部由補齊處理。</p>
     */
    public List<RelatedArticle> relatedTo(long campaignId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<RelatedArticle> result = new ArrayList<>(sameTag(campaignId, limit));
        if (result.size() < limit) {
            List<String> exclude = result.stream().map(RelatedArticle::slug).toList();
            result.addAll(latestExcluding(campaignId, exclude, limit - result.size()));
        }
        return result;
    }

    /** 第一段：共同 hashtag 數量多者優先，同數量時新的優先 */
    private List<RelatedArticle> sameTag(long campaignId, int limit) {
        return jdbc.query("""
            SELECT c.slug, c.subject, c.published_at, c.cover_media_id, c.cover_emoji
              FROM campaign c
              JOIN campaign_tag ct ON ct.campaign_id = c.id
              JOIN content_tag t ON t.id = ct.tag_id AND t.active = TRUE
             WHERE ct.tag_id IN (SELECT tag_id FROM campaign_tag WHERE campaign_id = ?)
               AND c.id <> ?
               AND c.slug IS NOT NULL AND c.published_at IS NOT NULL
             GROUP BY c.id, c.slug, c.subject, c.published_at, c.cover_media_id, c.cover_emoji
             ORDER BY count(*) DESC, c.published_at DESC
             LIMIT ?
            """, this::mapRow, campaignId, campaignId, limit);
    }

    /** 第二段：最新已發布文章，排除本篇與已入選的 slug */
    private List<RelatedArticle> latestExcluding(long campaignId, List<String> excludeSlugs, int limit) {
        if (excludeSlugs.isEmpty()) {
            return jdbc.query("""
                SELECT slug, subject, published_at, cover_media_id, cover_emoji
                  FROM campaign
                 WHERE id <> ? AND slug IS NOT NULL AND published_at IS NOT NULL
                 ORDER BY published_at DESC
                 LIMIT ?
                """, this::mapRow, campaignId, limit);
        }
        // 參數化 IN：slug 來自前一段查詢結果而非外部輸入，仍一律用 placeholder，
        // 不做字串拼接——這是本專案對所有動態 IN 清單的一致寫法（見 PublicCampaignTagService）
        String placeholders = String.join(",", Collections.nCopies(excludeSlugs.size(), "?"));
        List<Object> params = new ArrayList<>();
        params.add(campaignId);
        params.addAll(excludeSlugs);
        params.add(limit);
        return jdbc.query("""
            SELECT slug, subject, published_at, cover_media_id, cover_emoji
              FROM campaign
             WHERE id <> ? AND slug IS NOT NULL AND published_at IS NOT NULL
               AND slug NOT IN (%s)
             ORDER BY published_at DESC
             LIMIT ?
            """.formatted(placeholders), this::mapRow, params.toArray());
    }

    /** 把一列查詢結果轉成 RelatedArticle；cover_media_id 為 NULL 時保持 null */
    private RelatedArticle mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Long coverMediaId = rs.getObject("cover_media_id") == null ? null : rs.getLong("cover_media_id");
        return new RelatedArticle(
            rs.getString("slug"),
            rs.getString("subject"),
            rs.getObject("published_at", OffsetDateTime.class),
            coverMediaId,
            rs.getString("cover_emoji"));
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest=PublicRelatedArticleServiceTest
```

預期：5 個測試全綠。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/world/springai/survey/newsletter/PublicRelatedArticleService.java \
        src/test/java/world/springai/survey/newsletter/PublicRelatedArticleServiceTest.java
git commit -m "feat(reader): 相關文章查詢服務（同標籤優先、不足補最新）"
```

---

### Task 2: 文章頁側欄渲染與兩欄版面

**Files:**
- Modify: `src/main/java/world/springai/survey/reader/ReaderPageController.java`（建構子、`article()` 的 vars、新增 `renderSidebar` 系列私有方法）
- Modify: `src/main/resources/templates/reader/article.html:22-75`
- Modify: `src/main/resources/static/reader/reader.css`
- Test: `src/test/java/world/springai/survey/reader/ReaderPageControllerTest.java`（擴充）
- Test: `src/test/java/world/springai/survey/reader/ReaderStylesheetTest.java`（擴充）

**Interfaces:**
- Consumes: `PublicRelatedArticleService.relatedTo(long, int)` 與 `RelatedArticle`（Task 1）；既有 `PublicCampaignTagService.publicTags()`、`tagsByCampaign(List<Long>)`、`MediaAssetService.publicUrls(Iterable<Long>)`。
- Produces: `article.html` 的 `<!--ARTICLE_SIDEBAR-->` 佔位符；CSS class `.article-wrap`、`.article-layout`、`.article-main`、`.article-side`、`.side-card`。

**注意**：`ReaderPageController` 有兩個建構子——`@Autowired` 的完整版與「舊單元測試相容版」。相關文章服務加在完整版建構子的**最後一個參數**，相容版建構子把該欄位設為 `null`，並在渲染時視為「沒有相關文章」。這與現有 `campaignTagService` / `mediaAssetService` 的處理方式一致。

- [ ] **Step 1: 寫失敗測試（controller）**

在 `ReaderPageControllerTest` 加入 `@MockBean` 與三個測試。先在 `@MockBean` 區塊（`MediaAssetService` 那行之後）加：

```java
    /** 側欄相關文章服務（mock）；未 stub 時 Mockito 對 List 回傳空清單，等於「沒有相關文章」 */
    @MockBean world.springai.survey.newsletter.PublicRelatedArticleService relatedArticleService;
    /** 側欄分類服務（mock）：controller 以 ObjectProvider 取得，此處提供 bean 讓分類卡有資料 */
    @MockBean world.springai.survey.newsletter.PublicCampaignTagService campaignTagService;
```

> **注意這個 `@MockBean` 會改變既有 archive 測試的環境**：在此之前 `ObjectProvider` 取不到
> bean，`campaignTagService` 是 `null`，`renderTagFilters()` 直接回空字串；加了 mock 之後
> 它會回一個只含「全部」的篩選列。若 `ReaderPageControllerTest` 或 `ReaderNavGuardTest` 有
> 測試在斷言「archive 頁不含 `tag-filters`」，會因此變紅——那不是回歸，而是測試環境變了，
> 應把該斷言改成符合新環境的內容（例如斷言只有「全部」而沒有其他 chip）。

再加測試方法：

```java
    /** 側欄：相關文章與分類都渲染出來，且連結指向正確路徑 */
    @Test
    void 文章頁輸出側欄相關文章與分類() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);
        when(relatedArticleService.relatedTo(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(List.of(new world.springai.survey.newsletter.PublicRelatedArticleService
                .RelatedArticle("other-post", "另一篇文章", OffsetDateTime.parse("2026-06-01T10:00:00+08:00"),
                    null, "🚀")));
        when(campaignTagService.publicTags())
            .thenReturn(List.of(new world.springai.survey.newsletter.PublicCampaignTagService
                .TagSummary("RAG", "rag", 3)));

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("class=\"article-side\""), "文章頁必須輸出側欄容器");
        assertTrue(body.contains("/r/news/other-post"), "側欄必須含相關文章連結");
        assertTrue(body.contains("另一篇文章"), "側欄必須含相關文章標題");
        assertTrue(body.contains("/r/archive?tag=rag"), "側欄分類必須連到 archive 的標籤篩選");
        assertFalse(body.contains(SENTINEL), "側欄不得讓受限區內容外洩");
    }

    /** 沒有相關文章時整張卡不輸出，不留一張空卡在側欄 */
    @Test
    void 無相關文章時不輸出該卡() throws Exception {
        when(campaignRepository.findBySlug("test-article"))
            .thenReturn(Optional.of(gatedArticle(Campaign.TIER_BASIC, 0)));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);
        when(relatedArticleService.relatedTo(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("相關文章"), "沒有相關文章時不應輸出該卡標題");
    }

    /** 本篇所屬分類在側欄標成 active，讀者才看得出自己在哪一類 */
    @Test
    void 側欄標示本篇所屬分類() throws Exception {
        Campaign article = gatedArticle(Campaign.TIER_BASIC, 0);
        article.setId(7L);
        when(campaignRepository.findBySlug("test-article")).thenReturn(Optional.of(article));
        when(readerContext.resolve(any())).thenReturn(Optional.empty());
        stubDecision(AccessDecisionService.Access.PARTIAL, AccessDecisionService.Reason.NOT_LOGGED_IN, 0);
        when(campaignTagService.publicTags()).thenReturn(List.of(
            new world.springai.survey.newsletter.PublicCampaignTagService.TagSummary("RAG", "rag", 3),
            new world.springai.survey.newsletter.PublicCampaignTagService.TagSummary("AI", "ai", 5)));
        when(campaignTagService.tagsByCampaign(List.of(7L))).thenReturn(java.util.Map.of(
            7L, List.of(new world.springai.survey.newsletter.PublicCampaignTagService
                .TagSummary("RAG", "rag", 0))));

        String body = mvc.perform(get("/r/news/test-article"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("side-tag active\" href=\"/r/archive?tag=rag"),
            "本篇所屬分類必須帶 active；實際輸出：" + body);
    }
```

- [ ] **Step 2: 跑測試確認失敗**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest=ReaderPageControllerTest
```

預期：三個新測試 FAIL（`article-side` 等字串不存在）。

- [ ] **Step 3: 改 template**

`article.html` 把 `<div class="wrap">` 到 `</div>`（第 22–75 行）整段改為：

```html
<div class="wrap article-wrap">
  <div class="article-layout">
  <div class="article-main">
  <article>
    <header class="article-head">
      <p class="page-kicker">凱文大叔的電子報</p>
      <h1 class="article-title"><!--ARTICLE_TITLE--></h1>
      <div class="article-meta"><!--ARTICLE_META--></div>
      <!--ARTICLE_TAGS-->
    </header>
    <!--SUBSCRIBE_CTA-->
    <!--ARTICLE_COVER-->
    <div class="article-body"><!--ARTICLE_CONTENT--></div>
    <div id="reading-complete-sentinel" aria-hidden="true"></div>
  </article>
  <!--GATE_BLOCK-->
  <!--SUBSCRIBE_CTA-->
```

（分享卡 `<section class="card viral-share …">` 到 `<a class="back-link" …>` 完全保留不動，接在上面之後）然後在 `back-link` 之後、原本的 `</div>` 之前補上：

```html
  </div>
  <aside class="article-side"><!--ARTICLE_SIDEBAR--></aside>
  </div>
</div>
```

- [ ] **Step 4: 改 controller**

`ReaderPageController` 三處改動。

其一，欄位與建構子：在 `surveyBlockRenderer` 欄位之後加：

```java
    /** 側欄相關文章查詢；舊單元測試相容建構式為 null，此時側欄不輸出相關文章卡 */
    private final world.springai.survey.newsletter.PublicRelatedArticleService relatedArticleService;
```

`@Autowired` 建構子參數列最後加 `PublicRelatedArticleService relatedArticleService`（記得補 import），並在方法內 `this.relatedArticleService = relatedArticleService;`；相容版建構子加 `this.relatedArticleService = null;`。

其二，`article()` 的 `vars` 加一行（放在 `<!--ARTICLE_TAGS-->` 那行之後）：

```java
        vars.put("<!--ARTICLE_SIDEBAR-->", renderSidebar(campaign));
```

其三，新增私有方法（放在 `renderArticleTags` 之後）：

```java
    /** 側欄相關文章的顯示篇數上限 */
    private static final int SIDEBAR_RELATED_LIMIT = 5;

    /**
     * 渲染文章頁右側欄：分類選單與相關文章兩張卡。
     *
     * <p>兩張卡各自可獨立缺席——服務未注入（舊相容建構式）或查無資料時
     * 該卡輸出空字串，不留一張空卡在側欄。</p>
     */
    private String renderSidebar(Campaign campaign) {
        return renderCategoryCard(campaign) + renderRelatedCard(campaign);
    }

    /** 分類卡：列出所有公開 hashtag 與篇數，本篇所屬者標 active */
    private String renderCategoryCard(Campaign campaign) {
        if (campaignTagService == null) {
            return "";
        }
        List<PublicCampaignTagService.TagSummary> all = campaignTagService.publicTags();
        if (all.isEmpty()) {
            return "";
        }
        Set<String> own = campaign.getId() == null
            ? Set.of()
            : campaignTagService.tagsByCampaign(List.of(campaign.getId()))
                .getOrDefault(campaign.getId(), List.of()).stream()
                .map(PublicCampaignTagService.TagSummary::slug)
                .collect(Collectors.toSet());
        StringBuilder html = new StringBuilder(
            "<section class=\"side-card\"><h2 class=\"side-title\">分類</h2><div class=\"side-tags\">");
        for (PublicCampaignTagService.TagSummary tag : all) {
            html.append("<a class=\"side-tag")
                .append(own.contains(tag.slug()) ? " active" : "")
                .append("\" href=\"/r/archive?tag=")
                .append(java.net.URLEncoder.encode(tag.slug(), java.nio.charset.StandardCharsets.UTF_8))
                .append("\">#").append(HtmlTemplate.escapeHtml(tag.name()))
                .append(" <span>").append(tag.articleCount()).append("</span></a>");
        }
        return html.append("</div></section>").toString();
    }

    /**
     * 相關文章卡：同標籤優先、不足補最新（規則見 PublicRelatedArticleService）。
     *
     * <p>只輸出標題、日期與封面——刻意不放摘要，避免多一條可能帶出受限區的路徑。</p>
     */
    private String renderRelatedCard(Campaign campaign) {
        if (relatedArticleService == null || campaign.getId() == null) {
            return "";
        }
        List<world.springai.survey.newsletter.PublicRelatedArticleService.RelatedArticle> related =
            relatedArticleService.relatedTo(campaign.getId(), SIDEBAR_RELATED_LIMIT);
        if (related.isEmpty()) {
            return "";
        }
        Map<Long, String> coverUrls = mediaAssetService == null
            ? Map.of()
            : mediaAssetService.publicUrls(related.stream()
                .map(world.springai.survey.newsletter.PublicRelatedArticleService.RelatedArticle::coverMediaId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());
        StringBuilder html = new StringBuilder(
            "<section class=\"side-card\"><h2 class=\"side-title\">相關文章</h2><ul class=\"side-list\">");
        for (var article : related) {
            String coverUrl = article.coverMediaId() == null ? null : coverUrls.get(article.coverMediaId());
            html.append("<li><a href=\"/r/news/")
                .append(HtmlTemplate.escapeHtml(article.slug())).append("\">")
                .append("<span class=\"side-thumb\">");
            if (coverUrl != null) {
                html.append("<img src=\"").append(HtmlTemplate.escapeHtml(coverUrl))
                    .append("\" alt=\"\" loading=\"lazy\" decoding=\"async\">");
            } else {
                html.append(HtmlTemplate.escapeHtml(
                    article.coverEmoji() == null ? "📝" : article.coverEmoji()));
            }
            html.append("</span><span class=\"side-copy\"><strong>")
                .append(HtmlTemplate.escapeHtml(article.subject())).append("</strong>");
            if (article.publishedAt() != null) {
                html.append("<small>").append(article.publishedAt().format(DATE_FORMAT)).append("</small>");
            }
            html.append("</span></a></li>");
        }
        return html.append("</ul></section>").toString();
    }
```

- [ ] **Step 5: 跑 controller 測試確認通過**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest=ReaderPageControllerTest
```

預期：全綠（含既有的受限區不洩漏測試）。

- [ ] **Step 6: 寫失敗測試（CSS）**

在 `ReaderStylesheetTest` 加：

```java
    /**
     * 文章頁兩欄版面：側欄樣式存在，且 .wrap 的 760px 單欄寬度未被改動
     * ——放寬只能加在 .article-wrap 上，否則 archive／me／rules 全部跟著變寬。
     */
    @Test
    void articleSidebarLayoutExistsWithoutWideningOtherPages() throws IOException {
        String css = Files.readString(READER_STYLESHEET, StandardCharsets.UTF_8);

        assertTrue(css.contains(".wrap { width:min(100% - 36px, 760px)"),
            "共用 .wrap 的 760px 單欄寬度不得被改動");
        assertTrue(css.contains(".article-wrap"), "文章頁需有自己的加寬容器");
        assertTrue(css.contains(".article-layout"), "文章頁需有兩欄 grid 容器");
        assertTrue(css.contains(".article-side"), "需有側欄樣式");
        assertTrue(css.contains("@media (max-width:960px)"),
            "需有窄螢幕斷點讓側欄降到內文下方");
    }
```

- [ ] **Step 7: 跑測試確認失敗**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest=ReaderStylesheetTest
```

預期：FAIL，`文章頁需有自己的加寬容器`。

- [ ] **Step 8: 改 CSS**

在 `reader.css` 的 archive 卡片樣式區塊之後（`.article-excerpt` 那行附近）加入：

```css
/* 文章頁兩欄版面：主欄維持原閱讀寬度，右側加側欄。
   加寬刻意只作用在 .article-wrap，不動共用的 .wrap（760px），
   否則 archive／me／rules／survey 全部會跟著變寬。 */
.article-wrap { width:min(100% - 36px, 1120px); }
.article-layout { display:grid; grid-template-columns:minmax(0,1fr) 300px; gap:32px; align-items:start; }
.article-main { min-width:0; }
.article-side { position:sticky; top:24px; display:grid; gap:16px; }
.side-card { padding:18px 20px; background:var(--surface); border:1px solid var(--border);
  border-radius:var(--r-lg); box-shadow:var(--shadow-sm); }
.side-title { margin:0 0 12px; font-size:1rem; }
.side-tags { display:flex; flex-wrap:wrap; gap:8px; }
.side-tag { display:inline-flex; gap:6px; align-items:center; padding:5px 10px; border:1px solid var(--border);
  border-radius:var(--r-pill); color:var(--muted); text-decoration:none; font-size:.8rem; font-weight:700; }
.side-tag:hover,.side-tag.active { color:#fff; background:var(--accent-deep); border-color:var(--accent-deep); }
.side-tag span { opacity:.72; font-size:.74rem; }
.side-list { margin:0; padding:0; list-style:none; display:grid; gap:14px; }
.side-list a { display:flex; gap:11px; align-items:flex-start; text-decoration:none; color:var(--fg); }
.side-list a:hover strong { color:var(--accent-deep); }
.side-thumb { display:grid; place-items:center; flex:0 0 52px; width:52px; height:52px; overflow:hidden;
  border-radius:10px; font-size:1.5rem; background:linear-gradient(135deg,var(--accent-soft),#fff7df); }
.side-thumb img { width:100%; height:100%; object-fit:cover; }
.side-copy { display:grid; gap:3px; min-width:0; }
.side-copy strong { font-size:.92rem; font-weight:700; line-height:1.45; }
.side-copy small { color:var(--muted); font-size:.76rem; }

/* 窄螢幕收成單欄，側欄落到內文之後 */
@media (max-width:960px) {
  .article-layout { grid-template-columns:1fr; }
  .article-side { position:static; }
}
```

- [ ] **Step 9: 跑全部測試**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test
```

預期：全綠。若 `ReaderShareAssetTest` 或 `ReaderNavGuardTest` 因 template 結構改動而紅，依其錯誤訊息修正 template（不要改測試的斷言意圖）。

- [ ] **Step 10: 目視確認**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q spring-boot:run
```

開 `http://localhost:8080/r/news/<任一已發布 slug>`，確認右側有分類與相關文章、視窗縮到 900px 以下時側欄落到內文下方。確認後 Ctrl+C 結束。

- [ ] **Step 11: Commit**

```bash
git add src/main/java/world/springai/survey/reader/ReaderPageController.java \
        src/main/resources/templates/reader/article.html \
        src/main/resources/static/reader/reader.css \
        src/test/java/world/springai/survey/reader/ReaderPageControllerTest.java \
        src/test/java/world/springai/survey/reader/ReaderStylesheetTest.java
git commit -m "feat(reader): 文章頁右側相關文章與分類側欄"
```

---

### Task 3: 快建投票問卷（服務與端點）

**Files:**
- Modify: `src/main/java/world/springai/survey/form/FormSchemaService.java`（新增 `QuickVoteRequest` record 與 `createQuickVoteForm`）
- Modify: `src/main/java/world/springai/survey/form/FormSchemaController.java`（新增端點）
- Test: `src/test/java/world/springai/survey/form/FormSchemaServiceQuickVoteTest.java`

**Interfaces:**
- Consumes: 既有 `FormSchemaService.createForm`、`addField`、`publish`、`updateEmailVoteField`、`FieldRequest`、`EmailVoteQuestion`。
- Produces: `FormSchemaService.QuickVoteRequest(String title, String label, List<String> options)`；`FormSchemaService.createQuickVoteForm(QuickVoteRequest)` 回 `EmailVoteQuestion`；`POST /api/admin/forms/quick-vote`。Task 4 的前端依賴回傳物件的 `formKey` 欄位。

**背景**：`formKey` 必須符合既有 `[a-z0-9-]{3,50}`；`fieldKey` 必須符合 `[A-Za-z][A-Za-z0-9_.-]{0,63}`（用固定值 `vote`）。`addField` 的 `FieldRequest` 參數順序為 `(label, type, required, options, analyticsEnabled, analyticsView, filterable, sensitive, publicAnalytics, displayOrder, factKey)`。

- [ ] **Step 1: 寫失敗測試**

建立 `src/test/java/world/springai/survey/form/FormSchemaServiceQuickVoteTest.java`。`@BeforeAll`／連線常數／`fail` 訊息整段沿用 `FormSchemaServiceCreateFormTest` 的既有寫法，只把 `TEST_DB` 改成 `quick_vote_form_test`：

```java
package world.springai.survey.form;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link FormSchemaService#createQuickVoteForm} 驗證：一次呼叫即產出
 * 「已發布且已綁定信中一鍵題」的可嵌入問卷，以及選項與標題的輸入驗證。
 *
 * <p>走真實 5433 PG，模式比照 {@code FormSchemaServiceCreateFormTest}。</p>
 */
@SpringBootTest(properties = "spring.flyway.enabled=false")
class FormSchemaServiceQuickVoteTest {

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    private static final String DB_HOST = env("MIGRATION_TEST_DB_HOST", "127.0.0.1");
    private static final String DB_PORT = env("MIGRATION_TEST_DB_PORT", "5433");
    private static final String USER = env("MIGRATION_TEST_DB_USER", "postgres");
    private static final String PASS = env("MIGRATION_TEST_DB_PASSWORD", "password");
    private static final String ADMIN_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/postgres";
    private static final String TEST_DB = "quick_vote_form_test";
    private static final String TEST_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + TEST_DB;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASS);
    }

    @Autowired FormSchemaService service;

    @BeforeAll
    static void prepare() throws SQLException {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS)) {
            // 連得上即可
        } catch (SQLException e) {
            fail("連不到專用測試容器（" + ADMIN_URL + "）。請先 docker start survey-test-db");
        }
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + TEST_DB);
        }
        Flyway.configure().dataSource(TEST_URL, USER, PASS).load().migrate();
    }

    /** 一次呼叫即產出可嵌入問卷：PUBLISHED、已綁定信中一鍵題、選項順序保留 */
    @Test
    void 一次呼叫即產出可嵌入問卷() {
        FormSchemaService.EmailVoteQuestion question = service.createQuickVoteForm(
            new FormSchemaService.QuickVoteRequest("這期你最想看哪個主題？", "選一個最想深入的",
                List.of("RAG 實戰", "Agent 架構", "部署維運")));

        assertTrue(question.formKey().matches("[a-z0-9-]{3,50}"),
            "自動生成的 formKey 必須符合既有格式：" + question.formKey());
        assertEquals("vote", question.fieldKey(), "信中一鍵題固定綁在 vote 欄位");
        assertEquals("這期你最想看哪個主題？", question.title());
        assertEquals("選一個最想深入的", question.label());
        assertEquals(List.of("RAG 實戰", "Agent 架構", "部署維運"), question.options(),
            "選項順序必須原樣保留（optionIndex 依此對映）");

        // 真正的驗收條件：立刻就能被電子報標記嵌入，不需再到動態表單分頁做任何設定
        assertTrue(service.emailVoteQuestion(question.formKey()).isPresent(),
            "建立完成後應立即可嵌入");
        assertTrue(service.listEmbeddable().stream()
                .anyMatch(q -> q.formKey().equals(question.formKey())),
            "應出現在可嵌入清單中");
    }

    /** 選項少於 2 個沒有投票意義，以 400 擋在寫入前 */
    @Test
    void 選項少於兩個回400() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.createQuickVoteForm(new FormSchemaService.QuickVoteRequest(
                "標題", "說明", List.of("只有一個"))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    /** 選項超過 6 個在信件裡會排到爆版，以 400 擋下 */
    @Test
    void 選項超過六個回400() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.createQuickVoteForm(new FormSchemaService.QuickVoteRequest(
                "標題", "說明", List.of("1", "2", "3", "4", "5", "6", "7"))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    /** 重複選項會讓統計無法區分，以 400 擋下（去空白後比對） */
    @Test
    void 重複選項回400() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.createQuickVoteForm(new FormSchemaService.QuickVoteRequest(
                "標題", "說明", List.of("同一個", " 同一個 "))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    /** 空白標題回 400（沿用 createForm 既有的標題必填規則） */
    @Test
    void 空白標題回400() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.createQuickVoteForm(new FormSchemaService.QuickVoteRequest(
                "   ", "說明", List.of("A", "B"))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    /** 連續建立兩份不得撞 formKey（同一天內的生成必須帶足夠亂度） */
    @Test
    void 連續建立兩份不撞代號() {
        FormSchemaService.EmailVoteQuestion first = service.createQuickVoteForm(
            new FormSchemaService.QuickVoteRequest("第一份", "說明", List.of("A", "B")));
        FormSchemaService.EmailVoteQuestion second = service.createQuickVoteForm(
            new FormSchemaService.QuickVoteRequest("第二份", "說明", List.of("A", "B")));

        assertTrue(!first.formKey().equals(second.formKey()),
            "兩份問卷的 formKey 不可相同：" + first.formKey());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest=FormSchemaServiceQuickVoteTest
```

預期：編譯失敗，`cannot find symbol: method createQuickVoteForm`。

- [ ] **Step 3: 寫服務實作**

在 `FormSchemaService` 的 `VersionRequest` record 之後加入：

```java
    /** 編輯器快建投票的要求；options 為選項文字（依序，optionIndex 對映） */
    public record QuickVoteRequest(String title, String label, List<String> options) {}
```

在 `updateEmailVoteField` 之後加入：

```java
    /** 快建投票的固定欄位 key；信中一鍵題永遠綁在這個欄位上 */
    private static final String QUICK_VOTE_FIELD_KEY = "vote";
    /** 選項數量下限（少於 2 個沒有投票意義） */
    private static final int QUICK_VOTE_MIN_OPTIONS = 2;
    /** 選項數量上限（信件內按鈕排版可容納的上限） */
    private static final int QUICK_VOTE_MAX_OPTIONS = 6;
    /** 單一選項文字長度上限 */
    private static final int QUICK_VOTE_OPTION_MAX_LENGTH = 40;
    /** formKey 自動生成的碰撞重試次數 */
    private static final int QUICK_VOTE_KEY_ATTEMPTS = 5;

    /**
     * 一次建立「可直接嵌入電子報」的投票問卷：在單一交易內走完既有四步
     * （建立 → 加單選欄位 → 發布 → 綁定信中一鍵題）。
     *
     * <p><b>為什麼不讓管理員自訂 formKey</b>：中文標題無法可靠轉成
     * {@code [a-z0-9-]} 的 slug，而要求管理員自創代號正是原本「問卷難設」的一部分。
     * 因此代號一律自動生成 {@code vote-{yyyyMMdd}-{4 碼}}，撞鍵時重試。</p>
     *
     * <p><b>刻意串接既有方法而非另寫 SQL</b>：驗證規則（欄位型別、DRAFT 才可改、
     * 發布時封存舊版）只有一份實作，快建路徑不會逐漸與手動路徑失準。</p>
     */
    @Transactional
    public EmailVoteQuestion createQuickVoteForm(QuickVoteRequest request) {
        List<String> options = normalizeQuickVoteOptions(request);
        String title = request.title().trim();
        String label = StringUtils.hasText(request.label()) ? request.label().trim() : title;

        String formKey = null;
        for (int attempt = 0; attempt < QUICK_VOTE_KEY_ATTEMPTS; attempt++) {
            String candidate = generateVoteFormKey();
            Integer exists = jdbc.queryForObject(
                "SELECT count(*) FROM form_definition WHERE form_key = ?", Integer.class, candidate);
            if (exists == null || exists == 0) {
                formKey = candidate;
                break;
            }
        }
        if (formKey == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "問卷代號連續碰撞，請稍後再試");
        }

        createForm(formKey, title);
        // FieldRequest.options 的型別是 List<Object>，必須明寫泛型參數——
        // new ArrayList<>(options) 會推導成 ArrayList<String> 而編譯不過
        addField(formKey, 1, QUICK_VOTE_FIELD_KEY, new FieldRequest(
            label, "select", false, new ArrayList<Object>(options),
            true, "bar", true, false, false, 0, null));
        publish(formKey, 1);
        updateEmailVoteField(formKey, 1, QUICK_VOTE_FIELD_KEY);

        return emailVoteQuestion(formKey).orElseThrow(() -> new IllegalStateException(
            "快建投票完成後應立即可嵌入，但查不到信中一鍵題：" + formKey));
    }

    /**
     * 驗證並正規化快建投票的選項：去頭尾空白、去空字串，檢查數量、長度與重複。
     *
     * <p>全部在寫入前完成——交易不會留下半套資料（已建立的 form 但沒有欄位）。</p>
     */
    private List<String> normalizeQuickVoteOptions(QuickVoteRequest request) {
        if (request == null || !StringUtils.hasText(request.title())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "問卷標題為必填");
        }
        List<String> options = (request.options() == null ? List.<String>of() : request.options()).stream()
            .filter(java.util.Objects::nonNull)
            .map(String::trim)
            .filter(StringUtils::hasText)
            .toList();
        if (options.size() < QUICK_VOTE_MIN_OPTIONS || options.size() > QUICK_VOTE_MAX_OPTIONS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "投票選項需 " + QUICK_VOTE_MIN_OPTIONS + "–" + QUICK_VOTE_MAX_OPTIONS + " 個");
        }
        if (options.stream().anyMatch(option -> option.length() > QUICK_VOTE_OPTION_MAX_LENGTH)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "單一選項不可超過 " + QUICK_VOTE_OPTION_MAX_LENGTH + " 字");
        }
        if (new java.util.LinkedHashSet<>(options).size() != options.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "投票選項不可重複");
        }
        return options;
    }

    /** 生成 vote-{yyyyMMdd}-{4 碼小寫英數} 形式的 formKey，符合既有 [a-z0-9-]{3,50} 規則 */
    private String generateVoteFormKey() {
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        return "vote-" + date + "-" + suffix;
    }
```

- [ ] **Step 4: 跑測試確認通過**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest=FormSchemaServiceQuickVoteTest
```

預期：6 個測試全綠。

- [ ] **Step 5: 加端點**

在 `FormSchemaController` 的 `listEmbeddable` 方法之後加入（`guard.verify(key)` 的寫法與同檔其他 admin 端點一致）：

```java
    /**
     * 編輯器快建投票：一次建立可直接嵌入的投票問卷，回傳信中一鍵題描述
     * （前端需要 formKey 才能組出 &lt;!--survey:KEY--&gt; 標記）。
     */
    @PostMapping("/api/admin/forms/quick-vote")
    public FormSchemaService.EmailVoteQuestion createQuickVote(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody FormSchemaService.QuickVoteRequest request) {
        guard.verify(key);
        return formSchemaService.createQuickVoteForm(request);
    }
```

> 對照同檔既有 admin 端點確認三件事：金鑰標頭的參數寫法、`formSchemaService` 欄位的實際名稱、以及 `@RequestBody`／`@RequestHeader` 是否已 import。與既有寫法不一致就照既有的改。

- [ ] **Step 6: 跑全部測試**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test
```

預期：全綠。

- [ ] **Step 7: Commit**

```bash
git add src/main/java/world/springai/survey/form/FormSchemaService.java \
        src/main/java/world/springai/survey/form/FormSchemaController.java \
        src/test/java/world/springai/survey/form/FormSchemaServiceQuickVoteTest.java
git commit -m "feat(newsletter): 編輯器快建投票問卷（單交易建立→發布→綁定）"
```

---

### Task 4: 編輯器快建面板 UI

**Files:**
- Modify: `src/main/resources/static/admin.html:231`（按鈕換成面板）、`:887`（事件綁定）、`:2692-2709`（`insertSurvey` 改寫）

**Interfaces:**
- Consumes: `POST /api/admin/forms/quick-vote`（Task 3）、既有 `GET /api/admin/forms/embeddable`、既有 helper `api()`、`markdownBlock()`、`composeChanged()`、`$()`。
- Produces: 無（純前端；後續任務不依賴）。

**背景**：`admin.html` 是單檔原生 JS，無建置步驟。既有 `insertSurvey()` 用 `prompt()` 讓管理員輸入清單編號，本任務把它換掉。標記必須整行單獨存在，因此插入一律走 `markdownBlock()`（不是 `insertPromo` 的游標夾入法）。

- [ ] **Step 1: 換掉工具列按鈕**

把第 231 行的按鈕（含其上方兩行註解）換成：

```html
        <!-- 問卷投票面板：可「快速建立」新投票（呼叫 quick-vote 端點一次建好可嵌入問卷），
             或插入既有可嵌入問卷。兩者都插入獨立一行的 HTML 註解標記，由 SurveyBlockRenderer
             展開成投票卡；刻意不加 format-btn class，避免被 applyMarkdownFormat 的
             dataset 驅動邏輯誤處理。 -->
        <details class="media-panel" id="survey-panel">
          <summary class="btn ghost" title="在信中插入問卷一鍵投票">插入問卷投票 ▾</summary>
          <p class="hint">快速建立：填題目與 2–6 個選項即可，代號由系統自動產生，不必先到「動態表單」分頁設定。</p>
          <div><label for="survey-quick-title">題目</label>
            <input type="text" id="survey-quick-title" maxlength="120" placeholder="這期你最想看哪個主題？"></div>
          <div><label for="survey-quick-label">題目說明（留空則沿用題目）</label>
            <input type="text" id="survey-quick-label" maxlength="120" placeholder="選一個最想深入的"></div>
          <label>選項（2–6 個，每個最多 40 字）</label>
          <div id="survey-quick-options"></div>
          <div class="form-row">
            <button type="button" class="btn ghost" id="survey-add-option">＋ 新增選項</button>
            <button type="button" class="btn" id="survey-quick-create">建立並插入</button>
          </div>
          <div class="msg" id="survey-quick-msg"></div>
          <hr>
          <label for="survey-existing">插入既有問卷</label>
          <div class="form-row">
            <select id="survey-existing"><option value="">載入中…</option></select>
            <button type="button" class="btn ghost" id="survey-insert-existing">插入</button>
          </div>
        </details>
```

- [ ] **Step 2: 改寫 JS**

把第 2692–2709 行的 `insertSurvey()` 整個函式換成下面四個函式；同時把第 887 行的 `$('#insert-survey-btn').onclick=insertSurvey;` 換成 `initSurveyPanel();`。

```javascript
  /** 問卷面板初始化：預設兩列選項、綁定事件、載入既有可嵌入問卷清單。 */
  function initSurveyPanel(){
    addSurveyOptionRow();addSurveyOptionRow();
    $('#survey-add-option').onclick=()=>{
      // 上限與後端 QUICK_VOTE_MAX_OPTIONS 一致；超過就不再加列，避免送出才被 400 退回
      if($('#survey-quick-options').children.length>=6){
        showMsg('survey-quick-msg','選項最多 6 個',false);return;
      }
      addSurveyOptionRow();
    };
    $('#survey-quick-create').onclick=createQuickVote;
    $('#survey-insert-existing').onclick=insertExistingSurvey;
    loadEmbeddableSurveys();
  }

  /** 新增一列選項輸入框（附刪除鈕；列數 <=2 時刪除鈕不作用，維持最低 2 列）。 */
  function addSurveyOptionRow(){
    const row=document.createElement('div');row.className='form-row';
    const input=document.createElement('input');
    input.type='text';input.maxLength=40;input.placeholder='選項文字';
    const remove=document.createElement('button');
    remove.type='button';remove.className='btn ghost';remove.textContent='移除';
    remove.onclick=()=>{
      if($('#survey-quick-options').children.length<=2){
        showMsg('survey-quick-msg','至少需要 2 個選項',false);return;
      }
      row.remove();
    };
    row.append(input,remove);$('#survey-quick-options').append(row);
  }

  /** 快速建立：送出題目與選項，成功後直接把標記插入編輯器並清空面板。 */
  async function createQuickVote(){
    const title=$('#survey-quick-title').value.trim();
    const label=$('#survey-quick-label').value.trim();
    const options=[...$('#survey-quick-options').querySelectorAll('input')]
      .map(input=>input.value.trim()).filter(Boolean);
    if(!title){showMsg('survey-quick-msg','請填題目',false);return;}
    if(options.length<2){showMsg('survey-quick-msg','請填至少 2 個選項',false);return;}
    try{
      const question=await api('/api/admin/forms/quick-vote',{
        method:'POST',body:JSON.stringify({title,label,options})});
      insertSurveyMarker(question.formKey);
      showMsg('survey-quick-msg','已建立並插入：'+question.formKey,true);
      $('#survey-quick-title').value='';$('#survey-quick-label').value='';
      $('#survey-quick-options').replaceChildren();addSurveyOptionRow();addSurveyOptionRow();
      loadEmbeddableSurveys();
    }catch(error){
      if(error.message!=='401')showMsg('survey-quick-msg','建立失敗：'+error.message,false);
    }
  }

  /** 載入已發布且已設信中一鍵題的問卷，填入「插入既有問卷」下拉。 */
  async function loadEmbeddableSurveys(){
    try{
      const list=await api('/api/admin/forms/embeddable');
      const select=$('#survey-existing');select.replaceChildren();
      if(!list.length){select.add(new Option('（目前沒有可嵌入的問卷）',''));return;}
      select.add(new Option('選擇問卷…',''));
      list.forEach(question=>select.add(
        new Option(question.title+'（'+question.formKey+'）',question.formKey)));
    }catch(error){/* 401 已由 api 顯示閘門；其餘僅影響下拉，不打斷編輯 */}
  }

  /** 插入下拉選定的既有問卷標記。 */
  function insertExistingSurvey(){
    const formKey=$('#survey-existing').value;
    if(!formKey){showMsg('survey-quick-msg','請先選擇要插入的問卷',false);return;}
    insertSurveyMarker(formKey);
    showMsg('survey-quick-msg','已插入：'+formKey,true);
  }

  /**
   * 在游標處插入獨立一行的問卷標記。
   *
   * 用 markdownBlock（同「分隔線」等 data-block 按鈕）確保標記前後補足空行成獨立一段
   * ——SurveyBlockRenderer 以行級比對展開，標記夾在文字中間不會被辨識。
   */
  function insertSurveyMarker(formKey){
    const marker='<!--survey:'+formKey+'-->';
    const editor=$('#markdown'),start=editor.selectionStart,end=editor.selectionEnd;
    const block=markdownBlock(editor.value,start,end,marker);
    editor.setRangeText(block.text,start,end,'end');editor.focus();
    const cursor=start+block.text.length;
    editor.setSelectionRange(cursor,cursor);
    composeChanged();
  }
```

> 送出前先確認兩件事：`api()` 的第二參數格式（method／body 的傳法）與 `showMsg(id, text, ok)` 的參數順序，都以同檔既有呼叫點為準；若不同就照既有的改。

- [ ] **Step 3: 手動驗證**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q spring-boot:run
```

開後台 → 撰寫電子報 → 展開「插入問卷投票」：

1. 填題目 + 2 個選項 → 按「建立並插入」→ 編輯器出現 `<!--survey:vote-…-->` 且右側預覽出現投票卡。
2. 「插入既有問卷」下拉應已包含剛建立的那份 → 選擇 → 按「插入」→ 再插入一個標記。
3. 按 6 次「＋ 新增選項」→ 第 7 次應提示「選項最多 6 個」。
4. 開瀏覽器 console 確認全程無錯誤。

確認後 Ctrl+C 結束。

- [ ] **Step 4: 跑全部測試**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test
```

預期：全綠（後端未動，主要是確認沒有測試在斷言 `insert-survey-btn` 這個 id；若有，改測試指向新的面板 id）。

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/admin.html
git commit -m "feat(admin): 編輯器問卷投票面板，取代 prompt 輸入編號"
```

---

### Task 5: 投票發點的參數、常數與 migration

**Files:**
- Create: `src/main/resources/db/migration/V23__survey_vote_reward.sql`
- Modify: `src/main/java/world/springai/survey/reader/CreditTxn.java`（新常數）
- Modify: `src/main/java/world/springai/survey/AppSettingService.java`（新鍵）
- Modify: `src/main/java/world/springai/survey/reader/CreditPolicy.java`（後備值與 `surveyVoteReward()`）
- Modify: `src/main/java/world/springai/survey/AdminSettingController.java`（`ADJUSTABLE`、`DISPLAY_DEFAULTS`、`ordered()`）
- Modify: `src/main/resources/static/admin.html:2445-2451`（設定標籤表）
- Test: `src/test/java/world/springai/survey/reader/SurveyVoteRewardConstraintTest.java`
- Test: `src/test/java/world/springai/survey/reader/CreditPolicyTest.java`（擴充）

**Interfaces:**
- Consumes: 既有 `credit_txn.survey_form_key` 欄（V21 已加）、`AppSettingService.getInt(String, int)`。
- Produces: `CreditTxn.REASON_SURVEY_VOTE_REWARD`（值 `"SURVEY_VOTE_REWARD"`）、`AppSettingService.CREDIT_SURVEY_VOTE_REWARD`（值 `"credit.survey_vote_reward"`）、`CreditPolicy.surveyVoteReward()` 回 int。Task 6、7 依賴這三個名稱。

**背景**：`credit_txn.reason` **沒有** CHECK 約束（已逐一檢查 V1–V22），新 reason 只需 Java 常數。V22 是目前最後一個 migration，故本任務用 V23。`AdminSettingControllerTest` 會斷言 `ADJUSTABLE`／`DISPLAY_DEFAULTS`／`ordered()` 三者的 keySet 一致——**三處必須一起改**，否則該測試會紅。

- [ ] **Step 1: 寫失敗測試（policy）**

在 `CreditPolicyTest` 加入。該檔沒有共用的 mock 欄位，而是用 helper
`settingsReturning(String key, int value)` 建「指定鍵回指定值、其餘鍵回呼叫端 defaultValue」
的假物件，每個測試各自 `new CreditPolicy(...)`：

```java
    /** 投票獎勵：查無設定時採後備值 5 */
    @Test
    void surveyVoteRewardFallsBackToFive() {
        CreditPolicy policy = new CreditPolicy(settingsReturning("unrelated.key", 999));

        assertEquals(5, policy.surveyVoteReward());
    }

    /** 投票獎勵可被設為 0（關閉投票發點），但負值一律夾到 0 */
    @Test
    void surveyVoteRewardAllowsZeroAndClampsNegative() {
        assertEquals(0, new CreditPolicy(
                settingsReturning(AppSettingService.CREDIT_SURVEY_VOTE_REWARD, 0)).surveyVoteReward(),
            "0 是合法的『關閉投票發點』設定");

        assertEquals(0, new CreditPolicy(
                settingsReturning(AppSettingService.CREDIT_SURVEY_VOTE_REWARD, -5)).surveyVoteReward(),
            "負值必須夾到 0");
    }
```

- [ ] **Step 2: 跑測試確認失敗**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest=CreditPolicyTest
```

預期：編譯失敗，`cannot find symbol: CREDIT_SURVEY_VOTE_REWARD`。

- [ ] **Step 3: 加常數與 policy**

`CreditTxn.java`，在 `REASON_SURVEY_REWARD` 之後：

```java
    /** 交易原因：信中／頁面一鍵投票獎勵（每人每問卷一次，與完整填答獎勵各自獨立） */
    public static final String REASON_SURVEY_VOTE_REWARD = "SURVEY_VOTE_REWARD";
```

`AppSettingService.java`，在 `CREDIT_SURVEY_REWARD` 之後：

```java
    /** 一鍵投票獎勵點數 */
    public static final String CREDIT_SURVEY_VOTE_REWARD = "credit.survey_vote_reward";
```

`CreditPolicy.java`，在 `DEFAULT_SURVEY_REWARD` 之後加後備值、在 `surveyReward()` 之後加方法，並讓類別實作介面（介面於 Task 6 建立；本任務先只加方法，`implements` 留到 Task 6）：

```java
    /** 一鍵投票獎勵的後備值 */
    static final int DEFAULT_SURVEY_VOTE_REWARD = 5;
```

```java
    /**
     * 一鍵投票獎勵（點）；未設定時後備 5，負值夾到 0。
     *
     * <p>0 為合法值——代表關閉投票發點，此時問卷卡不會輸出點數提示
     * （見 {@code SurveyBlockRenderer}）。理由同 {@link #signupGrant()}：
     * 夾成 ≥ 1 會讓後台無法關閉贈點。</p>
     */
    public int surveyVoteReward() {
        return Math.max(0, appSettingService.getInt(
            AppSettingService.CREDIT_SURVEY_VOTE_REWARD, DEFAULT_SURVEY_VOTE_REWARD));
    }
```

`AdminSettingController.java` 三處各加一行：

```java
        Map.entry(AppSettingService.CREDIT_SURVEY_VOTE_REWARD, new Bound(0, CREDIT_MAX)),
```
```java
        Map.entry(AppSettingService.CREDIT_SURVEY_VOTE_REWARD, 5),
```
```java
            AppSettingService.CREDIT_SURVEY_VOTE_REWARD,
```

（`ordered()` 那行放在 `CREDIT_REFERRAL_REWARD` 之後，讓點數類參數排在一起。）

`admin.html` 第 2451 行之後加：

```javascript
    'credit.survey_vote_reward':['投票獎勵','讀者在信中或網頁一鍵投票即發放；每份問卷一次，設 0 可關閉'],
```

- [ ] **Step 4: 跑測試確認通過**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest='CreditPolicyTest,AdminSettingControllerTest'
```

預期：全綠（`AdminSettingControllerTest` 的三處 keySet 一致性檢查會驗證 Step 3 沒有漏改）。

- [ ] **Step 5: 寫 migration 與約束測試**

建立 `src/main/resources/db/migration/V23__survey_vote_reward.sql`：

```sql
-- V23__survey_vote_reward.sql
-- 一鍵投票發點：每人每問卷一次（partial unique 防併發重發）。
-- 形狀刻意與 V21 的 uq_credit_txn_survey_reward 完全一致——兩者是同一個
-- 「每人每問卷一次」不變式的兩種發點原因，索引形狀不同會讓日後維護者
-- 誤以為其中一種有額外語意。沿用 V21 已建立的 credit_txn.survey_form_key 欄位。
CREATE UNIQUE INDEX uq_credit_txn_survey_vote_reward
    ON credit_txn (reader_id, survey_form_key)
    WHERE reason = 'SURVEY_VOTE_REWARD';
```

建立 `src/test/java/world/springai/survey/reader/SurveyVoteRewardConstraintTest.java`。整份結構、`@SpringBootTest` 屬性、`requireTestDatabase()` 的完整失敗訊息、`insertReader` helper 全部照 `src/test/java/world/springai/survey/promo/SurveyRewardConstraintTest.java` 複製，只改三處：套件宣告改 `world.springai.survey.reader`、`TEST_DB` 改 `survey_vote_reward_credit_txn_test`、測試方法改為：

```java
    /**
     * 同一讀者同一問卷的第二筆 SURVEY_VOTE_REWARD 必須被
     * {@code uq_credit_txn_survey_vote_reward} 擋下——這是應用層
     * {@code existsByReaderIdAndSurveyFormKeyAndReason} 冪等檢查的最終防線，
     * 防止併發重複投票造成重複發點。
     */
    @Test
    void 同讀者同問卷第二筆SURVEY_VOTE_REWARD撞唯一約束() throws SQLException {
        long readerId = insertReader("survey-vote-dup@example.com");

        CreditTxn first = new CreditTxn(readerId, 5,
            CreditTxn.REASON_SURVEY_VOTE_REWARD, null, "投票獎勵", null);
        first.setSurveyFormKey("reader-poll");
        creditTxnRepository.saveAndFlush(first);

        CreditTxn dup = new CreditTxn(readerId, 5,
            CreditTxn.REASON_SURVEY_VOTE_REWARD, null, "投票獎勵", null);
        dup.setSurveyFormKey("reader-poll");
        assertThrows(DataIntegrityViolationException.class,
            () -> creditTxnRepository.saveAndFlush(dup),
            "同讀者同問卷的第二筆 SURVEY_VOTE_REWARD 必須被唯一索引擋下");
    }

    /**
     * 完整填答獎勵與投票獎勵是兩個獨立的 reason：同一讀者同一問卷可以各有一筆，
     * 兩條 partial unique index 不得互相干擾。
     */
    @Test
    void 填答獎勵與投票獎勵可並存() throws SQLException {
        long readerId = insertReader("survey-both-rewards@example.com");

        CreditTxn vote = new CreditTxn(readerId, 5,
            CreditTxn.REASON_SURVEY_VOTE_REWARD, null, "投票獎勵", null);
        vote.setSurveyFormKey("reader-poll");
        creditTxnRepository.saveAndFlush(vote);

        CreditTxn full = new CreditTxn(readerId, 20,
            CreditTxn.REASON_SURVEY_REWARD, null, "填答獎勵", null);
        full.setSurveyFormKey("reader-poll");
        assertDoesNotThrow(() -> creditTxnRepository.saveAndFlush(full),
            "兩種發點原因各自獨立防重發，同問卷應可並存");
    }
```

（記得 import `assertDoesNotThrow`。）

- [ ] **Step 6: 跑約束測試**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest='SurveyVoteRewardConstraintTest,MigrationSafetyTest'
```

預期：全綠。`MigrationSafetyTest` 會驗證新 migration 檔的命名與可套用性。

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/migration/V23__survey_vote_reward.sql \
        src/main/java/world/springai/survey/reader/CreditTxn.java \
        src/main/java/world/springai/survey/reader/CreditPolicy.java \
        src/main/java/world/springai/survey/AppSettingService.java \
        src/main/java/world/springai/survey/AdminSettingController.java \
        src/main/resources/static/admin.html \
        src/test/java/world/springai/survey/reader/SurveyVoteRewardConstraintTest.java \
        src/test/java/world/springai/survey/reader/CreditPolicyTest.java
git commit -m "feat(reader): 投票獎勵參數與 V23 冪等索引"
```

---

### Task 6: 投票發點服務與接線

**Files:**
- Create: `src/main/java/world/springai/survey/form/SurveyVoteRewardService.java`
- Modify: `src/main/java/world/springai/survey/form/SurveyVoteService.java`（建構子與 `recordVote` 尾端）
- Test: `src/test/java/world/springai/survey/form/SurveyVoteRewardServiceTest.java`
- Test: `src/test/java/world/springai/survey/form/SurveyVoteServiceTest.java`（擴充）

**Interfaces:**
- Consumes: `CreditTxn.REASON_SURVEY_VOTE_REWARD`、`CreditPolicy.surveyVoteReward()`（Task 5）；既有 `CreditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(Long, String, String)`、`ReaderRepository.findByEmailIgnoreCase(String)`、`ReaderRepository.addCredits(Long, int)`、`SurveyVote.IDENTITY_RECIPIENT`／`IDENTITY_READER`／`IDENTITY_ANON`。
- Produces: `SurveyVoteRewardService.grantIfEligible(String formKey, String formTitle, String identityType, String identityKey, Long campaignId)` 回 `Optional<Integer>`（發出的點數；未發點為 `Optional.empty()`）。Task 7 不依賴此簽章，Task 8 只依賴帳本內容。

**背景**：`SurveyVoteService.recordVote` 目前是 `private void`，由 `vote()` 包在 try/catch 內以 best-effort 呼叫（落票失敗只寫 log、不擋轉址）。發點掛在 `recordVote` 尾端，沿用同一條 best-effort 邊界；帳本一致性由 `SurveyVoteRewardService` 自己的交易保證。

- [ ] **Step 1: 寫失敗測試**

建立 `src/test/java/world/springai/survey/form/SurveyVoteRewardServiceTest.java`：

```java
package world.springai.survey.form;

import org.junit.jupiter.api.Test;
import world.springai.survey.reader.CreditPolicy;
import world.springai.survey.reader.CreditTxn;
import world.springai.survey.reader.CreditTxnRepository;
import world.springai.survey.reader.Reader;
import world.springai.survey.reader.ReaderRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SurveyVoteRewardService} 純 Mockito 單元測試：身分對映、冪等與帳本一致性。
 *
 * <p>形狀比照 {@code SurveyVoteServiceTest}（同套件、同 mock 風格、不需 DB）。</p>
 */
class SurveyVoteRewardServiceTest {

    private final ReaderRepository readerRepository = mock(ReaderRepository.class);
    private final CreditTxnRepository creditTxnRepository = mock(CreditTxnRepository.class);
    private final CreditPolicy creditPolicy = mock(CreditPolicy.class);
    private final SurveyVoteRewardService service =
        new SurveyVoteRewardService(readerRepository, creditTxnRepository, creditPolicy);

    /** 建一位有 id 的讀者 */
    private Reader reader(long id, String email) {
        Reader r = new Reader(email, "CODE" + id);
        r.setId(id);
        return r;
    }

    /** RECIPIENT 身分（信中連結）：以 email 反查讀者後發點 */
    @Test
    void recipient身分以email反查後發點() {
        when(creditPolicy.surveyVoteReward()).thenReturn(5);
        when(readerRepository.findByEmailIgnoreCase("a@example.com"))
            .thenReturn(Optional.of(reader(9L, "a@example.com")));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            9L, "reader-poll", CreditTxn.REASON_SURVEY_VOTE_REWARD)).thenReturn(false);
        when(readerRepository.addCredits(9L, 5)).thenReturn(1);

        Optional<Integer> granted = service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_RECIPIENT, "a@example.com", 3L);

        assertEquals(Optional.of(5), granted);
        verify(creditTxnRepository).save(any(CreditTxn.class));
        verify(readerRepository).addCredits(9L, 5);
    }

    /** READER 身分（網頁已登入）：identityKey 即 readerId，不需反查 email */
    @Test
    void reader身分直接以id發點() {
        when(creditPolicy.surveyVoteReward()).thenReturn(5);
        when(readerRepository.findById(9L)).thenReturn(Optional.of(reader(9L, "a@example.com")));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            9L, "reader-poll", CreditTxn.REASON_SURVEY_VOTE_REWARD)).thenReturn(false);
        when(readerRepository.addCredits(9L, 5)).thenReturn(1);

        Optional<Integer> granted = service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_READER, "9", null);

        assertEquals(Optional.of(5), granted);
        verify(readerRepository, never()).findByEmailIgnoreCase(any());
    }

    /** 匿名投票不發點，也不得寫任何帳本列 */
    @Test
    void 匿名身分不發點() {
        Optional<Integer> granted = service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_ANON, null, null);

        assertTrue(granted.isEmpty());
        verify(creditTxnRepository, never()).save(any());
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    /** email 找不到對應讀者（訂閱者尚未建帳）：照收投票、不發點 */
    @Test
    void 非註冊讀者不發點() {
        when(readerRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        Optional<Integer> granted = service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_RECIPIENT, "ghost@example.com", null);

        assertTrue(granted.isEmpty());
        verify(creditTxnRepository, never()).save(any());
    }

    /** 同一問卷已發過投票點數：改票不重發 */
    @Test
    void 同問卷已發過不重發() {
        when(readerRepository.findByEmailIgnoreCase("a@example.com"))
            .thenReturn(Optional.of(reader(9L, "a@example.com")));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            9L, "reader-poll", CreditTxn.REASON_SURVEY_VOTE_REWARD)).thenReturn(true);

        Optional<Integer> granted = service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_RECIPIENT, "a@example.com", null);

        assertTrue(granted.isEmpty());
        verify(creditTxnRepository, never()).save(any());
        verify(readerRepository, never()).addCredits(anyLong(), anyInt());
    }

    /** 後台把投票獎勵設為 0（關閉發點）：不寫帳本列，避免留下一堆 0 點交易 */
    @Test
    void 獎勵為零時不寫帳本() {
        when(creditPolicy.surveyVoteReward()).thenReturn(0);
        when(readerRepository.findByEmailIgnoreCase("a@example.com"))
            .thenReturn(Optional.of(reader(9L, "a@example.com")));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            9L, "reader-poll", CreditTxn.REASON_SURVEY_VOTE_REWARD)).thenReturn(false);

        Optional<Integer> granted = service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_RECIPIENT, "a@example.com", null);

        assertTrue(granted.isEmpty());
        verify(creditTxnRepository, never()).save(any());
    }

    /** addCredits 回 0 列代表讀者列已不存在：必須拋例外讓交易回滾，不可靜默放行 */
    @Test
    void 加點影響零列時拋例外() {
        when(creditPolicy.surveyVoteReward()).thenReturn(5);
        when(readerRepository.findByEmailIgnoreCase("a@example.com"))
            .thenReturn(Optional.of(reader(9L, "a@example.com")));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            9L, "reader-poll", CreditTxn.REASON_SURVEY_VOTE_REWARD)).thenReturn(false);
        when(readerRepository.addCredits(9L, 5)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_RECIPIENT, "a@example.com", null),
            "帳本已寫入但餘額沒更新到，必須回滾而非靜默成功");
    }

    /** identityKey 為病態值（READER 身分卻不是數字）：不發點，不得讓 NumberFormatException 竄出 */
    @Test
    void reader身分identityKey非數字時不發點() {
        Optional<Integer> granted = service.grantIfEligible(
            "reader-poll", "滿意度調查", SurveyVote.IDENTITY_READER, "not-a-number", null);

        assertTrue(granted.isEmpty());
        verify(creditTxnRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest=SurveyVoteRewardServiceTest
```

預期：編譯失敗，`cannot find symbol: class SurveyVoteRewardService`。

- [ ] **Step 3: 寫實作**

建立 `src/main/java/world/springai/survey/form/SurveyVoteRewardService.java`：

```java
package world.springai.survey.form;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.springai.survey.reader.CreditPolicy;
import world.springai.survey.reader.CreditTxn;
import world.springai.survey.reader.CreditTxnRepository;
import world.springai.survey.reader.Reader;
import world.springai.survey.reader.ReaderRepository;

import java.util.Optional;

/**
 * 一鍵投票發點服務：把投票身分對映到讀者帳號後發放點數。
 *
 * <p><b>與完整填答發點的關係</b>：兩者是各自獨立的 reason
 * （{@code SURVEY_VOTE_REWARD} 與 {@code SURVEY_REWARD}），各有一條
 * partial unique index 防重發，因此同一位讀者同一份問卷可以「投票拿一次、
 * 填完整問卷再拿一次」。形狀刻意比照
 * {@link NewsletterSubmissionService} 的 {@code grantRewardIfEligible}。</p>
 *
 * <p><b>不發點的四種情況</b>：匿名投票、email 找不到對應讀者（訂閱者尚未建帳）、
 * 該問卷已發過投票點數（改票不重發）、後台把獎勵設為 0（關閉發點）。
 * 前三種都照常計票，只是不觸發發點。</p>
 */
@Service
public class SurveyVoteRewardService {

    private static final Logger log = LoggerFactory.getLogger(SurveyVoteRewardService.class);

    private final ReaderRepository readerRepository;
    private final CreditTxnRepository creditTxnRepository;
    private final CreditPolicy creditPolicy;

    /** 注入讀者、帳本與點數規則 */
    public SurveyVoteRewardService(ReaderRepository readerRepository,
                                   CreditTxnRepository creditTxnRepository,
                                   CreditPolicy creditPolicy) {
        this.readerRepository = readerRepository;
        this.creditTxnRepository = creditTxnRepository;
        this.creditPolicy = creditPolicy;
    }

    /**
     * 依投票身分發點；回傳實際發出的點數，未發點回 {@link Optional#empty()}。
     *
     * @param formKey      問卷代號，同時是冪等鍵的一部分
     * @param formTitle    問卷標題，只用於帳本 note
     * @param identityType {@code SurveyVote.IDENTITY_*} 之一
     * @param identityKey  RECIPIENT 為 email、READER 為 readerId 的字串形式
     * @param campaignId   觸發投票的電子報活動，可為 null
     */
    @Transactional
    public Optional<Integer> grantIfEligible(String formKey, String formTitle,
                                             String identityType, String identityKey, Long campaignId) {
        Optional<Long> readerId = resolveReaderId(identityType, identityKey);
        if (readerId.isEmpty()) {
            return Optional.empty();
        }
        long id = readerId.get();
        if (creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
                id, formKey, CreditTxn.REASON_SURVEY_VOTE_REWARD)) {
            return Optional.empty(); // 改票不重發；唯一索引為併發時的最終防線
        }
        int reward = creditPolicy.surveyVoteReward();
        if (reward <= 0) {
            return Optional.empty(); // 後台關閉投票發點時不留一堆 0 點交易
        }
        String note = "投票「" + formTitle + "」獎勵";
        CreditTxn txn = new CreditTxn(id, reward, CreditTxn.REASON_SURVEY_VOTE_REWARD, campaignId, note);
        txn.setSurveyFormKey(formKey);
        creditTxnRepository.save(txn);
        // 條件式 UPDATE 回 0 列代表讀者列已不存在；帳本已寫入卻靜默放行會讓
        // reader.credits 與 sum(credit_txn) 對不起來——比照 ReferralGrowthService.addCredit
        // 一律拋例外讓交易回滾。
        if (readerRepository.addCredits(id, reward) == 0) {
            throw new IllegalStateException("投票發點失敗：readerId=" + id);
        }
        return Optional.of(reward);
    }

    /**
     * 把投票身分對映到讀者 id。
     *
     * <p>RECIPIENT 以 email 反查（信件收件人未必已建帳）；READER 的 identityKey
     * 本身即 readerId，但仍確認該列存在，避免對已刪除的帳號加點。匿名與病態值
     * 一律回 empty——這裡是 best-effort 路徑的一部分，不該讓格式問題變成例外。</p>
     */
    private Optional<Long> resolveReaderId(String identityType, String identityKey) {
        if (identityKey == null || identityKey.isBlank()) {
            return Optional.empty();
        }
        if (SurveyVote.IDENTITY_RECIPIENT.equals(identityType)) {
            return readerRepository.findByEmailIgnoreCase(identityKey).map(Reader::getId);
        }
        if (SurveyVote.IDENTITY_READER.equals(identityType)) {
            try {
                long id = Long.parseLong(identityKey.trim());
                return readerRepository.findById(id).map(Reader::getId);
            } catch (NumberFormatException e) {
                log.warn("READER 身分的 identityKey 不是數字，跳過發點：{}", identityKey);
                return Optional.empty();
            }
        }
        return Optional.empty(); // ANON 與未知身分型別
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest=SurveyVoteRewardServiceTest
```

預期：8 個測試全綠。

- [ ] **Step 5: 接到 SurveyVoteService**

`SurveyVoteService` 三處改動：

其一，欄位與建構子加 `SurveyVoteRewardService rewardService`（放在參數列最後），並補 `this.rewardService = rewardService;`：

```java
    /** 投票發點：落票成功後呼叫；發點失敗不影響落票與轉址 */
    private final SurveyVoteRewardService rewardService;
```

其二，`recordVote` 的簽章不變，但在方法內的兩條 save 路徑之後都要發點。最乾淨的做法是讓 `recordVote` 在結尾統一呼叫一次——把匿名分支的 `return` 保留（匿名不發點），並在方法最後（`if (existing.isPresent()) { … } else { … }` 之後）加：

```java
        // 落票成功後才發點：發點的冪等與帳本一致性由 SurveyVoteRewardService 自己的
        // 交易保證；此處若拋例外會被 vote() 的 best-effort catch 接住，只寫 log 不擋轉址。
        rewardService.grantIfEligible(question.formKey(), question.title(),
            identityType, identityKey, campaignId);
```

其三，類別 Javadoc 補一段說明投票會發點（現行 Javadoc 只講落票與轉址，不更新會變成第二個「說明與行為不符」的陷阱）：

```java
 * <p><b>投票發點</b>：具名身分（RECIPIENT／READER）且對應到已註冊讀者時，
 * 落票成功後由 {@link SurveyVoteRewardService} 發放投票獎勵，每人每問卷一次
 * （改票不重發）。發點失敗與落票失敗同屬 best-effort，只寫 log 不擋轉址。</p>
```

- [ ] **Step 6: 補 SurveyVoteService 的接線測試**

`SurveyVoteServiceTest` 的 mock 是 field-initialized、`service` 在 `@BeforeEach` 建立。
先加 mock 欄位並改建構呼叫（`@BeforeEach setUp()` 內）：

```java
    private final SurveyVoteRewardService rewardService = mock(SurveyVoteRewardService.class);
```
```java
        service = new SurveyVoteService(
            formSchemaService, voteRepository, tokenService, sessionService, campaignRepository,
            rewardService);
```

再加三個測試。該檔準備問卷的既有 helper 是 `givenQuestion(String formKey, String fieldKey, List<String> options)`（標題固定為 `"標題"`）：

```java
    /** 具名身分落票後必須觸發發點，且帶入正確的問卷、身分與活動 */
    @Test
    void 具名落票後觸發發點() {
        givenQuestion("reader-poll", "rating", List.of("A", "B"));
        when(sessionService.readReaderId(eq("cookie"), any())).thenReturn(Optional.of(9L));
        when(campaignRepository.existsById(3L)).thenReturn(true);

        service.vote("reader-poll", "rating", 0, 3L, null, "cookie");

        verify(rewardService).grantIfEligible(eq("reader-poll"), eq("標題"),
            eq(SurveyVote.IDENTITY_READER), eq("9"), eq(3L));
    }

    /** 匿名投票不得觸發發點（setUp 的預設就是無 token、無 session） */
    @Test
    void 匿名落票不觸發發點() {
        givenQuestion("reader-poll", "rating", List.of("A", "B"));

        service.vote("reader-poll", "rating", 0, null, null, null);

        verify(rewardService, never()).grantIfEligible(any(), any(), any(), any(), any());
    }

    /** 發點拋例外時轉址照常回傳——發點是輔助，不得擋住讀者的主體驗 */
    @Test
    void 發點失敗不影響轉址() {
        givenQuestion("reader-poll", "rating", List.of("A", "B"));
        when(sessionService.readReaderId(eq("cookie"), any())).thenReturn(Optional.of(9L));
        when(rewardService.grantIfEligible(any(), any(), any(), any(), any()))
            .thenThrow(new IllegalStateException("發點壞了"));

        Optional<String> redirect = service.vote("reader-poll", "rating", 0, null, null, "cookie");

        assertTrue(redirect.isPresent(), "發點失敗時仍須回傳接續頁轉址");
    }
```

> 需要 import `org.mockito.ArgumentMatchers.eq`（該檔目前只 import 了 `any`／`argThat`）。

- [ ] **Step 7: 跑全部測試**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test
```

預期：全綠。若 `@SpringBootTest` 類型的測試因新 bean 而失敗，檢查 `SurveyVoteRewardService` 的建構子依賴是否都是既有 bean（`ReaderRepository`／`CreditTxnRepository`／`CreditPolicy` 三者皆是）。

- [ ] **Step 8: Commit**

```bash
git add src/main/java/world/springai/survey/form/SurveyVoteRewardService.java \
        src/main/java/world/springai/survey/form/SurveyVoteService.java \
        src/test/java/world/springai/survey/form/SurveyVoteRewardServiceTest.java \
        src/test/java/world/springai/survey/form/SurveyVoteServiceTest.java
git commit -m "feat(form): 一鍵投票發點（身分對映、冪等、帳本同交易）"
```

---

### Task 7: 問卷卡點數提示（四種通道／狀態）

**Files:**
- Create: `src/main/java/world/springai/survey/SurveyVoteRewardView.java`
- Modify: `src/main/java/world/springai/survey/reader/CreditPolicy.java`（`implements SurveyVoteRewardView`）
- Modify: `src/main/java/world/springai/survey/newsletter/SurveyBlockRenderer.java`（建構子、三個 expand 方法、`renderCard`）
- Modify: `src/main/java/world/springai/survey/reader/ReaderPageController.java`（`expandForWeb` 多傳 `loggedIn`）
- Test: `src/test/java/world/springai/survey/newsletter/SurveyBlockRendererTest.java`（擴充）
- Test: `src/test/java/world/springai/survey/reader/ReaderPageControllerTest.java`（既有 stub 需改簽章）

**Interfaces:**
- Consumes: `CreditPolicy.surveyVoteReward()`（Task 5）。
- Produces: `world.springai.survey.SurveyVoteRewardView` 介面（方法 `int surveyVoteReward()`）；`SurveyBlockRenderer.expandForWeb(String html, Long campaignId, boolean loggedIn)`（**簽章變更**）。

**背景**：`SurveyBlockRenderer` 在 `newsletter` 套件，**不得** import `reader`（`PackageDependencyTest`）。因此點數值透過根套件介面取得——`ReaderSiteLinks` 是「根套件共用型別被 newsletter 使用」的既有先例。`expandForWeb` 的簽章變更會影響 `ReaderPageController`（唯一呼叫點）與 `ReaderPageControllerTest` 的 passthrough stub。

- [ ] **Step 1: 寫失敗測試**

在 `SurveyBlockRendererTest` 加入。該檔現有的 renderer 建構為 `new SurveyBlockRenderer(formSchemaService)`，改為兩參數後既有測試也要跟著改（本步驟一併處理）：

```java
    /** 投票獎勵取值來源（mock）：newsletter 不得依賴 reader，故只認根套件介面 */
    private final world.springai.survey.SurveyVoteRewardView rewardView =
        mock(world.springai.survey.SurveyVoteRewardView.class);
    private final SurveyBlockRenderer renderer = new SurveyBlockRenderer(formSchemaService, rewardView);
```

（把檔案開頭原本的 `renderer` 欄位宣告換成上面兩行。）新增測試：

```java
    /** 信件通道：提示須說明「限已註冊讀者」——收件人未必已建帳，不可對他們說謊 */
    @Test
    void email通道提示點數與限制() {
        when(rewardView.surveyVoteReward()).thenReturn(5);
        givenEmbeddable("reader-poll", "rating", "滿意度調查", "你覺得如何？", List.of("A", "B"));

        String html = renderer.expandForEmail(MARKER_COMMENT, "https://news.example.com");

        assertTrue(html.contains("投票即可獲得 5 點"), html);
        assertTrue(html.contains("限已註冊讀者"), "信件通道必須說明僅註冊讀者可得點：" + html);
    }

    /** 讀者頁已登入：提示不需再提「限已註冊讀者」，但要說明每份問卷一次 */
    @Test
    void web已登入提示點數() {
        when(rewardView.surveyVoteReward()).thenReturn(5);
        givenEmbeddable("reader-poll", "rating", "滿意度調查", "你覺得如何？", List.of("A", "B"));

        String html = renderer.expandForWeb(MARKER_COMMENT, 3L, true);

        assertTrue(html.contains("投票即可獲得 5 點"), html);
        assertTrue(html.contains("每份問卷一次"), html);
        assertFalse(html.contains("限已註冊讀者"), "已登入者不需要看到註冊限制：" + html);
    }

    /** 讀者頁未登入：匿名投票不發點，提示必須先講登入 */
    @Test
    void web未登入提示需先登入() {
        when(rewardView.surveyVoteReward()).thenReturn(5);
        givenEmbeddable("reader-poll", "rating", "滿意度調查", "你覺得如何？", List.of("A", "B"));

        String html = renderer.expandForWeb(MARKER_COMMENT, 3L, false);

        assertTrue(html.contains("登入後投票可獲得 5 點"),
            "未登入者投票不會發點，不可寫成「投票即可獲得」：" + html);
    }

    /** 後台預覽：提示照顯示（管理員要看到讀者會看到什麼），且保留「預覽不計票」 */
    @Test
    void 預覽通道同時有提示與不計票標示() {
        when(rewardView.surveyVoteReward()).thenReturn(5);
        givenEmbeddable("reader-poll", "rating", "滿意度調查", "你覺得如何？", List.of("A", "B"));

        String html = renderer.expandForPreview(MARKER_COMMENT);

        assertTrue(html.contains("投票即可獲得 5 點"), html);
        assertTrue(html.contains("預覽不計票"), html);
    }

    /** 後台把投票獎勵設為 0（關閉發點）時整列提示不輸出，不可顯示「獲得 0 點」 */
    @Test
    void 獎勵為零時不輸出提示() {
        when(rewardView.surveyVoteReward()).thenReturn(0);
        givenEmbeddable("reader-poll", "rating", "滿意度調查", "你覺得如何？", List.of("A", "B"));

        String html = renderer.expandForEmail(MARKER_COMMENT, "https://news.example.com");

        assertFalse(html.contains("點"), "關閉發點時不得出現任何點數字樣：" + html);
        assertTrue(html.contains("滿意度調查"), "卡片其餘內容仍須正常輸出");
    }

    /** 提示必須排在題目之前——需求就是「問答上方提示」 */
    @Test
    void 提示位置在題目之前() {
        when(rewardView.surveyVoteReward()).thenReturn(5);
        givenEmbeddable("reader-poll", "rating", "滿意度調查", "你覺得如何？", List.of("A", "B"));

        String html = renderer.expandForEmail(MARKER_COMMENT, "https://news.example.com");

        assertTrue(html.indexOf("投票即可獲得") < html.indexOf("滿意度調查"),
            "點數提示必須在題目上方：" + html);
    }
```

- [ ] **Step 2: 跑測試確認失敗**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest=SurveyBlockRendererTest
```

預期：編譯失敗，`cannot find symbol: class SurveyVoteRewardView`。

- [ ] **Step 3: 建介面並讓 CreditPolicy 實作**

建立 `src/main/java/world/springai/survey/SurveyVoteRewardView.java`：

```java
package world.springai.survey;

/**
 * 問卷卡片顯示投票獎勵所需的唯一取值來源。
 *
 * <p><b>為什麼需要這個介面</b>：問卷卡片渲染器住在 {@code newsletter} 套件，
 * 而點數規則住在 {@code reader} 套件的 {@code CreditPolicy}——{@code
 * PackageDependencyTest.newsletterMustNotDependOnReader} 明文禁止
 * {@code newsletter → reader}（會形成上層循環）。把介面放在根套件、由
 * {@code CreditPolicy} 實作，渲染器只認介面，依賴方向就變成
 * {@code newsletter → 根套件 ← reader}，同時保住「點數數字只有 CreditPolicy
 * 一個來源」這條規則（下限保護不會因為繞道 AppSettingService 而漏掉）。
 * 作法比照 {@link ReaderSiteLinks} 這個既有的根套件共用型別。</p>
 */
public interface SurveyVoteRewardView {

    /** 一鍵投票的獎勵點數；0 表示後台已關閉投票發點 */
    int surveyVoteReward();
}
```

`CreditPolicy` 的類別宣告改為：

```java
public class CreditPolicy implements SurveyVoteRewardView {
```

並在 `surveyVoteReward()` 上加 `@Override`。

- [ ] **Step 4: 改 SurveyBlockRenderer**

四處改動。

其一，建構子與欄位：

```java
    /** 投票獎勵點數的取值來源；只認根套件介面，維持 newsletter 不依賴 reader */
    private final world.springai.survey.SurveyVoteRewardView rewardView;

    /** 注入問卷 schema 服務與投票獎勵取值來源 */
    public SurveyBlockRenderer(FormSchemaService formSchemaService,
                               world.springai.survey.SurveyVoteRewardView rewardView) {
        this.formSchemaService = formSchemaService;
        this.rewardView = rewardView;
    }
```

其二，提示樣式常數（放在 `PREVIEW_BADGE` 之後）：

```java
    /** 點數提示樣式：與卡片同色系但字級較小，排在題目之上 */
    private static final String REWARD_HINT_STYLE =
        "margin:0 0 10px;font-size:13px;font-weight:700;color:#1d4ed8;";
```

其三，三個 expand 方法改為傳入提示文字（`expandForWeb` 多一個參數）：

```java
    /**
     * 信件通道展開：選項按鈕連結格式
     * {@code {readerBaseUrl}/s/v/{formKey}?f={fieldKey}&o={optionIndex}&c=__SURVEY_CID__&rt=__PROMO_RT__}。
     * campaignId 與收件人 token 皆留待呼叫端延遲替換（見 {@link #CID_PLACEHOLDER}）。
     *
     * <p>點數提示註明「限已註冊讀者」：收件人是訂閱者但未必已建立讀者帳號，
     * 未建帳者投票只計票不發點（見 {@code SurveyVoteRewardService}）。</p>
     */
    public String expandForEmail(String html, String readerBaseUrl) {
        String hint = rewardHint("投票即可獲得 %d 點（限已註冊讀者，每份問卷一次）");
        return expand(html, q -> renderCard(q,
            i -> emailOptionHref(q, i, readerBaseUrl), null, false, hint));
    }

    /**
     * 讀者頁通道展開：選項按鈕連結帶 {@code c}（campaignId，可為 null 則不帶），
     * 不帶 {@code rt}（改由 session 歸戶），並附一條「繼續填完整問卷」連結。
     *
     * <p>點數提示依 {@code loggedIn} 分歧：匿名投票不會發點，對未登入者說
     * 「投票即可獲得」就是假訊息。</p>
     */
    public String expandForWeb(String html, Long campaignId, boolean loggedIn) {
        String hint = loggedIn
            ? rewardHint("投票即可獲得 %d 點（每份問卷一次）")
            : rewardHint("登入後投票可獲得 %d 點");
        return expand(html, q -> renderCard(q,
            i -> webOptionHref(q, i, campaignId), continueHref(q.formKey(), campaignId), false, hint));
    }

    /** 預覽通道展開：卡片視覺與正式通道一致，但加「預覽不計票」標示，連結一律 {@code href="#"} */
    public String expandForPreview(String html) {
        String hint = rewardHint("投票即可獲得 %d 點（限已註冊讀者，每份問卷一次）");
        return expand(html, q -> renderCard(q, i -> "#", null, true, hint));
    }

    /**
     * 組出點數提示列；獎勵為 0（後台關閉投票發點）時回空字串。
     *
     * <p>回空字串而非顯示「獲得 0 點」：後者是把一個沒有好處的動作包裝成有好處，
     * 比不提示更糟。</p>
     */
    private String rewardHint(String template) {
        int reward = rewardView.surveyVoteReward();
        if (reward <= 0) {
            return "";
        }
        return "<p style=\"" + REWARD_HINT_STYLE + "\">🎁 "
            + escapeHtml(template.formatted(reward)) + "</p>";
    }
```

其四，`renderCard` 多收一個 `rewardHint` 參數並輸出在題目之前：

```java
    /** 組出問卷卡片 HTML：點數提示、標題、題目 label、逐一選項按鈕，選填一條續填連結與預覽標示 */
    private String renderCard(EmailVoteQuestion question, IntFunction<String> optionHref,
                               String continueHref, boolean previewMode, String rewardHint) {
        StringBuilder sb = new StringBuilder();
        sb.append(CARD_OPEN);
        if (previewMode) {
            sb.append(PREVIEW_BADGE);
        }
        // 點數提示排在題目之前：讀者要先知道有好處，才有動機讀題並投票
        sb.append(rewardHint);
        sb.append("<p style=\"").append(TITLE_STYLE).append("\">")
```

（其後的內容完全不變。）

- [ ] **Step 5: 改 ReaderPageController 呼叫點**

`article()` 內的展開改為：

```java
        if (surveyBlockRenderer != null) {
            contentHtml = surveyBlockRenderer.expandForWeb(contentHtml, campaign.getId(), reader != null);
        }
```

同時把該處註解補上 `loggedIn` 的用途：

```java
        // 問卷標記展開（Task 9 接線）：contentHtml 定案（免費區／全文皆已決定）後
        // 統一展開，campaignId 在讀者頁一律已知，選項連結改由 session 歸戶不帶 rt。
        // 第三參數為登入狀態——匿名投票不發點，提示文字必須跟著分歧，否則會對
        // 拿不到點的訪客說謊。surveyBlockRenderer 為 null 只會發生在舊單元測試
        // 相容建構式，維持原內容不動。
```

`ReaderPageControllerTest` 的 passthrough stub 也要改簽章：

```java
        when(surveyBlockRenderer.expandForWeb(any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenAnswer(invocation -> invocation.getArgument(0));
```

- [ ] **Step 6: 跑測試確認通過**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest='SurveyBlockRendererTest,ReaderPageControllerTest,PackageDependencyTest'
```

預期：全綠。`PackageDependencyTest` 必須綠——它是 D6 依賴反轉的守衛。

- [ ] **Step 7: 跑全部測試**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test
```

預期：全綠。`MailBodyRendererTest`／`CampaignServiceTest` 若因 `SurveyBlockRenderer` 建構子多一個參數而失敗，在那些測試補上 `mock(SurveyVoteRewardView.class)`。

- [ ] **Step 8: Commit**

```bash
git add src/main/java/world/springai/survey/SurveyVoteRewardView.java \
        src/main/java/world/springai/survey/reader/CreditPolicy.java \
        src/main/java/world/springai/survey/newsletter/SurveyBlockRenderer.java \
        src/main/java/world/springai/survey/reader/ReaderPageController.java \
        src/test/java/world/springai/survey/newsletter/SurveyBlockRendererTest.java \
        src/test/java/world/springai/survey/reader/ReaderPageControllerTest.java
git commit -m "feat(newsletter): 問卷卡點數提示（依通道與登入狀態分歧）"
```

---

### Task 8: 接續頁橫幅改實查帳本

**Files:**
- Modify: `src/main/java/world/springai/survey/form/SurveyPortalController.java`（建構子、`survey()`、`votedBanner`）
- Test: `src/test/java/world/springai/survey/form/SurveyPortalControllerTest.java`（擴充）

**Interfaces:**
- Consumes: `CreditTxn.REASON_SURVEY_VOTE_REWARD`（Task 5）、既有 `CreditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason`、`ReaderRepository.findByEmailIgnoreCase`、該類既有的 `resolveIdentity(rt, sessionCookie, now)`。
- Produces: 無（終端頁面）。

**背景**：現行 `votedBanner(String voted)` 只看 `voted` 參數有無，一律顯示「已收到你的投票」。改為依帳本實查，三種狀態。**不可**改成用 URL 參數帶點數——那能被偽造成「已獲得 N 點」。

- [ ] **Step 1: 寫失敗測試**

`SurveyPortalControllerTest` 是 standalone MockMvc（`MockMvcBuilders.standaloneSetup(...)`）＋
mock 依賴＋真實 `HtmlTemplate`，mock 以私有欄位宣告、在 `@BeforeEach setUp()` 內 `mock(...)`
並建構 controller。先加欄位與 helper：

```java
    private CreditTxnRepository creditTxnRepository;
```
```java
        // setUp() 內：與其他 mock 並列建立，並加到 controller 建構子的最後一個參數
        creditTxnRepository = mock(CreditTxnRepository.class);
```
```java
    /** 建一位有 id 的讀者，供帳本查詢的身分歸戶使用 */
    private Reader reader(long id, String email) {
        Reader r = new Reader(email, "CODE" + id);
        r.setId(id);
        return r;
    }
```

再加四個測試：

```java
    /** 已因投票發過點：橫幅顯示實際點數，數字取自 CreditPolicy 而非 URL */
    @Test
    void 已發點時橫幅顯示點數() throws Exception {
        when(tokenService.verify("token")).thenReturn(Optional.of("a@example.com"));
        when(readerRepository.findByEmailIgnoreCase("a@example.com"))
            .thenReturn(Optional.of(reader(9L, "a@example.com")));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            9L, "reader-poll", CreditTxn.REASON_SURVEY_VOTE_REWARD)).thenReturn(true);
        when(creditPolicy.surveyVoteReward()).thenReturn(5);

        String body = mvc.perform(get("/r/survey/reader-poll?voted=0&rt=token"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("已發送 5 點"), body);
    }

    /** 帳本沒有投票發點紀錄但身分可歸戶：可能是改票或發點被關閉，不可宣稱已發點 */
    @Test
    void 未發點時橫幅不宣稱已發點() throws Exception {
        when(tokenService.verify("token")).thenReturn(Optional.of("a@example.com"));
        when(readerRepository.findByEmailIgnoreCase("a@example.com"))
            .thenReturn(Optional.of(reader(9L, "a@example.com")));
        when(creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            9L, "reader-poll", CreditTxn.REASON_SURVEY_VOTE_REWARD)).thenReturn(false);

        String body = mvc.perform(get("/r/survey/reader-poll?voted=0&rt=token"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("已收到你的投票"), body);
        assertFalse(body.contains("已發送"), "沒有帳本紀錄時不得宣稱已發點：" + body);
    }

    /** 未歸戶到讀者帳號（匿名）：明示要成為讀者才拿得到投票點數 */
    @Test
    void 未歸戶時橫幅引導成為讀者() throws Exception {
        when(tokenService.verify(any())).thenReturn(Optional.empty());
        when(sessionService.readReaderId(any(), any())).thenReturn(Optional.empty());

        String body = mvc.perform(get("/r/survey/reader-poll?voted=0"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("訂閱成為讀者即可獲得投票點數"), body);
    }

    /** 沒有 voted 參數時完全不顯示橫幅（既有行為，回歸護欄） */
    @Test
    void 無voted參數不顯示橫幅() throws Exception {
        String body = mvc.perform(get("/r/survey/reader-poll"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("已收到你的投票"), body);
    }
```

- [ ] **Step 2: 跑測試確認失敗**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest=SurveyPortalControllerTest
```

預期：前三個新測試 FAIL（現行橫幅文字固定）。

- [ ] **Step 3: 寫實作**

`SurveyPortalController` 三處改動。

其一，建構子加 `CreditTxnRepository creditTxnRepository`（參數列最後）與對應欄位：

```java
    /** 帳本查詢：投票橫幅顯示的發點狀態必須來自實際紀錄，不可由 URL 參數決定 */
    private final CreditTxnRepository creditTxnRepository;
```

其二，`survey()` 內把 `votedBanner(voted)` 改為：

```java
        vars.put("<!--VOTED_BANNER-->", votedBanner(voted, formKey, identifiedEmail));
```

其三，`votedBanner` 改寫：

```java
    /**
     * 有 voted 參數（即使空字串）才顯示已收到投票的提示區塊；發點狀態<b>實查帳本</b>。
     *
     * <p><b>為什麼不用 URL 參數帶點數</b>：轉址網址是讀者可自由編輯的，若橫幅照
     * URL 顯示「已獲得 N 點」，任何人都能自造一個宣稱發了點的頁面，而帳本上沒有
     * 這筆——「頁面說的與實際不一致」正是 {@code CreditPolicy} 這一層存在要避免的
     * 問題。因此這裡多付一次查詢的代價，換取顯示與帳本一致。</p>
     */
    private String votedBanner(String voted, String formKey, Optional<String> identifiedEmail) {
        if (voted == null) {
            return "";
        }
        Optional<Long> readerId = identifiedEmail
            .flatMap(readerRepository::findByEmailIgnoreCase)
            .map(Reader::getId);
        if (readerId.isEmpty()) {
            return "<div class=\"msg ok show\">✅ 已收到你的投票，"
                + "訂閱成為讀者即可獲得投票點數。</div>";
        }
        boolean rewarded = creditTxnRepository.existsByReaderIdAndSurveyFormKeyAndReason(
            readerId.get(), formKey, CreditTxn.REASON_SURVEY_VOTE_REWARD);
        if (rewarded) {
            return "<div class=\"msg ok show\">✅ 已收到你的投票，已發送 "
                + creditPolicy.surveyVoteReward() + " 點，歡迎補充更多想法！</div>";
        }
        // 可能是改票（先前已發過而不重發，但那時 existsBy 會回 true）、
        // 後台關閉了投票發點，或發點在 best-effort 路徑上失敗。三者都不能宣稱已發點。
        return "<div class=\"msg ok show\">✅ 已收到你的投票，歡迎補充更多想法！</div>";
    }
```

> 補上 `CreditTxn`、`CreditTxnRepository` 的 import。

- [ ] **Step 4: 跑測試確認通過**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test -Dtest=SurveyPortalControllerTest
```

預期：全綠。

- [ ] **Step 5: 端到端手動驗證**

```bash
docker start survey-test-db
JAVA_HOME=/d/java/jdk-21 mvn -q spring-boot:run
```

1. 後台 → 設定 → 確認出現「投票獎勵」欄位，值為 5。
2. 撰寫電子報 → 用 Task 4 的面板快建一份投票 → 預覽卡片上方應有「🎁 投票即可獲得 5 點（限已註冊讀者，每份問卷一次）」與「預覽不計票」。
3. 發布文章 → 以已登入讀者開 `/r/news/{slug}` → 卡片提示應為「每份問卷一次」版本；登出後再開應變成「登入後投票可獲得 5 點」。
4. 以登入讀者點一個選項 → 轉到接續頁 → 橫幅顯示「已發送 5 點」；`/r/me` 的帳本應多一筆「投票「…」獎勵 +5」。
5. 回文章頁改投另一個選項 → 橫幅不再顯示「已發送」（改票不重發），帳本仍只有一筆。

確認後 Ctrl+C 結束。

- [ ] **Step 6: 跑全部測試並確認 spec 覆蓋**

```bash
JAVA_HOME=/d/java/jdk-21 mvn -q test
```

預期：全綠、0 failures / 0 errors。記下總測試數，回報時附上。

- [ ] **Step 7: Commit**

```bash
git add src/main/java/world/springai/survey/form/SurveyPortalController.java \
        src/test/java/world/springai/survey/form/SurveyPortalControllerTest.java
git commit -m "feat(form): 接續頁投票橫幅改實查帳本顯示發點狀態"
```

---

## Spec 覆蓋對照

| Spec 章節 | 對應任務 |
|---|---|
| §3.1 版面 | Task 2（Step 3、Step 8） |
| §3.2 相關文章查詢 | Task 1 |
| §3.3 側欄渲染 | Task 2（Step 4） |
| §4.1 快建服務 | Task 3（Step 3） |
| §4.2 端點 | Task 3（Step 5） |
| §4.3 後台 UI | Task 4 |
| §5.1 migration V23 | Task 5（Step 5） |
| §5.2 參數與常數 | Task 5（Step 3） |
| §5.3 發點路徑 | Task 6 |
| §5.4 提示文字 | Task 7 |
| §5.5 接續頁 | Task 8 |
| §6 測試策略 | 每個任務的 Step 1／最終 `mvn test` |
| §7 範圍外 | 無任務（刻意不做） |
