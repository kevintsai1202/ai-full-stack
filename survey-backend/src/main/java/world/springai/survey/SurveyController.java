package world.springai.survey;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 問卷收集與管理查詢端點 */
@RestController
public class SurveyController {
    private final SurveyResponseRepository repository;
    private final ObjectMapper objectMapper;
    private final UnsubscribeTokenService tokenService;     // 退訂 token 驗證
    private final WelcomeMailService welcomeMailService;    // 問卷送出後寄歡迎信
    private final AdminKeyGuard adminKeyGuard;              // 集中管理 X-Admin-Key 驗證

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

    /** 接收問卷；蜜罐有值則略過寫入（回 204），否則驗證後寫入（回 201） */
    @PostMapping("/api/survey")
    public ResponseEntity<Void> submit(@Valid @RequestBody SurveyRequest req) {
        if (StringUtils.hasText(req.getWebsite())) {
            return ResponseEntity.noContent().build();
        }
        SurveyResponse entity = new SurveyResponse();
        // 去除前後空白後寫入，確保與退訂端點的比對基準一致（退訂另以 lower() 處理大小寫）
        entity.setEmail(req.getEmail().trim());
        entity.setName(req.getName());
        entity.setRole(req.getRole());
        entity.setExperience(req.getExperience());
        entity.setFrontendExperience(req.getFrontendExperience());
        entity.setAnswers(req.getAnswers());
        entity.setInterest(req.getInterest());
        entity.setBudget(req.getBudget());
        entity.setUtm(req.getUtm());
        entity.setConsent(req.isConsent());
        repository.save(entity);
        // 寫入成功後寄歡迎信；sendWelcome 內部已 try/catch，失敗不影響此回應
        welcomeMailService.sendWelcome(entity.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** 管理用：列出全部問卷，需 X-Admin-Key；format=csv 回 CSV */
    @GetMapping("/api/admin/survey")
    public ResponseEntity<?> list(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                  @RequestParam(value = "format", required = false) String format) {
        // 委由 AdminKeyGuard 以固定時間比對，避免 timing attack；不符拋 401
        adminKeyGuard.verify(key);
        List<SurveyResponse> all = repository.findAllByOrderByCreatedAtDesc();
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(toCsv(all));
        }
        return ResponseEntity.ok(all);
    }

    /**
     * 公開即時統計：回傳總填寫數與各題選項計數，供問卷頁右側圖表使用。
     * 僅輸出聚合計數，不含任何個資，可安全公開（無需金鑰）。
     */
    @GetMapping("/api/survey/stats")
    public SurveyStats stats() {
        List<SurveyResponse> all = repository.findAllByOrderByCreatedAtDesc();
        // 想學主題：interest 為複選陣列，攤平後計數（不限筆數）
        Stream<String> interest = all.stream()
            .filter(r -> r.getInterest() != null)
            .flatMap(r -> r.getInterest().stream());
        // 目前狀態：取 answers 內的 status 單選值
        Stream<String> status = all.stream()
            .map(r -> r.getAnswers() == null ? null : r.getAnswers().get("status"))
            .filter(Objects::nonNull)
            .map(String::valueOf);
        // 身分職業：role 欄位，取前 6 名避免圖表過長
        Stream<String> role = all.stream().map(SurveyResponse::getRole);
        return new SurveyStats(all.size(), buckets(interest, 99), buckets(status, 99), buckets(role, 6));
    }

    /**
     * 公開退訂端點：使用者從行銷信件點擊退訂連結（GET）後以瀏覽器開啟，故回 HTML。
     * 連結形如 /api/survey/unsubscribe?email=<email>&t=<HMAC token>。
     * 設計重點：
     *  1. 防偽——僅當 t 為該 email 的合法 HMAC 簽章才執行退訂。
     *  2. 冪等——已退訂者再點、或名單查無此 email，都回相同成功頁，不報錯。
     *  3. 不洩漏名單——不論結果（含 token 不符）一律回相同訊息與 200。
     *  4. 回應頁為固定字串、不回顯使用者輸入，避免 XSS。
     */
    @GetMapping(value = "/api/survey/unsubscribe", produces = "text/html; charset=UTF-8")
    public ResponseEntity<String> unsubscribe(@RequestParam(value = "email", required = false) String email,
                                              @RequestParam(value = "t", required = false) String token) {
        // 僅在 email 有值且 token 通過驗證時才退訂；其餘情況靜默略過但仍回同一頁
        if (StringUtils.hasText(email) && tokenService.verify(email, token)) {
            repository.unsubscribeByEmail(email.trim());
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
            .body(UNSUBSCRIBE_HTML);
    }

    /** 退訂成功頁（固定內容，不含使用者輸入）；中文提示，置中簡潔樣式 */
    private static final String UNSUBSCRIBE_HTML = """
            <!doctype html>
            <html lang="zh-Hant">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>已取消訂閱</title>
              <style>
                body { font-family: system-ui, "Microsoft JhengHei", sans-serif; background: #f7f8fa;
                       display: flex; min-height: 100vh; margin: 0; align-items: center; justify-content: center; }
                .card { background: #fff; padding: 2.5rem 2rem; border-radius: 12px; max-width: 420px;
                        box-shadow: 0 8px 30px rgba(0,0,0,.08); text-align: center; }
                h1 { font-size: 1.4rem; margin: 0 0 .75rem; color: #1a1a2e; }
                p { color: #555; line-height: 1.6; margin: 0; }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>您已成功取消訂閱</h1>
                <p>我們不會再寄送行銷訊息給您。<br>若這是誤點，重新填寫問卷即可再次訂閱。</p>
              </div>
            </body>
            </html>
            """;

    /** 將字串串流計數後，去除空白值，依數量由多到少排序並取前 limit 名 */
    private List<SurveyStats.Bucket> buckets(Stream<String> values, int limit) {
        Map<String, Long> counts = values
            .filter(v -> v != null && !v.isBlank() && !"null".equals(v))
            .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        return counts.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(limit)
            .map(e -> new SurveyStats.Bucket(e.getKey(), e.getValue()))
            .toList();
    }

    /** 組成 CSV，前置 UTF-8 BOM 讓 Excel 正確判讀編碼 */
    private String toCsv(List<SurveyResponse> rows) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("id,email,name,role,experience,frontend_experience,interest,budget,answers,consent,unsubscribed,created_at\n");
        for (SurveyResponse r : rows) {
            sb.append(r.getId()).append(',')
              .append(csv(r.getEmail())).append(',')
              .append(csv(r.getName())).append(',')
              .append(csv(r.getRole())).append(',')
              .append(csv(r.getExperience())).append(',')
              .append(csv(r.getFrontendExperience())).append(',')
              .append(csv(r.getInterest() == null ? "" : String.join("|", r.getInterest()))).append(',')
              .append(csv(r.getBudget())).append(',')
              .append(csv(toJson(r.getAnswers()))).append(',')
              .append(r.isConsent()).append(',')
              .append(r.isUnsubscribed()).append(',')
              .append(csv(r.getCreatedAt() == null ? "" : r.getCreatedAt().toString())).append('\n');
        }
        return sb.toString();
    }

    /** 把 answers map 轉成 JSON 字串供 CSV 欄位使用；失敗或為空回空字串 */
    private String toJson(java.util.Map<String, Object> m) {
        if (m == null || m.isEmpty()) return "";
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return "";
        }
    }

    /** CSV 欄位跳脫：含逗號/引號/換行(CR/LF)時用雙引號包並把內部引號加倍（RFC 4180） */
    private String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
