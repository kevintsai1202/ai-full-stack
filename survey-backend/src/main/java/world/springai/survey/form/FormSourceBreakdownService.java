package world.springai.survey.form;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import world.springai.survey.audience.AudienceSourceService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 彈性表單的「來源分佈」統計：一份表單的提交紀錄實際來自哪些 {@code source_key}，
 * 各有多少筆，其中多少筆帶有真實問卷答案。
 *
 * <p><b>為什麼需要這支服務</b>：{@code audience_record} 裡 {@code record_type=
 * 'survey_submission'} 的紀錄不全是問卷填答——考試、Dify、電子報通道都會為了掛人物
 * 身分而建立同型別的空殼紀錄（{@code answers} 為 {@code {}}）。分析頁只顯示總筆數時，
 * 管理員無從得知 612 筆之中只有 61 筆真的填過問卷。本服務把兩者分開計數。</p>
 *
 * <p><b>「有答案」的判定刻意不採「answers 非空」</b>：{@code newsletter} 來源的紀錄
 * 帶有 {@code _ref}（推薦碼），那是歸因用的中繼資料而非問卷答案，用非空判定會讓它們
 * 被誤算成真實填答。這裡改為比對 schema 實際定義的欄位 key，是精確判定，
 * 也不依賴「底線前綴代表中繼資料」這種命名慣例。</p>
 */
@Service
public class FormSourceBreakdownService {

    /**
     * 程式自動寫入、不會出現在匯入來源註冊表的來源標籤。
     *
     * <p>{@code AudienceSourceService} 管的是管理員手動登記的匯入來源；
     * 這兩個是程式在提交流程中直接寫入的，永遠不會出現在那份清單裡，
     * 但管理員在畫面上同樣需要看懂它們是什麼。</p>
     */
    private static final Map<String, String> BUILTIN_LABELS = Map.of(
        "newsletter_survey", "讀者接續填答",
        "newsletter", "電子報通道");

    private final JdbcTemplate jdbc;
    private final FormSchemaService forms;
    private final AudienceSourceService sources;

    /** 注入 SQL、表單 schema 解析與名單來源標籤。 */
    public FormSourceBreakdownService(
            JdbcTemplate jdbc, FormSchemaService forms, AudienceSourceService sources) {
        this.jdbc = jdbc;
        this.forms = forms;
        this.sources = sources;
    }

    /** 單一來源的統計列；label 供畫面顯示，key 供「來源」篩選器帶入查詢。 */
    public record SourceRow(String key, String label, long total, long answered) {}

    /** 全部來源的加總；與 analytics 的 submissions 對照可看出空殼紀錄佔比。 */
    public record Totals(long total, long answered) {}

    /** 來源分佈結果。 */
    public record Breakdown(List<SourceRow> sources, Totals totals) {}

    /**
     * 統計指定表單各來源的提交筆數與有答案筆數。
     *
     * <p>篩選參數與 {@link FormSchemaService#analytics} 同語意，唯獨沒有 {@code source}
     * ——這支端點的職責就是列出所有來源，再給它一個來源篩選沒有意義。</p>
     *
     * @param allVersions 為 true 時合併該表單全部版本，欄位 key 取各版本聯集，
     *                    與 {@code analytics} 合併版本欄位的行為一致
     */
    public Breakdown breakdown(
            String formKey,
            Integer version,
            boolean allVersions,
            OffsetDateTime from,
            OffsetDateTime to,
            Long campaignId) {
        // 版本解析一律委派 FormSchemaService，不在這裡重寫一份「哪個版本算數」的邏輯：
        // 同一個問題有兩份實作，正是本次要修的缺陷成因。
        FormSchemaService.FormDefinition selected = forms.getDefinition(formKey, version);
        List<FormSchemaService.FormDefinition> definitions = allVersions
            ? forms.listDefinitions().stream().filter(form -> form.key().equals(formKey)).toList()
            : List.of(selected);
        List<String> schemaKeys = definitions.stream().map(forms::schemaKey).toList();
        List<String> fieldKeys = definitions.stream()
            .flatMap(form -> form.fields().stream())
            .map(FormSchemaService.FieldDefinition::key)
            .distinct()
            .toList();

        Map<String, String> labels = resolveLabels();
        List<SourceRow> rows = query(schemaKeys, fieldKeys, from, to, campaignId).stream()
            .map(row -> new SourceRow(
                row.key(),
                labels.getOrDefault(row.key(), row.key()),
                row.total(),
                row.answered()))
            .toList();

        long total = rows.stream().mapToLong(SourceRow::total).sum();
        long answered = rows.stream().mapToLong(SourceRow::answered).sum();
        return new Breakdown(rows, new Totals(total, answered));
    }

    /** 尚未套上顯示標籤的原始聚合列。 */
    private record RawRow(String key, long total, long answered) {}

    /**
     * 標籤解析順序：匯入來源註冊表 → 內建對照表 → 退回原始 key（由呼叫端 getOrDefault 處理）。
     *
     * <p>先放內建、再放註冊表，讓管理員自訂的名稱能覆蓋內建值。</p>
     */
    private Map<String, String> resolveLabels() {
        Map<String, String> labels = new LinkedHashMap<>(BUILTIN_LABELS);
        sources.list().forEach(source -> labels.put(source.key(), source.label()));
        return labels;
    }

    /**
     * 依 {@code source_key} 聚合；{@code answered} 只計 {@code answers} 內含至少一個
     * schema 欄位 key 的紀錄。
     *
     * <p>{@code jsonb_object_keys} 遇到非物件會拋錯，而 SQL 不保證 {@code AND} 的求值
     * 順序，因此型別檢查寫成 {@code CASE} 包在函式引數內，而不是併排的 AND 條件。</p>
     */
    private List<RawRow> query(
            List<String> schemaKeys,
            List<String> fieldKeys,
            OffsetDateTime from,
            OffsetDateTime to,
            Long campaignId) {
        if (schemaKeys.isEmpty()) {
            return List.of();
        }
        List<Object> params = new ArrayList<>();
        // 表單沒有任何欄位時不可能有答案；此時用常數 false，避免組出非法的 IN ()
        String answeredCondition = fieldKeys.isEmpty() ? "false" : """
            EXISTS (
                SELECT 1
                  FROM jsonb_object_keys(
                         CASE WHEN jsonb_typeof(raw_data -> 'answers') = 'object'
                              THEN raw_data -> 'answers'
                              ELSE '{}'::jsonb END) AS answer_key
                 WHERE answer_key IN (%s)
            )""".formatted(placeholders(fieldKeys.size()));
        params.addAll(fieldKeys);

        StringBuilder sql = new StringBuilder("""
            SELECT source_key,
                   count(*) AS total,
                   count(*) FILTER (WHERE %s) AS answered
              FROM audience_record
             WHERE record_type = 'survey_submission'
               AND schema_key IN (%s)
            """.formatted(answeredCondition, placeholders(schemaKeys.size())));
        params.addAll(schemaKeys);
        if (from != null) {
            sql.append(" AND occurred_at >= ?");
            params.add(from);
        }
        if (to != null) {
            sql.append(" AND occurred_at < ?");
            params.add(to);
        }
        if (campaignId != null) {
            sql.append(" AND raw_data ->> 'campaignId' = ?");
            params.add(String.valueOf(campaignId));
        }
        // 排序帶上 source_key 當第二鍵，筆數相同時順序才穩定，畫面不會每次重整就跳動
        sql.append(" GROUP BY source_key ORDER BY count(*) DESC, source_key");

        return jdbc.query(sql.toString(), (rs, rowNum) -> new RawRow(
            rs.getString("source_key"),
            rs.getLong("total"),
            rs.getLong("answered")), params.toArray());
    }

    /** 產生 n 個以逗號分隔的 JDBC 佔位符。 */
    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
