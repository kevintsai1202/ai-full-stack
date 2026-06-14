# GOAP Action 表（提示詞②的期望輸出範例）

> 用途：對照「AI 輔助提示詞②」的產出格式；也可直接餵給提示詞③生成 Java 骨架。

## 案例：客戶活動摘要與個人化方案

| Action | Preconditions（需要） | Postconditions（產生） | 執行者 |
|---|---|---|---|
| fetchActivity | CustomerQuery | TravellerActivity | 純程式（呼叫既有報表服務） |
| summarize | TravellerActivity | ActivitySummary | LLM（敘事；數字來自 @Tool） |
| proposeOffer | ActivitySummary | OfferDraft | LLM（含政策文件 RAG） |
| reviewOffer | OfferDraft | ReviewedOffer | 純程式規則 + 強模型覆核 |
| escalateToHuman | OfferDraft（審核未過 2 次） | HumanTicket | 純程式 |

## Goal

- blackboard 出現 `ReviewedOffer`。

## Replanning 情境

- `fetchActivity` 查無客戶 → 不產生 `TravellerActivity` → 後續 preconditions 不滿足，流程停在可解釋的失敗點。
- `reviewOffer` 未通過 → 無 `ReviewedOffer` → planner 重跑 `proposeOffer`；連續兩次未過改走 `escalateToHuman`。
