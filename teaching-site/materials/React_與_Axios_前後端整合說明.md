# React 與 Axios 前後端整合說明

## Axios 實例與攔截器

```typescript
// api/client.ts
import axios from "axios";

const api = axios.create({
  baseURL: "http://localhost:8080/api",
});

// 自動攜帶 JWT
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 統一錯誤處理
api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem("token");
      window.location.href = "/login";
    }
    return Promise.reject(err);
  }
);

export default api;
```

## React Query 搭配使用

```typescript
import { useQuery } from "@tanstack/react-query";
import api from "../api/client";

export function useCustomers() {
  return useQuery({
    queryKey: ["customers"],
    queryFn: () => api.get("/customers").then((r) => r.data),
  });
}
```

## Vite Proxy 設定

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
});
```

## CRM 工作台畫面與資料規格（提示詞的附件）

> 以下是 Unit 5 提示詞刻意「不寫進去」的技術細節。學員只需要用自己的話描述需求，
> 把這份講義一起附給 AI Agent，讓 AI 自己照規格實作，最後用驗收條件核對。

### 路由與畫面

| 路由 | 畫面 | 說明 |
| --- | --- | --- |
| `/login` | 登入頁 | 呼叫 `POST /api/auth/login`，成功後存 token 與使用者資訊 |
| `/dashboard` | 總覽儀表板 | 數字卡片、分析圖表、客戶分群 |
| `/customers` | 客戶清單 | 關鍵字／產業／狀態／負責業務篩選，分頁 |
| `/customers/:id` | 客戶詳情 | 分頁切換聯絡人／往來紀錄／生意機會 |
| `/team` | 團隊分析 | 僅 MANAGER、ADMIN 可見 |
| `/my-work` | 我的工作台 | 個人待辦與跟進 |

側欄項目固定為：總覽、客戶、生意機會、AI 助手、團隊分析、我的工作台。

### 登入狀態處理

- token 與使用者資訊存在 `localStorage`，以 Axios request 攔截器自動帶上 `Authorization: Bearer`。
- 以 `AuthContext` 讓所有頁面取得目前登入者與角色。
- 未登入開啟任何頁面導回 `/login`；API 回 `401` 時清除 token 並導回登入頁。

### 後端端點（本章允許新增的四支）

| 端點 | 用途 |
| --- | --- |
| `GET /api/dashboard/summary` | 總覽數字卡片：客戶數、進行中機會數與總金額、本月新增往來數 |
| `GET /api/dashboard/reports` | 一次算好所有圖表資料（漏斗、營收預測、產業分布、風險分布、續約提醒、業務排行、近期活動） |
| `GET /api/dashboard/drilldown` | 圖表點擊後的明細清單 |
| `GET /api/dashboard/rfm` | 客戶分群分數與標籤 |

除上述四支外，本章後端不得新增其他端點。前端若發現缺少後端功能，
必須先停下來列出清單，回 Unit 4 的權限表補一列後再實作。

客戶清單沿用 `GET /api/customers` 的分頁格式（`items`、`page`、`size`、`totalElements`、`totalPages`）；
生意機會看板沿用後端既有的「全公司生意機會清單」。

### 生意機會看板欄位順序

`QUALIFICATION` → `PROPOSAL` → `NEGOTIATION` → `CLOSED_WON` / `CLOSED_LOST`

拖拉卡片即呼叫更新階段的 API，失敗時卡片要退回原本的欄位。

### 儀表板圖表清單

1. 生意機會各階段漏斗圖
2. 未來六個月營收預測（依 `expectedCloseDate` 與金額）
3. 各產業營收分布
4. 客戶風險高低分布
5. 90 天內到期待續約客戶
6. 業務績效排行榜（成交金額與客戶數）
7. 最近往來活動

所有數字一律由後端計算，前端不得自行加總。每張圖都要能點擊下鑽，
以彈出視窗列出背後的客戶或生意機會，並可再點進客戶詳情。

### 客戶分群（RFM）計分規則

| 面向 | 判定依據 | 分數 |
| --- | --- | --- |
| Recency 多久沒聯絡 | 最近一次往來距今天數，越近分越高 | 1～5 |
| Frequency 往來頻率 | 近 90 天往來次數 | 1～5 |
| Monetary 生意金額 | 進行中與已成交生意金額總和 | 1～5 |

標籤規則：三項皆高＝「重點客戶」；最近有聯絡但金額仍小＝「有潛力」；
金額大但久未聯絡＝「需要喚醒」；其餘＝「需關注」。

### 驗收條件

- 以 `sales` 帳號登入，總覽數字、圖表、客戶清單與詳情皆為後端真實資料。
- 亞太智能製造的生意機會金額應為 `1,200,000`，與畫面對照一致。
- 鼎峰金融科技的分群標籤應為「需要喚醒」。
- `sales` 看不到團隊分析；改用 `manager` 登入則看得到。
- 登出或登入過期會回到登入頁。
- 上述流程寫成 Playwright 腳本放在 `frontend/e2e/`，可重複執行。
