# 電子報第 012 期主題二（EVENT 系列 E0：事件驅動的地圖）Java 程式碼實跑驗證
#
# 用途：本期的核心論點是「Spring 的 publishEvent() 預設是同步的，發事件不等於非同步」。
#       這是實務上極常見的誤解，也是 E0／E1 的分界點，因此不能只靠閱讀文件斷言，
#       必須真的把 Spring context 跑起來、量出執行緒名稱與執行順序。
#
# 與 010 期驗證腳本的差異：010 只做「編譯驗證」（檢查簽名與 API 是否存在），
# 本期做的是「執行驗證」——編譯後實際執行，斷言執行期的行為。
#
# 驗證項目：
#   R1  預設 @EventListener 與發布端在同一條執行緒（證明同步）
#   R2  publishEvent() 返回時，監聽器已執行完畢（證明是阻塞呼叫，不是排進佇列）
#   R3  監聽器拋出的例外會傳回發布端（同步呼叫的必然結果；非同步時不會）
#   R4  對照組：同一支程式加上 @Async 後，確實換到另一條執行緒
#       （這條同時是 E1 的素材：印出預設執行器的執行緒名稱）
#
# 刻意不做：不呼叫任何 LLM。本系列驗證的對象是「事件機制本身」，
#           真的呼叫 LLM 會產生費用且結果不可重現。
#
# 執行方式（PowerShell 7+）：
#   pwsh newsletter/scripts/verify-012-event-java.ps1
#
# 相依：JDK 21（Spring 6 需 Java 17+；本機預設 JAVA_HOME 為 JDK 8，故此處明確指定）
#       本機 ~/.m2 已存在的 Spring 6.2.12 jar

$ErrorActionPreference = 'Stop'

# --- 環境：明確指定 JDK 21，不依賴預設 JAVA_HOME（本機預設是 JDK 8）---
$jdk = 'D:\java\jdk-21'
$javac = Join-Path $jdk 'bin\javac.exe'
$java = Join-Path $jdk 'bin\java.exe'
if (-not (Test-Path $javac)) { throw "找不到 JDK 21 的 javac：$javac" }

$m2 = Join-Path $HOME '.m2\repository'
$jars = @(
  'org\springframework\spring-core\6.2.12\spring-core-6.2.12.jar'
  'org\springframework\spring-context\6.2.12\spring-context-6.2.12.jar'
  'org\springframework\spring-beans\6.2.12\spring-beans-6.2.12.jar'
  'org\springframework\spring-aop\6.2.12\spring-aop-6.2.12.jar'
  'org\springframework\spring-expression\6.2.12\spring-expression-6.2.12.jar'
  # spring-jcl 是 Spring 對 commons-logging 的橋接，編譯用不到但執行期必要
  'org\springframework\spring-jcl\6.2.12\spring-jcl-6.2.12.jar'
) | ForEach-Object { Join-Path $m2 $_ }

foreach ($j in $jars) { if (-not (Test-Path $j)) { throw "缺少相依 jar：$j" } }
$cp = $jars -join ';'

# --- 工作目錄 ---
$work = Join-Path ([System.IO.Path]::GetTempPath()) "nl012-event-$(Get-Random)"
New-Item -ItemType Directory -Path $work -Force | Out-Null

# --- 待驗證程式：電子報 E0 後段的最小骨架（含對照組 @Async）---
$source = @'
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 驗證 Spring 應用事件的預設行為：同步、同執行緒、例外會傳回發布端。 */
public class EventSyncProof {

    /** 電子報範例中的領域事件：客戶資料已更新 */
    public record CustomerUpdated(Long customerId) {}

    /** 對照組專用事件，避免與同步監聽器互相干擾 */
    public record CustomerUpdatedAsync(Long customerId) {}

    /** 記錄各監聽器實際執行的執行緒，供主程式斷言 */
    public static class Recorder {
        public static volatile String syncThread;
        public static volatile String asyncThread;
        public static final CountDownLatch asyncLatch = new CountDownLatch(1);
    }

    /** 預設監聽器：不加任何註解，驗證它跑在哪條執行緒 */
    public static class SyncListener {
        @EventListener
        public void on(CustomerUpdated event) {
            Recorder.syncThread = Thread.currentThread().getName();
        }
    }

    /** 對照組監聽器：加上 @Async，應換到別的執行緒 */
    public static class AsyncListener {
        @Async
        @EventListener
        public void on(CustomerUpdatedAsync event) {
            Recorder.asyncThread = Thread.currentThread().getName();
            Recorder.asyncLatch.countDown();
        }
    }

    /** 故意拋例外的監聽器：驗證例外是否會傳回發布端 */
    public static class ThrowingListener {
        @EventListener
        public void on(String event) {
            throw new IllegalStateException("監聽器爆炸");
        }
    }

    @Configuration
    @EnableAsync
    public static class AppConfig {
        @Bean public SyncListener syncListener() { return new SyncListener(); }
        @Bean public AsyncListener asyncListener() { return new AsyncListener(); }
        @Bean public ThrowingListener throwingListener() { return new ThrowingListener(); }
    }

    private static int failures = 0;

    /** 輸出單項檢查結果並累計失敗數 */
    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  OK   " : " FAIL  ") + name + (detail.isEmpty() ? "" : " -- " + detail));
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        var ctx = new AnnotationConfigApplicationContext(AppConfig.class);
        ApplicationEventPublisher publisher = ctx;
        String mainThread = Thread.currentThread().getName();

        // R1：預設監聽器與發布端同執行緒
        publisher.publishEvent(new CustomerUpdated(1L));
        check("R1 預設監聽器與發布端同執行緒", mainThread.equals(Recorder.syncThread),
              "publisher=" + mainThread + " / listener=" + Recorder.syncThread);

        // R2：publishEvent 返回時監聽器已跑完（若是排入佇列，此時應仍為 null）
        check("R2 publishEvent 返回時監聽器已執行完畢", Recorder.syncThread != null, "");

        // R3：監聽器例外會傳回發布端
        boolean propagated;
        try {
            publisher.publishEvent("boom");
            propagated = false;
        } catch (IllegalStateException ex) {
            propagated = "監聽器爆炸".equals(ex.getMessage());
        }
        check("R3 監聽器例外會傳回發布端", propagated, "");

        // R4：對照組，加 @Async 後換執行緒
        publisher.publishEvent(new CustomerUpdatedAsync(1L));
        boolean arrived = Recorder.asyncLatch.await(5, TimeUnit.SECONDS);
        check("R4 加上 @Async 後換到別的執行緒",
              arrived && !mainThread.equals(Recorder.asyncThread),
              "publisher=" + mainThread + " / listener=" + Recorder.asyncThread);

        ctx.close();
        System.out.println();
        if (failures > 0) {
            System.out.println("驗證失敗 " + failures + " 項");
            System.exit(1);
        }
        System.out.println("E0 程式碼行為驗證全部通過。");
    }
}
'@

$srcFile = Join-Path $work 'EventSyncProof.java'
Set-Content -Path $srcFile -Value $source -Encoding UTF8

Write-Host '--- 編譯 ---'
& $javac -encoding UTF-8 -cp $cp -d $work $srcFile
if ($LASTEXITCODE -ne 0) { throw '編譯失敗' }

Write-Host '--- 執行 ---'
# -D 開頭的參數必須加引號，否則 PowerShell 會把 -Dfile 當成自己的參數名解析
& $java '-Dfile.encoding=UTF-8' -cp "$cp;$work" EventSyncProof
$exit = $LASTEXITCODE

Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
exit $exit
