-- V20__promo_link_allow_mailto.sql
-- 工商連結開放 Email：除 https:// 外允許 mailto:。
-- 純 Email 輸入由應用層（PromoProposalService.normalizeLinkUrl）補上 mailto: 前綴後才入庫，
-- 因此 DB 層只需承認兩種 scheme。
ALTER TABLE promo_proposal DROP CONSTRAINT ck_promo_link_https;
ALTER TABLE promo_proposal ADD CONSTRAINT ck_promo_link_scheme
    CHECK (link_url LIKE 'https://%' OR link_url LIKE 'mailto:%');
