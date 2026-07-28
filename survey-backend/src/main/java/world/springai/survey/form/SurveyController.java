package world.springai.survey.form;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import world.springai.survey.AdminKeyGuard;
import world.springai.survey.audience.SurveyResponse;
import world.springai.survey.audience.SurveyResponseRepository;
import world.springai.survey.audience.WelcomeMailService;

/** 問卷收集與管理查詢端點 */
@RestController
public class SurveyController {
    /** /api/survey 的 source 白名單：目前唯一接受的非預設值，供 /r/ 電子報訂閱頁使用 */
    private static final String SOURCE_NEWSLETTER = "newsletter";
    /**
     * 邀請歸因在 answers 內的鍵名。
     *
     * <p>底線前綴用於區別「系統欄位」與問卷答案——{@link #stats()} 會排除
     * 所有底線開頭的鍵，否則推薦碼會出現在對外公開的統計圖表裡。</p>
     */
    static final String REF_KEY = "_ref";
    /** 文章分享轉換來源；底線前綴確保不進入公開問卷統計。 */
    static final String SHARE_ARTICLE_KEY = "_share_article";

    private final SurveyResponseRepository repository;
    private final ObjectMapper objectMapper;
    private final WelcomeMailService welcomeMailService;    // 問卷送出後寄歡迎信
    private final AdminKeyGuard adminKeyGuard;              // 集中管理 X-Admin-Key 驗證
    private final SurveySubmissionService submissionService; // 舊端點同步寫入彈性資料模型

    /** 注入資料層、JSON 序列化器、歡迎信、管理金鑰守衛與彈性寫入服務。 */
    public SurveyController(SurveyResponseRepository repository,
                            ObjectMapper objectMapper,
                            WelcomeMailService welcomeMailService,
                            AdminKeyGuard adminKeyGuard,
                            SurveySubmissionService submissionService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.welcomeMailService = welcomeMailService;
        this.adminKeyGuard = adminKeyGuard;
        this.submissionService = submissionService;
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
            // 文章來源只在推薦碼存在時才有歸因意義；格式不合直接忽略，
            // 不因被竄改的分享參數讓合法 email 訂閱整筆失敗。
            if (isSafeArticleSlug(req.getShare())) {
                answers.put(SHARE_ARTICLE_KEY, req.getShare().trim());
            }
            entity.setAnswers(answers);
        }
        entity.setInterest(req.getInterest());
        entity.setBudget(req.getBudget());
        entity.setUtm(req.getUtm());
        entity.setConsent(req.isConsent());
        // source 白名單：僅接受 "newsletter"（/r/ 訂閱頁使用），其餘一律忽略、
        // 沿用 entity 預設值 "survey_form"。不可讓呼叫端任意指定 source，
        // 否則外部能偽造 survey_form 灌水 stats() 對外公開的無金鑰統計數字。
        if (SOURCE_NEWSLETTER.equals(req.getSource())) {
            entity.setSource(SOURCE_NEWSLETTER);
        }
        SurveyResponse saved = repository.save(entity);
        // 舊 API 保留，但資料同時進入新版人物／活動／Fact；交易失敗時不留下半套資料。
        submissionService.mirrorLegacySubmission(saved);
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
        // 只統計問卷填寫來源；外部匯入名單（如線上測驗）不得灌水公開數字
        List<SurveyResponse> all = repository.findAllByOrderByCreatedAtDesc().stream()
            .filter(r -> "survey_form".equals(r.getSource()))
            .toList();
        // 想學主題：interest 為複選陣列，攤平後計數（不限筆數）
        Stream<String> interest = all.stream()
            .filter(r -> r.getInterest() != null)
            .flatMap(r -> r.getInterest().stream());
        // 目前狀態：取 answers 內的 status 單選值
        Stream<String> status = all.stream()
            .map(r -> answerOf(r, "status"))
            .filter(Objects::nonNull)
            .map(String::valueOf);
        // 身分職業：role 欄位，取前 6 名避免圖表過長
        Stream<String> role = all.stream().map(SurveyResponse::getRole);
        return new SurveyStats(all.size(), buckets(interest, 99), buckets(status, 99), buckets(role, 6));
    }

    /**
     * 取出某一題的答案值；底線開頭的系統鍵一律視為不存在。
     *
     * <p>統計是對外公開、無需金鑰的端點，而 answers 內混有系統欄位
     * （目前是邀請歸因 {@link #REF_KEY}）。集中在這個方法過濾，讓日後
     * 新增統計題目不必各自記得排除——忘記一次就是把讀者的邀請關係公開。</p>
     */
    static Object answerOf(SurveyResponse r, String key) {
        if (key.startsWith("_") || r.getAnswers() == null) {
            return null;
        }
        return r.getAnswers().get(key);
    }

    /** 僅接受站內文章 slug，避免把任意長字串或控制字元寫入歸因欄位。 */
    private static boolean isSafeArticleSlug(String value) {
        return StringUtils.hasText(value)
            && value.trim().matches("[a-z0-9][a-z0-9-]{0,159}");
    }

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
        sb.append("id,email,name,role,experience,frontend_experience,interest,budget,answers,utm,consent,unsubscribed,source,created_at\n");
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
              .append(csv(toJson(r.getUtm()))).append(',')
              .append(r.isConsent()).append(',')
              .append(r.isUnsubscribed()).append(',')
              .append(csv(r.getSource())).append(',')
              .append(csv(r.getCreatedAt() == null ? "" : r.getCreatedAt().toString())).append('\n');
        }
        return sb.toString();
    }

    /** 把 answers 或 UTM map 轉成 JSON 字串供 CSV 欄位使用；失敗或為空回空字串 */
    private String toJson(java.util.Map<?, ?> m) {
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
