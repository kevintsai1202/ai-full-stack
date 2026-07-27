package world.springai.survey.audience;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** XLSX 名單解析測試：中文欄名、格式錯誤、檔案內去重與無姓名情境。 */
class AudienceSpreadsheetReaderTest {

    private final AudienceSpreadsheetReader reader = new AudienceSpreadsheetReader();

    /** 「名字／Email」欄應可辨識，並正確統計無效與重複 Email。 */
    @Test
    void readsChineseNameAndEmailHeaders() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Dify 學員");
            var title = sheet.createRow(0);
            title.createCell(0).setCellValue("課程學員名單");
            var header = sheet.createRow(1);
            header.createCell(0).setCellValue("名字");
            header.createCell(1).setCellValue("Email");
            addRow(sheet, 2, "王小明", "one@example.com");
            addRow(sheet, 3, "重複資料", "ONE@example.com");
            addRow(sheet, 4, "格式錯誤", "not-an-email");
            addRow(sheet, 5, "", "two@example.com");
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        AudienceSpreadsheetReader.Preview result = reader.read(file("Dify 學員.xlsx", workbookBytes));

        assertEquals("Dify 學員", result.sheetName());
        assertEquals(4, result.totalRows());
        assertEquals(2, result.validCount());
        assertEquals(1, result.invalidCount());
        assertEquals(1, result.duplicateCount());
        assertEquals("王小明", result.people().get(0).name());
        assertEquals("one@example.com", result.people().get(0).email());
        assertNull(result.people().get(1).name());
    }

    /** 沒有姓名欄時仍可匯入 Email，姓名回 null。 */
    @Test
    void nameColumnIsOptional() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("電子郵件");
            sheet.createRow(1).createCell(0).setCellValue("student@example.com");
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        AudienceSpreadsheetReader.Preview result = reader.read(file("students.xlsx", workbookBytes));

        assertEquals(1, result.validCount());
        assertNull(result.people().getFirst().name());
    }

    /** Dify 匯出的「User Name」欄應視為姓名。 */
    @Test
    void readsDifyUserNameHeader() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Sheet 1");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("User Name");
            header.createCell(1).setCellValue("Email");
            addRow(sheet, 1, "王小明", "student@example.com");
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        AudienceSpreadsheetReader.Preview result = reader.read(file("Dify 學員.xlsx", workbookBytes));

        assertEquals("王小明", result.people().getFirst().name());
        assertEquals("student@example.com", result.people().getFirst().email());
    }

    /** 非 XLSX 檔案應在解析前被拒絕。 */
    @Test
    void rejectsUnsupportedExtension() {
        AudienceSpreadsheetReader.SpreadsheetException exception = assertThrows(
                AudienceSpreadsheetReader.SpreadsheetException.class,
                () -> reader.read(file("students.csv", new byte[] {1, 2, 3})));

        assertEquals("目前只支援 .xlsx 檔案", exception.getMessage());
    }

    /** 建立測試上傳檔案。 */
    private MockMultipartFile file(String filename, byte[] bytes) {
        return new MockMultipartFile(
                "file", filename,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);
    }

    /** 在測試工作簿新增一列姓名與 Email。 */
    private void addRow(org.apache.poi.ss.usermodel.Sheet sheet, int index, String name, String email) {
        var row = sheet.createRow(index);
        row.createCell(0).setCellValue(name);
        row.createCell(1).setCellValue(email);
    }
}
