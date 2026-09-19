# 電子報第 013 期主題二（EVENT 系列 E1：為什麼 Agent 一定要非同步）Java 程式碼實跑驗證
#
# 用途：本期主體是「@Async 的三個陷阱」。這三個陷阱的共同點是「程式照樣編譯、照樣執行、
#       只是沒有照你以為的方式跑」——靜態閱讀完全看不出來，只能實跑量測。
#
# 驗證項目：
#   A1  基準：@Async 監聽器確實換到另一條執行緒
#   A2  陷阱一（self-invocation）：同一個 bean 內部呼叫自己的 @Async 方法，
#       不會走 proxy，因此仍在原執行緒同步執行——@Async 等於沒加
#   A3  陷阱二（例外被吞）：@Async 的 void 方法拋例外，呼叫端 try-catch 抓不到
#   A4  陷阱三（預設執行器）：沒有自訂 TaskExecutor 時 Spring 用 SimpleAsyncTaskExecutor，
#       連續送 5 次會開 5 條不同的執行緒，完全不重用
#   A5  對照組：改用有界的 ThreadPoolTaskExecutor（core=2）後，
#       同樣送 5 次只會用到 2 條執行緒——證明執行緒有被重用
#
# 刻意不做：不呼叫任何 LLM。驗的是事件與執行緒機制本身。
#
# 執行方式（PowerShell 7+）：
#   pwsh newsletter/scripts/verify-013-async-java.ps1
#
# 相依：JDK 21（本機預設 JAVA_HOME 為 JDK 8，故此處明確指定）
#       本機 ~/.m2 已存在的 Spring 6.2.12 jar

$ErrorActionPreference = 'Stop'

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

$work = Join-Path ([System.IO.Path]::GetTempPath()) "nl013-async-$(Get-Random)"
New-Item -ItemType Directory -Path $work -Force | Out-Null

$source = @'
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 驗證 @Async 的三個常見陷阱：self-invocation、例外被吞、預設執行器不重用執行緒。 */
public class AsyncPitfallProof {

    /** 收集各次執行所在的執行緒名稱，用來判斷是否換執行緒／是否重用 */
    public static class Recorder {
        public static final Set<String> threads =
                Collections.synchronizedSet(new LinkedHashSet<>());
        public static volatile String selfInvokeThread;
        public static volatile CountDownLatch latch = new CountDownLatch(1);

        public static void reset(int count) {
            threads.clear();
            latch = new CountDownLatch(count);
        }
    }

    /** 主角：一個帶 @Async 方法的服務 */
    public static class SummaryService {

        /** 正常路徑：從外部呼叫，會走 proxy，確實非同步 */
        @Async
        public void generate() {
            Recorder.threads.add(Thread.currentThread().getName());
            Recorder.latch.countDown();
        }

        /** 陷阱一：同一個 bean 內部呼叫 this.generate()，繞過 proxy，不會非同步 */
        public void generateViaSelfInvocation() {
            this.generate();
            Recorder.selfInvokeThread = Thread.currentThread().getName();
        }

        /** 陷阱二：@Async 的 void 方法拋例外，呼叫端收不到 */
        @Async
        public void explode() {
            Recorder.latch.countDown();
            throw new IllegalStateException("非同步裡爆炸了");
        }
    }

    /** 情境一：沒有自訂 TaskExecutor，Spring 會退回 SimpleAsyncTaskExecutor */
    @Configuration
    @EnableAsync
    public static class DefaultExecutorConfig {
        @Bean public SummaryService summaryService() { return new SummaryService(); }
    }

    /** 情境二：自訂有界執行緒池，執行緒應被重用 */
    @Configuration
    @EnableAsync
    public static class PooledExecutorConfig {
        @Bean public SummaryService summaryService() { return new SummaryService(); }

        /** bean 名稱必須是 taskExecutor，@Async 才會預設採用它 */
        @Bean
        public ThreadPoolTaskExecutor taskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(50);
            executor.setThreadNamePrefix("crm-async-");
            executor.initialize();
            return executor;
        }
    }

    private static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  OK   " : " FAIL  ") + name + (detail.isEmpty() ? "" : " -- " + detail));
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        String mainThread = Thread.currentThread().getName();

        // ── 情境一：預設執行器 ──
        var ctx = new AnnotationConfigApplicationContext(DefaultExecutorConfig.class);
        SummaryService service = ctx.getBean(SummaryService.class);

        // A1 基準：從外部呼叫，應換執行緒
        Recorder.reset(1);
        service.generate();
        Recorder.latch.await(5, TimeUnit.SECONDS);
        String asyncThread = Recorder.threads.iterator().next();
        check("A1 @Async 從外部呼叫會換執行緒", !mainThread.equals(asyncThread),
              "caller=" + mainThread + " / async=" + asyncThread);

        // A2 陷阱一：self-invocation 繞過 proxy，應仍在原執行緒
        Recorder.reset(1);
        service.generateViaSelfInvocation();
        boolean finished = Recorder.latch.await(2, TimeUnit.SECONDS);
        String selfThread = Recorder.threads.isEmpty() ? "(未執行)" : Recorder.threads.iterator().next();
        check("A2 self-invocation 讓 @Async 失效（仍同步、同執行緒）",
              finished && selfThread.equals(Recorder.selfInvokeThread),
              "caller=" + Recorder.selfInvokeThread + " / 執行於=" + selfThread);

        // A3 陷阱二：@Async void 的例外，呼叫端抓不到
        Recorder.reset(1);
        boolean caught;
        try {
            service.explode();
            Recorder.latch.await(5, TimeUnit.SECONDS);
            Thread.sleep(300); // 留時間讓非同步執行緒真的把例外拋出來
            caught = false;
        } catch (RuntimeException ex) {
            caught = true;
        }
        check("A3 @Async void 的例外不會傳回呼叫端（被吞掉）", !caught,
              caught ? "呼叫端竟然抓到例外" : "呼叫端沒有收到任何例外");

        // A4 陷阱三：預設執行器不重用執行緒
        Recorder.reset(5);
        for (int i = 0; i < 5; i++) service.generate();
        Recorder.latch.await(5, TimeUnit.SECONDS);
        int distinctDefault = Recorder.threads.size();
        check("A4 預設 SimpleAsyncTaskExecutor 不重用執行緒", distinctDefault == 5,
              "送 5 次，用掉 " + distinctDefault + " 條不同執行緒 " + Recorder.threads);
        ctx.close();

        // ── 情境二：有界執行緒池 ──
        var pooledCtx = new AnnotationConfigApplicationContext(PooledExecutorConfig.class);
        SummaryService pooled = pooledCtx.getBean(SummaryService.class);
        Recorder.reset(5);
        for (int i = 0; i < 5; i++) pooled.generate();
        Recorder.latch.await(5, TimeUnit.SECONDS);
        int distinctPooled = Recorder.threads.size();
        check("A5 改用 ThreadPoolTaskExecutor(core=2) 後執行緒被重用", distinctPooled <= 2,
              "送 5 次，只用掉 " + distinctPooled + " 條執行緒 " + Recorder.threads);
        pooledCtx.close();

        System.out.println();
        if (failures > 0) {
            System.out.println("驗證失敗 " + failures + " 項");
            System.exit(1);
        }
        System.out.println("E1 程式碼行為驗證全部通過。");
    }
}
'@

$srcFile = Join-Path $work 'AsyncPitfallProof.java'
Set-Content -Path $srcFile -Value $source -Encoding UTF8

Write-Host '--- 編譯 ---'
& $javac -encoding UTF-8 -cp $cp -d $work $srcFile
if ($LASTEXITCODE -ne 0) { throw '編譯失敗' }

Write-Host '--- 執行 ---'
# -D 開頭的參數必須加引號，否則 PowerShell 會把 -Dfile 當成自己的參數名解析
& $java '-Dfile.encoding=UTF-8' -cp "$cp;$work" AsyncPitfallProof
$exit = $LASTEXITCODE

Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
exit $exit
