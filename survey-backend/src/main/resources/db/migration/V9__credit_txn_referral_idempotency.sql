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
CREATE UNIQUE INDEX uq_credit_txn_referral_note
    ON credit_txn (note) WHERE reason = 'REFERRAL';
