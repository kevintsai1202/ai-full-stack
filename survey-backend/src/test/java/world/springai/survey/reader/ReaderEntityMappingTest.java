package world.springai.survey.reader;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 讀者端 entity 的對應與行為測試。
 *
 * <p>ddl-auto=validate 只在啟動時檢查 entity 與表結構是否吻合，單元測試不會觸發，
 * 因此欄位名拼錯要到部署啟動才會發現。這裡以反射檢查 snake_case 欄位是否都有
 * 明確的 @Column(name)，把該類錯誤提早到測試階段。</p>
 */
class ReaderEntityMappingTest {

    /** 四個 entity 都必須明確指定表名，表名不得依賴類名推導 */
    @Test
    void entitiesDeclareExplicitTableNames() {
        assertEquals("reader", Reader.class.getAnnotation(Table.class).name());
        assertEquals("credit_txn", CreditTxn.class.getAnnotation(Table.class).name());
        assertEquals("article_access", ArticleAccess.class.getAnnotation(Table.class).name());
        assertEquals("login_token", LoginToken.class.getAnnotation(Table.class).name());
    }

    /** 所有駝峰命名的欄位都必須有 @Column(name = "snake_case")，否則 validate 會在啟動時失敗 */
    @Test
    void camelCaseFieldsHaveExplicitColumnNames() {
        List<String> missing = new ArrayList<>();
        for (Class<?> type : List.of(Reader.class, CreditTxn.class, ArticleAccess.class, LoginToken.class)) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                boolean isCamelCase = !field.getName().equals(field.getName().toLowerCase());
                if (!isCamelCase) {
                    continue;
                }
                Column column = field.getAnnotation(Column.class);
                if (column == null || column.name().isEmpty()) {
                    missing.add(type.getSimpleName() + "." + field.getName());
                }
            }
        }
        assertTrue(missing.isEmpty(),
            "以下駝峰欄位缺少 @Column(name = \"snake_case\")，會導致啟動時 validate 失敗：" + missing);
    }

    /** 新讀者預設為 FREE、0 點 */
    @Test
    void newReaderDefaultsToFreeWithZeroCredits() {
        Reader reader = new Reader("user@example.com", "ABC12345");

        assertEquals("user@example.com", reader.getEmail());
        assertEquals(Reader.TIER_FREE, reader.getTier());
        assertEquals(0, reader.getCredits());
        assertEquals("ABC12345", reader.getReferralCode());
    }

    /** FREE 讀者不是有效 VIP */
    @Test
    void freeReaderIsNotActiveVip() {
        Reader reader = new Reader("user@example.com", "ABC12345");
        assertFalse(reader.isActiveVip(OffsetDateTime.now()));
    }

    /** VIP 未到期為有效；已到期視為無效（不做自動降級，靠判斷時比對） */
    @Test
    void vipIsActiveOnlyBeforeExpiry() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-25T00:00:00+08:00");
        Reader reader = new Reader("vip@example.com", "VIP12345");
        reader.setTier(Reader.TIER_VIP);

        reader.setVipExpiresAt(now.plusDays(1));
        assertTrue(reader.isActiveVip(now), "未到期應為有效 VIP");

        reader.setVipExpiresAt(now.minusDays(1));
        assertFalse(reader.isActiveVip(now), "已到期應視為 FREE");
    }

    /** VIP 且到期時間為 NULL 表示無限期 */
    @Test
    void vipWithoutExpiryIsPermanent() {
        Reader reader = new Reader("vip@example.com", "VIP12345");
        reader.setTier(Reader.TIER_VIP);
        reader.setVipExpiresAt(null);

        assertTrue(reader.isActiveVip(OffsetDateTime.now()));
    }

    /** login token 標記為已使用後不可重複使用 */
    @Test
    void loginTokenCanBeMarkedUsedOnce() {
        OffsetDateTime now = OffsetDateTime.now();
        LoginToken token = new LoginToken("hash", "user@example.com", now.plusMinutes(15));

        assertFalse(token.isUsed());
        token.markUsed(now);
        assertTrue(token.isUsed());
    }

    /** login token 過期判斷 */
    @Test
    void loginTokenExpiryIsEvaluatedAgainstGivenTime() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-25T12:00:00+08:00");
        LoginToken token = new LoginToken("hash", "user@example.com", now.plusMinutes(15));

        assertFalse(token.isExpired(now));
        assertTrue(token.isExpired(now.plusMinutes(16)));
    }
}
