# 電子報第 014 期主題二（EVENT 系列 E2：事件送得出去——交易邊界與 Outbox）Java 實跑驗證
#
# 用途：本期的核心是「事件在交易 commit 之前就送出去了」這個 bug。
#       它的可怕之處在於平常完全看不出來——只有在交易 rollback 的那一次才會出事。
#       因此必須用真實交易（H2 記憶體資料庫 + DataSourceTransactionManager）實跑，
#       製造一次 rollback，觀察兩種監聽器的行為差異。
#
# 驗證項目：
#   T1  問題重現：一般 @EventListener 在交易 rollback 後「仍然執行了」
#       （Agent 對著一筆最後不存在的資料產生了摘要）
#   T2  修正驗證：@TransactionalEventListener(AFTER_COMMIT) 在 rollback 時不執行
#   T3  正常路徑：交易 commit 成功時，AFTER_COMMIT 監聽器確實執行
#   T4  Outbox 原子性：事件寫進同一個交易的 outbox 表，rollback 時一起消失
#   T5  Outbox 不遺失：交易 commit 成功時，outbox 紀錄一定在
#
# 刻意不做：不呼叫任何 LLM。驗的是交易邊界與事件送出時機。
#
# 執行方式（PowerShell 7+）：
#   pwsh newsletter/scripts/verify-014-tx-event-java.ps1
#
# 相依：JDK 21（本機預設 JAVA_HOME 為 JDK 8，故此處明確指定）
#       Spring 6.2.12（含 spring-tx / spring-jdbc）與 H2 2.3.232

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
  'org\springframework\spring-jcl\6.2.12\spring-jcl-6.2.12.jar'
  'org\springframework\spring-tx\6.2.12\spring-tx-6.2.12.jar'
  'org\springframework\spring-jdbc\6.2.12\spring-jdbc-6.2.12.jar'
  'com\h2database\h2\2.3.232\h2-2.3.232.jar'
) | ForEach-Object { Join-Path $m2 $_ }

foreach ($j in $jars) { if (-not (Test-Path $j)) { throw "缺少相依 jar：$j" } }
$cp = $jars -join ';'

$work = Join-Path ([System.IO.Path]::GetTempPath()) "nl014-tx-$(Get-Random)"
New-Item -ItemType Directory -Path $work -Force | Out-Null

$source = @'
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.sql.DataSource;

/** 驗證事件的送出時機與交易邊界的關係，以及 Outbox 的原子性。 */
public class TxEventProof {

    /** 客戶資料已更新 */
    public record CustomerUpdated(Long customerId) {}

    /** 記錄兩種監聽器各自有沒有被執行 */
    public static class Recorder {
        public static volatile boolean plainRan;
        public static volatile boolean txRan;
        public static void reset() { plainRan = false; txRan = false; }
    }

    /** 一般監聽器：只要 publishEvent 被呼叫就執行，不管交易後來成不成功 */
    public static class PlainListener {
        @EventListener
        public void on(CustomerUpdated event) { Recorder.plainRan = true; }
    }

    /** 交易感知監聽器：預設 phase 為 AFTER_COMMIT，交易沒 commit 就不執行 */
    public static class TxListener {
        @TransactionalEventListener
        public void on(CustomerUpdated event) { Recorder.txRan = true; }
    }

    public static class CustomerService {
        private final JdbcTemplate jdbc;
        private final ApplicationEventPublisher publisher;

        public CustomerService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
            this.jdbc = jdbc;
            this.publisher = publisher;
        }

        /** 直接發事件的版本：事件在交易還沒 commit 時就送出去了 */
        @Transactional
        public void update(long id, boolean fail) {
            jdbc.update("update customers set name = ? where id = ?", "改過的名字", id);
            publisher.publishEvent(new CustomerUpdated(id));
            if (fail) throw new IllegalStateException("交易後段失敗");
        }

        /** Outbox 版本：事件當成一筆資料，寫進同一個交易 */
        @Transactional
        public void updateWithOutbox(long id, boolean fail) {
            jdbc.update("update customers set name = ? where id = ?", "改過的名字", id);
            jdbc.update("insert into outbox(event_type, payload) values (?, ?)",
                        "CustomerUpdated", String.valueOf(id));
            if (fail) throw new IllegalStateException("交易後段失敗");
        }
    }

    @Configuration
    @EnableTransactionManagement
    public static class AppConfig {
        @Bean
        public DataSource dataSource() {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:txproof;DB_CLOSE_DELAY=-1");
            ds.setUsername("sa");
            ds.setPassword("");
            return ds;
        }

        @Bean public JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource ds) {
            return new DataSourceTransactionManager(ds);
        }

        @Bean
        public CustomerService customerService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
            return new CustomerService(jdbc, publisher);
        }

        @Bean public PlainListener plainListener() { return new PlainListener(); }
        @Bean public TxListener txListener() { return new TxListener(); }
    }

    private static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  OK   " : " FAIL  ") + name + (detail.isEmpty() ? "" : " -- " + detail));
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(AppConfig.class);
        JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
        CustomerService service = ctx.getBean(CustomerService.class);

        jdbc.execute("create table customers (id bigint primary key, name varchar(100))");
        jdbc.execute("create table outbox (id bigint auto_increment primary key, " +
                     "event_type varchar(50), payload varchar(200))");
        jdbc.update("insert into customers(id, name) values (1, '原本的名字')");

        // ── 情境一：交易失敗（rollback）──
        Recorder.reset();
        try {
            service.update(1, true);
        } catch (IllegalStateException expected) {
            // 預期會失敗，交易應該 rollback
        }
        String nameAfterRollback = jdbc.queryForObject(
                "select name from customers where id = 1", String.class);

        check("T1 交易 rollback 後，一般 @EventListener 竟然已經執行過了",
              Recorder.plainRan && "原本的名字".equals(nameAfterRollback),
              "監聽器執行=" + Recorder.plainRan + " / 資料庫實際內容=" + nameAfterRollback);

        check("T2 同一次 rollback，@TransactionalEventListener 沒有執行",
              !Recorder.txRan, "監聽器執行=" + Recorder.txRan);

        // ── 情境二：交易成功（commit）──
        Recorder.reset();
        service.update(1, false);
        check("T3 交易 commit 後，@TransactionalEventListener 確實執行",
              Recorder.txRan, "監聽器執行=" + Recorder.txRan);

        // ── 情境三：Outbox 與交易同生共死 ──
        jdbc.update("delete from outbox");
        try {
            service.updateWithOutbox(1, true);
        } catch (IllegalStateException expected) {
            // 預期失敗
        }
        Integer afterRollback = jdbc.queryForObject("select count(*) from outbox", Integer.class);
        check("T4 交易 rollback 時，outbox 紀錄一起消失", afterRollback != null && afterRollback == 0,
              "outbox 筆數=" + afterRollback);

        service.updateWithOutbox(1, false);
        Integer afterCommit = jdbc.queryForObject("select count(*) from outbox", Integer.class);
        check("T5 交易 commit 時，outbox 紀錄一定在", afterCommit != null && afterCommit == 1,
              "outbox 筆數=" + afterCommit);

        ctx.close();
        System.out.println();
        if (failures > 0) {
            System.out.println("驗證失敗 " + failures + " 項");
            System.exit(1);
        }
        System.out.println("E2 程式碼行為驗證全部通過。");
    }
}
'@

$srcFile = Join-Path $work 'TxEventProof.java'
Set-Content -Path $srcFile -Value $source -Encoding UTF8

Write-Host '--- 編譯 ---'
& $javac -encoding UTF-8 -cp $cp -d $work $srcFile
if ($LASTEXITCODE -ne 0) { throw '編譯失敗' }

Write-Host '--- 執行 ---'
# -D 開頭的參數必須加引號，否則 PowerShell 會把 -Dfile 當成自己的參數名解析
& $java '-Dfile.encoding=UTF-8' -cp "$cp;$work" TxEventProof
$exit = $LASTEXITCODE

Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
exit $exit
