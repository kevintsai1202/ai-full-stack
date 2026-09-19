# 電子報第 017 期主題一（EVENT 系列 E5：事件即 Agent 的稽核軌跡）Java 實跑驗證
#
# 用途：本期主張「把 Agent 每一步記成事件流，就能回放、稽核、算成本」。
#       這三件事聽起來很抽象，所以驗證方式是：真的建一張軌跡表、真的寫進一次
#       Agent 執行的完整過程，然後用 SQL 把那三個問題各回答一次。
#
# 驗證項目：
#   V1  回放：同一個 trace_id 的事件依 step 取回，順序與寫入時完全一致
#   V2  成本歸屬：從事件流加總 token，得到這一次執行的總成本
#   V3  稽核：只給一個 trace_id，查得回提示、工具呼叫、工具結果與最終回應
#   V4  多次執行混在同一張表時，依 trace_id 查詢不會互相汙染
#
# 刻意不做：不呼叫任何 LLM。軌跡內容以固定字串代入，驗的是軌跡結構本身。
#
# 執行方式（PowerShell 7+）：
#   pwsh newsletter/scripts/verify-017-audit-trail-java.ps1
#
# 相依：JDK 21、Spring 6.2.12（spring-jdbc）、H2 2.3.232

$ErrorActionPreference = 'Stop'

$jdk = 'D:\java\jdk-21'
$javac = Join-Path $jdk 'bin\javac.exe'
$java = Join-Path $jdk 'bin\java.exe'
if (-not (Test-Path $javac)) { throw "找不到 JDK 21 的 javac：$javac" }

$m2 = Join-Path $HOME '.m2\repository'
$jars = @(
  'org\springframework\spring-core\6.2.12\spring-core-6.2.12.jar'
  'org\springframework\spring-beans\6.2.12\spring-beans-6.2.12.jar'
  'org\springframework\spring-jcl\6.2.12\spring-jcl-6.2.12.jar'
  'org\springframework\spring-tx\6.2.12\spring-tx-6.2.12.jar'
  'org\springframework\spring-jdbc\6.2.12\spring-jdbc-6.2.12.jar'
  'com\h2database\h2\2.3.232\h2-2.3.232.jar'
) | ForEach-Object { Join-Path $m2 $_ }

foreach ($j in $jars) { if (-not (Test-Path $j)) { throw "缺少相依 jar：$j" } }
$cp = $jars -join ';'

$work = Join-Path ([System.IO.Path]::GetTempPath()) "nl017-audit-$(Get-Random)"
New-Item -ItemType Directory -Path $work -Force | Out-Null

$source = @'
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

/** 驗證 Agent 執行軌跡表能否回答「回放、成本、稽核」三個問題。 */
public class AuditTrailProof {

    static JdbcTemplate jdbc;
    static int step = 0;

    /** 記一步軌跡：每一步都是 append，不覆寫既有紀錄 */
    static void record(String traceId, String type, String content, int tokens) {
        jdbc.update("insert into agent_steps(trace_id, step_no, step_type, content, tokens) " +
                    "values (?, ?, ?, ?, ?)", traceId, ++step, type, content, tokens);
    }

    private static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  OK   " : " FAIL  ") + name + (detail.isEmpty() ? "" : " -- " + detail));
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:auditproof;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        jdbc = new JdbcTemplate(ds);

        jdbc.execute("create table agent_steps (" +
                     "id bigint auto_increment primary key, " +
                     "trace_id varchar(64) not null, " +
                     "step_no int not null, " +
                     "step_type varchar(30) not null, " +
                     "content varchar(500), " +
                     "tokens int not null default 0)");

        // 第一次執行：完整的一輪 Agent 軌跡
        String trace1 = "trace-a";
        step = 0;
        record(trace1, "PROMPT", "system prompt v3 + 客戶 42 的近況請求", 320);
        record(trace1, "TOOL_CALL", "findRecentInteractions(customerId=42)", 0);
        record(trace1, "TOOL_RESULT", "回傳 5 筆互動紀錄", 210);
        record(trace1, "TOOL_CALL", "calculateHealthScore(customerId=42)", 0);
        record(trace1, "TOOL_RESULT", "健康分數 72", 40);
        record(trace1, "LLM_RESPONSE", "客戶 42 近期互動頻率下降，建議主動聯繫", 180);

        // 第二次執行：另一個 trace，用來確認查詢不會互相汙染
        String trace2 = "trace-b";
        step = 0;
        record(trace2, "PROMPT", "system prompt v3 + 客戶 99 的近況請求", 300);
        record(trace2, "LLM_RESPONSE", "客戶 99 無異常", 90);

        // V1 回放：順序與寫入時一致
        List<String> replay = jdbc.queryForList(
                "select step_type from agent_steps where trace_id = ? order by step_no",
                String.class, trace1);
        List<String> expected = List.of("PROMPT", "TOOL_CALL", "TOOL_RESULT",
                                        "TOOL_CALL", "TOOL_RESULT", "LLM_RESPONSE");
        check("V1 回放：依 step_no 取回的順序與執行順序一致",
              replay.equals(expected), "實際=" + replay);

        // V2 成本歸屬：這一次執行總共花了多少 token
        Integer totalTokens = jdbc.queryForObject(
                "select sum(tokens) from agent_steps where trace_id = ?", Integer.class, trace1);
        check("V2 成本歸屬：單次執行的 token 可加總",
              totalTokens != null && totalTokens == 750, "trace-a 總 token=" + totalTokens);

        // V3 稽核：這份摘要是怎麼來的——四類軌跡都查得到
        Integer prompts = jdbc.queryForObject(
                "select count(*) from agent_steps where trace_id = ? and step_type = 'PROMPT'",
                Integer.class, trace1);
        Integer toolCalls = jdbc.queryForObject(
                "select count(*) from agent_steps where trace_id = ? and step_type = 'TOOL_CALL'",
                Integer.class, trace1);
        Integer toolResults = jdbc.queryForObject(
                "select count(*) from agent_steps where trace_id = ? and step_type = 'TOOL_RESULT'",
                Integer.class, trace1);
        String finalAnswer = jdbc.queryForObject(
                "select content from agent_steps where trace_id = ? and step_type = 'LLM_RESPONSE'",
                String.class, trace1);
        check("V3 稽核：提示、工具呼叫、工具結果與最終回應都查得回來",
              prompts != null && prompts == 1 && toolCalls != null && toolCalls == 2
                      && toolResults != null && toolResults == 2 && finalAnswer != null,
              "提示=" + prompts + " / 工具呼叫=" + toolCalls + " / 工具結果=" + toolResults
                      + " / 最終回應=「" + finalAnswer + "」");

        // V4 多次執行不互相汙染
        Integer trace2Steps = jdbc.queryForObject(
                "select count(*) from agent_steps where trace_id = ?", Integer.class, trace2);
        Integer trace2Tokens = jdbc.queryForObject(
                "select sum(tokens) from agent_steps where trace_id = ?", Integer.class, trace2);
        Integer allSteps = jdbc.queryForObject("select count(*) from agent_steps", Integer.class);
        check("V4 兩次執行混在同一張表，依 trace_id 查詢不互相汙染",
              trace2Steps != null && trace2Steps == 2 && trace2Tokens != null && trace2Tokens == 390
                      && allSteps != null && allSteps == 8,
              "trace-b 步數=" + trace2Steps + " / token=" + trace2Tokens + " / 全表共=" + allSteps);

        System.out.println();
        if (failures > 0) {
            System.out.println("驗證失敗 " + failures + " 項");
            System.exit(1);
        }
        System.out.println("E5 程式碼行為驗證全部通過。");
    }
}
'@

$srcFile = Join-Path $work 'AuditTrailProof.java'
Set-Content -Path $srcFile -Value $source -Encoding UTF8

Write-Host '--- 編譯 ---'
& $javac -encoding UTF-8 -cp $cp -d $work $srcFile
if ($LASTEXITCODE -ne 0) { throw '編譯失敗' }

Write-Host '--- 執行 ---'
# -D 開頭的參數必須加引號，否則 PowerShell 會把 -Dfile 當成自己的參數名解析
& $java '-Dfile.encoding=UTF-8' -cp "$cp;$work" AuditTrailProof
$exit = $LASTEXITCODE

Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
exit $exit
