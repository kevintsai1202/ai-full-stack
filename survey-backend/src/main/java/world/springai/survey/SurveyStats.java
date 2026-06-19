package world.springai.survey;

import java.util.List;

/**
 * 問卷即時聚合統計（公開端點回傳用）。
 * 僅含各題選項的「計數」，不包含任何個資（Email、稱呼等），可安全對外公開。
 *
 * @param total    目前總填寫筆數
 * @param interest 「想學主題」各選項計數（依數量由多到少）
 * @param status   「目前狀態」各選項計數（依數量由多到少）
 * @param role     「身分／職業」計數（取前幾名）
 */
public record SurveyStats(long total, List<Bucket> interest, List<Bucket> status, List<Bucket> role) {
    /**
     * 單一統計分桶：一個選項標籤對應一個計數。
     *
     * @param label 選項文字
     * @param count 該選項被選次數
     */
    public record Bucket(String label, long count) {}
}
