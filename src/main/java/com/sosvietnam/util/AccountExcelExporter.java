package com.sosvietnam.util;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * Xuất danh sách tài khoản ra file Excel (.xlsx), cột theo bảng tài khoản của bản cũ
 * (services/accounts-excel-generator.js).
 * Cột "Mật Khẩu": DB chỉ lưu bcrypt, nên chỉ ghi được mật khẩu mà người gọi biết
 * (passwordOf trả về null = không biết).
 */
@Component
public class AccountExcelExporter {

    private static final String SHEET_NAME = "Danh sách tài khoản";
    public static final String UNKNOWN_PASSWORD = "Đã mã hoá – không xem được";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String[] HEADERS = {
            "STT", "Khu Vực (Tỉnh/TP)", "Lực Lượng Nghiệp Vụ", "Cấp Đơn Vị", "Đơn Vị / Họ Tên",
            "Địa Bàn (Xã/Phường)", "Email Đăng Nhập", "Mật Khẩu", "Vai Trò", "Số Điện Thoại", "Địa Chỉ",
            "Trạng Thái", "Ngày Tạo"
    };
    private static final int[] WIDTHS = {6, 18, 22, 16, 40, 22, 30, 26, 12, 15, 45, 14, 18};

    public byte[] export(List<Account> accounts, Function<Account, String> passwordOf) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle bodyStyle = bodyStyle(workbook);

            Row header = sheet.createRow(0);
            header.setHeightInPoints(22);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, WIDTHS[i] * 256);
            }

            int rowIndex = 1;
            for (Account account : accounts) {
                Row row = sheet.createRow(rowIndex);
                String[] values = {
                        String.valueOf(rowIndex),
                        orEmpty(account.provinceName()),
                        account.getAgencyType() != null ? account.getAgencyType().getLabel() : "",
                        levelLabel(account.getAgencyType(), account.getUnitLevel()),
                        account.fullName(),
                        orEmpty(account.wardName()),
                        account.getEmail(),
                        passwordOrUnknown(passwordOf.apply(account)),
                        account.getRole() != null ? account.getRole().getRoleName() : "",
                        orEmpty(account.getPhone()),
                        orEmpty(account.getAddress()),
                        account.isLocked() ? "Đã khoá" : (account.isActive() ? "Hoạt động" : "Ngưng hoạt động"),
                        account.getCreatedDate() != null ? account.getCreatedDate().format(DATE_FORMAT) : ""
                };
                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(bodyStyle);
                }
                rowIndex++;
            }

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(rowIndex - 1, 0), 0, HEADERS.length - 1));
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** Tên cấp theo lực lượng: CSGT, PCCC gọi là cấp Đội / cấp Phòng, còn lại theo UnitLevel. */
    static String levelLabel(AgencyType agency, UnitLevel level) {
        if (level == null) return "";
        return agency != null ? agency.levelLabel(level) : level.getLabel();
    }

    private static String passwordOrUnknown(String plain) {
        return plain != null ? plain : UNKNOWN_PASSWORD;
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }

    private static CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        setThinBorders(style);
        return style;
    }

    private static CellStyle bodyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        setThinBorders(style);
        return style;
    }

    private static void setThinBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
