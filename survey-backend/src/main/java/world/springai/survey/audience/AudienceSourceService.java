package world.springai.survey.audience;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import world.springai.survey.AppSettingService;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 名單來源管理：保留系統預設來源，並將管理員新增的來源持久化到 app_setting。
 *
 * <p>來源清單屬於營運設定，不值得為它新增資料表；使用既有 app_setting 可讓新增項目
 * 立即生效，也能在不同裝置與重新部署後繼續使用。</p>
 */
@Service
public class AudienceSourceService {

    /** 自訂來源存放於 app_setting 的鍵。 */
    static final String SETTING_KEY = "audience.sources";
    /** 單一來源名稱長度上限，避免後台選單與報表被過長文字撐開。 */
    static final int LABEL_MAX_LENGTH = 40;
    /** 自訂來源數量上限，防止設定值無限制膨脹。 */
    static final int CUSTOM_SOURCE_LIMIT = 100;

    /** 系統既有來源；鍵值不可更改，否則舊資料與統計條件會失聯。 */
    private static final List<SourceOption> DEFAULTS = List.of(
            new SourceOption("survey_form", "問卷填寫"),
            new SourceOption("exam", "線上測驗"),
            new SourceOption("dify", "Dify 學員"));

    private final AppSettingService settings;
    private final ObjectMapper objectMapper;

    /** 注入共用設定服務與 JSON 序列化器。 */
    public AudienceSourceService(AppSettingService settings, ObjectMapper objectMapper) {
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    /** 後台選單使用的來源項目；key 寫入 survey_response.source，label 只供畫面顯示。 */
    public record SourceOption(String key, String label) {}

    /**
     * 列出系統預設與自訂來源。
     *
     * <p>設定損壞時回退到預設來源，不讓名單匯入整頁因單一設定值而無法使用。</p>
     */
    public List<SourceOption> list() {
        Map<String, SourceOption> merged = new LinkedHashMap<>();
        DEFAULTS.forEach(source -> merged.put(source.key(), source));
        for (SourceOption source : readCustomSources()) {
            if (isStoredSourceValid(source)
                    && merged.values().stream().noneMatch(existing ->
                    existing.label().equalsIgnoreCase(source.label()))) {
                merged.putIfAbsent(source.key(), source);
            }
        }
        return List.copyOf(merged.values());
    }

    /**
     * 新增一個可重複使用的名單來源；同名來源不分大小寫視為同一項並直接回傳既有值。
     */
    public SourceOption add(String requestedLabel) {
        String label = normalizeLabel(requestedLabel);
        List<SourceOption> all = list();
        SourceOption existing = all.stream()
                .filter(source -> source.label().equalsIgnoreCase(label))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        List<SourceOption> custom = all.stream()
                .filter(source -> !isDefaultKey(source.key()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        if (custom.size() >= CUSTOM_SOURCE_LIMIT) {
            throw new IllegalArgumentException("自訂名單來源最多 100 個");
        }

        SourceOption created = new SourceOption(uniqueKey(label, all), label);
        custom.add(created);
        writeCustomSources(custom);
        return created;
    }

    /** 清理並驗證顯示名稱；允許中文與常用符號，只拒絕空白、控制字元與過長內容。 */
    private String normalizeLabel(String requestedLabel) {
        String label = requestedLabel == null ? "" : requestedLabel.trim().replaceAll("\\s+", " ");
        if (!StringUtils.hasText(label)) {
            throw new IllegalArgumentException("請輸入名單來源名稱");
        }
        if (label.length() > LABEL_MAX_LENGTH) {
            throw new IllegalArgumentException("名單來源名稱不可超過 40 個字");
        }
        if (label.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("名單來源名稱不可包含控制字元");
        }
        return label;
    }

    /** 產生只供系統儲存的穩定鍵；畫面永遠顯示 label，不要求管理員理解代碼。 */
    private String uniqueKey(String label, List<SourceOption> existing) {
        String ascii = Normalizer.normalize(label, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        String base = "custom-" + (ascii.isBlank()
                ? Integer.toUnsignedString(label.toLowerCase(Locale.ROOT).hashCode(), 36)
                : ascii);
        String candidate = base;
        int suffix = 2;
        while (containsKey(existing, candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    /** 檢查來源鍵是否已存在。 */
    private boolean containsKey(List<SourceOption> sources, String key) {
        return sources.stream().anyMatch(source -> source.key().equalsIgnoreCase(key));
    }

    /** 是否為不可刪改的系統預設來源鍵。 */
    private boolean isDefaultKey(String key) {
        return DEFAULTS.stream().anyMatch(source -> source.key().equals(key));
    }

    /** 過濾設定中的壞資料，避免錯誤 JSON 內容污染選單。 */
    private boolean isStoredSourceValid(SourceOption source) {
        return source != null
                && StringUtils.hasText(source.key())
                && StringUtils.hasText(source.label())
                && source.label().length() <= LABEL_MAX_LENGTH;
    }

    /** 從 app_setting 讀取自訂來源；無設定或解析失敗時回空清單。 */
    private List<SourceOption> readCustomSources() {
        String raw = settings.get(SETTING_KEY);
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<SourceOption>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /** 將自訂來源清單序列化後寫入 app_setting。 */
    private void writeCustomSources(List<SourceOption> custom) {
        try {
            settings.set(SETTING_KEY, objectMapper.writeValueAsString(custom));
        } catch (Exception exception) {
            throw new IllegalStateException("無法儲存名單來源", exception);
        }
    }
}
