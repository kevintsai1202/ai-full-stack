package world.springai.survey.audience;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 讀取管理員上傳的 XLSX 名單，並整理成可預覽、可匯入的人員資料。 */
@Component
public class AudienceSpreadsheetReader {

    /** 上傳檔案上限，避免管理 API 因大型壓縮工作簿耗盡記憶體。 */
    static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    /** 可處理的資料列上限，足以涵蓋課程名單且避免誤上傳大型報表。 */
    static final int MAX_DATA_ROWS = 5_000;
    /** 最多搜尋前 20 列找欄名，容許工作簿在表格上方放標題或說明。 */
    private static final int HEADER_SEARCH_ROWS = 20;
    /** Email 使用與既有 JSON 匯入 API 相同的寬鬆格式規則。 */
    private static final Pattern EMAIL_RE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** 正規化後可辨識的 Email 欄名。 */
    private static final Set<String> EMAIL_HEADERS = Set.of(
            "email", "電子郵件", "電子信箱", "信箱", "郵箱", "邮箱", "郵件地址", "邮件地址");
    /** 正規化後可辨識的姓名欄名；姓名欄可省略。 */
    private static final Set<String> NAME_HEADERS = Set.of(
            "name", "username", "fullname", "姓名", "名字", "學員姓名", "学员姓名", "顯示名稱", "显示名称",
            "名稱", "名称", "使用者名稱", "使用者名称", "用戶名", "用户名", "學員名稱", "学员名称");

    /** 單一可匯入人員；欄位名稱與既有 JSON 匯入 API 相同。 */
    public record Person(String email, String name) {}

    /** 預覽結果：people 僅包含 Email 合法且在檔案內去重後的資料。 */
    public record Preview(
            String sheetName,
            int totalRows,
            int validCount,
            int invalidCount,
            int duplicateCount,
            List<Person> people) {

        /** 避免除錯或記錄預覽物件時，record 預設字串把姓名與 Email 寫進日誌。 */
        @Override
        public String toString() {
            return "Preview[sheetName=%s, totalRows=%d, validCount=%d, invalidCount=%d, duplicateCount=%d]"
                    .formatted(sheetName, totalRows, validCount, invalidCount, duplicateCount);
        }
    }

    /** 找到的欄位位置與標題列位置。 */
    private record Header(int rowIndex, int emailColumn, int nameColumn) {}

    /** 解析錯誤會由 Controller 轉成可讀的 HTTP 400 訊息。 */
    public static class SpreadsheetException extends RuntimeException {
        public SpreadsheetException(String message) {
            super(message);
        }

        public SpreadsheetException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 解析 XLSX 第一個包含 Email 欄位的工作表。
     * 不寫入資料庫，也不記錄姓名或 Email，讓管理員能先安全預覽。
     */
    public Preview read(MultipartFile file) {
        validateFile(file);
        try (InputStream input = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.TAIWAN);
            for (Sheet sheet : workbook) {
                Header header = findHeader(sheet, formatter, evaluator);
                if (header != null) {
                    return readSheet(sheet, header, formatter, evaluator);
                }
            }
            throw new SpreadsheetException("找不到 Email 欄位，請確認欄名包含 Email、電子郵件或電子信箱");
        } catch (SpreadsheetException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new SpreadsheetException("無法讀取 XLSX，請確認檔案未損壞且不是加密工作簿", exception);
        }
    }

    /** 檢查檔案存在、大小與副檔名，錯誤時提供管理員可直接修正的訊息。 */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new SpreadsheetException("請選擇要匯入的 XLSX 檔案");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new SpreadsheetException("XLSX 檔案不可超過 2 MB");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new SpreadsheetException("目前只支援 .xlsx 檔案");
        }
    }

    /** 在工作表前幾列尋找必要的 Email 欄與選填的姓名欄。 */
    private Header findHeader(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int lastRow = Math.min(sheet.getLastRowNum(), HEADER_SEARCH_ROWS - 1);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            int emailColumn = -1;
            int nameColumn = -1;
            for (Cell cell : row) {
                String header = normalizeHeader(cellText(cell, formatter, evaluator));
                if (EMAIL_HEADERS.contains(header)) {
                    emailColumn = cell.getColumnIndex();
                } else if (NAME_HEADERS.contains(header)) {
                    nameColumn = cell.getColumnIndex();
                }
            }
            if (emailColumn >= 0) {
                return new Header(rowIndex, emailColumn, nameColumn);
            }
        }
        return null;
    }

    /** 逐列整理資料，計算格式錯誤與檔案內重複筆數。 */
    private Preview readSheet(
            Sheet sheet,
            Header header,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        List<Person> people = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        int totalRows = 0;
        int invalidCount = 0;
        int duplicateCount = 0;
        for (int rowIndex = header.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            String email = cellText(row, header.emailColumn(), formatter, evaluator).trim();
            String name = cellText(row, header.nameColumn(), formatter, evaluator).trim();
            if (email.isEmpty() && name.isEmpty()) {
                continue;
            }
            totalRows++;
            if (totalRows > MAX_DATA_ROWS) {
                throw new SpreadsheetException("名單不可超過 5,000 筆");
            }
            if (!EMAIL_RE.matcher(email).matches()) {
                invalidCount++;
                continue;
            }
            String normalizedEmail = email.toLowerCase(Locale.ROOT);
            if (!seenEmails.add(normalizedEmail)) {
                duplicateCount++;
                continue;
            }
            people.add(new Person(email, name.isEmpty() ? null : name));
        }
        return new Preview(
                sheet.getSheetName(),
                totalRows,
                people.size(),
                invalidCount,
                duplicateCount,
                List.copyOf(people));
    }

    /** 安全取得指定欄位文字；姓名欄不存在時直接回空字串。 */
    private String cellText(
            Row row,
            int columnIndex,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        if (row == null || columnIndex < 0) {
            return "";
        }
        return cellText(row.getCell(columnIndex), formatter, evaluator);
    }

    /** 使用 POI 格式化器讀取儲存格，保留畫面上看到的文字格式。 */
    private String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator);
    }

    /** 移除常見分隔符號並統一大小寫，讓 Email、E-mail、電子郵件都能辨識。 */
    private String normalizeHeader(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-－—()（）:：]+", "");
    }
}
