# 電子報第 015 期主題二（EVENT 系列 E3：事件收得下來——重複執行的代價）Java 實跑驗證
#
# 用途：本期主張「冪等保護的順序不能顛倒」——先卡冪等鍵才呼叫 LLM，不能先查再做。
#       這個主張只有在「並行」情境下才看得出差別，單執行緒怎麼測都會過，
#       所以驗證腳本必須真的開多條執行緒同時搶同一個事件。
#
# 驗證項目：
#   I1  沒有任何保護時，同一個事件送兩次就呼叫兩次 LLM（付兩次錢）
#   I2  正確順序（先 insert 冪等鍵、靠唯一約束擋、成功才呼叫 LLM）：送兩次只呼叫一次
#   I3  正確順序在並行下依然成立：10 條執行緒同時搶同一個事件，LLM 只被呼叫一次
#   I4  反例（先 select 檢查再呼叫 LLM，最後才寫入）：同樣 10 條並行，
#       LLM 被呼叫超過一次——證明「先查後做」在並行下是無效的保護
#
# I4 是這期的反例佐證：它留在腳本裡當迴歸測試，也是文中那句警語的證據。
#
# 刻意不做：不呼叫任何 LLM。以計數器代替，驗的是冪等機制本身。
#
# 執行方式（PowerShell 7+）：
#   pwsh newsletter/scripts/verify-015-idempotency-java.ps1
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

$work = Join-Path ([System.IO.Path]::GetTempPath()) "nl015-idem-$(Get-Random)"
New-Item -ItemType Directory -Path $work -Force | Out-Null

$source = @'
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** 驗證冪等保護的有效性，以及「先卡鍵再做事」與「先查再做」在並行下的差別。 */
public class IdempotencyProof {

    /** 假的 LLM：只計次並停頓一下，模擬真實呼叫的耗時；絕不真的連線 */
    static final AtomicInteger llmCalls = new AtomicInteger();

    static void callLlm() {
        llmCalls.incrementAndGet();
        try {
            Thread.sleep(20);   // 讓並行競爭有機會發生
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static JdbcTemplate jdbc;

    /** 沒有任何保護：收到就做 */
    static void handleUnprotected(String eventId) {
        callLlm();
    }

    /** 正確順序：先卡冪等鍵，唯一約束擋下重複，成功才呼叫 LLM */
    static void handleGuarded(String eventId) {
        try {
            jdbc.update("insert into processed_events(event_id) values (?)", eventId);
        } catch (DuplicateKeyException alreadyHandled) {
            return;   // 這個事件處理過了，安靜跳過
        }
        callLlm();
    }

    /** 反例：先查再做，最後才寫入——並行時兩條執行緒會同時通過檢查 */
    static void handleWrongOrder(String eventId) {
        Integer seen = jdbc.queryForObject(
                "select count(*) from processed_events where event_id = ?", Integer.class, eventId);
        if (seen != null && seen > 0) return;
        callLlm();
        try {
            jdbc.update("insert into processed_events(event_id) values (?)", eventId);
        } catch (DuplicateKeyException ignored) {
            // 已被別人寫入
        }
    }

    /** 開 n 條執行緒同時處理同一個事件，回傳 LLM 實際被呼叫的次數 */
    static int runConcurrently(int n, String eventId, java.util.function.Consumer<String> handler)
            throws InterruptedException {
        llmCalls.set(0);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            new Thread(() -> {
                try {
                    start.await();          // 全部就位後同時起跑，製造真正的競爭
                    handler.accept(eventId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();
        return llmCalls.get();
    }

    private static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  OK   " : " FAIL  ") + name + (detail.isEmpty() ? "" : " -- " + detail));
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:idemproof;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        jdbc = new JdbcTemplate(ds);

        // event_id 設為主鍵：這是最後一道、也是唯一真正可靠的防線
        jdbc.execute("create table processed_events (event_id varchar(64) primary key)");

        // I1 沒有保護
        llmCalls.set(0);
        handleUnprotected("evt-1");
        handleUnprotected("evt-1");
        check("I1 沒有保護時，同一事件送兩次就呼叫兩次 LLM", llmCalls.get() == 2,
              "LLM 呼叫次數=" + llmCalls.get());

        // I2 正確順序，單執行緒
        llmCalls.set(0);
        handleGuarded("evt-2");
        handleGuarded("evt-2");
        check("I2 先卡冪等鍵：送兩次只呼叫一次 LLM", llmCalls.get() == 1,
              "LLM 呼叫次數=" + llmCalls.get());

        // I3 正確順序，10 條執行緒並行
        int guarded = runConcurrently(10, "evt-3", IdempotencyProof::handleGuarded);
        check("I3 10 條執行緒同時搶同一事件，LLM 仍只被呼叫一次", guarded == 1,
              "LLM 呼叫次數=" + guarded);

        // I4 反例：先查再做，並行下失效
        int wrong = runConcurrently(10, "evt-4", IdempotencyProof::handleWrongOrder);
        check("I4 反例——先查再做，並行下擋不住（呼叫超過一次）", wrong > 1,
              "LLM 呼叫次數=" + wrong + "（10 條執行緒中有這麼多條同時通過了檢查）");

        System.out.println();
        if (failures > 0) {
            System.out.println("驗證失敗 " + failures + " 項");
            System.exit(1);
        }
        System.out.println("E3 程式碼行為驗證全部通過。");
    }
}
'@

$srcFile = Join-Path $work 'IdempotencyProof.java'
Set-Content -Path $srcFile -Value $source -Encoding UTF8

Write-Host '--- 編譯 ---'
& $javac -encoding UTF-8 -cp $cp -d $work $srcFile
if ($LASTEXITCODE -ne 0) { throw '編譯失敗' }

Write-Host '--- 執行 ---'
# -D 開頭的參數必須加引號，否則 PowerShell 會把 -Dfile 當成自己的參數名解析
& $java '-Dfile.encoding=UTF-8' -cp "$cp;$work" IdempotencyProof
$exit = $LASTEXITCODE

Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
exit $exit
