# 安全基準資料：OWASP Top 10:2025 與 CWE Top 25:2025

> 本檔是課堂查核用的版本快照索引，不取代官方頁面。上課時仍要記錄實際查閱日期與報告使用的版本。

## OWASP Top 10:2025 全部類別

來源：[OWASP Top 10:2025](https://owasp.org/Top10/)，官方入口頁。

| 代號 | 類別 |
|---|---|
| A01:2025 | Broken Access Control |
| A02:2025 | Security Misconfiguration |
| A03:2025 | Software Supply Chain Failures |
| A04:2025 | Cryptographic Failures |
| A05:2025 | Injection |
| A06:2025 | Insecure Design |
| A07:2025 | Authentication Failures |
| A08:2025 | Software or Data Integrity Failures |
| A09:2025 | Security Logging and Alerting Failures |
| A10:2025 | Mishandling of Exceptional Conditions |

## CWE Top 25:2025 全部排名

來源：[MITRE CWE 2025 Top 25 archive](https://cwe.mitre.org/top25/archive/2025/2025_cwe_top25.html)。官方頁面標示 2025 清單頁面更新日期為 2025-12-15；入口頁說明其資料集涵蓋 39,080 筆 CVE 紀錄。下表保留 rank、CWE ID、官方名稱、score 與 CVEs in KEV，供課堂報告引用。

| Rank | CWE | 官方名稱（保留原文） | Score | CVEs in KEV |
|---:|---|---|---:|---:|
| 1 | CWE-79 | Improper Neutralization of Input During Web Page Generation ('Cross-site Scripting') | 60.38 | 7 |
| 2 | CWE-89 | Improper Neutralization of Special Elements used in an SQL Command ('SQL Injection') | 28.72 | 4 |
| 3 | CWE-352 | Cross-Site Request Forgery (CSRF) | 13.64 | 0 |
| 4 | CWE-862 | Missing Authorization | 13.28 | 0 |
| 5 | CWE-787 | Out-of-bounds Write | 12.68 | 12 |
| 6 | CWE-22 | Improper Limitation of a Pathname to a Restricted Directory ('Path Traversal') | 8.99 | 10 |
| 7 | CWE-416 | Use After Free | 8.47 | 14 |
| 8 | CWE-125 | Out-of-bounds Read | 7.88 | 3 |
| 9 | CWE-78 | Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection') | 7.85 | 20 |
| 10 | CWE-94 | Improper Control of Generation of Code ('Code Injection') | 7.57 | 7 |
| 11 | CWE-120 | Buffer Copy without Checking Size of Input ('Classic Buffer Overflow') | 6.96 | 0 |
| 12 | CWE-434 | Unrestricted Upload of File with Dangerous Type | 6.87 | 4 |
| 13 | CWE-476 | NULL Pointer Dereference | 6.41 | 0 |
| 14 | CWE-121 | Stack-based Buffer Overflow | 5.75 | 4 |
| 15 | CWE-502 | Deserialization of Untrusted Data | 5.23 | 11 |
| 16 | CWE-122 | Heap-based Buffer Overflow | 5.21 | 6 |
| 17 | CWE-863 | Incorrect Authorization | 4.14 | 4 |
| 18 | CWE-20 | Improper Input Validation | 4.09 | 2 |
| 19 | CWE-284 | Improper Access Control | 4.07 | 1 |
| 20 | CWE-200 | Exposure of Sensitive Information to an Unauthorized Actor | 4.01 | 1 |
| 21 | CWE-306 | Missing Authentication for Critical Function | 3.47 | 11 |
| 22 | CWE-918 | Server-Side Request Forgery (SSRF) | 3.36 | 0 |
| 23 | CWE-77 | Improper Neutralization of Special Elements used in a Command ('Command Injection') | 3.15 | 2 |
| 24 | CWE-639 | Authorization Bypass Through User-Controlled Key | 2.62 | 0 |
| 25 | CWE-770 | Allocation of Resources Without Limits or Throttling | 2.54 | 0 |

## 課堂使用邊界

- Rank 與 score 是基準清單的資料，不是學員專案的漏洞判定。
- `CVEs in KEV` 是該清單欄位，不能解讀成學員專案一定受到這些漏洞影響。
- 要判斷一個專案是否命中，仍需提供程式位置、設定、輸入、執行結果或測試證據。
- 2025 清單未命中不代表沒有風險；它只代表本次基準檢查沒有足夠證據對應到該項目。

