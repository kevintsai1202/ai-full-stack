# 電子報第 016 期主題一（EVENT 系列 E4：事件回得了前端——進度、失敗與死信）Java 實跑驗證
#
# 用途：本期給出退避重試與死信的實作。退避的公式看起來很單純，
#       但裡面藏了一個位移溢位的坑——溢位之後退避時間會變成負數，
#       重試不但沒有變慢，反而變成沒有間隔的重試風暴。這種事只有實跑才看得出來。
#
# 驗證項目：
#   B1  退避序列正確：1s → 2s → 4s → 8s → 16s → 30s（碰到上限後維持）
#   B2  反例——沒有夾住指數時，attempt 夠大會位移溢位，退避時間變成負數
#       （負的退避＝完全不等，重試風暴）
#   B3  正確版在同樣的 attempt 下仍回傳上限值，不會出現負數
#   B4  死信：重試達上限後，事件從待處理搬到 dead_letter，不再被撈出
#
# B2 是本期的反例佐證，留在腳本裡當迴歸測試。
#
# 刻意不做：不呼叫任何 LLM。驗的是退避計算與狀態流轉。
#
# 執行方式（PowerShell 7+）：
#   pwsh newsletter/scripts/verify-016-retry-dlq-java.ps1
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

$work = Join-Path ([System.IO.Path]::GetTempPath()) "nl016-retry-$(Get-Random)"
New-Item -ItemType Directory -Path $work -Force | Out-Null

$source = @'
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.List;

/** 驗證指數退避的計算（含溢位反例）與死信轉移。 */
public class RetryDlqProof {

    static final long BASE_MS = 1000;
    static final long MAX_MS = 30000;

    /** 正確版：先把指數夾在安全範圍內，再位移 */
    static long backoffMillis(int attempt) {
        int safe = Math.min(attempt, 30);               // 1L << 30 仍在安全範圍
        long delay = BASE_MS << (safe - 1);
        return Math.min(delay, MAX_MS);
    }

    /** 反例：沒有夾住指數，attempt 夠大時 1L << (attempt-1) 會溢位 */
    static long backoffUnsafe(int attempt) {
        long delay = BASE_MS * (1L << (attempt - 1));
        return Math.min(delay, MAX_MS);
    }

    private static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  OK   " : " FAIL  ") + name + (detail.isEmpty() ? "" : " -- " + detail));
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        // B1 退避序列
        List<Long> series = new ArrayList<>();
        for (int attempt = 1; attempt <= 8; attempt++) series.add(backoffMillis(attempt));
        List<Long> expected = List.of(1000L, 2000L, 4000L, 8000L, 16000L, 30000L, 30000L, 30000L);
        check("B1 退避序列 1s→2s→4s→8s→16s→30s（碰上限後維持）",
              series.equals(expected), "實際=" + series);

        // B2 反例：attempts 一路累加下去，退避不再遞增，反而歸零或變負
        // （55 次溢位成負數、64 次剛好歸零——兩者都代表「完全不等」）
        long unsafe55 = backoffUnsafe(55);
        long unsafe64 = backoffUnsafe(64);
        check("B2 反例——沒夾住指數時退避會歸零或變負（等於完全不等）",
              unsafe55 <= 0 && unsafe64 <= 0,
              "backoffUnsafe(55)=" + unsafe55 + " / backoffUnsafe(64)=" + unsafe64 + " 毫秒");

        // B3 正確版在同樣的 attempt 下仍是上限值
        long safe55 = backoffMillis(55);
        long safe64 = backoffMillis(64);
        check("B3 正確版同樣的 attempt 仍回傳上限，不會歸零或變負",
              safe55 == MAX_MS && safe64 == MAX_MS,
              "backoffMillis(55)=" + safe55 + " / backoffMillis(64)=" + safe64 + " 毫秒");

        // B4 死信轉移
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:dlqproof;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        jdbc.execute("create table event_tasks (event_id varchar(64) primary key, " +
                     "status varchar(20), attempts int, last_error varchar(500))");
        jdbc.execute("create table dead_letters (event_id varchar(64) primary key, " +
                     "attempts int, last_error varchar(500))");

        jdbc.update("insert into event_tasks(event_id, status, attempts) values ('evt-1', 'PENDING', 0)");

        final int maxAttempts = 3;
        for (int round = 1; round <= 5; round++) {
            // 撈出還可以重試的任務
            List<String> pending = jdbc.queryForList(
                    "select event_id from event_tasks where status = 'PENDING'", String.class);
            if (pending.isEmpty()) break;

            for (String id : pending) {
                int attempts = jdbc.queryForObject(
                        "select attempts from event_tasks where event_id = ?", Integer.class, id) + 1;
                // 假設每次都失敗
                if (attempts >= maxAttempts) {
                    jdbc.update("insert into dead_letters(event_id, attempts, last_error) values (?, ?, ?)",
                                id, attempts, "LLM 連續失敗");
                    jdbc.update("delete from event_tasks where event_id = ?", id);
                } else {
                    jdbc.update("update event_tasks set attempts = ?, last_error = ? where event_id = ?",
                                attempts, "LLM 失敗", id);
                }
            }
        }

        Integer stillPending = jdbc.queryForObject("select count(*) from event_tasks", Integer.class);
        Integer inDlq = jdbc.queryForObject("select count(*) from dead_letters", Integer.class);
        Integer dlqAttempts = jdbc.queryForObject(
                "select attempts from dead_letters where event_id = 'evt-1'", Integer.class);

        check("B4 重試達上限後移入死信，且不再被撈出",
              stillPending != null && stillPending == 0 && inDlq != null && inDlq == 1
                      && dlqAttempts != null && dlqAttempts == maxAttempts,
              "待處理=" + stillPending + " / 死信=" + inDlq + " / 死信中記錄的嘗試次數=" + dlqAttempts);

        System.out.println();
        if (failures > 0) {
            System.out.println("驗證失敗 " + failures + " 項");
            System.exit(1);
        }
        System.out.println("E4 程式碼行為驗證全部通過。");
    }
}
'@

$srcFile = Join-Path $work 'RetryDlqProof.java'
Set-Content -Path $srcFile -Value $source -Encoding UTF8

Write-Host '--- 編譯 ---'
& $javac -encoding UTF-8 -cp $cp -d $work $srcFile
if ($LASTEXITCODE -ne 0) { throw '編譯失敗' }

Write-Host '--- 執行 ---'
# -D 開頭的參數必須加引號，否則 PowerShell 會把 -Dfile 當成自己的參數名解析
& $java '-Dfile.encoding=UTF-8' -cp "$cp;$work" RetryDlqProof
$exit = $LASTEXITCODE

Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
exit $exit
