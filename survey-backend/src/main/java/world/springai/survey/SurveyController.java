package world.springai.survey;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 問卷收集與管理查詢端點 */
@RestController
public class SurveyController {
    private final SurveyResponseRepository repository;
    private final String adminApiKey;
    private final ObjectMapper objectMapper;

    /** 注入資料層、管理金鑰與 JSON 序列化器（CSV 匯出 answers 用） */
    public SurveyController(SurveyResponseRepository repository,
                            @Value("${app.admin-api-key}") String adminApiKey,
                            ObjectMapper objectMapper) {
        this.repository = repository;
        this.adminApiKey = adminApiKey;
        this.objectMapper = objectMapper;
    }

    /** 接收問卷；蜜罐有值則略過寫入（回 204），否則驗證後寫入（回 201） */
    @PostMapping("/api/survey")
    public ResponseEntity<Void> submit(@Valid @RequestBody SurveyRequest req) {
        if (StringUtils.hasText(req.getWebsite())) {
            return ResponseEntity.noContent().build();
        }
        SurveyResponse entity = new SurveyResponse();
        entity.setEmail(req.getEmail());
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
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** 管理用：列出全部問卷，需 X-Admin-Key；format=csv 回 CSV */
    @GetMapping("/api/admin/survey")
    public ResponseEntity<?> list(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                  @RequestParam(value = "format", required = false) String format) {
        // 用固定時間比對避免 timing attack
        if (key == null || !MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8), adminApiKey.getBytes(StandardCharsets.UTF_8))) {
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
