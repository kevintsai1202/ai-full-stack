# 測試與上線檢查表（提示詞⑦的期望輸出範例）

> 用途：對照「AI 輔助提示詞⑦」的產出格式；也可直接作為案例專案的驗收清單。

## 測試

### fetchActivity（純程式）
- [ ] mock `TravelActivityReportingService`，查得到客戶時回傳 `TravellerActivity`
- [ ] 查無客戶時回傳 null / 不產生物件（planner 應停在可解釋失敗點）

### summarize（LLM action — 測 prompt 組裝，不斷言輸出字串）
- [ ] prompt 帶入 `max-words` 設定值
- [ ] prompt 帶入高消費 / 常旅客門檻
- [ ] 只暴露 `TravellerActivity` 的 @Tool（最小工具暴露）
- [ ] 使用預設 LLM 角色（非審核用強模型）

### reviewOffer（規則）
- [ ] 折扣超過 10% 的 OfferDraft 必須被擋下
- [ ] 90 天內已收過方案的客戶必須被擋下
- [ ] 通過者產生 `ReviewedOffer` 並記錄 approvedBy

### 整條 flow（整合測試）
- [ ] `CustomerQuery` 進 → `ReviewedOffer` 出
- [ ] 每一步產生 ActionAudit 事件

## 觀測（上線後）

- [ ] 記錄 action execution（輸入/輸出/耗時）
- [ ] 記錄 LLM call（模型、token cost）
- [ ] 記錄 tool invocation（工具名、參數、結果）
- [ ] 客訴回溯 SOP：依客戶 ID 查 AgentProcess → 逐 action 檢視 audit

## Guardrail

- [ ] 折扣上限檢查放在 reviewOffer 的「輸入檢查」側
- [ ] 摘要字數上限放在 summarize 的「輸出檢查」側
