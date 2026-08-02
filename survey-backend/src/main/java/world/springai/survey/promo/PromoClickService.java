package world.springai.survey.promo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import world.springai.survey.reader.ReaderSessionService;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 工商連結點擊：解析目的地並 best-effort 記錄歸戶。
 *
 * <p>歸戶順序 RECIPIENT（token）→ READER（session）→ ANON；
 * 只有 COMMITTED 版位記錄——DRAFT 涵蓋測試信與預覽，天然不入統計（spec §5）。</p>
 *
 * <p><b>記錄失敗不擋轉址</b>：點擊統計是輔助數據，讀者到得了目的地是主體驗；
 * 寫入失敗記 log 讓監控看到即可。</p>
 */
@Service
public class PromoClickService {

    private static final Logger log = LoggerFactory.getLogger(PromoClickService.class);

    private final PromoPlacementRepository placementRepository;
    private final PromoProposalRepository proposalRepository;
    private final PromoClickRepository clickRepository;
    private final PromoRecipientTokenService tokenService;
    private final ReaderSessionService sessionService;

    /** 注入版位、提案、點擊、token 與 session 服務 */
    public PromoClickService(PromoPlacementRepository placementRepository,
                             PromoProposalRepository proposalRepository,
                             PromoClickRepository clickRepository,
                             PromoRecipientTokenService tokenService,
                             ReaderSessionService sessionService) {
        this.placementRepository = placementRepository;
        this.proposalRepository = proposalRepository;
        this.clickRepository = clickRepository;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
    }

    /** 查目的地並記錄點擊；empty＝版位或提案不存在（404） */
    public Optional<String> resolveAndRecord(long placementId, String rt, String sessionCookie) {
        Optional<PromoPlacement> placement = placementRepository.findById(placementId);
        if (placement.isEmpty()) return Optional.empty();
        Optional<PromoProposal> proposal = proposalRepository.findById(placement.get().getProposalId());
        if (proposal.isEmpty()) return Optional.empty();

        if (PromoPlacement.STATUS_COMMITTED.equals(placement.get().getStatus())) {
            PromoClick click = buildClick(placementId, rt, sessionCookie); // 身分解析例外要浮出，不屬 best-effort 範圍
            try {
                clickRepository.save(click);
            } catch (RuntimeException e) {
                log.warn("promo 點擊記錄失敗 placement={}，轉址照常", placementId, e);
            }
        }
        return Optional.of(proposal.get().getLinkUrl());
    }

    /** 依歸戶順序組出點擊列 */
    private PromoClick buildClick(long placementId, String rt, String sessionCookie) {
        Optional<String> email = tokenService.verify(rt);
        if (email.isPresent()) {
            return new PromoClick(placementId, PromoClick.CHANNEL_EMAIL,
                PromoClick.IDENTITY_RECIPIENT, email.get());
        }
        Optional<Long> readerId = sessionService.readReaderId(sessionCookie, OffsetDateTime.now());
        if (readerId.isPresent()) {
            return new PromoClick(placementId, PromoClick.CHANNEL_WEB,
                PromoClick.IDENTITY_READER, String.valueOf(readerId.get()));
        }
        return new PromoClick(placementId, PromoClick.CHANNEL_WEB,
            PromoClick.IDENTITY_ANON, null);
    }
}
