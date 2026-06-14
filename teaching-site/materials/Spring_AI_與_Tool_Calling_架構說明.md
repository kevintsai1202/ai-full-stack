# Spring AI 與 Tool Calling 架構說明

## ChatClient 設定

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder) {
    return builder
        .defaultSystem("""
            你是 AI CRM 業務助理。
            可查詢客戶資料、商機資訊。
            僅回答與 CRM 相關的問題。
            """)
        .build();
}
```

## Tool Calling 註冊

```java
@Component
public class CrmTools {

    @Tool("根據客戶 ID 查詢客戶基本資料")
    public Customer getCustomer(@P("客戶 ID") Long id) {
        return customerRepo.findById(id).orElse(null);
    }

    @Tool("查詢指定客戶的商機清單")
    public List<Opportunity> getOpportunities(@P("客戶 ID") Long customerId) {
        return opportunityRepo.findByCustomerId(customerId);
    }
}
```

## 呼叫流程

```
使用者 → ChatClient → System Prompt → Tool Calling
                                       ↓
                                Repository 查詢
                                       ↓
                                格式化回應 → 使用者
```
