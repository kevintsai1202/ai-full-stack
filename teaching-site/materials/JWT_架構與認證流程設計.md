# JWT 架構與認證流程設計

## 認證流程

```
客戶端                    伺服器
  │                        │
  │── POST /auth/login ────│ 驗證帳密
  │                        │ 產生 JWT Token
  │◄── { token: "..." } ──│
  │                        │
  │── GET /api/customers ──│ 驗證 JWT
  │   Authorization:       │ 取出角色
  │   Bearer <token>       │ 執行授權檢查
  │◄── 200 OK ────────────│
```

## JWT 結構

```
header.payload.signature

header:  { "alg": "RS256", "typ": "JWT" }
payload: { "sub": "user1", "roles": ["ADMIN"], "iat": 1700000000 }
```

## Spring Security 設定重點

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/**").permitAll()
            .requestMatchers(POST, "/api/customers/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
}
```
