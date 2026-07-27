package world.springai.survey.audience;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import world.springai.survey.AdminKeyGuard;

/** 外部名單匯入後台 API：匯入為待確認狀態（consent=false），需 X-Admin-Key */
@RestController
public class AdminImportController {

    private final AdminKeyGuard guard;
    private final AudienceSpreadsheetReader spreadsheetReader;
    private final AudienceSourceService sourceService;
    private final AudienceImportService importService;

    /** 注入金鑰守衛、XLSX 解析器、來源管理與通用匯入服務。 */
    public AdminImportController(
            AdminKeyGuard guard,
            AudienceSpreadsheetReader spreadsheetReader,
            AudienceSourceService sourceService,
            AudienceImportService importService) {
        this.guard = guard;
        this.spreadsheetReader = spreadsheetReader;
        this.sourceService = sourceService;
        this.importService = importService;
    }

    /** 匯入的單一人員：email 必填，name 選填 */
    public record Person(String email, String name) {}

    /** 匯入請求：來源標記（如 exam）與人員清單 */
    public record ImportRequest(String source, List<Person> people) {}

    /** 新增來源請求；label 是後台顯示名稱，內部 key 由服務產生。 */
    public record SourceRequest(String label) {}

    /** 列出系統預設與管理員自訂的名單來源。 */
    @GetMapping("/api/admin/import/sources")
    public List<AudienceSourceService.SourceOption> listSources(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        guard.verify(key);
        return sourceService.list();
    }

    /** 新增並保存一個名單來源；同名來源直接回傳既有項目。 */
    @PostMapping("/api/admin/import/sources")
    public AudienceSourceService.SourceOption addSource(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody SourceRequest request) {
        guard.verify(key);
        try {
            return sourceService.add(request == null ? null : request.label());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    /**
     * 預覽 XLSX 名單，不寫入資料庫。
     * 回傳合法人員清單與無效／重複計數，確認後再交給既有 JSON 匯入 API。
     */
    @PostMapping(value = "/api/admin/import/xlsx/preview", consumes = "multipart/form-data")
    public AudienceSpreadsheetReader.Preview previewSpreadsheet(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestPart("file") MultipartFile file) {
        guard.verify(key);
        try {
            return spreadsheetReader.read(file);
        } catch (AudienceSpreadsheetReader.SpreadsheetException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    /**
     * 匯入外部名單：人物依 Email 合併，但每個來源活動仍會建立或更新；
     * 同批重複與格式錯誤列入 invalid，退訂狀態不會被匯入覆蓋。
     */
    @PostMapping("/api/admin/import")
    public AudienceImportService.ImportResult importPeople(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody ImportRequest req) {
        guard.verify(key);
        // source 與 people 為必填，缺一回 400
        if (!StringUtils.hasText(req.source()) || req.people() == null || req.people().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source 與 people 為必填");
        }
        List<AudienceImportService.ImportPerson> people = req.people().stream()
            .map(person -> new AudienceImportService.ImportPerson(
                person.email(), person.name(), java.util.Map.of()))
            .toList();
        return importService.importPeople(req.source(), people);
    }
}
