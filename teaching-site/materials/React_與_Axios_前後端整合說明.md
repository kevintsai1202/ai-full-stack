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
