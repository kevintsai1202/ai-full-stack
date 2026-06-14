# Spring AI 與 Tool Calling 架構說明

本講義為「AI 賦能全端開發」課程的 Spring AI 整合與函數呼叫教材。

## 什麼是 Tool Calling (函數呼叫)？
大語言模型 (LLM) 本身只是一個基於機率預測下一個 Token 的黑盒子，它無法讀取您資料庫中的即時狀態，也無法做高精準度的數學計算。

**Tool Calling** 是一種機制：在呼叫 LLM 時，我們向它提供一組本地 Java 方法（Tools）的宣告。當 LLM 判定它需要這些工具來回答使用者的問題時（例如：「請幫我看看客戶 A 的近況」），LLM 會暫停產生文本，並要求呼叫特定 Java 方法；後端執行該 Java 方法，將結果回傳給 LLM，LLM 最終整理結果回覆給使用者。

## Java 端註冊工具
使用 Spring AI 提供的 `@Tool` 註解將方法宣告為可供 AI 呼叫的工具：

```java
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class CrmInsightTools {

    private final CustomerService customerService;

    public CrmInsightTools(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Tool(description = "查詢指定客戶的最近統計摘要，包含商機、負責業務與最近互動")
    public CustomerSummary fetchCustomerSummary(Long customerId) {
        return customerService.getSummary(customerId);
    }
}
```

## ChatClient 配置
在與 LLM 通訊時，明確指定要啟用的工具名稱：

```javascript
Flux<String> stream = chatClient.prompt()
    .user(userPrompt)
    .tools("fetchCustomerSummary") // 啟用指定工具
    .stream()
    .content();
```
這樣模型就能安全、準確地回答，消除 AI 憑空捏造資料的幻想。
