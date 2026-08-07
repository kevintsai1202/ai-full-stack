# 電子報第 010 期（WebSocket／WebRTC）Java 程式碼片段編譯驗證
#
# 用途：把 newsletter-10-realtime-websocket-webrtc.md「多實例廣播」小節的
#       Java 片段補上最小骨架後實際編譯，驗證其方法簽名與 API 呼叫是否正確。
#       片段本身是節錄（省略 class 宣告與欄位），因此驗證重點是「簽名與 API」，
#       不是片段能否獨立成檔。
#
# 驗證項目：
#   J1  迴歸測試：修正前的錯誤寫法 @Override public void onMessage(String message)
#       應「編譯失敗」——確保日後不會有人把電子報改回這個版本
#   J2  電子報現行第一段：Spring Data Redis 官方 MessageListener 簽名
#       （相容 Spring Boot 3.x，即多數讀者目前的環境）
#   J3  電子報現行 @RedisListener 段：Spring Boot 4.1 / Data Redis 4.1 起的註解寫法
#       （另用 Spring 7 + Data Redis 4.1.0 的 classpath 編譯）
#   J4  版本分界舉證：同段 @RedisListener 在 Spring Data Redis 3.5.x 應編譯失敗，
#       用以支持電子報「這個套件在 3.x 根本不存在」的敘述
#
# 執行方式（PowerShell 7+）：
#   pwsh newsletter/scripts/verify-010-java.ps1
#
# 相依：JDK 21（Spring 6 需 Java 17+；本機預設 JAVA_HOME 為 JDK 8，故此處明確指定）
#       本機 ~/.m2 已存在的 Spring 6.2.x / Data Redis 3.5.x（舊線）
#       與 Spring 7.0.x / Data Redis 4.1.x（新線）jar

$ErrorActionPreference = 'Stop'

# --- 環境：明確指定 JDK 21，不依賴預設 JAVA_HOME（本機預設是 JDK 8）---
$jdk = 'D:\java\jdk-21'
$javac = Join-Path $jdk 'bin\javac.exe'
if (-not (Test-Path $javac)) { throw "找不到 JDK 21 的 javac：$javac" }

$m2 = Join-Path $HOME '.m2\repository'
$jars = @(
  'org\springframework\spring-websocket\6.2.12\spring-websocket-6.2.12.jar'
  'org\springframework\spring-core\6.2.12\spring-core-6.2.12.jar'
  'org\springframework\spring-context\6.2.12\spring-context-6.2.12.jar'
  'org\springframework\spring-beans\6.2.12\spring-beans-6.2.12.jar'
  'org\springframework\spring-web\6.2.12\spring-web-6.2.12.jar'
  'org\springframework\spring-tx\6.2.12\spring-tx-6.2.12.jar'
  'org\springframework\data\spring-data-redis\3.5.5\spring-data-redis-3.5.5.jar'
) | ForEach-Object { Join-Path $m2 $_ }

foreach ($j in $jars) { if (-not (Test-Path $j)) { throw "缺少相依 jar：$j" } }
$cp = $jars -join ';'

# 新線 classpath：Spring 7.0.x + Spring Data Redis 4.1.x，供 @RedisListener 驗證使用
$jarsNew = @(
  'org\springframework\spring-core\7.0.8\spring-core-7.0.8.jar'
  'org\springframework\spring-context\7.0.8\spring-context-7.0.8.jar'
  'org\springframework\spring-beans\7.0.8\spring-beans-7.0.8.jar'
  'org\springframework\spring-messaging\7.0.8\spring-messaging-7.0.8.jar'
  'org\springframework\spring-tx\7.0.8\spring-tx-7.0.8.jar'
  'org\springframework\spring-websocket\7.0.8\spring-websocket-7.0.8.jar'
  'org\springframework\data\spring-data-redis\4.1.0\spring-data-redis-4.1.0.jar'
  'org\springframework\data\spring-data-commons\4.1.0\spring-data-commons-4.1.0.jar'
) | ForEach-Object { Join-Path $m2 $_ }

foreach ($j in $jarsNew) { if (-not (Test-Path $j)) { throw "缺少相依 jar：$j" } }
$cpNew = $jarsNew -join ';'

$work = Join-Path ([System.IO.Path]::GetTempPath()) "nl010-java-verify"
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Item -ItemType Directory -Path $work | Out-Null

# --- J1：電子報原文寫法 ---
# 骨架為驗證所補（class 宣告、欄位、import），方法簽名與內容逐字取自電子報
$srcOriginal = @'
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ChatHandlerOriginal extends TextWebSocketHandler {

  private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
  private RedisTemplate<String, String> redis;

  // ===== 電子報「Spring 端的最小骨架」片段（逐字） =====
  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.add(session);       // 上線登記
  }
  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage msg) {
    redis.convertAndSend("chat", msg.getPayload());  // 進 pub/sub，不直發
  }
  // ===== 片段結束 =====

  // ===== 電子報「Redis pub/sub」片段（逐字） =====
  @Override
  public void onMessage(String message) {
    sessions.forEach(s -> send(s, message));
  }
  // ===== 片段結束 =====

  private void send(WebSocketSession s, String message) { }
}
'@

# --- J2：對照組，改用 Spring Data Redis 官方 MessageListener 簽名 ---
$srcFixed = @'
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ChatHandlerFixed extends TextWebSocketHandler implements MessageListener {

  private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
  private RedisTemplate<String, String> redis;

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.add(session);
  }
  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage msg) {
    redis.convertAndSend("chat", msg.getPayload());
  }
  // 官方簽名：onMessage(Message, byte[])
  @Override
  public void onMessage(Message message, byte[] pattern) {
    String body = new String(message.getBody(), StandardCharsets.UTF_8);
    sessions.forEach(s -> send(s, body));
  }

  private void send(WebSocketSession s, String message) { }
}
'@

# --- J3：Spring Boot 4.1 / Spring Data Redis 4.1 起的 @RedisListener 註解寫法 ---
$srcAnnotated = @'
import org.springframework.data.redis.annotation.RedisListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ChatHandlerAnnotated extends TextWebSocketHandler {

  private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
  private RedisTemplate<String, String> redis;

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.add(session);
  }
  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage msg) {
    redis.convertAndSend("chat", msg.getPayload());
  }

  // 註解式：不需 MessageListener / MessageListenerAdapter，也不需 @Override
  @RedisListener(topic = "chat")
  public void onChat(String message) {
    sessions.forEach(s -> send(s, message));
  }

  private void send(WebSocketSession s, String message) { }
}
'@

function Invoke-Compile {
  param([string]$Name, [string]$Source, [string]$ClassPath = $cp)

  $file = Join-Path $work "$Name.java"
  Set-Content -Path $file -Value $Source -Encoding UTF8
  # javac 的非零退出碼在此為預期結果之一，故不讓它中斷腳本
  $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  $output = & $javac -encoding UTF-8 -nowarn -cp $ClassPath -d $work $file 2>&1 | Out-String
  $code = $LASTEXITCODE
  $ErrorActionPreference = $prev
  return [pscustomobject]@{ ExitCode = $code; Output = $output.Trim() }
}

Write-Host "使用 JDK：$jdk"
Write-Host ("=" * 64)

$r1 = Invoke-Compile -Name 'ChatHandlerOriginal' -Source $srcOriginal
$j1Pass = ($r1.ExitCode -ne 0)   # 預期「編譯失敗」才代表我們正確偵測到問題
Write-Host ("[{0}] J1 迴歸測試：修正前的 onMessage(String) 寫法應編譯失敗" -f $(if ($j1Pass) { 'CONFIRMED' } else { 'UNEXPECTED' }))
Write-Host "       exitCode=$($r1.ExitCode)"
if ($r1.Output) { Write-Host "       $($r1.Output -replace "`r?`n", "`n       ")" }
Write-Host ""

$r2 = Invoke-Compile -Name 'ChatHandlerFixed' -Source $srcFixed
Write-Host ("[{0}] J2 電子報現行第一段（官方 MessageListener 簽名，Spring Boot 3.x 線）" -f $(if ($r2.ExitCode -eq 0) { 'PASS' } else { 'FAIL' }))
Write-Host "       exitCode=$($r2.ExitCode)  classpath=Spring 6.2.12 / Data Redis 3.5.5"
if ($r2.Output) { Write-Host "       $($r2.Output -replace "`r?`n", "`n       ")" }
Write-Host ""

# J3：註解式寫法，改用 Spring 7 / Data Redis 4.1 的 classpath
$r3 = Invoke-Compile -Name 'ChatHandlerAnnotated' -Source $srcAnnotated -ClassPath $cpNew
Write-Host ("[{0}] J3 電子報現行 @RedisListener 段（Spring Boot 4.1 線）" -f $(if ($r3.ExitCode -eq 0) { 'PASS' } else { 'FAIL' }))
Write-Host "       exitCode=$($r3.ExitCode)  classpath=Spring 7.0.8 / Data Redis 4.1.0"
if ($r3.Output) { Write-Host "       $($r3.Output -replace "`r?`n", "`n       ")" }
Write-Host ""

# J4：版本分界舉證 —— 同一份 @RedisListener 原始碼，改用舊線 classpath 應找不到類別
$r4 = Invoke-Compile -Name 'ChatHandlerAnnotatedOnOldCp' -Source ($srcAnnotated -replace 'ChatHandlerAnnotated', 'ChatHandlerAnnotatedOnOldCp')
$j4Pass = ($r4.ExitCode -ne 0)   # 預期舊線編譯失敗，才能證明版本分界確實存在
Write-Host ("[{0}] J4 版本分界：同一段 @RedisListener 在 Data Redis 3.5.5 是否不可用" -f $(if ($j4Pass) { 'CONFIRMED' } else { 'UNEXPECTED' }))
Write-Host "       exitCode=$($r4.ExitCode)  classpath=Spring 6.2.12 / Data Redis 3.5.5"
if ($r4.Output) { Write-Host "       $(($r4.Output -split "`r?`n" | Select-Object -First 3) -join "`n       ")" }
Write-Host ""

Write-Host ("=" * 64)
Write-Host "結論："
$allOk = $j1Pass -and ($r2.ExitCode -eq 0) -and ($r3.ExitCode -eq 0) -and $j4Pass
if ($allOk) {
  Write-Host "  電子報現行的兩段 Java 皆可編譯，且版本分界敘述有實證支持："
  Write-Host "  1. 修正前的 onMessage(String) 仍編譯失敗 → 迴歸防護有效"
  Write-Host "  2. 官方 MessageListener 簽名可編譯 → 適用 Spring Boot 3.x（多數讀者環境）"
  Write-Host "  3. @RedisListener 可編譯 → 僅適用 Spring Boot 4.1 / Data Redis 4.1 以上"
  Write-Host "  4. 同段註解在 Data Redis 3.5.5 編譯失敗 → 電子報的版本警語屬實"
  exit 0
} else {
  Write-Host "  驗證結果與預期不符，請逐項檢查上方輸出。"
  exit 2
}
