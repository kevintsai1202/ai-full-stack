-- V23__survey_vote_reward.sql
-- 一鍵投票發點：每人每問卷一次（partial unique 防併發重發）。
-- 形狀刻意與 V21 的 uq_credit_txn_survey_reward 完全一致——兩者是同一個
-- 「每人每問卷一次」不變式的兩種發點原因，索引形狀不同會讓日後維護者
-- 誤以為其中一種有額外語意。沿用 V21 已建立的 credit_txn.survey_form_key 欄位。
CREATE UNIQUE INDEX uq_credit_txn_survey_vote_reward
    ON credit_txn (reader_id, survey_form_key)
    WHERE reason = 'SURVEY_VOTE_REWARD';
