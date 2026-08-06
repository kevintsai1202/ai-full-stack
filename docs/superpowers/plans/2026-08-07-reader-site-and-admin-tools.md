# 讀者站體驗強化與後台營運工具 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 依 `docs/superpowers/specs/2026-08-07-reader-site-and-admin-tools-design.md` 實作 8 項需求——讀者站（暗色模式、右上工具列、首頁身分區、首頁問卷列表）與後台（文章側邊欄投票統計、原始資料逐列寄券、漏斗語意重構與漏斗圖、投票卡關係說明）。

**Architecture:** Spring Boot 3.5（JDK 21）後端 + 無框架靜態前端（admin.html 單檔、reader 模板由 `HtmlTemplate` 以註解佔位符字串替換渲染）。表單層**沒有 JPA entity**——`form_definition`/`form_field` 兩張表由 `FormSchemaService` 以 JdbcTemplate 直接下 SQL。唯一 migration 是 V25（`form_definition` 加兩欄）。暗色模式全部 scoped 於 `[data-theme="dark"]`，亮色規則零改動。

**Tech Stack:** Spring Boot 3.5.14 / Flyway / PostgreSQL / JUnit 5 + Mockito（standalone MockMvc）/ Playwright（`scripts/verify-*.mjs`）

## Global Constraints

以下適用於**每一個任務**，不再逐一重複：

1. **JDK 21 必須明確指定**：shell 預設 `JAVA_HOME` 是 JDK 8，跑 Maven 前一律：
   ```powershell
   cd d:\GitHub\hahow-ai-full-stack\survey-backend
   $env:JAVA_HOME='D:\java\jdk-21'
   mvn -q -Dtest=<TestClass> test          # 單一測試類
   mvn -q test                              # 全套（僅 Task 13）
   ```
   忘記設 JAVA_HOME 的錯誤訊息會偽裝成「檔案損壞／編碼錯誤」，不要被誤導。
2. **真 DB 測試**用本機容器 `survey-test-db`（`127.0.0.1:5433`，帳密 `postgres`/`password`）；測試類自建自己的資料庫（比照 `CouponCampaignRepositoryTest` 的 `@BeforeAll` 模式）。容器未啟動時先 `docker start survey-test-db`。
3. **verify 腳本慣例**：後端須先在本機 8080 啟動（`APP_ALLOW_INSECURE_DEV_SECRETS=true`、`ADMIN_API_KEY=dev-admin-key`，DB 指向 5433）；腳本放 `survey-backend/scripts/`、中文註解、可重跑、`process.exitCode` 反映結果；Playwright 解析沿用 `verify-admin-toolbar-theme.mjs` 的 `loadPlaywright()` 寫法。**絕不指向正式站**。
4. **commit 紀律**：只 `git add` 明確列出的路徑，**禁止** `git add -A`／`git add .`——工作樹有無關的 `newsletter/**` 未提交檔案，不得混入。
5. **`ReaderNav` 安全不變量**：導覽列輸出永遠是固定字串，任何使用者可控值（email、slug…）不得進入 `<nav>`；`ReaderNavGuardTest` 維持原樣不放寬。email 顯示一律經 `HtmlTemplate.escapeHtml`。
6. **中文註解**：所有新函式需有函式級中文註解；重要變數同。
7. **狀態語意（D6）**：單筆寄券不得把 `coupon_campaign.status` 翻成 `SENT`。
8. 所有暗色模式修改 scoped 於 `:root[data-theme="dark"]`；亮色規則唯一允許的改動是修復 `reader.css:372` 的 `var(--shadow-soft)` 死引用。

---

## 檔案結構總覽

| 檔案 | 動作 | 任務 |
| --- | --- | --- |
| `src/main/resources/db/migration/V25__homepage_form_exposure.sql` | 新增 | 1 |
| `src/main/java/world/springai/survey/form/FormSchemaService.java` | 修改（homepage 查詢/更新、`FormDefinition` 擴欄） | 1 |
| `src/test/java/world/springai/survey/form/FormSchemaServiceHomepageTest.java` | 新增（真 DB） | 1 |
| `src/main/java/world/springai/survey/form/FormSchemaController.java` | 修改（PUT homepage 端點） | 2 |
| `src/test/java/world/springai/survey/form/FormSchemaControllerHomepageTest.java` | 新增 | 2 |
| `src/main/resources/static/admin.html` | 修改 | 2, 9, 11, 12 |
| `src/main/resources/templates/reader/index.html` | 修改（問卷列表佔位符、訂閱區佔位符、主題啟動腳本） | 3, 4, 5 |
| `src/main/java/world/springai/survey/reader/ReaderAuthController.java` | 修改（indexPage 渲染） | 3, 4 |
| `src/test/java/world/springai/survey/reader/ReaderIndexPageTest.java` | 新增 | 3, 4 |
| `src/main/resources/static/reader/reader.css` | 修改（暗色覆寫、工具列、問卷列表、側欄投票樣式） | 3, 5, 6, 7 |
| `src/main/resources/templates/reader/*.html`（全部） | 修改（主題啟動腳本） | 5 |
| `src/test/java/world/springai/survey/ReaderThemeGuardTest.java` | 新增 | 5 |
| `src/main/resources/static/reader/reader-nav.js` | 修改（右上工具列） | 6 |
| `scripts/verify-reader-theme.mjs` | 新增 | 6 |
| `src/main/java/world/springai/survey/newsletter/SurveyBlockRenderer.java` | 修改（`embeddedFormKeys`） | 7 |
| `src/main/java/world/springai/survey/reader/ReaderPageController.java` | 修改（側邊欄投票卡） | 7 |
| `src/main/java/world/springai/survey/coupon/CouponSendService.java` | 修改（single 模式） | 8 |
| `src/main/java/world/springai/survey/coupon/CouponRecipientService.java` | 修改（sentCouponsByEmail） | 8 |
| `src/main/java/world/springai/survey/coupon/AdminCouponController.java` | 修改（sent-map 端點、SendRequest 擴欄） | 8 |
| `src/main/java/world/springai/survey/mail/EmailLogRepository.java` | 修改（StartingWith 查詢） | 8 |
| `scripts/verify-raw-row-coupon.mjs` | 新增 | 9 |
| `src/main/java/world/springai/survey/reader/ReaderFunnelView.java` | 新增 | 10 |
| `src/main/java/world/springai/survey/reader/AdminReferralGrowthController.java` | 修改 | 10 |
| `src/test/java/world/springai/survey/reader/ReaderFunnelViewTest.java` | 新增 | 10 |
| `scripts/verify-growth-funnel.mjs` | 新增 | 11 |
| `scripts/verify-vote-card-context.mjs` | 新增 | 12 |

---

### Task 1: V25 migration + FormSchemaService 首頁曝光資料層

**Files:**
- Create: `survey-backend/src/main/resources/db/migration/V25__homepage_form_exposure.sql`
- Modify: `survey-backend/src/main/java/world/springai/survey/form/FormSchemaService.java`
- Test: `survey-backend/src/test/java/world/springai/survey/form/FormSchemaServiceHomepageTest.java`

**Interfaces:**
- Consumes: 既有 `form_definition` 表（V10 建立）、`FormSchemaService` 的 JdbcTemplate 與 `definition()` 私有查詢
- Produces（後續任務依賴的精確簽名）:
  - `public record HomepageForm(String key, String title, Integer homepageOrder)`（`FormSchemaService` 內嵌 record）
  - `public List<HomepageForm> listHomepageForms()`
  - `@Transactional public void updateHomepageExposure(String formKey, boolean visible, Integer order)`（key 不存在 → `ResponseStatusException(404)`）
  - `FormDefinition` record 在 `emailVoteFieldKey` 之後、`fields` 之前新增兩欄：`boolean homepageVisible, Integer homepageOrder`

- [ ] **Step 1: 寫 migration**

```sql
-- 首頁問卷曝光：預設 false，既有問卷（含 verify-* 測試問卷與 vote-* 信中一鍵題）不會突然曝光給讀者
ALTER TABLE form_definition ADD COLUMN homepage_visible BOOLEAN NOT NULL DEFAULT false;
-- 首頁排序：NULL 表示未指定，讀者端排在最後（避免新勾選的問卷因忘了填順序而消失在清單中間）
ALTER TABLE form_definition ADD COLUMN homepage_order INT;
```

- [ ] **Step 2: 寫失敗測試（真 DB）**

新建 `FormSchemaServiceHomepageTest`，開頭比照 `FormSchemaServiceCreateFormTest`（同套件已存在，直接抄它的 `@SpringBootTest` 屬性、`@BeforeAll` 建庫、`@DynamicPropertySource` 佈線；`TEST_DB = "form_schema_homepage_test"`）。測試方法：

```java
/** 預設不曝光：新建並發布的問卷不出現在首頁清單（spec §6「既有問卷不被曝光」） */
@Test
void newlyCreatedFormIsHiddenByDefault() {
    service.createForm("hp-default-test", "預設隱藏");
    service.publish("hp-default-test", 1);
    assertTrue(service.listHomepageForms().stream()
        .noneMatch(f -> f.key().equals("hp-default-test")));
}

/** 勾選曝光後出現；未發布（DRAFT-only）的 key 即使勾選也不出現 */
@Test
void exposureRequiresBothVisibleAndPublished() {
    service.createForm("hp-visible-test", "已勾選已發布");
    service.publish("hp-visible-test", 1);
    service.updateHomepageExposure("hp-visible-test", true, null);

    service.createForm("hp-draft-test", "已勾選未發布");
    service.updateHomepageExposure("hp-draft-test", true, null);

    List<String> keys = service.listHomepageForms().stream()
        .map(FormSchemaService.HomepageForm::key).toList();
    assertTrue(keys.contains("hp-visible-test"));
    assertFalse(keys.contains("hp-draft-test"));
}

/** 排序：homepage_order 升冪、NULL 排最後、NULL 內部依建立時間新到舊 */
@Test
void orderingPutsNullLastThenNewestFirst() {
    // 依序建立 a(order=2)、b(order=1)、c(order=null 較舊)、d(order=null 較新)
    for (String k : List.of("hp-ord-a", "hp-ord-b", "hp-ord-c", "hp-ord-d")) {
        service.createForm(k, k);
        service.publish(k, 1);
    }
    service.updateHomepageExposure("hp-ord-a", true, 2);
    service.updateHomepageExposure("hp-ord-b", true, 1);
    service.updateHomepageExposure("hp-ord-c", true, null);
    service.updateHomepageExposure("hp-ord-d", true, null);
    // c 比 d 早建立 → NULL 區內 d 在前
    List<String> keys = service.listHomepageForms().stream()
        .map(FormSchemaService.HomepageForm::key)
        .filter(k -> k.startsWith("hp-ord-")).toList();
    assertEquals(List.of("hp-ord-b", "hp-ord-a", "hp-ord-d", "hp-ord-c"), keys);
}

/** 不存在的 key → 404 */
@Test
void updateUnknownKeyThrows404() {
    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.updateHomepageExposure("hp-no-such-key", true, null));
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
}
```

注意 `orderingPutsNullLastThenNewestFirst` 的 c/d 建立時間差：`created_at` 精度為微秒，連續 INSERT 可能同刻——在建立 c 與 d 之間 `jdbc.update("UPDATE form_definition SET created_at = created_at - interval '1 minute' WHERE form_key = 'hp-ord-c'")` 直接把 c 調舊，別用 `Thread.sleep`。

- [ ] **Step 3: 跑測試確認失敗**

Run: `mvn -q -Dtest=FormSchemaServiceHomepageTest test`
Expected: FAIL——`listHomepageForms` 方法不存在（編譯錯誤）。

- [ ] **Step 4: 實作**

`FormSchemaService` 新增（放在 `listEmbeddable()` 之後）：

```java
/** 首頁曝光問卷：key 連結 /r/survey/{key}，title 顯示用，homepageOrder 供排序（NULL 排最後） */
public record HomepageForm(String key, String title, Integer homepageOrder) {}

/**
 * 列出後台勾選曝光且已發布的問卷；每個 key 取最新已發布版本的標題。
 * 排序：homepage_order 升冪、NULL 排最後、NULL 內部依建立時間新到舊（spec §3.5）。
 */
public List<HomepageForm> listHomepageForms() {
    record Row(String key, String title, Integer order, OffsetDateTime createdAt) {}
    List<Row> rows = jdbc.query("""
        SELECT DISTINCT ON (form_key) form_key, title, homepage_order, created_at
          FROM form_definition
         WHERE homepage_visible = true AND status = 'PUBLISHED'
         ORDER BY form_key, version DESC
        """,
        (rs, i) -> new Row(
            rs.getString("form_key"),
            rs.getString("title"),
            (Integer) rs.getObject("homepage_order"),
            rs.getObject("created_at", OffsetDateTime.class)));
    return rows.stream()
        .sorted(Comparator
            .comparing(Row::order, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Row::createdAt, Comparator.reverseOrder()))
        .map(row -> new HomepageForm(row.key(), row.title(), row.order()))
        .toList();
}

/**
 * 更新指定問卷的首頁曝光設定。以 form_key 為單位對所有版本列一致寫入，
 * 避免版本間旗標漂移（新版本發布後首頁不會突然掉線）。
 */
@Transactional
public void updateHomepageExposure(String formKey, boolean visible, Integer order) {
    int updated = jdbc.update(
        "UPDATE form_definition SET homepage_visible = ?, homepage_order = ?, updated_at = now() WHERE form_key = ?",
        visible, order, formKey);
    if (updated == 0) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到指定表單");
    }
}
```

同時把 `FormDefinition` record 擴為（在 `emailVoteFieldKey` 之後插入兩欄）：

```java
public record FormDefinition(
        long id,
        String key,
        int version,
        String title,
        String status,
        boolean publicAnalyticsEnabled,
        String emailVoteFieldKey,
        boolean homepageVisible,
        Integer homepageOrder,
        List<FieldDefinition> fields) {}
```

並更新 `listDefinitions()` 與 `definition()` 的 SELECT 子句加上 `homepage_visible, homepage_order`、RowMapper 對應帶入。**編譯會揪出所有 `new FormDefinition(...)` 呼叫點（含測試）——逐一補上 `false, null`**；不要用 IDE 自動修復以外的猜測值。

- [ ] **Step 5: 跑測試確認通過**

Run: `mvn -q -Dtest=FormSchemaServiceHomepageTest test`
Expected: PASS（4 tests）

- [ ] **Step 6: 跑同套件既有測試確認沒弄壞**

Run: `mvn -q -Dtest='FormSchema*,Survey*' test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add survey-backend/src/main/resources/db/migration/V25__homepage_form_exposure.sql survey-backend/src/main/java/world/springai/survey/form/FormSchemaService.java survey-backend/src/test/java/world/springai/survey/form/FormSchemaServiceHomepageTest.java
# 若編譯修復波及其他測試檔，逐一明確 add，禁止 -A
git commit -m "feat(form): V25 首頁問卷曝光欄位與資料層（homepage_visible/homepage_order）"
```

---

### Task 2: 後台首頁曝光 API + admin.html 勾選 UI

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/form/FormSchemaController.java`
- Modify: `survey-backend/src/main/resources/static/admin.html`（schema-settings 區塊約 197-217 行、JS 約 1119-1177 行附近）
- Test: `survey-backend/src/test/java/world/springai/survey/form/FormSchemaControllerHomepageTest.java`

**Interfaces:**
- Consumes: Task 1 的 `updateHomepageExposure(String, boolean, Integer)`；`FormDefinition.homepageVisible()/homepageOrder()`
- Produces: `PUT /api/admin/forms/{formKey}/homepage`，body `{"visible": true, "order": 1}` → 204；`GET /api/admin/forms` 回傳的每個元素多出 `homepageVisible`/`homepageOrder`（record 擴欄自動生效）

- [ ] **Step 1: 寫失敗測試**

standalone MockMvc（比照 `AdminCouponControllerTest` 的建法：`MockMvcBuilders.standaloneSetup` + 全 mock 協作者 + `ApiExceptionHandler`）：

```java
/** 首頁曝光端點：驗證 guard 先行、參數透傳、204 回應 */
@Test
void updateHomepageExposureDelegatesToService() throws Exception {
    mockMvc.perform(put("/api/admin/forms/my-form/homepage")
            .header("X-Admin-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"visible\":true,\"order\":3}"))
        .andExpect(status().isNoContent());
    verify(service).updateHomepageExposure("my-form", true, 3);
}

/** order 省略時傳 null（清除排序） */
@Test
void omittedOrderBecomesNull() throws Exception {
    mockMvc.perform(put("/api/admin/forms/my-form/homepage")
            .header("X-Admin-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"visible\":false}"))
        .andExpect(status().isNoContent());
    verify(service).updateHomepageExposure("my-form", false, null);
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `mvn -q -Dtest=FormSchemaControllerHomepageTest test`
Expected: FAIL（404，端點不存在）

- [ ] **Step 3: 實作端點**

`FormSchemaController` 新增（放在 email-vote-field 端點之後）：

```java
/** 首頁曝光設定請求：visible 是否在讀者首頁曝光、order 排序（可空＝未指定，排最後） */
public record HomepageExposureRequest(boolean visible, Integer order) {}

/** 設定問卷是否於讀者首頁曝光與排序（A4）；以 form_key 為單位作用於所有版本 */
@PutMapping("/api/admin/forms/{formKey}/homepage")
public ResponseEntity<Void> updateHomepageExposure(
        @RequestHeader(value = "X-Admin-Key", required = false) String key,
        @PathVariable String formKey,
        @RequestBody HomepageExposureRequest request) {
    guard.verify(key);
    service.updateHomepageExposure(formKey, request.visible(), request.order());
    return ResponseEntity.noContent().build();
}
```

- [ ] **Step 4: 跑測試確認通過**

Run: `mvn -q -Dtest=FormSchemaControllerHomepageTest test`
Expected: PASS

- [ ] **Step 5: admin.html UI**

在 `schema-settings` 的 `<details>` 內（`#email-vote-field` 區塊之後）加入：

```html
<div class="form-row" style="margin-top:10px">
  <label><span class="hint">讀者首頁曝光</span><input type="checkbox" id="homepage-visible"></label>
  <label><span class="hint">首頁排序（小的在前，可留空）</span><input type="number" id="homepage-order" style="width:90px"></label>
  <button class="btn ghost" id="homepage-save">儲存首頁曝光</button>
</div>
```

JS（放在 `refreshEmailVoteField` 附近）：

```js
/** 依目前選取的表單，把首頁曝光勾選與排序帶入畫面（取該 key 最新版本列的值） */
function refreshHomepageExposure(){
  const key=$('#dynamic-form').value;
  const definition=formDefinitions.find(item=>item.key===key);
  $('#homepage-visible').checked=!!definition?.homepageVisible;
  $('#homepage-order').value=definition?.homepageOrder??'';
}
/** 儲存首頁曝光設定並重載表單清單，讓快取與畫面同步 */
async function saveHomepageExposure(){
  const key=$('#dynamic-form').value;
  if(!key){msg('#schema-msg','請先選擇表單',false);return;}
  const order=$('#homepage-order').value;
  await api(`/api/admin/forms/${encodeURIComponent(key)}/homepage`,{method:'PUT',body:JSON.stringify({visible:$('#homepage-visible').checked,order:order===''?null:Number(order)})});
  await loadFormDefinitions();
  msg('#schema-msg','已更新首頁曝光設定',true);
}
```

接線：`#homepage-save` 的 click 綁 `saveHomepageExposure`；在 `#dynamic-form` 的 change 處理鏈（現有呼叫 `refreshEmailVoteField()` 的地方）加上 `refreshHomepageExposure()`。`msg(...)` 若無此 helper，沿用檔內現行的訊息顯示寫法（搜 `#schema-msg` 的既有賦值方式照抄）。

- [ ] **Step 6: 實際驗證 UI**

本機起後端（Global Constraints #3），瀏覽器或既有 verify 腳本手法確認：勾選→儲存→重整後勾選狀態保留。最低限度以 curl 驗證端點：

```powershell
curl -X PUT http://127.0.0.1:8080/api/admin/forms/<某formKey>/homepage -H "X-Admin-Key: dev-admin-key" -H "Content-Type: application/json" -d '{"visible":true,"order":1}' -i
# 預期 204；再 GET /api/admin/forms 確認該 key 的 homepageVisible=true
```

- [ ] **Step 7: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/form/FormSchemaController.java survey-backend/src/main/resources/static/admin.html survey-backend/src/test/java/world/springai/survey/form/FormSchemaControllerHomepageTest.java
git commit -m "feat(admin): 問卷首頁曝光設定端點與後台勾選 UI（Task 2）"
```

---

### Task 3: 讀者首頁問卷列表（A4 讀者端）

**Files:**
- Modify: `survey-backend/src/main/resources/templates/reader/index.html`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/ReaderAuthController.java`（`indexPage`，約 97-107 行）
- Modify: `survey-backend/src/main/resources/static/reader/reader.css`（新增 `.survey-list` 樣式）
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReaderIndexPageTest.java`（新建，Task 4 共用）

**Interfaces:**
- Consumes: Task 1 的 `FormSchemaService.listHomepageForms()` → `List<HomepageForm>`；`HtmlTemplate.render(String, Map<String,String>)`、`HtmlTemplate.escapeHtml(String)`
- Produces: `index.html` 新增佔位符 `<!--SURVEY_LIST-->`；`ReaderAuthController` 注入 `FormSchemaService`

- [ ] **Step 1: 寫失敗測試**

`ReaderAuthController` 建構子注入多個協作者——先讀該類確認建構子形狀，測試以 standalone MockMvc 或直接 new controller 呼叫 `indexPage(...)` 皆可（回傳 `ResponseEntity<String>`，直接斷言 body 字串最簡單）。mock `FormSchemaService`：

```java
/** 有曝光問卷時：首頁出現「問卷調查」區塊，每份問卷連向 /r/survey/{key}，標題經跳脫 */
@Test
void homepageListsExposedSurveys() {
    when(formSchemaService.listHomepageForms()).thenReturn(List.of(
        new FormSchemaService.HomepageForm("course-interest", "課程興趣調查 <b>", null)));
    String html = controller.indexPage(null).getBody();
    assertTrue(html.contains("href=\"/r/survey/course-interest\""));
    assertTrue(html.contains("課程興趣調查 &lt;b&gt;"));   // escapeHtml 生效
    assertTrue(html.contains("問卷調查"));
}

/** 無任何曝光問卷時：整個區塊不出現（不出現空標題） */
@Test
void homepageHidesSurveySectionWhenEmpty() {
    when(formSchemaService.listHomepageForms()).thenReturn(List.of());
    String html = controller.indexPage(null).getBody();
    assertFalse(html.contains("問卷調查"));
    assertFalse(html.contains("<!--SURVEY_LIST-->"));  // 佔位符必須被替換為空字串，不能殘留
}
```

（`indexPage` 現簽名只吃 sessionCookie 一參；`readerContext.resolve(null)` mock 成 `Optional.empty()`。）

- [ ] **Step 2: 跑測試確認失敗**

Run: `mvn -q -Dtest=ReaderIndexPageTest test`
Expected: FAIL

- [ ] **Step 3: 實作**

`index.html` 在 `supporting-links` 區塊之前插入一行 `<!--SURVEY_LIST-->`。

`ReaderAuthController`：建構子注入 `FormSchemaService formSchemaService`（欄位加中文註解），`indexPage` 的 render Map 改為可變 Map 並加入：

```java
vars.put("<!--SURVEY_LIST-->", renderSurveyList());
```

```java
/** 首頁問卷列表（A4）：列出後台勾選曝光的問卷；無任何曝光問卷時回空字串，整個區塊不顯示 */
private String renderSurveyList() {
    List<FormSchemaService.HomepageForm> forms = formSchemaService.listHomepageForms();
    if (forms.isEmpty()) {
        return "";
    }
    StringBuilder sb = new StringBuilder("<div class=\"card\"><h2 class=\"section-title\">問卷調查</h2><ul class=\"survey-list\">");
    for (FormSchemaService.HomepageForm form : forms) {
        sb.append("<li><a href=\"/r/survey/").append(HtmlTemplate.escapeHtml(form.key()))
          .append("\">").append(HtmlTemplate.escapeHtml(form.title())).append("</a></li>");
    }
    return sb.append("</ul></div>").toString();
}
```

`reader.css` 附加：

```css
/* 首頁問卷列表（A4） */
.survey-list { margin:0; padding-left:20px; }
.survey-list li { margin:6px 0; }
```

- [ ] **Step 4: 跑測試確認通過**

Run: `mvn -q -Dtest=ReaderIndexPageTest test`
Expected: PASS

- [ ] **Step 5: 跑導覽列守衛測試**

Run: `mvn -q -Dtest=ReaderNavGuardTest test`
Expected: PASS（問卷連結在 `<nav>` 之外、`/r/survey/` 不在守衛字串清單，不應觸發）

- [ ] **Step 6: Commit**

```bash
git add survey-backend/src/main/resources/templates/reader/index.html survey-backend/src/main/java/world/springai/survey/reader/ReaderAuthController.java survey-backend/src/main/resources/static/reader/reader.css survey-backend/src/test/java/world/springai/survey/reader/ReaderIndexPageTest.java
git commit -m "feat(reader): 首頁問卷列表——僅列出後台勾選曝光的問卷（Task 3）"
```

---

### Task 4: 讀者首頁身分區（A3）

**Files:**
- Modify: `survey-backend/src/main/resources/templates/reader/index.html`（訂閱卡 26-34 行、內聯 script 54-63 行）
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/ReaderAuthController.java`
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReaderIndexPageTest.java`（擴充）
- Modify: `survey-backend/src/main/resources/static/reader/reader.css`（`.identity-line`）

**Interfaces:**
- Consumes: `readerContext.resolve(sessionCookie)` → `Optional<ReaderContext.Current>`；`Current.reader().getEmail()`
- Produces: `index.html` 訂閱卡內容改為佔位符 `<!--SUBSCRIBE_BLOCK-->`

- [ ] **Step 1: 寫失敗測試（加入 ReaderIndexPageTest）**

```java
/** 已登入：訂閱表單換成「已訂閱：email」，email 經跳脫；不出現 subscribe-form */
@Test
void loggedInReaderSeesIdentityInsteadOfSubscribeForm() {
    Reader reader = mock(Reader.class);
    when(reader.getEmail()).thenReturn("a<b>@example.com");
    when(readerContext.resolve("cookie")).thenReturn(
        Optional.of(new ReaderContext.Current(reader, true)));
    String html = controller.indexPage("cookie").getBody();
    assertTrue(html.contains("已訂閱："));
    assertTrue(html.contains("a&lt;b&gt;@example.com"));
    assertFalse(html.contains("id=\"subscribe-form\""));
}

/** 未登入：維持現況——email 輸入框 + 訂閱按鈕 */
@Test
void anonymousReaderSeesSubscribeForm() {
    when(readerContext.resolve(null)).thenReturn(Optional.empty());
    String html = controller.indexPage(null).getBody();
    assertTrue(html.contains("id=\"subscribe-form\""));
    assertFalse(html.contains("已訂閱："));
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `mvn -q -Dtest=ReaderIndexPageTest test`
Expected: 新 2 案 FAIL

- [ ] **Step 3: 實作**

`index.html` 把訂閱卡的可變部分（`<p class="muted">…` 到 `<div class="msg" id="msg"></div>`，即現行 28-33 行）整段換成 `<!--SUBSCRIBE_BLOCK-->`（`<h2 class="section-title">訂閱電子報</h2>` 留在模板）。並把內聯 script 的表單綁定包上存在檢查：

```js
const form = document.getElementById('subscribe-form');
const msg = document.getElementById('msg');
// 已登入時 server 端不渲染訂閱表單，此 script 直接不動作
if (form) {
  /* …既有 showMsg 與 submit 監聽整段搬進 if 內，內容不變… */
}
```

`ReaderAuthController`：

```java
/** 未登入時的訂閱表單區塊（自 index.html 原樣搬入；行為由頁內既有 script 驅動） */
private static final String SUBSCRIBE_FORM_HTML = """
    <p class="muted">填入 Email，訂閱立即生效，之後會收到一封歡迎信。</p>
    <form id="subscribe-form" class="form-row">
      <input type="email" id="email" name="email" placeholder="your@email.com" required autocomplete="email">
      <button class="btn" type="submit">訂閱</button>
    </form>
    <div class="msg" id="msg"></div>""";

/** 首頁身分區（A3）：登入顯示「已訂閱：email」（經跳脫），未登入顯示訂閱表單 */
private String renderSubscribeBlock(Optional<ReaderContext.Current> current) {
    return current
        .map(c -> "<p class=\"identity-line\">已訂閱：<strong>"
            + HtmlTemplate.escapeHtml(c.reader().getEmail()) + "</strong></p>")
        .orElse(SUBSCRIBE_FORM_HTML);
}
```

`indexPage` 內把 `resolve` 結果存變數重用（登入判斷 + 身分區），render Map 加 `vars.put("<!--SUBSCRIBE_BLOCK-->", renderSubscribeBlock(current))`。

`reader.css` 附加：

```css
/* 首頁身分區（A3）：登入後取代訂閱表單的識別列 */
.identity-line { margin:4px 0 0; font-size:1.05rem; }
.identity-line strong { color:var(--accent-deep); word-break:break-all; }
```

- [ ] **Step 4: 跑測試確認通過**

Run: `mvn -q -Dtest=ReaderIndexPageTest test`
Expected: PASS（4 tests）

- [ ] **Step 5: 跑守衛測試 + 相關既有測試**

Run: `mvn -q -Dtest='ReaderNavGuardTest,Reader*' test`
Expected: PASS（email 不在 `<nav>` 內，guard 不受影響）

- [ ] **Step 6: Commit**

```bash
git add survey-backend/src/main/resources/templates/reader/index.html survey-backend/src/main/java/world/springai/survey/reader/ReaderAuthController.java survey-backend/src/main/resources/static/reader/reader.css survey-backend/src/test/java/world/springai/survey/reader/ReaderIndexPageTest.java
git commit -m "feat(reader): 首頁身分區——登入後顯示已訂閱 email 取代訂閱表單（Task 4）"
```

---

### Task 5: 讀者站暗色模式 CSS + 主題啟動腳本（A1）

**Files:**
- Modify: `survey-backend/src/main/resources/static/reader/reader.css`
- Modify: `survey-backend/src/main/resources/templates/reader/*.html`（**全部**讀者模板，用 Glob 列出）
- Test: `survey-backend/src/test/java/world/springai/survey/ReaderThemeGuardTest.java`（新建）

**Interfaces:**
- Consumes: `reader.css` 既有 `:root` token（第 2-13 行）
- Produces: `localStorage` key `reader-theme`（值 `'dark'`/`'light'`，Task 6 的切換鈕與 verify 腳本依賴）；`documentElement` 的 `data-theme` 屬性；主題啟動腳本識別字串 `data-theme',localStorage.getItem('reader-theme')`（guard 測試比對用）

- [ ] **Step 1: 寫失敗的守衛測試**

新建 `ReaderThemeGuardTest`（比照 `ReaderNavGuardTest` 的檔案掃描寫法——走訪 `src/main/resources/templates/reader/*.html`）：

```java
/**
 * 主題啟動守衛：每個讀者模板都必須在樣式表載入前內聯主題啟動腳本，
 * 否則暗色偏好者進站會閃白（新增模板時此測試會自動把規範帶到新頁）。
 */
@Test
void everyReaderTemplateBootsThemeBeforeStylesheet() throws IOException {
    Path dir = Path.of("src/main/resources/templates/reader");
    try (Stream<Path> files = Files.list(dir)) {
        List<String> violations = files
            .filter(p -> p.toString().endsWith(".html"))
            .filter(p -> {
                String html = read(p);
                int boot = html.indexOf("localStorage.getItem('reader-theme')");
                int css = html.indexOf("reader.css");
                return boot < 0 || (css >= 0 && boot > css);
            })
            .map(p -> p.getFileName().toString())
            .toList();
        assertTrue(violations.isEmpty(), "缺少主題啟動腳本或位置在樣式表之後：" + violations);
    }
}
```

（`read(p)` 為 `Files.readString` 小 helper，照 `ReaderNavGuardTest` 的寫法。）

- [ ] **Step 2: 跑測試確認失敗**

Run: `mvn -q -Dtest=ReaderThemeGuardTest test`
Expected: FAIL，列出所有模板

- [ ] **Step 3: 每個讀者模板 `<head>` 內、`<link rel="stylesheet" href="/r/reader.css">` 之前，插入**

```html
<script>
// 主題啟動：在 CSS 套用前寫入 data-theme，避免暗色偏好者進站閃白（偏好存 localStorage，首訪跟隨系統）
document.documentElement.setAttribute('data-theme',
  localStorage.getItem('reader-theme') ||
  (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'));
</script>
```

用 `Glob templates/reader/*.html` 列出全部檔案逐一插入（含 login.html、not-found.html、survey.html 等靜態頁）。

- [ ] **Step 4: reader.css 附加暗色區塊（檔案最末）**

```css
/* ========== 暗色模式（A1）：所有覆寫 scoped 於 [data-theme="dark"]，亮色規則零改動 ========== */
:root[data-theme="dark"]{
  --bg:#141817; --fg:#e4ebe9; --muted:#a3b4af; --muted-2:#87988f;
  --surface:#1c2321; --surface-2:#232c29; --surface-3:#2a3431;
  --border:#32403c; --border-strong:#4c5f5a;
  /* --accent 維持 #087f72 不覆寫：它作為按鈕底色搭配 color:#fff，變亮會摔破對比 */
  --accent-deep:#5ec9bb;        /* 連結色：暗底需要更亮的 accent */
  --accent-soft:#173430;
  --accent-2-soft:#3a2f14; --accent-2-deep:#e8b158;
  --err:#f28b8b; --ok:#4ade80;
  --shadow-sm:0 8px 24px rgb(0 0 0 / .4);
  --shadow-nav:0 12px 34px rgb(0 0 0 / .5);
}
/* 硬編亮底的暗色補丁（比照 admin.html 的做法；選擇器對應 reader.css 亮色原行號） */
:root[data-theme="dark"] .site-head{background:rgb(28 35 33 / .88);border-bottom-color:rgb(50 64 60 / .82)}
:root[data-theme="dark"] .site-head nav{background:rgb(35 44 41 / .82);border-color:rgb(50 64 60 / .88)}
:root[data-theme="dark"] .site-head nav a:hover{background:rgb(42 52 49 / .82)}
:root[data-theme="dark"] .site-head a.brand::before{background:linear-gradient(145deg,#0b7264,#08564e)}
:root[data-theme="dark"] .subscriber-count{background:#173430;border-color:#2a5a51;color:#8fd8cb}
:root[data-theme="dark"] input[type=email],
:root[data-theme="dark"] input[type=text]{background:var(--surface-2);color:var(--fg);border-color:var(--border)}
:root[data-theme="dark"] input::placeholder{color:var(--muted-2)}
:root[data-theme="dark"] .msg.err{background:#3a1f1f}
:root[data-theme="dark"] .article-cover,
:root[data-theme="dark"] .side-thumb,
:root[data-theme="dark"] .share-subscribe-cta{background:linear-gradient(135deg,var(--accent-soft),#2a2a1c)}
:root[data-theme="dark"] .campaign-banner{background:#332b14;border-color:#5c4d1f}
:root[data-theme="dark"] .milestone-track{background:#2a3431}
:root[data-theme="dark"] .milestone-dot{background:#3c4a46;box-shadow:0 0 0 2px #3c4a46}
:root[data-theme="dark"] .promo-form input,
:root[data-theme="dark"] .promo-form select,
:root[data-theme="dark"] .promo-form textarea{background:var(--surface-2);color:var(--fg);border-color:var(--border)}
:root[data-theme="dark"] .promo-cost-bar.lack{background:#3a1f1f}
:root[data-theme="dark"] .promo-status.pending{background:#3a2f14;color:#e8b158}
:root[data-theme="dark"] .promo-status.rejected{background:#3a1f1f}
:root[data-theme="dark"] .promo-status.archived{background:#2a2f2d;color:#9aa8a3}
```

同時修復第 372 行的死引用（唯一允許的亮色改動）：`var(--shadow-soft)` → `var(--shadow-sm)`，附註解 `/* 修復死引用：--shadow-soft 從未定義，box-shadow 原本靜默失效 */`。

**注意**：上列色值是起點，最終值以 Task 6 的 WCAG 腳本斷言為準——若腳本量出不足 4.5:1，調整變數值直到腳本綠，不得調降腳本門檻。

- [ ] **Step 5: 跑守衛測試確認通過**

Run: `mvn -q -Dtest='ReaderThemeGuardTest,ReaderNavGuardTest' test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add survey-backend/src/main/resources/static/reader/reader.css survey-backend/src/main/resources/templates/reader survey-backend/src/test/java/world/springai/survey/ReaderThemeGuardTest.java
git commit -m "feat(reader): 暗色模式 token 覆寫與主題啟動腳本，亮色規則零改動（Task 5）"
```

---

### Task 6: 右上工具列（日夜切換＋登出）＋ WCAG 驗證腳本（A2 + A1 驗收）

**Files:**
- Modify: `survey-backend/src/main/resources/static/reader/reader-nav.js`
- Modify: `survey-backend/src/main/resources/static/reader/reader.css`（`.head-tools` 樣式）
- Create: `survey-backend/scripts/verify-reader-theme.mjs`

**Interfaces:**
- Consumes: Task 5 的 `data-theme` 屬性與 `reader-theme` localStorage key；既有 `POST /api/reader/logout`（204 + 清 cookie）；`ReaderNav` 登入時輸出的 `<a href="/r/me">`（登入判定依據）
- Produces: 每頁右上出現 `#reader-theme-btn`（恆顯示）與 `#reader-logout-btn`（僅登入時）

- [ ] **Step 1: reader-nav.js 加入工具列**

在檔案的初始化流程（現有 DOMContentLoaded 或立即執行段）加入呼叫 `mountHeadTools()`：

```js
/**
 * 右上工具列（A2）：日夜切換恆顯示；登出僅在登入時顯示。
 * 登入判定：ReaderNav 只在登入時輸出「我的帳戶」連結——這是 server 對 session
 * 的真實判斷，前端不需要（也拿不到，cookie 是 httpOnly）另外的登入 API。
 */
function mountHeadTools() {
  const head = document.querySelector('.site-head-inner');
  if (!head || head.querySelector('.head-tools')) return;
  const tools = document.createElement('div');
  tools.className = 'head-tools';

  const themeBtn = document.createElement('button');
  themeBtn.type = 'button';
  themeBtn.id = 'reader-theme-btn';
  themeBtn.className = 'head-tool-btn';
  themeBtn.title = '切換日夜模式';
  themeBtn.setAttribute('aria-label', '切換日夜模式');
  themeBtn.textContent = document.documentElement.getAttribute('data-theme') === 'dark' ? '🌙' : '☀';
  themeBtn.addEventListener('click', () => {
    const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('reader-theme', next);
    themeBtn.textContent = next === 'dark' ? '🌙' : '☀';
  });
  tools.append(themeBtn);

  if (document.querySelector('nav a[href="/r/me"]')) {
    const logoutBtn = document.createElement('button');
    logoutBtn.type = 'button';
    logoutBtn.id = 'reader-logout-btn';
    logoutBtn.className = 'head-tool-btn';
    logoutBtn.textContent = '登出';
    logoutBtn.addEventListener('click', async () => {
      // 既有登出端點會清除 reader_session cookie；成功後回首頁重載
      await fetch('/api/reader/logout', { method: 'POST' });
      location.href = '/r/';
    });
    tools.append(logoutBtn);
  }
  head.append(tools);
}
```

`reader.css` 附加（亮色通用樣式，非暗色補丁，因是新元件不受「零改動」限制）：

```css
/* 右上工具列（A2） */
.head-tools { display:flex; gap:8px; align-items:center; margin-left:auto; }
.head-tool-btn { border:1px solid var(--border); background:var(--surface); color:var(--fg);
  border-radius:var(--r-pill); padding:6px 12px; cursor:pointer; font-size:.85rem; }
.head-tool-btn:hover { border-color:var(--border-strong); }
```

（`.site-head-inner` 若已是 flex 且 nav 佔滿，實際加上後檢查排版；必要時對 `.site-head-inner` 加 `gap`，不動既有規則的其他屬性。）

- [ ] **Step 2: 寫 verify-reader-theme.mjs**

整體結構、`loadPlaywright()`、`ok/fail` 計數、`okContrast()`（WCAG 相對亮度公式）、`ensureTheme()` 全部照抄 `verify-admin-toolbar-theme.mjs`（78-135 行），改動點：

- `BASE = process.env.READER_BASE || 'http://127.0.0.1:8080'`，進入頁 `/r/`
- 無需登入即可測（首頁 + 文章頁匿名可看）；登出鈕的「登入才顯示」用 route 攔截驗證：對 `/r/` 的回應以 `page.route` 改寫 `<!--NAV_LINKS-->` 已渲染結果不可行——改為直接斷言「匿名狀態下 `#reader-logout-btn` 不存在」，登入態的顯示邏輯以 DOM 注入驗證：`page.evaluate` 在 nav 內臨時塞 `<a href="/r/me">`，重呼叫 `mountHeadTools` 不可重入（有 early return），故改為 reload 前用 `page.route` 攔 `/r/` 回應、把 `href="/r/login"` 字串替換為 `href="/r/me"` 模擬登入版 nav，再斷言登出鈕出現
- 對比斷言（亮色與暗色**各跑一輪**，全部 4.5:1 門檻，不用大字豁免）：
  - `body` 主文字 vs `--bg`
  - `.page-intro`（muted 文字）vs `--bg`
  - `#email` 輸入框文字 vs 其 background（暗色下必須是 surface-2 而非 #fff）
  - `.btn`（訂閱鈕）文字 vs 背景
  - `.site-head nav a` vs nav 背景
  - `.head-tool-btn` vs 其背景
  - 文章頁 `/r/news/{slug}`：`.card` 內文字 vs card 背景（slug 用環境變數 `ARTICLE_SLUG` 或從 `/r/archive` 頁自動抓第一篇連結）
- 主題切換行為斷言：點 `#reader-theme-btn` → `data-theme` 翻轉、localStorage `reader-theme` 更新；reload 後保持
- 匿名斷言：`#reader-logout-btn` 為 null；模擬登入後為非 null，點擊後攔截確認發出 `POST /api/reader/logout`
- 腳本頭部中文註解：用途、前置條件（後端已啟動）、重跑安全性（會清 localStorage）

- [ ] **Step 3: 本機起後端跑腳本**

```powershell
# 後端已依 Global Constraints #3 啟動後：
node survey-backend/scripts/verify-reader-theme.mjs
```

Expected: 全部斷言 PASS（exit 0）。對比不足時回 Task 5 的暗色變數調值重跑，**不得調降門檻**。

- [ ] **Step 4: 跑既有 reader 相關腳本確認沒弄壞**

```powershell
node survey-backend/scripts/verify-reader-flow.mjs
```

Expected: PASS（工具列注入不影響既有流程）

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/resources/static/reader/reader-nav.js survey-backend/src/main/resources/static/reader/reader.css survey-backend/scripts/verify-reader-theme.mjs
git commit -m "feat(reader): 右上工具列（日夜切換＋登出）與 WCAG 對比驗證腳本（Task 6）"
```

---

### Task 7: 文章側邊欄投票統計（B1）

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/newsletter/SurveyBlockRenderer.java`（新增 `embeddedFormKeys`）
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/ReaderPageController.java`（`article()` 約 170-245 行、`renderSidebar` 約 459-542 行）
- Modify: `survey-backend/src/main/resources/static/reader/reader.css`（`.side-votes` 樣式）
- Test: `survey-backend/src/test/java/world/springai/survey/newsletter/SurveyBlockRendererFormKeysTest.java`（新建）＋ `ReaderPageController` 既有測試檔擴充（先 Glob `src/test/java/**/reader/ReaderPage*` 找到既有測試檔沿用其建法；若無則新建 standalone 測試）

**Interfaces:**
- Consumes: `SurveyBlockRenderer.MARKER`（`<!--survey:([a-z0-9-]+)-->`，package 內可見）；`SurveyVoteStatsService.voteStats(String formKey)` → `Map{options:[{value,named,anon}], totalVotes, totalNamed, byCampaign}`；`FormSchemaService.emailVoteQuestion(String)` → `Optional<EmailVoteQuestion(formKey,title,fieldKey,label,options)>`
- Produces: `public List<String> embeddedFormKeys(String html)`（`SurveyBlockRenderer`）；`renderSidebar` 簽名改為 `renderSidebar(Campaign campaign, List<String> embeddedFormKeys)`

- [ ] **Step 1: 寫失敗測試——embeddedFormKeys**

```java
/** 依內文出現順序列出全部內嵌問卷 key；重複標記去重保留首次；無標記回空 */
@Test
void listsMarkersInDocumentOrder() {
    SurveyBlockRenderer renderer = /* 沿用 SurveyBlockRenderer 既有測試檔的建構方式 */;
    String html = "<p>a</p><!--survey:form-b--><p>b</p><!--survey:form-a--><!--survey:form-b-->";
    assertEquals(List.of("form-b", "form-a"), renderer.embeddedFormKeys(html));
    assertEquals(List.of(), renderer.embeddedFormKeys("<p>沒有標記</p>"));
    assertEquals(List.of(), renderer.embeddedFormKeys(null));
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `mvn -q -Dtest=SurveyBlockRendererFormKeysTest test`
Expected: FAIL（方法不存在）

- [ ] **Step 3: 實作 embeddedFormKeys**

```java
/** 依內文出現順序列出所有內嵌問卷標記的 formKey（去重、保留首次出現順序）；供文章側邊欄投票統計（B1）決定要列哪些卡 */
public List<String> embeddedFormKeys(String html) {
    if (html == null || html.isBlank()) {
        return List.of();
    }
    LinkedHashSet<String> keys = new LinkedHashSet<>();
    Matcher matcher = MARKER.matcher(html);
    while (matcher.find()) {
        keys.add(matcher.group(1));
    }
    return List.copyOf(keys);
}
```

Run: `mvn -q -Dtest=SurveyBlockRendererFormKeysTest test` → PASS

- [ ] **Step 4: 寫失敗測試——側邊欄投票卡**

在 `ReaderPageController` 測試（沿用既有測試檔的 mock 佈線）加：

```java
/** 內文含 survey 標記時，側邊欄出現投票卡：題目、各選項票數與百分比、共 N 人參與；不含轉換率（D4） */
@Test
void sidebarShowsVoteStatsForEmbeddedSurveys() {
    when(surveyVoteStatsService.voteStats("vote-key")).thenReturn(Map.of(
        "options", List.of(
            Map.of("value", "選項Ａ", "named", 6L, "anon", 2L),
            Map.of("value", "選項Ｂ", "named", 3L, "anon", 3L)),
        "totalVotes", 14L, "totalNamed", 9L));
    when(formSchemaService.emailVoteQuestion("vote-key")).thenReturn(Optional.of(
        new FormSchemaService.EmailVoteQuestion("vote-key", "你最想學什麼？", "q1", "題目", List.of())));
    // …安排一篇內文含 <!--survey:vote-key--> 的已發布文章（照既有測試的 campaign 佈線）…
    String html = /* GET /r/news/{slug} 的 body */;
    assertTrue(html.contains("你最想學什麼？"));
    assertTrue(html.contains("選項Ａ"));
    assertTrue(html.contains("8 票"));          // 6+2
    assertTrue(html.contains("57%"));            // 8/14 四捨五入
    assertTrue(html.contains("共 14 人參與"));
    assertFalse(html.contains("轉換率"));        // D4
}

/** 無內嵌問卷的文章：側邊欄維持原樣（無投票卡） */
@Test
void sidebarUnchangedWithoutEmbeddedSurvey() {
    String html = /* 內文無標記的文章頁 body */;
    assertFalse(html.contains("人參與"));
}
```

- [ ] **Step 5: 跑測試確認失敗**，Expected: FAIL

- [ ] **Step 6: 實作**

`ReaderPageController`：

1. 建構子注入 `SurveyVoteStatsService surveyVoteStatsService`（與既有 nullable 協作者同款處理——若既有建構子有 legacy 簡化版，新參數比照 `surveyBlockRenderer` 的 nullable 慣例）。
2. `article()` 內、呼叫 `expandForWeb` **之前**先取 keys：

```java
// 側邊欄投票統計（B1）：標記在 expandForWeb 後會被換成投票卡 HTML，必須先掃
List<String> embeddedFormKeys = surveyBlockRenderer != null
    ? surveyBlockRenderer.embeddedFormKeys(contentHtml) : List.of();
```

3. `renderSidebar(campaign)` 改簽名 `renderSidebar(Campaign campaign, List<String> embeddedFormKeys)`，回傳 `renderVoteStatsCards(embeddedFormKeys) + renderCategoryCard(campaign) + renderRelatedCard(campaign)`（投票卡最前——文章專屬資訊優先於通用分類）。

```java
/**
 * 內嵌問卷投票統計卡（B1）：每份問卷一張卡，依標記在內文出現的順序全部列出（spec §4.1 不截斷）。
 * 顯示各選項票數＋百分比與「共 N 人參與」；不顯示轉換率（D4）。
 * 未設定信中一鍵題（emailVoteQuestion 為空）的 key 跳過——該標記本來就不會被渲染成投票卡。
 */
private String renderVoteStatsCards(List<String> formKeys) {
    if (formKeys.isEmpty() || surveyVoteStatsService == null || formSchemaService == null) {
        return "";
    }
    StringBuilder sb = new StringBuilder();
    for (String formKey : formKeys) {
        Optional<FormSchemaService.EmailVoteQuestion> question = formSchemaService.emailVoteQuestion(formKey);
        if (question.isEmpty()) {
            continue;
        }
        Map<String, Object> stats = surveyVoteStatsService.voteStats(formKey);
        long total = ((Number) stats.getOrDefault("totalVotes", 0L)).longValue();
        sb.append("<section class=\"side-card\"><h2 class=\"side-title\">")
          .append(HtmlTemplate.escapeHtml(question.get().title())).append("</h2>");
        if (total == 0) {
            sb.append("<p class=\"side-note\">尚無人投票</p></section>");
            continue;
        }
        sb.append("<ul class=\"side-votes\">");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options = (List<Map<String, Object>>) stats.getOrDefault("options", List.of());
        for (Map<String, Object> option : options) {
            long votes = ((Number) option.getOrDefault("named", 0L)).longValue()
                       + ((Number) option.getOrDefault("anon", 0L)).longValue();
            long percent = Math.round(votes * 100.0 / total);
            sb.append("<li><span class=\"vote-label\">")
              .append(HtmlTemplate.escapeHtml(String.valueOf(option.get("value"))))
              .append("</span><span class=\"vote-bar\"><i style=\"width:").append(percent)
              .append("%\"></i></span><span class=\"vote-count\">").append(votes)
              .append(" 票 · ").append(percent).append("%</span></li>");
        }
        sb.append("</ul><p class=\"side-note\">共 ").append(total).append(" 人參與</p></section>");
    }
    return sb.toString();
}
```

（百分比為 long 計算、選項文字經 `escapeHtml`；`width` 只插入數字，無注入面。）

`reader.css` 附加：

```css
/* 文章側邊欄投票統計卡（B1） */
.side-votes { list-style:none; margin:0; padding:0; }
.side-votes li { display:grid; grid-template-columns:1fr auto; gap:2px 8px; margin:8px 0; font-size:.85rem; }
.side-votes .vote-bar { grid-column:1 / -1; height:6px; border-radius:var(--r-pill); background:var(--surface-3); overflow:hidden; }
.side-votes .vote-bar i { display:block; height:100%; background:var(--accent); }
.side-votes .vote-count { color:var(--muted); }
.side-note { margin:8px 0 0; color:var(--muted); font-size:.8rem; }
```

- [ ] **Step 7: 跑測試確認通過**

Run: `mvn -q -Dtest='SurveyBlockRendererFormKeysTest,ReaderPage*' test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/newsletter/SurveyBlockRenderer.java survey-backend/src/main/java/world/springai/survey/reader/ReaderPageController.java survey-backend/src/main/resources/static/reader/reader.css survey-backend/src/test/java/world/springai/survey/newsletter/SurveyBlockRendererFormKeysTest.java
# 加上實際修改的 ReaderPageController 測試檔路徑
git commit -m "feat(reader): 文章側邊欄內嵌問卷投票統計卡（Task 7 / B1）"
```

---

### Task 8: 寄券後端——sent-map 端點與單筆寄送模式（B2 後端）

**Files:**
- Modify: `survey-backend/src/main/java/world/springai/survey/mail/EmailLogRepository.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/coupon/CouponRecipientService.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/coupon/CouponSendService.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/coupon/AdminCouponController.java`
- Test: `survey-backend/src/test/java/world/springai/survey/coupon/CouponSendServiceTest.java`、`CouponRecipientServiceTest.java`、`AdminCouponControllerTest.java`（皆為擴充既有檔）

**Interfaces:**
- Consumes: `EmailLog(recipient, subject, type, providerMessageId, status, error)`；`coupon:` type 前綴慣例
- Produces:
  - `EmailLogRepository`: `List<EmailLog> findByTypeStartingWithAndStatus(String typePrefix, String status);`
  - `CouponRecipientService`: `public Map<String, List<Long>> sentCouponsByEmail()`（email 小寫正規化 → 已成功收過的活動 id 清單）
  - `CouponSendService`: `public SendResult send(long campaignId, List<String> emails, Integer limit, boolean single)`；既有三參 `send(...)` 保留為委派 `single=false` 的 overload
  - `AdminCouponController`: `SendRequest` 擴為 `record SendRequest(List<String> emails, Integer limit, Boolean single)`；新端點 `GET /api/admin/coupons/sent-map` → `Map<String, List<Long>>`

- [ ] **Step 1: 寫失敗測試——D6 單筆寄送語意（CouponSendServiceTest 擴充）**

```java
/** D6：單筆寄送成功後 status 維持 DRAFT、sent_count 累加、sent_at 記錄本次時間 */
@Test
void singleSendKeepsDraftStatus() {
    // 沿用檔內既有的 campaign/mock 佈線（一人名單、寄送成功路徑）
    CouponSendService.SendResult result = service.send(1L, List.of("one@example.com"), 1, true);
    assertEquals(1, result.sent());
    assertEquals(CouponCampaign.STATUS_DRAFT, campaign.getStatus());   // 不翻 SENT
    assertNotNull(campaign.getSentAt());
    assertEquals(1, campaign.getSentCount());
}

/** D6 對照組：批次寄送（single=false）行為不變——status 翻 SENT、sentAt 首次寫入後不覆蓋 */
@Test
void batchSendStillMarksSent() {
    CouponSendService.SendResult result = service.send(1L, List.of("one@example.com"), 1, false);
    assertEquals(CouponCampaign.STATUS_SENT, campaign.getStatus());
}

/** 單筆重複寄送：同一人同一張券第二次呼叫被已寄過濾擋下（400），email_log 不會多一列 sent */
@Test
void singleResendIsRejectedByIdempotencyFilter() {
    when(emailLogRepository.findByTypeAndStatus("coupon:1", "sent"))
        .thenReturn(List.of(new EmailLog("one@example.com", "s", "coupon:1", "id", "sent", null)));
    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.send(1L, List.of("one@example.com"), 1, true));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    verify(mailSender, never()).send(any(), any(), any());
}
```

- [ ] **Step 2: 寫失敗測試——sentCouponsByEmail（CouponRecipientServiceTest 擴充）**

```java
/** 已寄總覽：跨活動彙整每個 email（小寫正規化）收過哪些券 */
@Test
void sentCouponsByEmailGroupsAcrossCampaigns() {
    when(emailLogRepository.findByTypeStartingWithAndStatus("coupon:", "sent")).thenReturn(List.of(
        new EmailLog("One@Example.com", "s", "coupon:1", "a", "sent", null),
        new EmailLog("one@example.com", "s", "coupon:2", "b", "sent", null),
        new EmailLog("two@example.com", "s", "coupon:1", "c", "sent", null)));
    Map<String, List<Long>> map = service.sentCouponsByEmail();
    assertEquals(List.of(1L, 2L), map.get("one@example.com"));
    assertEquals(List.of(1L), map.get("two@example.com"));
}
```

- [ ] **Step 3: 寫失敗測試——controller（AdminCouponControllerTest 擴充）**

```java
/** sent-map 端點：guard 先行，回傳 recipientService 的彙整結果 */
@Test
void sentMapEndpointReturnsAggregation() throws Exception {
    when(recipientService.sentCouponsByEmail()).thenReturn(Map.of("one@example.com", List.of(1L)));
    mockMvc.perform(get("/api/admin/coupons/sent-map").header("X-Admin-Key", "test-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$['one@example.com'][0]").value(1));
}

/** send 端點透傳 single 旗標；未帶時為 false（既有批次行為） */
@Test
void sendEndpointPassesSingleFlag() throws Exception {
    mockMvc.perform(post("/api/admin/coupons/1/send").header("X-Admin-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"emails\":[\"one@example.com\"],\"limit\":1,\"single\":true}"))
        .andExpect(status().isOk());
    verify(sendService).send(1L, List.of("one@example.com"), 1, true);
}
```

- [ ] **Step 4: 跑三個測試類確認失敗**

Run: `mvn -q -Dtest='CouponSendServiceTest,CouponRecipientServiceTest,AdminCouponControllerTest' test`
Expected: 新增案 FAIL（編譯錯誤或 404）

- [ ] **Step 5: 實作**

`EmailLogRepository` 加：

```java
/** 依 type 前綴＋狀態查詢：供優惠券「已寄總覽」以 coupon: 前綴一次撈出所有活動的寄送記錄 */
List<EmailLog> findByTypeStartingWithAndStatus(String typePrefix, String status);
```

`CouponRecipientService` 加：

```java
/**
 * 已寄總覽（B2）：彙整 email_log 中所有 type=coupon:{id}、status=sent 的記錄，
 * 回傳「email（小寫正規化）→ 已成功收過的活動 id 清單（依 id 升冪）」。
 * 資料本就存在 email_log，不需新表（spec §4.2）。
 */
public Map<String, List<Long>> sentCouponsByEmail() {
    Map<String, java.util.SortedSet<Long>> grouped = new java.util.HashMap<>();
    for (EmailLog log : emailLogRepository.findByTypeStartingWithAndStatus("coupon:", "sent")) {
        long campaignId = Long.parseLong(log.getType().substring("coupon:".length()));
        grouped.computeIfAbsent(normalize(log.getRecipient()), k -> new java.util.TreeSet<>()).add(campaignId);
    }
    Map<String, List<Long>> result = new java.util.HashMap<>();
    grouped.forEach((email, ids) -> result.put(email, List.copyOf(ids)));
    return result;
}
```

`CouponSendService.send`：四參版本為主體，三參 overload 委派：

```java
/** 既有批次路徑的相容 overload：single=false，行為與改動前完全一致 */
public SendResult send(long campaignId, List<String> emails, Integer limit) {
    return send(campaignId, emails, limit, false);
}

public SendResult send(long campaignId, List<String> emails, Integer limit, boolean single) {
```

寄送成功後的狀態段改為：

```java
if (sent > 0) {
    if (single) {
        // 單筆寄送（D6）：逐人發放是持續動作，不把活動翻成 SENT；sent_at 記錄最後一次寄送時間
        campaign.setSentAt(OffsetDateTime.now());
    } else {
        // 批次寄送：沿用原語意——sentAt 只在首次成功寄送時寫入，狀態翻 SENT
        if (campaign.getSentAt() == null) {
            campaign.setSentAt(OffsetDateTime.now());
        }
        campaign.setStatus(CouponCampaign.STATUS_SENT);
    }
    campaign.setSentCount(campaign.getSentCount() + sent);
    campaignRepository.save(campaign);
}
```

（並更新類別 Javadoc 補述 single 模式。）

`AdminCouponController`：`SendRequest` 擴為 `record SendRequest(List<String> emails, Integer limit, Boolean single)`；send 端點改呼叫 `sendService.send(id, request.emails(), request.limit(), Boolean.TRUE.equals(request.single()))`；新增：

```java
/** 已寄券總覽（B2）：email → 已成功收過的優惠券活動 id 清單，供原始資料表逐列顯示與下拉停用判斷 */
@GetMapping("/api/admin/coupons/sent-map")
public Map<String, List<Long>> sentMap(
        @RequestHeader(value = KEY_HEADER, required = false) String key) {
    guard.verify(key);
    return recipientService.sentCouponsByEmail();
}
```

- [ ] **Step 6: 跑測試確認通過**

Run: `mvn -q -Dtest='Coupon*,AdminCoupon*' test`
Expected: PASS（含既有案全綠——三參 overload 保證既有呼叫點與測試不變）

- [ ] **Step 7: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/mail/EmailLogRepository.java survey-backend/src/main/java/world/springai/survey/coupon/CouponRecipientService.java survey-backend/src/main/java/world/springai/survey/coupon/CouponSendService.java survey-backend/src/main/java/world/springai/survey/coupon/AdminCouponController.java survey-backend/src/test/java/world/springai/survey/coupon/CouponSendServiceTest.java survey-backend/src/test/java/world/springai/survey/coupon/CouponRecipientServiceTest.java survey-backend/src/test/java/world/springai/survey/coupon/AdminCouponControllerTest.java
git commit -m "feat(coupon): 單筆寄送模式（D6）與已寄券總覽端點（Task 8 / B2 後端）"
```

---

### Task 9: 原始資料逐列寄券 UI（B2 前端）

**Files:**
- Modify: `survey-backend/src/main/resources/static/admin.html`（RAW_COLUMNS 767-773 行、`buildRawHeader` 1365 行、`renderRawTable` 1368-1372 行、`loadAnalytics` 1330-1334 行；dialog 比照 `#reader-detail-dialog` 528-539 行）
- Create: `survey-backend/scripts/verify-raw-row-coupon.mjs`

**Interfaces:**
- Consumes: Task 8 的 `GET /api/admin/coupons/sent-map` 與 `POST /api/admin/coupons/{id}/send`（body 含 `single:true`）；既有 `GET /api/admin/coupons`（`CouponCampaign` 全欄，含 `expiresAt`（`YYYY-MM-DD` 或 null）、`couponCode`、`courseName`、`createdAt`）；原始資料列的 `row.email`
- Produces: 原始資料表每列的操作欄（寄券鈕＋已寄券標示）、`#coupon-send-dialog`

- [ ] **Step 1: admin.html 加入 dialog 標記**（放在 `#reader-detail-dialog` 之後）

```html
<dialog id="coupon-send-dialog" class="reader-detail-dialog" style="width:min(480px,94vw)">
  <h3 style="margin-top:0">寄送優惠券</h3>
  <p>收件人：<strong id="coupon-send-dialog-email"></strong></p>
  <label><span class="hint">選擇券別（僅列未過期；已寄過的停用）</span>
    <select id="coupon-send-dialog-select" style="width:100%"></select></label>
  <div class="msg" id="coupon-send-dialog-msg"></div>
  <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:14px">
    <button class="btn ghost" id="coupon-send-dialog-cancel" type="button">取消</button>
    <button class="btn" id="coupon-send-dialog-confirm" type="button">確認寄出</button>
  </div>
</dialog>
```

- [ ] **Step 2: JS——狀態、載入、渲染**

模組層新增（放 831-843 行既有 coupon 狀態變數附近）：

```js
let couponSentMap={};        // email(小寫) → 已收過的活動 id 陣列（/api/admin/coupons/sent-map）
let couponCampaignsAll=[];   // 全部優惠券活動（/api/admin/coupons），逐列寄券下拉用
```

`loadAnalytics()` 載入 `surveyRows` 之後並行補載兩份資料（失敗不擋原始資料表——包 try-catch，錯誤顯示於 `#raw-count` 旁即可）：

```js
// 逐列寄券（B2）所需資料：券活動清單與已寄總覽；失敗時操作欄顯示「載入失敗」但不影響原始資料
try{[couponCampaignsAll,couponSentMap]=await Promise.all([api('/api/admin/coupons'),api('/api/admin/coupons/sent-map')]);}
catch(e){couponCampaignsAll=[];couponSentMap={};}
```

`buildRawHeader()` 尾端加 `操作` th；`renderRawTable()` 每列尾端加操作欄：

```js
// 操作欄（B2）：寄券按鈕＋已寄券別標示
const actionTd=document.createElement('td');
const sendBtn=document.createElement('button');
sendBtn.className='btn ghost';sendBtn.type='button';sendBtn.textContent='寄券';
sendBtn.onclick=()=>openCouponSendDialog(row.email);
actionTd.append(sendBtn);
const sentLabel=sentCouponLabel(row.email);
if(sentLabel){const span=document.createElement('span');span.className='hint';span.style.display='block';span.textContent=sentLabel;actionTd.append(span);}
tr.append(actionTd);
```

Helper 與 dialog 邏輯：

```js
/** 該 email 已收過的券別標示文字：以優惠碼呈現（找不到活動時退回 #id） */
function sentCouponLabel(email){
  const ids=couponSentMap[(email||'').trim().toLowerCase()]||[];
  if(!ids.length)return '';
  return '已寄：'+ids.map(id=>{const c=couponCampaignsAll.find(x=>x.id===id);return c?c.couponCode:('#'+id);}).join('、');
}
/** 開啟逐列寄券視窗（D5）：下拉僅列未過期券（建立時間新到舊）；已寄過的標示並停用 */
function openCouponSendDialog(email){
  const dialog=$('#coupon-send-dialog');
  $('#coupon-send-dialog-email').textContent=email;
  const select=$('#coupon-send-dialog-select');select.replaceChildren();
  const today=new Date().toISOString().slice(0,10);
  const sent=couponSentMap[(email||'').trim().toLowerCase()]||[];
  const candidates=couponCampaignsAll
    .filter(c=>!c.expiresAt||c.expiresAt>=today)          // 過期券不列（spec §4.2）
    .sort((a,b)=>String(b.createdAt).localeCompare(String(a.createdAt)));
  candidates.forEach(c=>{
    const opt=new Option(`${c.courseName}（${c.couponCode}）${sent.includes(c.id)?'（已寄過）':''}`,c.id);
    opt.disabled=sent.includes(c.id);
    select.add(opt);
  });
  $('#coupon-send-dialog-msg').textContent=candidates.length?'':'沒有可寄送的未過期優惠券';
  dialog.dataset.email=email;
  dialog.showModal();
}
/** 確認寄出：single:true 走單筆語意（D6），成功後就地更新已寄快取與表格 */
async function confirmCouponSend(){
  const dialog=$('#coupon-send-dialog');
  const email=dialog.dataset.email;
  const id=Number($('#coupon-send-dialog-select').value);
  if(!id)return;
  const msgBox=$('#coupon-send-dialog-msg');
  try{
    const result=await api(`/api/admin/coupons/${id}/send`,{method:'POST',body:JSON.stringify({emails:[email],limit:1,single:true})});
    if(result.sent>0){
      const key=email.trim().toLowerCase();
      (couponSentMap[key]=couponSentMap[key]||[]).push(id);
      renderRawTable();
      dialog.close();
    }else{
      msgBox.textContent=`未寄出（skipped ${result.skipped}、failed ${result.failed}）`;
    }
  }catch(e){msgBox.textContent='寄送失敗：'+e.message;}  // 含「不在命中名單」的 400 訊息原样呈現
}
```

接線（既有事件綁定區）：`#coupon-send-dialog-confirm` → `confirmCouponSend`；`#coupon-send-dialog-cancel` → `dialog.close()`。`api()` helper 的錯誤格式先讀檔內現行實作，讓 400 的中文錯誤訊息能透出（若 `api()` 只丟 status，改用其現行慣例呈現）。

- [ ] **Step 3: 寫 verify-raw-row-coupon.mjs（route 攔截、不動真資料）**

沿用 `verify-admin-toolbar-theme.mjs` 骨架 + `loginWithKey`。以 `page.route` 攔截：

- `GET /api/admin/survey` → 兩列假資料（`one@example.com`、`two@example.com`）
- `GET /api/admin/coupons` → 三張券：id 1 未過期、id 2 `expiresAt:'2020-01-01'`（已過期）、id 3 未過期
- `GET /api/admin/coupons/sent-map` → `{"one@example.com":[3]}`
- `POST /api/admin/coupons/1/send` → 記下 request body，回 `{attempted:1,sent:1,skipped:0,failed:0,remaining:0}`

斷言：

1. 原始資料表每列有「寄券」按鈕；`one@example.com` 列顯示「已寄：」標示
2. 點 `one@example.com` 的寄券 → dialog 開啟、收件人正確；下拉**不含已過期的 id 2**；id 3 的 option `disabled` 且文字含「已寄過」
3. 選 id 1 確認 → 攔截到的 body 為 `{"emails":["one@example.com"],"limit":1,"single":true}`；dialog 關閉；該列已寄標示更新為含兩張券

- [ ] **Step 4: 本機起後端跑腳本**

```powershell
node survey-backend/scripts/verify-raw-row-coupon.mjs
```

Expected: 全部斷言 PASS

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/resources/static/admin.html survey-backend/scripts/verify-raw-row-coupon.mjs
git commit -m "feat(admin-ui): 原始資料逐列寄券——彈窗選券、過期不列、已寄停用（Task 9 / B2 前端）"
```

---

### Task 10: Reader 漏斗語意重構（B3 後端）

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/reader/ReaderFunnelView.java`
- Modify: `survey-backend/src/main/java/world/springai/survey/reader/AdminReferralGrowthController.java`（dashboard 71-79 行附近）
- Test: `survey-backend/src/test/java/world/springai/survey/reader/ReaderFunnelViewTest.java`

**Interfaces:**
- Consumes: dashboard 既有的六個 `count(distinct visitor_key)` 計數
- Produces: dashboard 回應新增 key `readerFunnelStructured`，形狀：

```json
{
  "totalViews": 120,
  "subscribePath": [ {"key":"subscribeAttempts","label":"送出訂閱","count":30}, {"key":"subscribeSuccess","label":"訂閱成功","count":22} ],
  "unlockPath":    [ {"key":"unlockClicks","label":"點選解鎖","count":15},  {"key":"unlockSuccess","label":"解鎖成功","count":11} ]
}
```

既有 `readerFunnel`／`funnel` key **原樣保留**（spec §8：新舊數值並存驗證）。

- [ ] **Step 1: 寫失敗測試**

```java
/** D7：頂端為平行入口（文章瀏覽＋訂閱首頁瀏覽）的加總；兩條路徑各自成鏈 */
@Test
void topLayerSumsParallelEntries() {
    ReaderFunnelView view = ReaderFunnelView.from(100, 20, 30, 22, 15, 11);
    assertEquals(120, view.totalViews());
    assertEquals(List.of(
        new ReaderFunnelView.Step("subscribeAttempts", "送出訂閱", 30),
        new ReaderFunnelView.Step("subscribeSuccess", "訂閱成功", 22)), view.subscribePath());
    assertEquals(List.of(
        new ReaderFunnelView.Step("unlockClicks", "點選解鎖", 15),
        new ReaderFunnelView.Step("unlockSuccess", "解鎖成功", 11)), view.unlockPath());
}
```

- [ ] **Step 2: 跑測試確認失敗** → FAIL（類不存在）

- [ ] **Step 3: 實作**

```java
package world.springai.survey.reader;

import java.util.List;

/**
 * Reader 漏斗的語意正確結構（D7）：
 * 原始的六個事件計數之間沒有包含關係——articleViews 與 subscriptionHomeViews 是平行入口，
 * 訂閱與解鎖是兩條不同路徑；並排成單一漏斗必然倒掛。本 view 把頂端定義為平行入口的加總，
 * 其下拆成兩條各自遞減的鏈。原始計數在 dashboard 回應中原樣保留（readerFunnel key），
 * 供新舊數值並存驗證。
 */
public record ReaderFunnelView(long totalViews, List<Step> subscribePath, List<Step> unlockPath) {

    /** 漏斗單層：key 供前端程式化比對、label 顯示、count 去重訪客數 */
    public record Step(String key, String label, long count) {}

    /** 由六個原始事件計數組裝語意正確的結構；純函數，方便單測鎖住加總語意 */
    public static ReaderFunnelView from(long articleViews, long subscriptionHomeViews,
            long subscribeAttempts, long subscribeSuccess, long unlockClicks, long unlockSuccess) {
        return new ReaderFunnelView(
            articleViews + subscriptionHomeViews,
            List.of(new Step("subscribeAttempts", "送出訂閱", subscribeAttempts),
                    new Step("subscribeSuccess", "訂閱成功", subscribeSuccess)),
            List.of(new Step("unlockClicks", "點選解鎖", unlockClicks),
                    new Step("unlockSuccess", "解鎖成功", unlockSuccess)));
    }
}
```

`AdminReferralGrowthController#dashboard`：readerFunnel 六數已有區域變數（或直接複用查詢結果），在 `readerFunnel` put 之後加：

```java
// D7：語意正確的漏斗結構——頂端為平行入口加總，其下分訂閱／解鎖兩條路徑
response.put("readerFunnelStructured", ReaderFunnelView.from(
    articleViews, subscriptionHomeViews, subscribeAttempts, subscribeSuccess, unlockClicks, unlockSuccess));
```

（六個計數目前是 inline 塞進 Map——若尚無區域變數，先抽成區域變數再雙用，讀 43-79 行現碼決定。）

- [ ] **Step 4: 跑測試確認通過**

Run: `mvn -q -Dtest='ReaderFunnelViewTest,AdminReferral*' test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/reader/ReaderFunnelView.java survey-backend/src/main/java/world/springai/survey/reader/AdminReferralGrowthController.java survey-backend/src/test/java/world/springai/survey/reader/ReaderFunnelViewTest.java
git commit -m "feat(growth): Reader 漏斗語意重構——頂端加總平行入口、雙路徑結構（Task 10 / B3 後端）"
```

---

### Task 11: 漏斗圖渲染（B3 前端）

**Files:**
- Modify: `survey-backend/src/main/resources/static/admin.html`（growth 區塊 610-647 行、`loadGrowth` 2808-2834 行、CSS 區）
- Create: `survey-backend/scripts/verify-growth-funnel.mjs`

**Interfaces:**
- Consumes: dashboard 的 `funnel`（clicks/submitted/confirmed/approved）與 Task 10 的 `readerFunnelStructured`
- Produces: `#share-funnel-chart` 與 `#reader-funnel-chart` 兩個漏斗圖容器；`renderFunnelChart(container, layers)` JS 函式

- [ ] **Step 1: HTML——growth 區塊、`#growth-kpis` 之前加**

```html
<h3 style="margin:14px 0 6px">分享漏斗</h3>
<div class="funnel" id="share-funnel-chart"></div>
<h3 style="margin:14px 0 6px">Reader 漏斗</h3>
<div class="funnel-split" id="reader-funnel-chart"></div>
```

- [ ] **Step 2: CSS（admin.html 的 `<style>` 區）**

```css
/* 行銷漏斗圖（B3）：寬度依數值比例、嚴格遞減（§4.3.1），倒掛層標示警示不隱藏異常 */
.funnel{display:flex;flex-direction:column;align-items:center;gap:2px}
.funnel-layer{background:var(--accent-soft);color:var(--fg);border:1px solid var(--border);
  border-radius:6px;text-align:center;padding:6px 8px;font-size:.85rem;min-width:72px;transition:width .3s}
.funnel-layer strong{margin-left:6px}
.funnel-rate{color:var(--muted);font-size:.75rem;margin:0}
.funnel-warn{color:var(--amber,#b45309);font-size:.75rem}
.funnel-split{display:flex;gap:18px;flex-wrap:wrap;align-items:flex-start;justify-content:center}
.funnel-split .funnel{flex:1;min-width:220px}
.funnel-split-title{font-size:.8rem;color:var(--muted);text-align:center;margin:4px 0}
```

- [ ] **Step 3: JS——渲染函式（放 `loadGrowth` 之前）**

```js
/**
 * 漏斗圖（B3）：layers=[{label,count}]，寬度依數值比例、由上而下嚴格遞減（§4.3.1）。
 * 顯示寬度 = min(比例寬, 上一層顯示寬 - 6%)，下限 14%——資料異常（下層>上層）時
 * 形狀仍上大下小，但該層加註警示與實際數值，異常不被隱藏。相鄰層標示轉換率。
 */
function renderFunnelChart(container,layers){
  container.replaceChildren();
  const max=Math.max(1,...layers.map(l=>l.count));
  let prevWidth=100,prevCount=null;
  layers.forEach(layer=>{
    if(prevCount!==null){
      const rate=document.createElement('p');rate.className='funnel-rate';
      rate.textContent=prevCount>0?`↓ ${(layer.count/prevCount*100).toFixed(1)}%`:'↓ —';
      container.append(rate);
    }
    const ideal=layer.count/max*100;
    const width=Math.max(14,Math.min(ideal,prevWidth-6));
    const div=document.createElement('div');div.className='funnel-layer';div.style.width=width+'%';
    div.textContent=layer.label;
    const strong=document.createElement('strong');strong.textContent=layer.count;div.append(strong);
    if(prevCount!==null&&layer.count>prevCount){
      const warn=document.createElement('span');warn.className='funnel-warn';
      warn.textContent=' ⚠ 高於上一層';div.append(warn);
    }
    container.append(div);
    prevWidth=width;prevCount=layer.count;
  });
}
/** Reader 漏斗（D7）：頂端總瀏覽一層，其下訂閱／解鎖兩條路徑並排各自渲染 */
function renderReaderFunnel(structured){
  const root=$('#reader-funnel-chart');root.replaceChildren();
  const top=document.createElement('div');top.className='funnel';top.style.flexBasis='100%';
  renderFunnelChart(top,[{label:'總瀏覽（文章＋訂閱首頁）',count:structured.totalViews}]);
  root.append(top);
  [['訂閱路徑',structured.subscribePath],['解鎖路徑',structured.unlockPath]].forEach(([title,steps])=>{
    const wrap=document.createElement('div');wrap.className='funnel';
    const t=document.createElement('p');t.className='funnel-split-title';t.textContent=title;wrap.append(t);
    const chart=document.createElement('div');chart.className='funnel';wrap.append(chart);
    renderFunnelChart(chart,steps.map(s=>({label:s.label,count:s.count})));
    root.append(wrap);
  });
}
```

`loadGrowth()` 開頭取得 `data` 後加：

```js
renderFunnelChart($('#share-funnel-chart'),[
  {label:'分享點擊',count:f.clicks},{label:'完成填表',count:f.submitted},
  {label:'信箱確認',count:f.confirmed},{label:'審核通過',count:f.approved}]);
if(data.readerFunnelStructured)renderReaderFunnel(data.readerFunnelStructured);
```

（既有 KPI 區塊保留——新舊數值並存。）

- [ ] **Step 4: 寫 verify-growth-funnel.mjs**

route 攔截 `GET /api/admin/referrals/dashboard`，餵兩組資料各驗一輪：

1. 正常遞減資料：斷言 `#share-funnel-chart .funnel-layer` 的 `offsetWidth` 嚴格遞減、`.funnel-rate` 顯示轉換率、無 `.funnel-warn`
2. 倒掛資料（`submitted > clicks`）：斷言寬度**仍**嚴格遞減（§4.3.1）、倒掛層出現 `.funnel-warn` 且文字含「高於上一層」、實際數值照顯
3. `readerFunnelStructured`：斷言頂層文字含「總瀏覽」與加總值（120=100+20 的假資料）、出現「訂閱路徑」「解鎖路徑」兩塊

- [ ] **Step 5: 本機起後端跑腳本**

```powershell
node survey-backend/scripts/verify-growth-funnel.mjs
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add survey-backend/src/main/resources/static/admin.html survey-backend/scripts/verify-growth-funnel.mjs
git commit -m "feat(admin-ui): 行銷漏斗圖——寬度嚴格遞減、倒掛警示、Reader 雙路徑（Task 11 / B3 前端）"
```

---

### Task 12: 投票卡關係說明與轉換率（B4）

**Files:**
- Modify: `survey-backend/src/main/resources/static/admin.html`（信中投票卡 219-228 行、`loadVoteStats` 1225-1234 行）
- Create: `survey-backend/scripts/verify-vote-card-context.mjs`

**Interfaces:**
- Consumes: `GET /api/admin/analytics/forms/{key}/votes` → `{totalVotes, totalNamed, options, byCampaign}`；`GET /api/admin/analytics/forms/{key}?allVersions=true` → `{summary:{submissions,…}}`

- [ ] **Step 1: HTML——信中投票卡的 hint 改為**

```html
<p class="hint" style="margin:4px 0 0">信中一鍵題點擊數，<strong>不受上方篩選條件影響</strong>（上方圖表吃版本／日期／來源／期別篩選，本卡永遠是該表單全量）。投票與上方「完整提交」是同一條漏斗的兩端：先在信中點一票，再回站完成整份問卷。</p>
```

- [ ] **Step 2: JS——loadVoteStats 擴充**

```js
/** 信中投票卡（B4）：投票統計外，加上與完整提交的漏斗兩端關係與轉換率（點擊→完填） */
async function loadVoteStats(){
  const key=$('#dynamic-form').value;
  if(!key){$('#vote-summary').textContent='請先選擇表單。';return;}
  try{
    const [stats,analytics]=await Promise.all([
      api(`/api/admin/analytics/forms/${encodeURIComponent(key)}/votes`),
      api(`/api/admin/analytics/forms/${encodeURIComponent(key)}?allVersions=true`).catch(()=>null)]);
    const total=stats.totalVotes||0,named=stats.totalNamed||0;
    const submissions=analytics?.summary?.submissions;
    let text=`累計 ${total} 票 · 具名 ${named} 票 · 匿名 ${total-named} 票`;
    if(submissions!=null){
      text+=` ｜ 完整提交（全版本不分期）${submissions} 筆`;
      if(total>0)text+=` · 點擊→完填轉換率 ${(submissions/total*100).toFixed(1)}%`;
    }
    $('#vote-summary').textContent=text;
    renderVoteOptions(stats.options||[]);
    renderVoteCampaignTable(stats.byCampaign||[]);
  }catch(error){if(error.message!=='401')$('#vote-summary').textContent='投票統計載入失敗：'+error.message;}
}
```

（analytics 可能 404——例如表單無已發布版本——故 `.catch(()=>null)`，缺資料時只顯示票數，不噴錯。）

- [ ] **Step 3: 寫 verify-vote-card-context.mjs**

route 攔截兩個 API（votes 回 `totalVotes:14,totalNamed:9`；analytics 回 `summary:{submissions:8}`），斷言：

1. 卡片 hint 文字含「不受上方篩選條件影響」
2. `#vote-summary` 文字含「累計 14 票」「完整提交（全版本不分期）8 筆」「57.1%」
3. analytics 攔截改回 404 → `#vote-summary` 仍顯示「累計 14 票」且不含「轉換率」（優雅降級）

- [ ] **Step 4: 本機起後端跑腳本**

```powershell
node survey-backend/scripts/verify-vote-card-context.mjs
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/resources/static/admin.html survey-backend/scripts/verify-vote-card-context.mjs
git commit -m "feat(admin-ui): 投票卡標注篩選關係與點擊→完填轉換率（Task 12 / B4）"
```

---

### Task 13: 迴歸總驗證

**Files:** 無新檔；只驗證與必要修復

- [ ] **Step 1: 後端全套測試**

```powershell
cd d:\GitHub\hahow-ai-full-stack\survey-backend
$env:JAVA_HOME='D:\java\jdk-21'
mvn -q test
```

Expected: 全綠（基準：本輪開始前 993+ 案通過；新增案全數計入）。失敗即修，修完重跑。

- [ ] **Step 2: 全部 verify 腳本**

本機起後端（Global Constraints #3）後依序跑：既有 9 支 `verify-*.mjs`（以 `Glob survey-backend/scripts/verify-*.mjs` 列出、逐支執行，**排除**預設指向正式站的 `verify-admin.mjs` 與 `smoke-live-reader.mjs`）＋ 本輪新增 4 支（reader-theme、raw-row-coupon、growth-funnel、vote-card-context）。

Expected: 全部 exit 0。

- [ ] **Step 3: 手動煙霧測試（本機瀏覽器）**

1. `/r/` 亮暗切換、重整保持；登入後首頁顯示 email、右上有登出；問卷列表出現已勾選的問卷
2. 含內嵌問卷的文章側邊欄出現票數分布
3. 後台：原始資料寄券彈窗、漏斗圖形狀、投票卡轉換率

- [ ] **Step 4: Commit（若有修復）並回報**

修復一律逐項明確 `git add`。回報內容：測試數、各腳本結果、spec 8 項需求逐項對照完成狀態。

---

## 執行備註

- **任務相依**：1→2→3 有嚴格順序（資料層→API→讀者端）；4 依賴 3 的測試檔；5→6 嚴格順序；7、8→9、10→11、12 彼此獨立可並行；13 收尾。
- **admin.html 是單檔**——Task 2、9、11、12 都改它，衝突面在不同區塊但**不可並行實作**，依序執行。
- 若 `ReaderPageController` 既有測試建構方式與計畫假設不符（例如沒有現成測試檔），以現場實況為準建 standalone 測試，但**斷言內容不變**。
