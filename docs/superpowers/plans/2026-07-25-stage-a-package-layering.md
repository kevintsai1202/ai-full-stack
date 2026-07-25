# 階段 A：survey-backend package 分層 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `survey-backend` 目前平放在單一 package 的 30 個生產類與 11 個測試類，重組為 `audience` / `mail` / `newsletter` / `form` 四個子 package 加根 package 共用元件，行為完全不變。

**Architecture:** 純機械重構。`@SpringBootApplication` 位於 `world.springai.survey`，Spring 的 component scan、entity scan、repository scan 都自動涵蓋子 package，因此**不需要修改任何設定檔**。四個 entity 皆有明確 `@Table(name = "...")`，表名與 package 無關；`SurveyResponseRepository` 的 JPQL 使用 entity 簡名，類名不變故查詢仍有效。本階段**不含任何 migration、完全不動資料庫**。

**Tech Stack:** Java 21、Spring Boot 3.5.0、Maven 3.9.11、PostgreSQL（本階段不連線）、JUnit 5 + Mockito（`spring-boot-starter-test`）

## Global Constraints

- **JDK 必須是 21**。系統預設 `java` 是 JDK 8（`JAVA_HOME=D:\java\jdk8u432-b06`），直接跑 `mvn` 會編譯失敗。每個 Maven 指令都必須先設 `$env:JAVA_HOME = "D:\java\jdk-21"`。
- **測試基線：11 個測試檔、70 個測試、全綠。** 重構後必須仍是 70 個測試全綠（Task 4 會再新增 1 個測試檔／1 個測試，屆時為 12 檔、71 測試）。
- **既有資料不可清除**（spec §4.0）。本階段不得建立、修改或執行任何 Flyway migration；不得執行 `DROP` / `TRUNCATE` / 無條件 `DELETE` / `flyway clean`；不得連線正式資料庫。
- **依賴方向單向**：`reader` / `newsletter` / `form` 可依賴 `audience` / `mail` / `media`；下層**不得** import 上層。同層之間 `audience → mail` 是允許的（`WelcomeMailService` 需要寄信），但 `mail` 不得 import `audience`。
- **所有程式碼需具備中文註解**（專案 CLAUDE.md）。本階段搬移既有類，原有註解一律原樣保留，不得刪減。
- **指令相容 PowerShell 7+**（專案 CLAUDE.md）。
- **不得順手改行為**。這是重構：不改邏輯、不改方法簽章、不改註解內容、不重新命名。任何「順手改進」都會讓 70 個測試的驗證失去意義。

---

## File Structure

### 生產類搬移對照（30 個檔）

**`world.springai.survey.audience`（名單中心，6 檔）**

| 檔案 | 職責 |
|---|---|
| `SurveyResponse.java` | 名單 entity（`@Table(name = "survey_response")`） |
| `SurveyResponseRepository.java` | 名單資料存取，含 JPQL 與 native query |
| `RecipientService.java` | 依 role / interest 取可寄送名單 |
| `UnsubscribeTokenService.java` | 退訂／確認連結的 HMAC 簽發與驗證 |
| `AdminImportController.java` | `POST /api/admin/import` 外部名單匯入 |
| `WelcomeMailService.java` | 問卷送出後寄歡迎信 |

**`world.springai.survey.mail`（寄信基礎設施，10 檔）**

| 檔案 | 職責 |
|---|---|
| `MailSender.java` | 寄信介面（單封／批量／排程／取消） |
| `ZSendMailSender.java` | ZSend REST API 實作 |
| `NoopMailSender.java` | 無 api-key 時的空實作 |
| `MailConfig.java` | 依設定選擇 MailSender 實作 |
| `MailQuotaService.java` | Zeabur GraphQL 查詢 ZSend 額度 |
| `EmailLog.java` | 寄送記錄 entity（`@Table(name = "email_log")`） |
| `EmailLogRepository.java` | 寄送記錄資料存取 |
| `MailTemplate.java` | 信件範本 entity（`@Table(name = "mail_template")`） |
| `MailTemplateRepository.java` | 信件範本資料存取 |
| `EmailTemplate.java` | 信件 HTML 外框包裝 |

**`world.springai.survey.newsletter`（電子報營運端，6 檔）**

| 檔案 | 職責 |
|---|---|
| `Campaign.java` | 發送批次 entity（`@Table(name = "campaign")`） |
| `CampaignRepository.java` | 發送批次資料存取 |
| `CampaignService.java` | 渲染、批量／排程發送、寫 campaign 與 email_log |
| `AdminCampaignController.java` | 電子報後台 API |
| `MarkdownRenderer.java` | Markdown → HTML（commonmark，允許 raw HTML） |
| `InviteService.java` | 邀請信與補送提醒 |

**`world.springai.survey.form`（問卷表單，3 檔）**

| 檔案 | 職責 |
|---|---|
| `SurveyController.java` | 問卷送出、統計、退訂／確認、admin 匯出 |
| `SurveyRequest.java` | 問卷送出請求 DTO |
| `SurveyStats.java` | 公開統計聚合 |

**根 package `world.springai.survey`（共用元件，5 檔，不搬移）**

| 檔案 | 為何留在根 |
|---|---|
| `SurveyApplication.java` | 啟動類，`@SpringBootApplication` 必須在所有子 package 之上；內含 package-private 的 `HealthController` |
| `AdminKeyGuard.java` | 所有 `/api/admin` 端點共用 |
| `ApiExceptionHandler.java` | 全域錯誤處理 |
| `WebConfig.java` | CORS 設定 |
| `TrackingController.java` | 廣告追蹤腳本產生器（GA4／Meta／LINE pixel），供 land-page 與問卷頁共用，不屬任何單一領域 |

> **spec 補充**：spec §3 的 package 圖遺漏了 `TrackingController`。它是廣告 pixel 腳本產生器（**不是**開信追蹤，開信追蹤是階段 E 才新建的功能），跨越 land-page 與問卷頁，因此歸入根 package 共用元件。Task 5 會回頭補進 spec。

**本階段不建立** `media/` 與 `reader/` 目錄——階段 A 沒有屬於它們的類，空目錄無意義（git 也不追蹤空目錄）。

### 測試類搬移對照（11 檔，跟著被測類走）

| 目標 package | 測試檔 |
|---|---|
| `audience` | `UnsubscribeTokenServiceTest.java`、`AdminImportControllerTest.java`、`WelcomeMailServiceTest.java` |
| `mail` | `MailQuotaServiceTest.java`、`ZSendMailSenderTest.java`、`EmailTemplateTest.java` |
| `newsletter` | `CampaignServiceTest.java`、`AdminCampaignControllerTest.java`、`InviteServiceTest.java`、`MarkdownRendererTest.java` |
| `form` | `SurveyControllerTest.java` |

### 新增檔（Task 4）

| 檔案 | 職責 |
|---|---|
| `src/test/java/world/springai/survey/PackageDependencyTest.java` | 架構守衛：掃描 `src/main/java` 的 import 語句，斷言下層 package 未反向依賴上層 |

### 可見性：不需任何調整

已逐項查核，所有 package-private 成員在搬移後仍與其使用者同 package：

| package-private 成員 | 所在類 → 目標 package | 使用者 → 目標 package | 結論 |
|---|---|---|---|
| `MailQuotaService.BATCH_CAP` | `MailQuotaService` → `mail` | `MailQuotaServiceTest` → `mail` | 同 package，OK |
| `InviteService.TEMPLATE_KEY`、`CONFIRM_LINK_PLACEHOLDER`、`REMINDER_MIN_INTERVAL_DAYS`、`REMINDER_TYPE` | `InviteService` → `newsletter` | `InviteServiceTest`、`AdminCampaignController` → `newsletter` | 同 package，OK |
| `class HealthController` | `SurveyApplication.java` → 根（不搬） | Spring 掃描 | 不變，OK |

**不要**把任何成員改成 `public`。若編譯出現可見性錯誤，表示搬移分類有誤，應回頭檢查分類而非放寬修飾字。

### 跨 package import 對照表

搬移後需要新增 import 的檔案與確切 import 清單（**這是 Task 2 Step 7 的依據**）：

**`audience/WelcomeMailService.java`**
```java
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailSender;
```

**`audience/AdminImportController.java`**
```java
import world.springai.survey.AdminKeyGuard;
```

**`newsletter/InviteService.java`**
```java
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;
import world.springai.survey.audience.UnsubscribeTokenService;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailSender;
import world.springai.survey.mail.MailTemplate;
import world.springai.survey.mail.MailTemplateRepository;
```

**`newsletter/CampaignService.java`**
```java
import world.springai.survey.audience.RecipientService;
import world.springai.survey.audience.UnsubscribeTokenService;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailSender;
```

**`newsletter/AdminCampaignController.java`**
```java
import world.springai.survey.AdminKeyGuard;
import world.springai.survey.audience.RecipientService;
import world.springai.survey.mail.MailQuotaService;
import world.springai.survey.mail.MailTemplate;
```

**`form/SurveyController.java`**
```java
import world.springai.survey.AdminKeyGuard;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;
import world.springai.survey.audience.UnsubscribeTokenService;
import world.springai.survey.audience.WelcomeMailService;
```

**`mail/` 底下所有檔案：不需要任何跨 package import。** `mail` 是最底層，只依賴自己與 JDK／Spring。這是分層正確的訊號——若發現需要加，表示分類有誤。

**`audience/RecipientService.java`：不需要。** 它只用同 package 的 `SurveyResponseRepository`。

**`newsletter/Campaign.java`、`CampaignRepository.java`、`MarkdownRenderer.java`：不需要。** 只用同 package 型別與外部函式庫。

---

## Task 1: 環境確認與基線鎖定

**Files:**
- 不修改任何檔案（純驗證）

**Interfaces:**
- Consumes: 無
- Produces: 確認過的測試基線數字（70），供 Task 2、3 比對

- [ ] **Step 1: 設定 JDK 21 並確認版本**

在 `d:\GitHub\hahow-ai-full-stack\survey-backend` 執行：

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
& "$env:JAVA_HOME\bin\java.exe" -version
```

Expected: 輸出含 `openjdk version "21`（若顯示 1.8 表示路徑錯誤，先確認 `D:\java\jdk-21` 存在）

- [ ] **Step 2: 確認工作區乾淨**

```powershell
git status --porcelain survey-backend
```

Expected: 無輸出。若有未提交的變更，先提交或 stash——重構過程需要能用 `git diff` 檢查搬移是否純粹。

- [ ] **Step 3: 跑基線測試**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `BUILD SUCCESS`，且結尾摘要為 `Tests run: 70, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 4: 記錄每個測試檔的測試數（重構後逐檔比對用）**

```powershell
Select-String -Path target/surefire-reports/*.txt -Pattern "Tests run" | ForEach-Object { $_.Line }
```

Expected: 11 行，數字應為：

| 測試檔 | 測試數 |
|---|---|
| `AdminCampaignControllerTest` | 11 |
| `AdminImportControllerTest` | 5 |
| `CampaignServiceTest` | 5 |
| `EmailTemplateTest` | 1 |
| `InviteServiceTest` | 13 |
| `MailQuotaServiceTest` | 4 |
| `MarkdownRendererTest` | 2 |
| `SurveyControllerTest` | 18 |
| `UnsubscribeTokenServiceTest` | 5 |
| `WelcomeMailServiceTest` | 2 |
| `ZSendMailSenderTest` | 4 |
| **合計** | **70** |

若任一數字不符，**停止並回報**——基線與計畫不一致時不應開始重構。

---

## Task 2: 搬移生產類到四個子 package

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/{audience,mail,newsletter,form}/`（目錄）
- Modify（搬移 + 改 `package` 宣告 + 加 import）：上方「生產類搬移對照」的 25 個檔（根 package 的 5 檔不動）
- Test: 沿用既有 11 個測試檔（本任務結束時必須仍全綠）

**Interfaces:**
- Consumes: Task 1 確認的基線 70
- Produces: 四個子 package 內的生產類，完整限定名為 `world.springai.survey.audience.*`、`world.springai.survey.mail.*`、`world.springai.survey.newsletter.*`、`world.springai.survey.form.*`。後續所有階段的新類都放進這些 package。

> **重構的本質**：搬移過程中專案會處於**無法編譯**的中間狀態，這是正常的。本任務的邊界就是「全部搬完且測試綠」，不要試圖在中途跑測試。

- [ ] **Step 1: 建立四個 package 目錄**

```powershell
cd d:\GitHub\hahow-ai-full-stack\survey-backend\src\main\java\world\springai\survey
New-Item -ItemType Directory -Force audience, mail, newsletter, form | Out-Null
Get-ChildItem -Directory | Select-Object Name
```

Expected: 列出 `audience`、`form`、`mail`、`newsletter`

- [ ] **Step 2: 用 git mv 搬移 audience 的 6 個檔**

```powershell
git mv SurveyResponse.java SurveyResponseRepository.java RecipientService.java UnsubscribeTokenService.java AdminImportController.java WelcomeMailService.java audience/
```

Expected: 無輸出（成功）。用 `git mv` 而非 `Move-Item`，這樣 git 會記錄為 rename，`git log --follow` 仍可追溯檔案歷史。

- [ ] **Step 3: 搬移 mail 的 10 個檔**

```powershell
git mv MailSender.java ZSendMailSender.java NoopMailSender.java MailConfig.java MailQuotaService.java EmailLog.java EmailLogRepository.java MailTemplate.java MailTemplateRepository.java EmailTemplate.java mail/
```

Expected: 無輸出

- [ ] **Step 4: 搬移 newsletter 的 6 個檔**

```powershell
git mv Campaign.java CampaignRepository.java CampaignService.java AdminCampaignController.java MarkdownRenderer.java InviteService.java newsletter/
```

Expected: 無輸出

- [ ] **Step 5: 搬移 form 的 3 個檔**

```powershell
git mv SurveyController.java SurveyRequest.java SurveyStats.java form/
```

Expected: 無輸出

- [ ] **Step 6: 確認根 package 只剩 5 個檔**

```powershell
Get-ChildItem *.java | Select-Object -ExpandProperty Name
```

Expected 恰好這 5 個：`AdminKeyGuard.java`、`ApiExceptionHandler.java`、`SurveyApplication.java`、`TrackingController.java`、`WebConfig.java`

- [ ] **Step 7: 改寫每個搬移檔的 package 宣告**

每個檔案第一行的 `package world.springai.survey;` 要改成對應子 package。逐目錄執行：

```powershell
foreach ($pkg in "audience", "mail", "newsletter", "form") {
  Get-ChildItem "$pkg/*.java" | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    $content = $content -replace '^package world\.springai\.survey;', "package world.springai.survey.$pkg;"
    Set-Content -Path $_.FullName -Value $content -NoNewline
  }
}
```

驗證：

```powershell
Select-String -Path audience/*.java, mail/*.java, newsletter/*.java, form/*.java -Pattern "^package " | ForEach-Object { $_.Line } | Sort-Object -Unique
```

Expected 恰好 4 行：

```
package world.springai.survey.audience;
package world.springai.survey.form;
package world.springai.survey.mail;
package world.springai.survey.newsletter;
```

- [ ] **Step 8: 加入跨 package import**

依上方「跨 package import 對照表」，為這 5 個檔案加入 import。import 語句放在既有 import 區塊中，維持原有的分組習慣（jakarta / org.springframework / java / world 各自成組）。

以 `form/SurveyController.java` 為例，在既有 import 區塊末尾加入：

```java
import world.springai.survey.AdminKeyGuard;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;
import world.springai.survey.audience.UnsubscribeTokenService;
import world.springai.survey.audience.WelcomeMailService;
```

需要修改的共 **6 個檔**（其餘 19 檔不需要任何跨 package import）：

| 檔案 | 需加的 import 數 |
|---|---|
| `audience/WelcomeMailService.java` | 4（全部來自 `mail`） |
| `audience/AdminImportController.java` | 1（`AdminKeyGuard`） |
| `newsletter/InviteService.java` | 9 |
| `newsletter/CampaignService.java` | 6 |
| `newsletter/AdminCampaignController.java` | 4 |
| `form/SurveyController.java` | 5 |

- [ ] **Step 9: 編譯，逐一修掉剩餘的 import 錯誤**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn -q compile
```

Expected: `BUILD SUCCESS`。

若出現 `cannot find symbol`，讀錯誤訊息中的類名，查上方「生產類搬移對照」表確認它去了哪個 package，加上對應 import。**若出現可見性錯誤（`is not public in ...; cannot be accessed from outside package`），不要改修飾字**——那表示某個類被分到了錯誤的 package，回頭檢查分類。

- [ ] **Step 10: 跑測試確認行為未變**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 70, Failures: 0, Errors: 0, Skipped: 0`

測試檔此時仍在根 package，**必須為它們補上跨 package import 才能編譯**。Java 的簡名解析不會因為「這些類曾經同 package」而繼續生效——搬走之後，測試檔對 `SurveyResponse`、`MailSender` 等的簡名引用全部失效。

需要補 import 的 10 個測試檔與其 import 清單，見 Task 3 Step 3 的對照表（該表原本是為搬移後準備的，此處提前使用同一份清單）。

**唯一的例外**：`MailQuotaServiceTest` 引用 package-private 的 `MailQuotaService.BATCH_CAP`，補 import 無法解決可見性問題，必須**在此步就把它搬到 `mail/`**：

```powershell
cd d:\GitHub\hahow-ai-full-stack\survey-backend\src	est\java\world\springai\survey
New-Item -ItemType Directory -Force mail | Out-Null
git mv MailQuotaServiceTest.java mail/
```

搬完後同步把它的 `package` 宣告改成 `world.springai.survey.mail`，並移除因此變得多餘的 `import ...MailQuotaService;`。

**若此步失敗，先修到綠再繼續**——不要帶著紅燈進 Task 3。

- [ ] **Step 11: 確認搬移是純粹的（只有 package 行與 import 變動）**

```powershell
git add -A
git diff --cached --stat
git diff --cached -M --diff-filter=R -- "*.java" | Select-String -Pattern "^[+-]" | Where-Object { $_.Line -notmatch "^(\+\+\+|---)" } | ForEach-Object { $_.Line }
```

Expected: 所有 `+`/`-` 行都只是 `package ...;` 或 `import ...;`。**若看到任何邏輯行變動，還原它**——這個階段不改行為。

- [ ] **Step 12: Commit**

```powershell
git commit -m @'
refactor(survey-backend): 生產類分層為 audience/mail/newsletter/form

純機械搬移，行為不變、不動資料庫：
- audience：名單中心（SurveyResponse/Repository、RecipientService、
  UnsubscribeTokenService、AdminImportController、WelcomeMailService）
- mail：寄信基礎設施（MailSender 及實作、Quota、EmailLog、MailTemplate、
  EmailTemplate）— 最底層，無跨 package import
- newsletter：電子報營運端（Campaign、CampaignService、InviteService、
  MarkdownRenderer、AdminCampaignController）
- form：問卷表單（SurveyController、SurveyRequest、SurveyStats）
- 根 package 保留共用元件：SurveyApplication（含 HealthController）、
  AdminKeyGuard、ApiExceptionHandler、WebConfig、TrackingController

@SpringBootApplication 位於父 package，component/entity/repository scan
自動涵蓋子 package，故無需修改任何設定。entity 皆有明確 @Table，
JPQL 使用 entity 簡名，表名與查詢均不受影響。

70 個既有測試全綠。

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## Task 3: 搬移測試類到對應子 package

**Files:**
- Modify（搬移 + 改 `package` 宣告 + 加 import）：11 個測試檔，依上方「測試類搬移對照」
- Test: 就是這些測試檔本身

**Interfaces:**
- Consumes: Task 2 產出的四個子 package
- Produces: 測試檔與被測類同 package，後續階段的新測試依同一規則放置

> **為什麼測試也要搬**：Task 2 已為根 package 的測試檔補上跨 package import，所以此刻是能編譯的。但 `InviteService` 的四個 package-private 常數（`TEMPLATE_KEY` 等）若日後被測試引用就會失去存取權，而測試與被測類同 package 也讓後續階段的新測試有明確落點。
>
> **本 task 的前提**：`MailQuotaServiceTest` 已在 Task 2 被迫提前搬到 `mail/`（它引用 package-private 的 `BATCH_CAP`，補 import 無法解決），因此本 task 只需搬其餘 10 個檔。

- [ ] **Step 1: 建立測試目錄並搬移**

```powershell
cd d:\GitHub\hahow-ai-full-stack\survey-backend\src\test\java\world\springai\survey
New-Item -ItemType Directory -Force audience, mail, newsletter, form | Out-Null
git mv UnsubscribeTokenServiceTest.java AdminImportControllerTest.java WelcomeMailServiceTest.java audience/
git mv ZSendMailSenderTest.java EmailTemplateTest.java mail/   # MailQuotaServiceTest 已於 Task 2 搬移，此處不再列入
git mv CampaignServiceTest.java AdminCampaignControllerTest.java InviteServiceTest.java MarkdownRendererTest.java newsletter/
git mv SurveyControllerTest.java form/
Get-ChildItem *.java | Select-Object -ExpandProperty Name
```

Expected: 最後一行無輸出（根測試 package 已空）

- [ ] **Step 2: 改寫測試檔的 package 宣告**

```powershell
foreach ($pkg in "audience", "mail", "newsletter", "form") {
  Get-ChildItem "$pkg/*.java" | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    $content = $content -replace '^package world\.springai\.survey;', "package world.springai.survey.$pkg;"
    Set-Content -Path $_.FullName -Value $content -NoNewline
  }
}
Select-String -Path */*.java -Pattern "^package " | ForEach-Object { $_.Line } | Sort-Object -Unique
```

Expected 恰好 4 行，與生產類相同的四個 package 宣告

- [ ] **Step 3: 校正測試檔的 import**

Task 2 已為這些測試檔補過 import（當時它們還在根 package，需要 import 所有搬走的類）。搬進子 package 後，**指向自己所在 package 的那些 import 變成多餘**——Java 不會因此編譯失敗，只會留下未使用的 import 警告與技術債，必須清掉。

下表是每個測試檔搬移後**應該保留的完整 import 集合**。逐檔比對：不在表內的跨 package import 一律刪除，表內缺少的補上。

例如 `newsletter/CampaignServiceTest.java`：Task 2 為它加的 `world.springai.survey.newsletter.Campaign`、`...CampaignRepository`、`...CampaignService`、`...MarkdownRenderer` 四行，在檔案搬進 `newsletter/` 後全部變成同 package 引用，應刪除；表內列出的 `audience.*` 與 `mail.*` 則保留。

`audience/AdminImportControllerTest.java`
```java
import world.springai.survey.AdminKeyGuard;
```

`audience/WelcomeMailServiceTest.java`
```java
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailSender;
```

`newsletter/InviteServiceTest.java`
```java
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;
import world.springai.survey.audience.UnsubscribeTokenService;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.MailSender;
import world.springai.survey.mail.MailTemplate;
import world.springai.survey.mail.MailTemplateRepository;
```

`newsletter/CampaignServiceTest.java`
```java
import world.springai.survey.audience.RecipientService;
import world.springai.survey.audience.UnsubscribeTokenService;
import world.springai.survey.mail.EmailLog;
import world.springai.survey.mail.EmailLogRepository;
import world.springai.survey.mail.EmailTemplate;
import world.springai.survey.mail.MailSender;
```

`newsletter/AdminCampaignControllerTest.java`
```java
import world.springai.survey.AdminKeyGuard;
import world.springai.survey.audience.RecipientService;
import world.springai.survey.mail.MailQuotaService;
import world.springai.survey.mail.MailTemplate;
```

`form/SurveyControllerTest.java`
```java
import world.springai.survey.AdminKeyGuard;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;
import world.springai.survey.audience.UnsubscribeTokenService;
import world.springai.survey.audience.WelcomeMailService;
```

`mail/MailQuotaServiceTest.java`、`mail/ZSendMailSenderTest.java`、`mail/EmailTemplateTest.java`、`newsletter/MarkdownRendererTest.java`、`audience/UnsubscribeTokenServiceTest.java`：**不需要新增 import**（只用同 package 型別）。

- [ ] **Step 4: 跑測試**

```powershell
cd d:\GitHub\hahow-ai-full-stack\survey-backend
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 70, Failures: 0, Errors: 0, Skipped: 0`

若出現 `cannot find symbol`，對照 Step 3 清單補 import。若出現 `@WebMvcTest` 或 `@Import` 相關的 context 載入失敗，檢查 `SurveyControllerTest` 的 `@Import({UnsubscribeTokenService.class, AdminKeyGuard.class})` 兩個類是否都已 import。

- [ ] **Step 5: 逐檔比對測試數與基線一致**

```powershell
Select-String -Path target/surefire-reports/*.txt -Pattern "Tests run" | ForEach-Object { $_.Line }
```

Expected: 11 行，每檔數字與 Task 1 Step 4 的表格**完全相同**（11 / 5 / 5 / 1 / 13 / 4 / 2 / 18 / 5 / 2 / 4）。

檔名前綴會從 `world.springai.survey.XxxTest.txt` 變成 `world.springai.survey.<pkg>.XxxTest.txt`，這是預期的。

- [ ] **Step 6: Commit**

```powershell
git add -A
git commit -m @'
refactor(survey-backend): 測試類跟隨被測類分層

測試檔搬到與被測類相同的子 package，維持 package-private 成員的
可測性（MailQuotaService.BATCH_CAP、InviteService 的四個常數）。
70 個測試全綠，逐檔測試數與重構前一致。

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## Task 4: 新增依賴方向守衛測試

**Files:**
- Create: `survey-backend/src/test/java/world/springai/survey/PackageDependencyTest.java`
- Test: 同一個檔案

**Interfaces:**
- Consumes: Task 2、3 完成的 package 結構
- Produces: `PackageDependencyTest` — 後續階段新增 `reader` / `media` package 時，此測試自動涵蓋，無需修改

> **為什麼要這個測試**：spec §3 把依賴方向定為「單向，不可逆」的架構約束。沒有自動化檢查的約束會隨時間腐化——某天有人在 `audience` 裡 import `newsletter`，編譯照樣通過，分層就悄悄失效了。spec §12 明訂「無新測試依賴」，所以不用 ArchUnit，改以純 JDK 掃描 import 語句實作。

- [ ] **Step 1: 寫測試（此時它應該通過，因為 Task 2 已正確分層）**

Create `survey-backend/src/test/java/world/springai/survey/PackageDependencyTest.java`:

```java
package world.springai.survey;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 架構守衛：確保 package 依賴方向單向。
 *
 * <p>下層（audience／mail／media）是基礎設施，不得依賴上層（reader／newsletter／form）。
 * 這層邊界是日後真要拆成獨立服務時的拆解線，一旦出現反向依賴就拆不開了。
 * 以掃描 import 語句實作，避免為此引入 ArchUnit 等新測試依賴。</p>
 */
class PackageDependencyTest {

    /** 下層 package：基礎設施，不得反向依賴上層 */
    private static final List<String> LOWER_LAYERS = List.of("audience", "mail", "media");

    /** 上層 package：領域功能，可以依賴下層 */
    private static final List<String> UPPER_LAYERS = List.of("reader", "newsletter", "form");

    /** 生產程式碼根目錄（surefire 的工作目錄是 module 根，即 survey-backend） */
    private static final Path SOURCE_ROOT = Path.of("src/main/java/world/springai/survey");

    /** 下層 package 不得 import 任何上層 package 的型別 */
    @Test
    void lowerLayersMustNotDependOnUpperLayers() throws IOException {
        List<String> violations = new ArrayList<>();

        for (String lower : LOWER_LAYERS) {
            Path dir = SOURCE_ROOT.resolve(lower);
            // 尚未建立的 package（如階段 A 的 media）直接略過
            if (!Files.isDirectory(dir)) {
                continue;
            }
            for (Path javaFile : javaFilesIn(dir)) {
                for (String line : Files.readAllLines(javaFile)) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("import ")) {
                        continue;
                    }
                    for (String upper : UPPER_LAYERS) {
                        if (trimmed.contains("world.springai.survey." + upper + ".")) {
                            violations.add("%s（%s 層）→ %s：%s"
                                .formatted(javaFile.getFileName(), lower, upper, trimmed));
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
            "下層 package 出現反向依賴上層，違反 spec §3 的單向依賴約束：\n"
                + String.join("\n", violations));
    }

    /** mail 是最底層，連同層的 audience 都不該依賴（audience → mail 允許，反向不允許） */
    @Test
    void mailMustNotDependOnAudience() throws IOException {
        Path dir = SOURCE_ROOT.resolve("mail");
        List<String> violations = new ArrayList<>();

        for (Path javaFile : javaFilesIn(dir)) {
            for (String line : Files.readAllLines(javaFile)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("import ") && trimmed.contains("world.springai.survey.audience.")) {
                    violations.add(javaFile.getFileName() + "：" + trimmed);
                }
            }
        }

        assertTrue(violations.isEmpty(),
            "mail 是最底層，不得依賴 audience（WelcomeMailService 的 audience → mail 方向才是對的）：\n"
                + String.join("\n", violations));
    }

    /** 列出目錄下所有 .java 檔 */
    private static List<Path> javaFilesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}
```

- [ ] **Step 2: 跑新測試確認通過**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=PackageDependencyTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 3: 驗證守衛真的會抓到違規（故意製造一個違規）**

在 `audience/RecipientService.java` 的 import 區塊暫時加一行：

```java
import world.springai.survey.newsletter.Campaign;
```

執行：

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=PackageDependencyTest
```

Expected: **FAIL**，錯誤訊息含 `RecipientService.java（audience 層）→ newsletter`

**一定要做這步。** 沒驗證過會失敗的測試等於沒有測試——一個永遠通過的守衛提供的是假安全感。

- [ ] **Step 4: 移除故意的違規，確認回到綠燈**

```powershell
git checkout -- src/main/java/world/springai/survey/audience/RecipientService.java
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test
```

Expected: `Tests run: 72, Failures: 0, Errors: 0, Skipped: 0`（原 70 + 新增 2）

- [ ] **Step 5: Commit**

```powershell
git add src/test/java/world/springai/survey/PackageDependencyTest.java
git commit -m @'
test(survey-backend): 新增 package 依賴方向守衛

以掃描 import 語句的方式強制 spec §3 的單向依賴約束
（不引入 ArchUnit，符合 spec §12「無新測試依賴」）：
- 下層 audience/mail/media 不得 import 上層 reader/newsletter/form
- mail 為最底層，不得 import audience（反向的 audience → mail 才合法）

尚未建立的 package 自動略過，故新增 media/reader 時無需改動此測試。
已實測故意違規會使測試失敗。

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## Task 5: 啟動驗證與 spec 補正

**Files:**
- Modify: `docs/superpowers/specs/2026-07-25-reader-newsletter-platform-design.md`（§3 補上 `TrackingController`）

**Interfaces:**
- Consumes: Task 2–4 完成的重構
- Produces: 可進入階段 B 的乾淨基礎

> **為什麼要啟動驗證**：測試全綠只證明單元行為不變，不保證 Spring 能真的組裝起整個應用。component scan 若因分層出問題（例如某個 `@Component` 落在掃描範圍外），只有實際啟動才會顯現。

- [ ] **Step 1: 確認資料庫結構未被觸碰**

```powershell
cd d:\GitHub\hahow-ai-full-stack
git status --porcelain survey-backend/src/main/resources/db/
git log --oneline -3 -- survey-backend/src/main/resources/db/
```

Expected: 第一個指令無輸出；第二個指令最新 commit 是本次重構**之前**的（即 V6 那次）。本階段不該有任何 migration 變動。

- [ ] **Step 2: 確認 application.yml 未被修改**

```powershell
git diff HEAD~3 -- survey-backend/src/main/resources/application.yml
```

Expected: 無輸出。分層不需要改任何設定——若這裡有變動，表示做了計畫外的事。

- [ ] **Step 3: 啟動應用並確認健康檢查**

需要本機 PostgreSQL（`jdbc:postgresql://127.0.0.1:5432/survey`）。**若本機沒有資料庫，跳到 Step 4**，改以 context 載入測試替代。

```powershell
cd d:\GitHub\hahow-ai-full-stack\survey-backend
$env:JAVA_HOME = "D:\java\jdk-21"
mvn spring-boot:run
```

另開一個終端：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

Expected: `status : ok`

同時檢查啟動日誌**不應**出現 `No qualifying bean`、`UnsatisfiedDependencyException` 或 Flyway 的 `Migrating schema`（既有 DB 已在 V6，不該有新 migration 要跑）。確認後 `Ctrl+C` 停止。

- [ ] **Step 4: （無本機 DB 時的替代）以 context 載入測試驗證組裝**

若 Step 3 因無資料庫而無法執行，改建立一個一次性驗證：

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
mvn test -Dtest=SurveyControllerTest
```

Expected: `Tests run: 18, Failures: 0` — `@WebMvcTest` 會載入 `SurveyApplication` 作為 `@SpringBootConfiguration`（Task 1 的基線日誌已可見 `Found @SpringBootConfiguration world.springai.survey.SurveyApplication`），足以證明啟動類仍能被子 package 的測試正確定位。

在此情況下，**於回報中明確記錄「未執行實機啟動驗證，原因：本機無 PostgreSQL」**，並列為部署前必做項目。不要聲稱做了沒做的驗證。

- [ ] **Step 5: 補正 spec §3 的 TrackingController 遺漏**

在 spec 的 §3 架構圖中，根 package 共用元件那段文字加入 `TrackingController`。找到這行：

```
**共用元件**（`AdminKeyGuard`、`ApiExceptionHandler`、`WebConfig`、`SurveyApplication`）留在根 package，並新增 `AppSettingService`
```

改為：

```
**共用元件**（`AdminKeyGuard`、`ApiExceptionHandler`、`WebConfig`、`SurveyApplication`、`TrackingController`）留在根 package，並新增 `AppSettingService`
```

並在該段後補一句說明：

```markdown
`TrackingController` 是廣告追蹤腳本產生器（GA4／Meta Pixel／LINE Tag，供 land-page 與問卷頁共用），**與 §5.7 的開信追蹤無關**，不屬任何單一領域故留在根 package。
```

- [ ] **Step 6: 最終全量驗證並 commit**

```powershell
cd d:\GitHub\hahow-ai-full-stack\survey-backend
$env:JAVA_HOME = "D:\java\jdk-21"
mvn clean test
```

Expected: `Tests run: 72, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS`

用 `clean` 確保沒有殘留的舊 class 檔造成假通過。

```powershell
cd d:\GitHub\hahow-ai-full-stack
git add docs/superpowers/specs/2026-07-25-reader-newsletter-platform-design.md
git commit -m @'
docs(spec): §3 補上 TrackingController 的歸屬

TrackingController 是廣告 pixel 腳本產生器（GA4/Meta/LINE），
與 §5.7 的開信追蹤無關，跨越 land-page 與問卷頁，
故歸入根 package 共用元件。原架構圖遺漏了它。

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
'@
```

---

## 階段 A 完成標準

全部滿足才算完成：

- [ ] `mvn clean test` 為 `Tests run: 72, Failures: 0, Errors: 0`
- [ ] 逐檔測試數與 Task 1 基線表格一致（11 檔共 70），另加 `PackageDependencyTest` 的 2 個
- [ ] 根 package 只剩 5 個 `.java` 檔
- [ ] `src/main/resources/db/migration/` 無任何變動（仍是 V1–V6）
- [ ] `application.yml` 無任何變動
- [ ] `git diff` 顯示搬移檔的變動只有 `package` 與 `import` 行
- [ ] `PackageDependencyTest` 已實測會抓到故意的違規
- [ ] 實機啟動驗證通過，或明確記錄未執行的原因

## 給階段 B 的交接資訊

- 新類的落點：`audience` / `mail` / `newsletter` / `form` 已存在；`media` 與 `reader` 需在階段 B、D 建立時自行 `New-Item`。
- `PackageDependencyTest` 已預先涵蓋 `reader` 與 `media`，新增這兩個 package 不需修改該測試。
- 所有 Maven 指令都要先 `$env:JAVA_HOME = "D:\java\jdk-21"`。
- 階段 B 的 V7／V8 migration 是本專案**第一次**在有既有資料的 DB 上做 migration，spec §4.0 的部署前置檢查與 §4.2 的 backfill 都必須落實。
