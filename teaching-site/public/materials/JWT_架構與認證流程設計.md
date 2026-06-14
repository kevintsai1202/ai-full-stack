# JWT 架構與認證流程設計

本講義為「AI 賦能全端開發」課程的安全防護與 JWT 設計教材。

## 什麼是 JWT？
JSON Web Token (JWT) 是一種開放標準 (RFC 7519)，用於在各方之間以 JSON 物件安全地傳輸資訊。此資訊可以被驗證和信任，因為它是經過數位簽章的。

## JWT 結構
一個 JWT 由三部分組成，中間以點 `.` 分隔：
1. **Header (標頭)**：包含 token 的類型 (JWT) 與採用的簽章演算法 (如 HMAC SHA256)。
2. **Payload (負載)**：包含聲明 (Claims)，例如使用者 ID、使用者帳號與角色權限 (Roles)。
3. **Signature (簽章)**：由編碼後的 Header、Payload，搭配一個金鑰 (Secret Key)，並經由指定的演算法計算而成，用以驗證 token 在傳輸過程中未被竄改。

## 認證與授權流程

1. **使用者登入**：前端發送 `POST /api/auth/login` 攜帶帳密。
2. **簽發 Token**：後端驗證成功後，生成含有使用者資訊與權限的 JWT，並回傳給前端。
3. **前端儲存**：前端將 JWT 儲存在 `localStorage` 中。
4. **帶 Token 請求**：前端在後續呼叫受保護 API 時，自動在 HTTP Header 加上 `Authorization: Bearer <Token>`。
5. **過濾器解析**：後端的 `JwtAuthenticationFilter` 攔截請求，驗證 Token 的合法性。驗證通過後，將認證資訊寫入 `SecurityContextHolder` 供 Spring Security 進行方法級別或路由級別的權限控管。
