-- 邀請獎勵的冪等改由資料庫保證（spec §5.4）。
--
-- 在此之前，ReferralService.rewardFor 以「先查 credit_txn 有沒有這個 note、沒有才寫」
-- 判斷是否已發過，是典型的 check-then-act。失效情境不是理論問題：Outlook Safe Links、
-- Gmail 的圖片代理會對信中連結做背景 GET，與讀者本人的點擊構成真實的併發，
-- 兩個獨立交易都可能在對方提交前判讀為「未發過」而各自發獎。
--
-- 冪等鍵是 credit_txn.note，內容為「被邀者的 email」（寫入端與檢查端用完全相同的
-- 正規化值），語意是「一個被邀者一生只能讓人成功邀請一次」。

-- 【為什麼是「部分」索引，而不是 (reason, note) 複合唯一】
-- 其他 reason 的 note 本來就會重複，而且那些重複全是正常操作：
--   SIGNUP_GRANT：note 對每一位讀者都是同一個固定字串「首次登入初始贈點」，
--                 複合唯一會讓「第二位讀者首次登入」直接失敗。
--   READ        ：note 是文章主旨，同一篇被第二個人解鎖就重複；
--                 不同期電子報主旨相同（例如系列文沿用同一標題）也會重複。
--   ADMIN_GRANT ：note 是後台加點的說明，同一批學員每一筆都填同一句。
-- 也就是說複合唯一擋掉的不是濫用，而是日常。因此唯一性只加在
-- reason = 'REFERRAL' 這個子集上，其餘 reason 完全不受影響。

-- 【note IS NULL 的殘留空隙是刻意保留的，不是遺漏】
-- PostgreSQL 的 UNIQUE 把 NULL 視為互異（NULL 不等於任何值，包含另一個 NULL），
-- 所以 reason='REFERRAL' 且 note IS NULL 的列可以有無限多筆，不受本索引保護。
-- 在述詞裡再加 AND note IS NOT NULL 對唯一性語意毫無差別（那些列本來就不受約束），
-- 只會讓述詞看起來多了一道其實不存在的保護，故不加。
-- 這個空隙由程式端關閉：REFERRAL 一律寫入被邀者 email（ReferralService.rewardFor），
-- 並由 ReferralServiceTest.ledgerNoteIsExactlyTheInviteeEmail 釘住那個值。
-- 反過來說，本索引也不強制 note 非空——credit_txn.note 對其他 reason 可為 NULL
-- 是既有慣例（V7），不因本次修正而改變。

-- 【既有資料風險為零】
-- credit_txn 由 V7 建立，而正式資料庫目前仍在 V6（V7／V8 尚未部署）。
-- 因此 V7→V9 首次在正式環境執行時，本索引是建在剛建好的空表上，
-- 不存在「既有重複列導致 CREATE UNIQUE INDEX 失敗」的可能。

-- 【鎖與 CONCURRENTLY 的取捨：不要照抄本檔的寫法去對大表加索引】
-- 下面兩道 CREATE INDEX 都是「非 CONCURRENTLY」的普通建索引，會對整張表取
-- ACCESS EXCLUSIVE 鎖，建索引期間該表連 SELECT 都會被擋住。在這裡是安全的，
-- 唯一的理由是「兩張表在本次部署當下都是空的或極小」（見上一段），
-- 空表上取鎖是瞬時操作，鎖不鎖沒有差別。
--
-- 反過來說：日後要為已上線且有數十萬列以上的表加索引時，絕不可照抄這個形狀，
-- 否則整張表會在建索引期間被鎖死。那種情況要用 CREATE INDEX CONCURRENTLY，
-- 而它有三個非顯而易見的限制：
--   ① CONCURRENTLY 不能在交易區塊內執行。Flyway 預設把每一支 migration 包在
--      一個交易裡，所以該檔案「必須」在最上方加上：
--        -- flyway:executeInTransaction=false
--      漏了這行會直接失敗（ERROR: CREATE INDEX CONCURRENTLY cannot run inside
--      a transaction block），而不是靜默退化成普通建索引。
--   ② 關掉交易的代價是該支 migration 不再是原子的：中途失敗會留下已執行的部分，
--      因此那種檔案裡只該放這一道敘述。
--   ③ CONCURRENTLY 失敗（例如唯一索引撞到既有重複列、或連線中斷）會留下一個
--      INVALID 的索引，它不會被查詢使用卻仍會拖慢寫入，必須人工
--      DROP INDEX（可加 CONCURRENTLY）清掉，Flyway 不會幫你回收。
--      清掉之前重跑同名索引也會因為名稱衝突而失敗。

CREATE UNIQUE INDEX uq_credit_txn_referral_note
    ON credit_txn (note) WHERE reason = 'REFERRAL';

-- 【reader.referred_by 的索引】
-- V7 只宣告了這個欄位，沒有為它建索引（當時沒有任何查詢用到它）。現在
-- ReferralService.stats 的邀請人數是「帳本 REFERRAL 的 note」與「referred_by 指向
-- 自己的讀者 email」的聯集，後者是 ReaderRepository.findInviteeEmailsByReferredBy
-- 的 WHERE 條件——而 /r/invite 是登入讀者可以任意重新整理的頁面。沒有索引的話，
-- 讀者數成長到數萬之後，每次重載都是一次 reader 全表掃描。
-- V7 已經為 referral_code 與 credit_txn(reader_id, created_at) 建了索引，
-- 唯獨這個「後來才出現的查詢路徑」漏了，在此補上。
--
-- 用部分索引（WHERE referred_by IS NOT NULL）而不是普通索引：referred_by 只有
-- 「透過別人的邀請連結進來且已建帳」的讀者才有值，絕大多數列是 NULL。
-- 把 NULL 列排除在索引外可讓索引小很多，而查詢一律帶 referred_by = ?
-- （= 隱含 IS NOT NULL），規劃器仍然用得到這個部分索引。
CREATE INDEX idx_reader_referred_by
    ON reader (referred_by) WHERE referred_by IS NOT NULL;
