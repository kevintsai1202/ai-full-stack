# React 與 Axios 前後端整合說明

本講義為「AI 賦能全端開發」課程的前後端串接與 Axios 攔截器設計教材。

## Axios 實例與請求攔截器
在 React 專案中，我們封裝一個統一的 Axios 實例，用以管理基礎路徑 (Base URL) 與自動攜帶 JWT：

```javascript
import axios from 'axios';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
});

// 請求攔截器：自動注入 Token
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);
```

## 回應攔截器與 401 處理
當 Token 過期或失效時，後端會回傳 `401 Unauthorized`。我們可以在回應攔截器中捕獲此錯誤，清除本地快取並重導向至登入頁面：

```javascript
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default apiClient;
```
