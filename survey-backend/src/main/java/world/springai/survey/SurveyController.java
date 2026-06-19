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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
            return ResponseEntity.noContent().build();
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
              .append(csv(r.getCreatedAt() == null ? "" : r.getCreatedAt().toString())).append('\n');
        }
        return sb.toString();
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
