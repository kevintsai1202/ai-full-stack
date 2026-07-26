package world.springai.survey.reader;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * <b>本測試<u>不</u>執行任何 SQL，也不驗證任何 repository 的 {@code @Query}</b>——
 * {@link ReaderRepository} 與 {@link CreditTxnRepository} 全部是 {@code @MockBean}，
 * 生產查詢一句都不會被送到資料庫。它<b>唯一</b>驗證的事情是
 * {@link AdminReaderService} 的 {@code @Transactional} 真的經過 Spring proxy。
 *
 * <p><b>不要把它讀成 {@code updateVip} 或 {@code addCredits} 的覆蓋</b>：
 * 把那兩支 {@code @Modifying} 查詢改成什麼都不做（甚至改成 {@code and 1 = 0}），
 * 本測試仍然全綠——mock 照樣回傳被 stub 的筆數。實測確認過。那兩支查詢本體的覆蓋
 * 在 {@link AdminVipPersistenceTest}（真實 PostgreSQL + {@code StatementInspector}）。
 * 類名裡的 {@code AdminReader} 與 {@code @SpringBootTest} 上那組真實 datasource
 * 設定曾經誤導過一整批工作（那組 datasource 只是為了讓 context 起得來，
 * 本測試從頭到尾沒有用它讀寫任何一列），故此段不可刪。</p>
 *
 * <p><b>為什麼一定要一個會啟動 context 的測試</b>：
 * {@link AdminReaderControllerTest} 用 {@code standaloneSetup} 直接 new 出實例，
 * 沒有 proxy，交易註解在那裡根本不參與——把兩處 {@code @Transactional} 整個註解掉，
 * 那一整份測試仍然全綠。也就是說，整個「餘額變動與帳本寫入必須在同一交易內」
 * 的核心保證，在那份測試裡是零覆蓋。</p>
 *
 * <p>日後可能讓它失效的重構，每一種都不會有其他測試變紅：
 * ① 把交易性程式碼抽成<b>同類別</b>的私有方法再加註解（同類別內部呼叫不過 proxy）；
 * ② 以「controller 不該有交易」為由把註解搬回 controller 或直接移除；
 * ③ 把 {@link AdminReaderService} 改成 controller 自己 new 出來的物件。
 * 三種都會讓餘額變動與帳本寫入落到兩個獨立交易，中途失敗就留下
 * 「餘額加了但帳本沒寫」——而 {@code reader.credits} 永遠等於
 * {@code credit_txn} 總和是本系統的核心不變式。</p>
 *
 * <p>作法：在被 proxy 的 bean 上呼叫方法，於 Mockito stub 的回呼裡斷言
 * {@link TransactionSynchronizationManager#isActualTransactionActive()}。
 * 所有 repository 都是 {@code @MockBean}，本身不會開任何交易，
 * 所以「交易存在」只可能來自本服務的註解——這是唯一能證明 proxy 真的生效的方式。</p>
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/postgres",
    "spring.datasource.username=postgres",
    "spring.datasource.password=password"
})
class AdminReaderTransactionTest {

    private static final long READER_ID = 3L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-25T12:00:00+08:00");

    /** 由 Spring 注入，因此帶著交易 proxy——這正是本測試的重點 */
    @Autowired AdminReaderService service;

    @MockBean ReaderRepository readerRepository;
    @MockBean CreditTxnRepository creditTxnRepository;
    @MockBean ReaderAccountService readerAccountService;

    /** 建一個帶 id 的讀者 */
    private static Reader reader(String email) {
        Reader r = new Reader(email, "CODE1234");
        r.setId(READER_ID);
        r.setCredits(300);
        return r;
    }

    /** 目前是否處於實體交易中；不在交易內回 null 名稱 */
    private static String currentTransactionName() {
        return TransactionSynchronizationManager.isActualTransactionActive()
            ? TransactionSynchronizationManager.getCurrentTransactionName()
            : null;
    }

    /**
     * 批次加點的帳本寫入必須發生在一個實體交易內。
     *
     * <p>把 {@code AdminReaderService.grantCredits} 的 {@code @Transactional}
     * 拿掉，這個測試就會失敗（{@code creditTxnRepository} 是 mock，
     * 不會自己開交易）——那正是它存在的理由。</p>
     */
    @Test
    void grantCreditsWritesLedgerInsideTransaction() {
        AtomicReference<String> ledgerTxn = new AtomicReference<>();
        AtomicReference<String> balanceTxn = new AtomicReference<>();

        when(readerRepository.findByEmailIgnoreCase(anyString()))
            .thenReturn(Optional.of(reader("a@b.com")));
        when(readerRepository.addCredits(anyLong(), anyInt())).thenAnswer(inv -> {
            balanceTxn.set(currentTransactionName());
            return 1;
        });
        when(creditTxnRepository.save(any(CreditTxn.class))).thenAnswer(inv -> {
            ledgerTxn.set(currentTransactionName());
            return inv.getArgument(0);
        });

        service.grantCredits(List.of("a@b.com"), 100, "測試");

        assertNotNull(balanceTxn.get(), "餘額變動不在交易內：@Transactional 沒有經過 proxy");
        assertNotNull(ledgerTxn.get(), "帳本寫入不在交易內：@Transactional 沒有經過 proxy");
        // 同一個交易名稱才能證明兩個寫入會一起提交、一起回滾
        assertEquals(balanceTxn.get(), ledgerTxn.get(), "餘額變動與帳本寫入落在不同交易");
        assertTrue(ledgerTxn.get().contains("AdminReaderService.grantCredits"),
            "交易不是由 AdminReaderService.grantCredits 開的：" + ledgerTxn.get());
    }

    /**
     * 授予 VIP 時「建帳戶（含初始贈點帳本）」與「改 tier／到期日」必須同屬一個交易。
     *
     * <p>分成兩個交易的話，建了帳戶卻沒設成 VIP 會半套落地：
     * 站方看到操作失敗，但讀者已經憑空多出一個帳戶與一筆贈點帳本。</p>
     */
    @Test
    void grantVipCreatesAccountAndSetsTierInSameTransaction() {
        AtomicReference<String> ledgerTxn = new AtomicReference<>();
        AtomicReference<String> vipUpdateTxn = new AtomicReference<>();

        when(readerRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        // 模擬真實的 findOrCreateWithoutLogin：建帳的同時會寫一筆初始贈點帳本
        when(readerAccountService.findOrCreateWithoutLogin(anyString(), any())).thenAnswer(inv -> {
            creditTxnRepository.save(new CreditTxn(
                READER_ID, 300, CreditTxn.REASON_SIGNUP_GRANT, null, "首次登入初始贈點"));
            return reader("new@b.com");
        });
        when(creditTxnRepository.save(any(CreditTxn.class))).thenAnswer(inv -> {
            ledgerTxn.set(currentTransactionName());
            return inv.getArgument(0);
        });
        when(readerRepository.updateVip(anyLong(), anyString(), any())).thenAnswer(inv -> {
            vipUpdateTxn.set(currentTransactionName());
            return 1;
        });

        service.grantVip("new@b.com", 30, NOW);

        assertNotNull(ledgerTxn.get(), "建帳帳本寫入不在交易內：@Transactional 沒有經過 proxy");
        assertNotNull(vipUpdateTxn.get(), "VIP 更新不在交易內：@Transactional 沒有經過 proxy");
        assertEquals(ledgerTxn.get(), vipUpdateTxn.get(), "建帳與 VIP 設定落在不同交易");
        assertTrue(vipUpdateTxn.get().contains("AdminReaderService.grantVip"),
            "交易不是由 AdminReaderService.grantVip 開的：" + vipUpdateTxn.get());
    }
}
