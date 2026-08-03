package world.springai.survey.form;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 投票統計與 campaign 歸因分析服務：彙總 {@code survey_vote} 選項具名／匿名分布，
 * 以及各電子報活動的投票→完填轉換率。
 *
 * <p>轉換率的分母是該 campaign 在 {@code survey_vote} 的票數，分子是
 * {@code audience_record}（{@code source_key='newsletter_survey'}，即 Task 10
 * {@link NewsletterSubmissionService} 寫入的電子報通道完整填答）中
 * {@code raw_data->>'campaignId'} 相符的紀錄數。全部聚合皆為純 SQL {@code GROUP BY}，
 * 沒有可供 mock 的 entity，因此以 5433 PG 整合測試驗證（見
 * {@code SurveyVoteStatsServiceTest}）。</p>
 */
@Service
public class SurveyVoteStatsService {

    private final JdbcTemplate jdbc;

    /** 注入 JdbcTemplate；統計走純 SQL 聚合，不透過 JPA entity。 */
    public SurveyVoteStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 彙總指定表單的投票統計。表單完全沒有票時回零值結構（options／byCampaign 皆為
     * 空陣列、total 為 0），不拋 404——沒資料的圖表仍要能正常渲染空狀態。
     */
    public Map<String, Object> voteStats(String formKey) {
        List<Map<String, Object>> options = optionBreakdown(formKey);
        List<Map<String, Object>> byCampaign = campaignBreakdown(formKey);

        long totalVotes = options.stream()
            .mapToLong(row -> ((Number) row.get("named")).longValue()
                + ((Number) row.get("anon")).longValue())
            .sum();
        long totalNamed = options.stream()
            .mapToLong(row -> ((Number) row.get("named")).longValue())
            .sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("options", options);
        result.put("byCampaign", byCampaign);
        result.put("totalVotes", totalVotes);
        result.put("totalNamed", totalNamed);
        return result;
    }

    /** 依選項聚合具名（RECIPIENT／READER）與匿名（ANON）票數，依總票數由高到低排序。 */
    private List<Map<String, Object>> optionBreakdown(String formKey) {
        return jdbc.query("""
            SELECT option_value,
                   count(*) FILTER (WHERE identity_type <> 'ANON') AS named,
                   count(*) FILTER (WHERE identity_type = 'ANON') AS anon
              FROM survey_vote
             WHERE form_key = ?
             GROUP BY option_value
             ORDER BY count(*) DESC, option_value
            """, (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("value", rs.getString("option_value"));
                row.put("named", rs.getLong("named"));
                row.put("anon", rs.getLong("anon"));
                return row;
            }, formKey);
    }

    /**
     * 依 campaign 聚合票數、完整填答數與投票→完填轉換率；未歸因（campaign_id 為
     * null）的票不計入 byCampaign。
     */
    private List<Map<String, Object>> campaignBreakdown(String formKey) {
        List<Map<String, Object>> votesByCampaign = jdbc.query("""
            SELECT campaign_id, count(*) AS votes
              FROM survey_vote
             WHERE form_key = ? AND campaign_id IS NOT NULL
             GROUP BY campaign_id
             ORDER BY campaign_id
            """, (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("campaignId", rs.getLong("campaign_id"));
                row.put("votes", rs.getLong("votes"));
                return row;
            }, formKey);
        if (votesByCampaign.isEmpty()) {
            return List.of();
        }

        // 完整填答數：電子報通道問卷提交（source=newsletter_survey），schema_key 的
        // form_key 前綴需與本表單一致，campaignId 取 raw_data 內的原值比對。
        Map<Long, Long> submissionsByCampaign = new LinkedHashMap<>();
        RowCallbackHandler collectSubmissions = rs -> submissionsByCampaign.put(
            rs.getLong("campaign_id"), rs.getLong("submissions"));
        jdbc.query("""
            SELECT (raw_data ->> 'campaignId')::bigint AS campaign_id, count(*) AS submissions
              FROM audience_record
             WHERE record_type = 'survey_submission'
               AND source_key = 'newsletter_survey'
               AND split_part(schema_key, '@', 1) = ?
               AND raw_data ->> 'campaignId' IS NOT NULL
             GROUP BY (raw_data ->> 'campaignId')::bigint
            """, collectSubmissions, formKey);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : votesByCampaign) {
            long campaignId = ((Number) row.get("campaignId")).longValue();
            long votes = ((Number) row.get("votes")).longValue();
            long submissions = submissionsByCampaign.getOrDefault(campaignId, 0L);
            Map<String, Object> merged = new LinkedHashMap<>();
            merged.put("campaignId", campaignId);
            merged.put("votes", votes);
            merged.put("submissions", submissions);
            merged.put("conversionRate", votes == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(submissions)
                    .divide(BigDecimal.valueOf(votes), 3, RoundingMode.HALF_UP));
            result.add(merged);
        }
        return result;
    }
}
