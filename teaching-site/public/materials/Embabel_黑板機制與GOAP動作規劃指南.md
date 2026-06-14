# Embabel 黑板機制與 GOAP 動作規劃指南

本講義為「AI 賦能全端開發」課程的 Embabel 智慧代理 (Agent) 開發實務教材。

## 什麼是 Embabel 框架？
Embabel 是一個針對 Java/Kotlin 設計的型別安全 Agent 框架。與傳統只能做線性對話或 hard-coded 連接器的 pipelines 不同，Embabel 導入了目標導向的動作規劃 (GOAP, Goal-Oriented Action Planning)。

## 核心機制

### 1. Blackboard (黑板)
黑板是 Agent 執行的共用工作記憶體 (Working Memory)。所有的環境狀態、輸入參數與中間結果，都會在執行過程中被寫入 Blackboard，供各個 `@Action` 進行共享與動態修改。

### 2. GOAP 規劃器 (Planner)
1. **Goal (目標)**：我們給 Agent 設定一個最終目標（例如：一份安全且審核通過的客戶銷售建議）。
2. **Action (動作)**：我們向 Planner 註冊一組 Actions。每一個 Action 都有特定的前提條件 (Preconditions) 與產出效果 (Effects)。
3. **路徑規劃**：Planner 會動態運算出一條從當前 World State 達到最終 Goal 的最佳 Action 鏈條。

### 3. Replanning (重新規劃)
如果在執行某個步驟（例如「合規性安全審核」）時判定失敗，或者中途發現資料庫數據不全，Agent 會自動進行 Replanning，修改 Blackboard 狀態並動態尋找其他替代 Action（例如重新讀取資料或降低折扣），而不是直接崩潰退出。

## Java 實作 Record 範例
使用 Java 21 Record 實作型別安全與結構化的 Action 參數定義：
```java
public record CustomerSnapshot(
    Long customerId,
    String name,
    List<String> tags
) {}

public record OpportunityRisk(
    double riskScore,
    String riskFactor,
    String recommendation
) {}
```
這使得每一次 LLM 決策與工具調用都能維持型別約束，大幅降低程式碼出錯機率。
