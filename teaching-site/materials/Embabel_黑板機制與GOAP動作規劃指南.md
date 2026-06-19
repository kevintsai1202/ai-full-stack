# Embabel 黑板機制與 GOAP 動作規劃指南

> 本文件為選修附錄。Embabel 0.4.x 目前基於 Spring Boot 3.5.x，待發布相容 Spring AI 2.0 GA 的新版本後可升級為正式單元。

## 黑板架構 (Blackboard)

黑板是一種共享記憶體模式，讓多個 Agent 元件可讀寫同一份狀態：

```
+-------------------------------------------+
|                Blackboard                   |
|  - customerContext                         |
|  - currentTask                              |
|  - extractedEntities                        |
|  - lastActionResult                         |
+-------------------------------------------+
         ↑ 讀/寫      ↑ 讀/寫
    ┌─────────┐  ┌─────────┐
    │ Planner  │  │ Executor │
    └─────────┘  └─────────┘
```

## GOAP 演算法

Goal-Oriented Action Planning 將任務拆解為：

1. **目標 (Goal)**：例如「取得客戶完整資訊」
2. **動作 (Action)**：查詢基本資料、查詢聯絡紀錄、查詢商機
3. **計畫 (Plan)**：排列動作順序達成目標
4. **重規劃 (Replan)**：當環境變動時重新計算計畫

## Embabel 使用範例

```java
Blackboard blackboard = new Blackboard();
blackboard.set("customerId", 123L);

Planner planner = new Planner(blackboard);
Goal goal = new Goal("查詢客戶完整資訊");
Plan plan = planner.plan(goal);
plan.execute();
```
