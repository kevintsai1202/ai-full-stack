# 子專案 B：問卷頁 + 後端儲存 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 `survey-backend`（Spring Boot 3.5 / Java 21 + PostgreSQL）儲存 landing page 問卷回應，並新增 `land-page/survey.html` 問卷頁，部署到既有 Zeabur 專案。

**Architecture:** survey.html（land-page 靜態頁）以 fetch POST 到獨立的 survey-backend 服務；後端用 Spring Data JPA + Flyway 寫入 Zeabur PostgreSQL；提供公開的 `POST /api/survey`、受 `X-Admin-Key` 保護的 `GET /api/admin/survey`（JSON/CSV）、`GET /api/health`。寄信全部不在本計畫（留給子專案 C）。

**Tech Stack:** Spring Boot 3.5.x、Java 21、Maven、Spring Data JPA、Flyway、PostgreSQL（Hibernate `@JdbcTypeCode(SqlTypes.JSON)` 對應 jsonb）、Bean Validation、原生 HTML/JS 前端、Zeabur 部署。

設計來源：`docs/superpowers/specs/2026-06-19-survey-backend-design.md`

---

## 重要慣例

- 新服務目錄：`survey-backend/`（repo 子目錄，與 land-page / teaching-site 並列）。
- Java package：`world.springai.survey`。
- 所有 Java/JS 需中文函式級註解。
- 機密（DB 連線、ADMIN_API_KEY）一律從環境變數讀，不寫死。
- git 提交一律用明確路徑（工作區另有無關待處理變更）：`git commit -m "..." -- <path>`。禁止 `git add -A`、bare commit、`--no-verify`。
- 不要切換分支。
- 後端建置/測試指令（需 JAVA_HOME 指向 JDK 21）：`mvn -pl survey-backend test`、`mvn -pl survey-backend spring-boot:run`。本 repo 根目錄無 parent pom，故用 `-f survey-backend/pom.xml` 或先 `cd survey-backend`。本計畫一律用 `cd survey-backend && mvn ...`。

---

## 檔案結構

```
survey-backend/
├── pom.xml
├── src/main/java/world/springai/survey/
│   ├── SurveyApplication.java          啟動類 + /api/health
│   ├── SurveyResponse.java             JPA Entity（jsonb 欄位）
│   ├── SurveyResponseRepository.java   Spring Data JPA
│   ├── SurveyRequest.java              POST 請求 DTO（驗證 + 蜜罐）
│   ├── SurveyController.java           POST /api/survey、GET /api/admin/survey
│   ├── WebConfig.java                  CORS 設定
│   └── ApiExceptionHandler.java        ProblemDetail 驗證錯誤處理
├── src/main/resources/
│   ├── application.yml                 DB、Flyway、CORS、admin key 設定
│   └── db/migration/V1__create_survey_response.sql
├── src/test/java/world/springai/survey/
│   └── SurveyControllerTest.java       @WebMvcTest 驗證/蜜罐/admin key 測試
└── scripts/test-survey-api.ps1         整合驗證腳本

land-page/
├── survey.html                         問卷頁（新增）
└── index.html                          加一個進入問卷的連結（修改）
```

---

## Task 1: Spring Boot 專案骨架 + 健康檢查

**Files:**
- Create: `survey-backend/pom.xml`
- Create: `survey-backend/src/main/java/world/springai/survey/SurveyApplication.java`
- Create: `survey-backend/src/main/resources/application.yml`

- [ ] **Step 1: 建立 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.0</version>
    <relativePath/>
  </parent>

  <groupId>world.springai</groupId>
  <artifactId>survey-backend</artifactId>
  <version>1.0.0</version>
  <name>survey-backend</name>
  <description>Landing page 問卷收集後端</description>

  <properties>
    <java.version>21</java.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: 建立啟動類與健康檢查端點**

`survey-backend/src/main/java/world/springai/survey/SurveyApplication.java`：

```java
package world.springai.survey;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 問卷收集後端啟動類 */
@SpringBootApplication
public class SurveyApplication {
    public static void main(String[] args) {
        SpringApplication.run(SurveyApplication.class, args);
    }
}

/** 健康檢查端點，供 Zeabur 與監控確認服務存活 */
@RestController
class HealthController {
    /** 回傳服務存活狀態 */
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
```

- [ ] **Step 3: 建立 application.yml**

`survey-backend/src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: survey-backend
  # 資料庫連線：全部由環境變數提供（Zeabur 注入），本機可用預設值
  datasource:
    url: ${JDBC_URL:jdbc:postgresql://127.0.0.1:5432/survey}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate   # schema 由 Flyway 管理，JPA 只驗證
    open-in-view: false
  flyway:
    enabled: true
    baseline-on-migrate: true

server:
  port: ${PORT:8080}

# 管理 API 金鑰與 CORS 允許來源（環境變數覆寫）
app:
  admin-api-key: ${ADMIN_API_KEY:dev-admin-key}
  cors-allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:http://127.0.0.1:5174,http://localhost:5174}
```

- [ ] **Step 4: 驗證可編譯**

Run: `cd survey-backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS（無編譯錯誤）。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/pom.xml survey-backend/src/main/java/world/springai/survey/SurveyApplication.java survey-backend/src/main/resources/application.yml
git commit -m "feat(survey-backend): Spring Boot 骨架與健康檢查端點" -- survey-backend
```

---

## Task 2: Entity + Flyway migration + Repository

**Files:**
- Create: `survey-backend/src/main/resources/db/migration/V1__create_survey_response.sql`
- Create: `survey-backend/src/main/java/world/springai/survey/SurveyResponse.java`
- Create: `survey-backend/src/main/java/world/springai/survey/SurveyResponseRepository.java`

- [ ] **Step 1: 建立 Flyway migration**

`survey-backend/src/main/resources/db/migration/V1__create_survey_response.sql`：

```sql
-- 問卷回應表：儲存 landing page 收集到的問卷與名單
CREATE TABLE survey_response (
    id            BIGSERIAL PRIMARY KEY,
    email         TEXT        NOT NULL,                  -- 後續電子報/通知用
    name          TEXT,                                  -- 稱呼，選填
    role          TEXT,                                  -- 身分：學生/後端/前端/全端/其他
    experience    TEXT,                                  -- Java/Spring 經驗區間
    interest      JSONB,                                 -- 複選主題陣列
    budget        TEXT,                                  -- 可接受價位帶
    utm           JSONB,                                 -- UTM 歸因
    consent       BOOLEAN     NOT NULL,                  -- 同意接收（PDPA）
    unsubscribed  BOOLEAN     NOT NULL DEFAULT FALSE,    -- 退訂旗標，留給子專案 C
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()     -- 送出時間
);

-- 依 email 查詢（子專案 C 寄信會用）
CREATE INDEX idx_survey_response_email ON survey_response (email);
```

- [ ] **Step 2: 建立 Entity**

`survey-backend/src/main/java/world/springai/survey/SurveyResponse.java`：

```java
package world.springai.survey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 問卷回應實體，對應資料表 survey_response */
@Entity
@Table(name = "survey_response")
public class SurveyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    private String name;
    private String role;
    private String experience;

    /** 複選主題，存成 jsonb 陣列 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> interest;

    private String budget;

    /** UTM 歸因，存成 jsonb 物件 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> utm;

    @Column(nullable = false)
    private boolean consent;

    @Column(nullable = false)
    private boolean unsubscribed = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // --- getter / setter ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public List<String> getInterest() { return interest; }
    public void setInterest(List<String> interest) { this.interest = interest; }
    public String getBudget() { return budget; }
    public void setBudget(String budget) { this.budget = budget; }
    public Map<String, String> getUtm() { return utm; }
    public void setUtm(Map<String, String> utm) { this.utm = utm; }
    public boolean isConsent() { return consent; }
    public void setConsent(boolean consent) { this.consent = consent; }
    public boolean isUnsubscribed() { return unsubscribed; }
    public void setUnsubscribed(boolean unsubscribed) { this.unsubscribed = unsubscribed; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 3: 建立 Repository**

`survey-backend/src/main/java/world/springai/survey/SurveyResponseRepository.java`：

```java
package world.springai.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 問卷回應資料存取層 */
public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {
    /** 依建立時間新到舊回傳全部回應（管理 API 用） */
    List<SurveyResponse> findAllByOrderByCreatedAtDesc();
}
```

- [ ] **Step 4: 驗證可編譯**

Run: `cd survey-backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 5: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/SurveyResponse.java survey-backend/src/main/java/world/springai/survey/SurveyResponseRepository.java survey-backend/src/main/resources/db/migration/V1__create_survey_response.sql
git commit -m "feat(survey-backend): 問卷回應 Entity、Flyway migration 與 Repository" -- survey-backend
```

---

## Task 3: POST /api/survey（DTO 驗證 + 蜜罐 + ProblemDetail）以 TDD 進行

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/SurveyRequest.java`
- Create: `survey-backend/src/main/java/world/springai/survey/SurveyController.java`
- Create: `survey-backend/src/main/java/world/springai/survey/ApiExceptionHandler.java`
- Test: `survey-backend/src/test/java/world/springai/survey/SurveyControllerTest.java`

- [ ] **Step 1: 寫失敗測試（@WebMvcTest，mock repository）**

`survey-backend/src/test/java/world/springai/survey/SurveyControllerTest.java`：

```java
package world.springai.survey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SurveyController 行為測試：驗證、蜜罐、admin 金鑰 */
@WebMvcTest(SurveyController.class)
@TestPropertySource(properties = {"app.admin-api-key=test-key", "app.cors-allowed-origins=http://localhost"})
class SurveyControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    SurveyResponseRepository repository;

    /** 合法問卷應寫入並回 201 */
    @Test
    void validSurveyReturns201() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"consent\":true}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isCreated());
        verify(repository).save(any(SurveyResponse.class));
    }

    /** 未同意（consent=false）應回 400 且不寫入 */
    @Test
    void missingConsentReturns400() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"consent\":false}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

    /** email 格式錯誤應回 400 */
    @Test
    void invalidEmailReturns400() throws Exception {
        String body = "{\"email\":\"not-an-email\",\"consent\":true}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

    /** 蜜罐欄位 website 有值視為機器人，回 204 且不寫入 */
    @Test
    void honeypotFilledReturns204AndSkips() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"consent\":true,\"website\":\"spam\"}";
        mvc.perform(post("/api/survey").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isNoContent());
        verify(repository, never()).save(any());
    }

    /** 管理 API 無金鑰回 401 */
    @Test
    void adminWithoutKeyReturns401() throws Exception {
        mvc.perform(get("/api/admin/survey"))
           .andExpect(status().isUnauthorized());
    }

    /** 管理 API 金鑰正確回 200 */
    @Test
    void adminWithKeyReturns200() throws Exception {
        mvc.perform(get("/api/admin/survey").header("X-Admin-Key", "test-key"))
           .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run: `cd survey-backend && mvn -q test -Dtest=SurveyControllerTest`
Expected: 編譯失敗或測試失敗（SurveyController/SurveyRequest 尚未建立）。

- [ ] **Step 3: 建立請求 DTO（含驗證與蜜罐）**

`survey-backend/src/main/java/world/springai/survey/SurveyRequest.java`：

```java
package world.springai.survey;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** 問卷送出請求；website 為蜜罐欄位（正常使用者不應填寫） */
public class SurveyRequest {

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @Size(max = 100)
    private String name;
    private String role;
    private String experience;
    private List<String> interest;
    private String budget;
    private Map<String, String> utm;

    /** 必須為 true 才算同意（PDPA） */
    @AssertTrue
    private boolean consent;

    /** 蜜罐：以 CSS 隱藏，機器人才會填 */
    private String website;

    // --- getter / setter ---
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public List<String> getInterest() { return interest; }
    public void setInterest(List<String> interest) { this.interest = interest; }
    public String getBudget() { return budget; }
    public void setBudget(String budget) { this.budget = budget; }
    public Map<String, String> getUtm() { return utm; }
    public void setUtm(Map<String, String> utm) { this.utm = utm; }
    public boolean isConsent() { return consent; }
    public void setConsent(boolean consent) { this.consent = consent; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
}
```

- [ ] **Step 4: 建立 Controller**

`survey-backend/src/main/java/world/springai/survey/SurveyController.java`：

```java
package world.springai.survey;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 問卷收集與管理查詢端點 */
@RestController
public class SurveyController {

    private final SurveyResponseRepository repository;
    private final String adminApiKey;

    /** 注入資料層與管理金鑰 */
    public SurveyController(SurveyResponseRepository repository,
                            @Value("${app.admin-api-key}") String adminApiKey) {
        this.repository = repository;
        this.adminApiKey = adminApiKey;
    }

    /** 接收問卷；蜜罐有值則略過寫入（回 204），否則驗證後寫入（回 201） */
    @PostMapping("/api/survey")
    public ResponseEntity<Void> submit(@Valid @RequestBody SurveyRequest req) {
        if (StringUtils.hasText(req.getWebsite())) {
            return ResponseEntity.noContent().build(); // 疑似機器人，靜默略過
        }
        SurveyResponse entity = new SurveyResponse();
        entity.setEmail(req.getEmail());
        entity.setName(req.getName());
        entity.setRole(req.getRole());
        entity.setExperience(req.getExperience());
        entity.setInterest(req.getInterest());
        entity.setBudget(req.getBudget());
        entity.setUtm(req.getUtm());
        entity.setConsent(req.isConsent());
        repository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** 管理用：列出全部問卷，需 X-Admin-Key；format=csv 回 CSV */
    @GetMapping("/api/admin/survey")
    public ResponseEntity<?> list(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                  @RequestParam(value = "format", required = false) String format) {
        if (key == null || !key.equals(adminApiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid admin key");
        }
        List<SurveyResponse> all = repository.findAllByOrderByCreatedAtDesc();
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(toCsv(all));
        }
        return ResponseEntity.ok(all);
    }

    /** 組成 CSV，前置 UTF-8 BOM 讓 Excel 正確判讀編碼 */
    private String toCsv(List<SurveyResponse> rows) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("id,email,name,role,experience,interest,budget,consent,unsubscribed,created_at\n");
        for (SurveyResponse r : rows) {
            sb.append(r.getId()).append(',')
              .append(csv(r.getEmail())).append(',')
              .append(csv(r.getName())).append(',')
              .append(csv(r.getRole())).append(',')
              .append(csv(r.getExperience())).append(',')
              .append(csv(r.getInterest() == null ? "" : String.join("|", r.getInterest()))).append(',')
              .append(csv(r.getBudget())).append(',')
              .append(r.isConsent()).append(',')
              .append(r.isUnsubscribed()).append(',')
              .append(r.getCreatedAt()).append('\n');
        }
        return sb.toString();
    }

    /** CSV 欄位跳脫：含逗號/引號/換行時用雙引號包並把內部引號加倍 */
    private String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
```

- [ ] **Step 5: 建立 ProblemDetail 例外處理**

`survey-backend/src/main/java/world/springai/survey/ApiExceptionHandler.java`：

```java
package world.springai.survey;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 統一錯誤回應：把驗證失敗轉成 400 ProblemDetail */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Bean Validation 失敗（含 email 格式、consent 必須為 true）回 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("問卷資料驗證失敗");
        pd.setDetail(ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + " " + e.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b).orElse("invalid request"));
        return pd;
    }
}
```

- [ ] **Step 6: 跑測試確認通過**

Run: `cd survey-backend && mvn -q test -Dtest=SurveyControllerTest`
Expected: 6 個測試全部 PASS。

> 註：`@WebMvcTest` 只載入 web 層，`ResponseStatusException(401)` 由 Spring 預設處理回 401。CORS 設定（Task 5 的 WebConfig）不影響本測試。

- [ ] **Step 7: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/SurveyRequest.java survey-backend/src/main/java/world/springai/survey/SurveyController.java survey-backend/src/main/java/world/springai/survey/ApiExceptionHandler.java survey-backend/src/test/java/world/springai/survey/SurveyControllerTest.java
git commit -m "feat(survey-backend): 問卷提交與管理查詢端點（驗證/蜜罐/CSV/金鑰）" -- survey-backend
```

---

## Task 4: CORS 設定

**Files:**
- Create: `survey-backend/src/main/java/world/springai/survey/WebConfig.java`

- [ ] **Step 1: 建立 CORS 設定**

`survey-backend/src/main/java/world/springai/survey/WebConfig.java`：

```java
package world.springai.survey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS 設定：只允許設定檔列出的來源呼叫 /api/** */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    /** 從 app.cors-allowed-origins 讀逗號分隔的允許來源 */
    public WebConfig(@Value("${app.cors-allowed-origins}") String origins) {
        this.allowedOrigins = origins.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }
}
```

- [ ] **Step 2: 驗證測試仍通過**

Run: `cd survey-backend && mvn -q test`
Expected: 全部測試 PASS（WebConfig 不破壞既有測試）。

- [ ] **Step 3: Commit**

```bash
git add survey-backend/src/main/java/world/springai/survey/WebConfig.java
git commit -m "feat(survey-backend): 限定來源的 CORS 設定" -- survey-backend
```

---

## Task 5: 問卷前端頁 survey.html

**Files:**
- Create: `land-page/survey.html`
- Modify: `land-page/index.html`（在導覽或 CTA 加一個進入問卷的連結）

- [ ] **Step 1: 建立 survey.html**

`land-page/survey.html`（沿用 land-page 視覺；`API_BASE` 之後改為 survey-backend 的 Zeabur 網域）：

```html
<!DOCTYPE html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>課程興趣調查 · AI 賦能全端開發</title>
<meta name="robots" content="noindex" />
<link rel="icon" href="assets/logo.png" type="image/png" />
<script src="tracking.js"></script>
<style>
  :root { --accent:#4f46e5; --bg:#0b1020; --fg:#e7ebf5; --muted:#9aa6c4; --border:rgba(255,255,255,.14); }
  * { box-sizing:border-box; }
  body { margin:0; font-family:system-ui,-apple-system,"Noto Sans TC",sans-serif; background:var(--bg); color:var(--fg); line-height:1.7; }
  .wrap { max-width:640px; margin:0 auto; padding:40px 20px 80px; }
  h1 { font-size:1.6rem; margin:0 0 8px; }
  p.lead { color:var(--muted); margin:0 0 28px; }
  .card { background:rgba(255,255,255,.04); border:1px solid var(--border); border-radius:16px; padding:24px; }
  label { display:block; font-weight:700; margin:18px 0 6px; }
  input[type=text], input[type=email], select { width:100%; padding:11px 13px; border-radius:10px; border:1px solid var(--border); background:rgba(255,255,255,.06); color:var(--fg); font-size:1rem; }
  .checks label, .radios label { font-weight:400; display:flex; align-items:center; gap:8px; margin:6px 0; }
  .checks input, .radios input { width:auto; }
  .hp { position:absolute; left:-9999px; width:1px; height:1px; overflow:hidden; }
  .btn { margin-top:24px; width:100%; padding:14px; border:none; border-radius:12px; background:var(--accent); color:#fff; font-size:1.05rem; font-weight:800; cursor:pointer; }
  .btn:disabled { opacity:.6; cursor:not-allowed; }
  .consent { display:flex; gap:8px; align-items:flex-start; margin-top:20px; font-size:.92rem; color:var(--muted); }
  .msg { margin-top:18px; padding:14px; border-radius:10px; display:none; }
  .msg.ok { display:block; background:rgba(34,197,94,.15); color:#86efac; }
  .msg.err { display:block; background:rgba(239,68,68,.15); color:#fca5a5; }
</style>
</head>
<body>
<div class="wrap">
  <h1>課程興趣調查</h1>
  <p class="lead">花 1 分鐘讓我們更了解你，未來課程資訊與早鳥優惠會優先通知你。</p>
  <div class="card">
    <form id="survey-form">
      <label for="email">Email（必填）</label>
      <input type="email" id="email" name="email" required placeholder="you@example.com" />

      <label for="name">稱呼</label>
      <input type="text" id="name" name="name" placeholder="怎麼稱呼你" />

      <label for="role">你的身分</label>
      <select id="role" name="role">
        <option value="">不指定</option>
        <option>學生</option><option>後端工程師</option><option>前端工程師</option>
        <option>全端工程師</option><option>其他</option>
      </select>

      <label for="experience">Java / Spring 經驗</label>
      <select id="experience" name="experience">
        <option value="">不指定</option>
        <option>沒有經驗</option><option>半年內</option><option>1-3 年</option><option>3 年以上</option>
      </select>

      <label>最想學的主題（可複選）</label>
      <div class="checks" id="interest">
        <label><input type="checkbox" value="RAG 知識庫" /> RAG 知識庫</label>
        <label><input type="checkbox" value="Tool Calling" /> Tool Calling</label>
        <label><input type="checkbox" value="MCP" /> MCP</label>
        <label><input type="checkbox" value="Spring Security" /> Spring Security</label>
        <label><input type="checkbox" value="部署上線" /> 部署上線</label>
      </div>

      <label for="budget">可接受的價位帶</label>
      <select id="budget" name="budget">
        <option value="">不指定</option>
        <option>2000 以下</option><option>2000-4000</option><option>4000-6000</option><option>6000 以上</option>
      </select>

      <!-- 蜜罐欄位：一般使用者看不到，機器人才會填 -->
      <div class="hp"><label>Website</label><input type="text" id="website" name="website" tabindex="-1" autocomplete="off" /></div>

      <div class="consent">
        <input type="checkbox" id="consent" required />
        <label for="consent" style="font-weight:400;margin:0;">我同意接收課程資訊與電子報，並了解可隨時退訂（依個資法保護我的資料）。</label>
      </div>

      <button class="btn" type="submit" id="submit-btn">送出</button>
      <div class="msg" id="msg"></div>
    </form>
  </div>
</div>

<script>
  // survey-backend 的 API 位址；部署後改成 Zeabur 網域（見計畫 Task 7）
  const API_BASE = 'http://127.0.0.1:8080';

  const form = document.getElementById('survey-form');
  const btn = document.getElementById('submit-btn');
  const msg = document.getElementById('msg');

  /** 從 sessionStorage 取 tracking.js 解析好的 UTM 歸因 */
  function readUtm() {
    try { return JSON.parse(sessionStorage.getItem('utm') || '{}'); } catch (e) { return {}; }
  }

  form.addEventListener('submit', async function (e) {
    e.preventDefault();
    msg.className = 'msg';
    if (!document.getElementById('consent').checked) {
      msg.className = 'msg err'; msg.textContent = '請先勾選同意接收課程資訊。'; return;
    }
    const interest = Array.from(document.querySelectorAll('#interest input:checked')).map(i => i.value);
    const payload = {
      email: document.getElementById('email').value.trim(),
      name: document.getElementById('name').value.trim() || null,
      role: document.getElementById('role').value || null,
      experience: document.getElementById('experience').value || null,
      interest: interest,
      budget: document.getElementById('budget').value || null,
      utm: readUtm(),
      consent: true,
      website: document.getElementById('website').value // 蜜罐
    };
    btn.disabled = true; btn.textContent = '送出中…';
    try {
      const res = await fetch(API_BASE + '/api/survey', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
      });
      if (res.ok) {
        msg.className = 'msg ok'; msg.textContent = '已收到你的問卷，謝謝！未來會優先通知你課程資訊。';
        form.reset();
        if (window.Tracking) window.Tracking.event('survey_submit'); // 接上廣告追蹤
      } else {
        msg.className = 'msg err'; msg.textContent = '送出失敗，請確認 Email 格式後再試一次。';
      }
    } catch (err) {
      msg.className = 'msg err'; msg.textContent = '網路錯誤，請稍後再試。';
    } finally {
      btn.disabled = false; btn.textContent = '送出';
    }
  });
</script>
</body>
</html>
```

- [ ] **Step 2: 在 index.html 導覽加入問卷連結**

在 `land-page/index.html` 的導覽列（`<a href="#faq" class="nav-link">常見問題</a>` 之後）插入：

```html
    <a href="survey.html" class="nav-link">課程調查</a>
```

> 用 `Grep` 先確認 `常見問題</a>` 字串唯一，再用 Edit 在其後加入上行。

- [ ] **Step 3: 本機驗證頁面可開（不需後端）**

Run: `cd land-page && python -m http.server 5174`（背景），瀏覽器開 `http://127.0.0.1:5174/survey.html`
Expected: 表單正常顯示、蜜罐欄位不可見、未勾同意送出會出現紅色提示。（後端串接於 Task 6 整合腳本驗證。）

- [ ] **Step 4: Commit**

```bash
git add land-page/survey.html land-page/index.html
git commit -m "feat(land-page): 新增課程興趣調查問卷頁並於導覽連入" -- land-page/survey.html land-page/index.html
```

---

## Task 6: PowerShell 整合驗證腳本

**Files:**
- Create: `survey-backend/scripts/test-survey-api.ps1`

- [ ] **Step 1: 建立整合測試腳本**

`survey-backend/scripts/test-survey-api.ps1`：

```powershell
# 問卷後端整合驗證：POST 一筆 → 用 admin 金鑰 GET → 檢查存在；並測 consent/蜜罐
# 用法：先啟動後端，再執行
#   $env:ADMIN_API_KEY = "dev-admin-key"; pwsh ./survey-backend/scripts/test-survey-api.ps1 -BaseUrl http://127.0.0.1:8080
param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [string]$AdminKey = $(if ($env:ADMIN_API_KEY) { $env:ADMIN_API_KEY } else { "dev-admin-key" })
)
$ErrorActionPreference = "Stop"
$mark = "test-$(Get-Random)@example.com"

# 1. 合法問卷 → 預期 201
$body = @{ email = $mark; name = "測試"; interest = @("RAG 知識庫","MCP"); consent = $true; utm = @{ utm_source="test" } } | ConvertTo-Json
$r = Invoke-WebRequest -Uri "$BaseUrl/api/survey" -Method Post -ContentType "application/json" -Body $body -SkipHttpErrorCheck
if ($r.StatusCode -ne 201) { throw "合法問卷預期 201，實得 $($r.StatusCode)" }
Write-Host "OK 合法問卷 201"

# 2. 未同意 → 預期 400
$bad = @{ email = "x@y.com"; consent = $false } | ConvertTo-Json
$r2 = Invoke-WebRequest -Uri "$BaseUrl/api/survey" -Method Post -ContentType "application/json" -Body $bad -SkipHttpErrorCheck
if ($r2.StatusCode -ne 400) { throw "未同意預期 400，實得 $($r2.StatusCode)" }
Write-Host "OK 未同意 400"

# 3. 蜜罐有值 → 預期 204
$hp = @{ email = "bot@y.com"; consent = $true; website = "spam" } | ConvertTo-Json
$r3 = Invoke-WebRequest -Uri "$BaseUrl/api/survey" -Method Post -ContentType "application/json" -Body $hp -SkipHttpErrorCheck
if ($r3.StatusCode -ne 204) { throw "蜜罐預期 204，實得 $($r3.StatusCode)" }
Write-Host "OK 蜜罐 204"

# 4. 管理 API 無金鑰 → 預期 401
$r4 = Invoke-WebRequest -Uri "$BaseUrl/api/admin/survey" -SkipHttpErrorCheck
if ($r4.StatusCode -ne 401) { throw "無金鑰預期 401，實得 $($r4.StatusCode)" }
Write-Host "OK 無金鑰 401"

# 5. 管理 API 有金鑰 → 預期 200 且含剛才那筆 email
$r5 = Invoke-WebRequest -Uri "$BaseUrl/api/admin/survey" -Headers @{ "X-Admin-Key" = $AdminKey } -SkipHttpErrorCheck
if ($r5.StatusCode -ne 200) { throw "有金鑰預期 200，實得 $($r5.StatusCode)" }
if ($r5.Content -notmatch [regex]::Escape($mark)) { throw "名單中找不到剛送出的 $mark" }
Write-Host "OK 管理查詢含剛送出資料"

Write-Host "`n全部通過 ✅"
```

- [ ] **Step 2: 本機跑整合測試（需本機 PostgreSQL + 啟動後端）**

```powershell
# 起本機 PostgreSQL（若無）：docker run -d --name survey-pg -e POSTGRES_PASSWORD=password -e POSTGRES_DB=survey -p 5432:5432 postgres:16
cd survey-backend; $env:JAVA_HOME="D:\java\jdk-21"; $env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run   # 另開一個視窗
# 回到 repo 根目錄執行：
pwsh ./survey-backend/scripts/test-survey-api.ps1 -BaseUrl http://127.0.0.1:8080
```
Expected: 終端輸出 5 個 OK 與「全部通過 ✅」。

- [ ] **Step 3: Commit**

```bash
git add survey-backend/scripts/test-survey-api.ps1
git commit -m "test(survey-backend): PowerShell 整合驗證腳本" -- survey-backend/scripts/test-survey-api.ps1
```

---

## Task 7: 部署到 Zeabur（PostgreSQL + survey-backend 服務）

**Files:**（無程式碼；操作步驟。部分需 Dashboard 手動，如同 land-page/teaching-site 的 Root Directory。）

- [ ] **Step 1: push 目前進度到 GitHub**

```bash
git push origin main
```
Expected: 推送成功（Zeabur 之後會從 main 取得 survey-backend 子目錄）。

- [ ] **Step 2: 在專案部署 PostgreSQL**

用 `zeabur-template-deploy` 技能，把 PostgreSQL 部署到專案 `6a3483c107afd8c0435e56c0`。記下它提供的連線資訊（host/port/db/user/password 或連線字串環境變數）。

- [ ] **Step 3: 建立 survey-backend git 服務**

```bash
npx zeabur@latest service deploy --json -i=false \
  --project-id 6a3483c107afd8c0435e56c0 \
  --template GIT --repo-id 1269237950 --branch-name main \
  --name survey-backend
```
記下回傳的 service id。

- [ ] **Step 4: 設定環境變數**

用 `zeabur-variables` 技能在 survey-backend 服務設定（值參照 Step 2 的 PostgreSQL）：
- `JDBC_URL`（例：`jdbc:postgresql://<pg-host>:5432/<db>`）
- `DB_USERNAME`、`DB_PASSWORD`
- `ADMIN_API_KEY`（自訂一組強隨機字串）
- `APP_CORS_ALLOWED_ORIGINS=https://springai.world`

> 跨服務連線值請在 Zeabur Dashboard 用變數參照設定（CLI 對 `${...}` 參照不可靠，見 zeabur-variables 技能說明）。

- [ ] **Step 5: 設定 Root Directory 並產生網域（手動）**

在 Dashboard → 專案 → 服務 survey-backend → Settings → **Root Directory** 填 `survey-backend` → 儲存（自動重建）。
重建為 Java/Maven。完成後產生網域：
```bash
npx zeabur@latest domain create --id <service-id> --env-id <env-id> -g --domain springai-survey-api -y -i=false --json
```

- [ ] **Step 6: 對線上後端跑整合腳本**

```powershell
pwsh ./survey-backend/scripts/test-survey-api.ps1 -BaseUrl https://springai-survey-api.zeabur.app -AdminKey "<你設定的 ADMIN_API_KEY>"
```
Expected: 5 個 OK 與「全部通過 ✅」。

- [ ] **Step 7: 把 survey.html 的 API_BASE 指向線上後端並上線**

把 `land-page/survey.html` 的 `const API_BASE = 'http://127.0.0.1:8080';` 改為 `const API_BASE = 'https://springai-survey-api.zeabur.app';`，commit + push：
```bash
git commit -m "chore(land-page): survey.html 指向線上 survey-backend" -- land-page/survey.html
git push origin main
```
Expected: land-page 自動重建；`https://springai.world/survey` 送出問卷後出現感謝訊息，且 `GET /api/admin/survey` 看得到該筆。

---

## Self-Review 對照

- **Spec 架構（survey.html→backend→PostgreSQL）**：Task 1/2/5/7 涵蓋。✅
- **資料模型（jsonb interest/utm、consent、unsubscribed、created_at）**：Task 2 migration + entity 一致。✅
- **POST /api/survey（驗證、consent 必 true、蜜罐 204）**：Task 3 含對應測試。✅
- **GET /api/admin/survey（X-Admin-Key、CSV+BOM、401）**：Task 3 controller + 測試。✅
- **GET /api/health**：Task 1。✅
- **CORS 只允許 springai.world**：Task 4 + Task 7 env var。✅
- **ProblemDetail 錯誤**：Task 3 ApiExceptionHandler。✅
- **survey.html（視覺、UTM 帶入、survey_submit 事件、PDPA、蜜罐隱藏）**：Task 5。✅
- **部署（PostgreSQL 模板、git 服務、Root Directory、env、網域）**：Task 7。✅
- **驗證腳本**：Task 6。✅
- **型別一致**：`SurveyRequest`/`SurveyResponse` 欄位名一致；`findAllByOrderByCreatedAtDesc` 在 Task 2 定義、Task 3 使用。✅
- **YAGNI（不含寄信）**：全計畫無寄信，符合留給 C。✅
