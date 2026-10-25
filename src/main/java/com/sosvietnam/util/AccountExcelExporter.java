package com.sosvietnam.util;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.UnitMember;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.enums.UnitRole;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Xuất danh sách tài khoản ra file Excel (.xlsx), cột theo bảng tài khoản của bản cũ
 * (services/accounts-excel-generator.js), thêm sheet "Cán bộ" (mỗi dòng 1 cán bộ của đơn vị).
 * Cột "Mật Khẩu" / "PIN": DB chỉ lưu bcrypt, nên chỉ ghi được giá trị mà người gọi biết
 * (passwordOf / pinOf trả về null = không biết).
 * Dùng SXSSF (ghi dần ra file tạm) vì toàn quốc có hơn 10 nghìn đơn vị, 30 nghìn cán bộ.
 */
@Component
public class AccountExcelExporter {

    private static final String SHEET_NAME = "Danh sách tài khoản";
    private static final String MEMBER_SHEET_NAME = "Cán bộ";
    public static final String UNKNOWN_PASSWORD = "Đã mã hoá – không xem được";
    public static final String UNKNOWN_PIN = "Đã đổi – không xem được";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String[] HEADERS = {
            "STT", "Khu Vực (Tỉnh/TP)", "Lực Lượng Nghiệp Vụ", "Cấp Đơn Vị", "Đơn Vị / Họ Tên",
            "Địa Bàn (Xã/Phường)", "Email Đăng Nhập", "Mật Khẩu", "Vai Trò", "Số Điện Thoại", "Địa Chỉ",
            "Chỉ Huy", "Trực Ban Hiện Tại", "Số Cán Bộ", "Trạng Thái", "Ngày Tạo"
    };
    private static final int[] WIDTHS = {6, 18, 22, 16, 40, 22, 30, 26, 12, 15, 45, 28, 28, 10, 14, 18};

    private static final String[] MEMBER_HEADERS = {
            "STT", "Khu Vực (Tỉnh/TP)", "Lực Lượng Nghiệp Vụ", "Cấp Đơn Vị", "Đơn Vị", "Email Đơn Vị",
            "Họ Tên", "Số Hiệu", "Cấp Bậc", "Chức Vụ", "Vai Trò", "Đang Trực Ban", "Số Điện Thoại", "PIN",
            "Trạng Thái", "Ngày Tạo"
    };
    private static final int[] MEMBER_WIDTHS = {6, 18, 22, 16, 40, 34, 26, 12, 12, 24, 10, 12, 15, 24, 16, 18};

    /** Chỉ sheet tài khoản (không có dữ liệu cán bộ). */
    public byte[] export(List<Account> accounts, Function<Account, String> passwordOf) throws IOException {
        return export(accounts, passwordOf, List.of(), new java.util.HashMap<>(), m -> null); // HashMap: get(null) hợp lệ
    }

    /**
     * @param members      cán bộ đang hoạt động của các tài khoản trong {@code accounts}, đã sắp sẵn thứ tự
     * @param dutyByAccount account id -> member id của trực ban đang trực
     */
    public byte[] export(List<Account> accounts, Function<Account, String> passwordOf, List<UnitMember> members,
                         Map<UUID, UUID> dutyByAccount, Function<UnitMember, String> pinOf) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle bodyStyle = bodyStyle(workbook);

            Map<UUID, List<UnitMember>> membersByAccount = members.stream()
                    .collect(Collectors.groupingBy(m -> m.getAccount().getId()));
            Sheet sheet = createSheet(workbook, SHEET_NAME, HEADERS, WIDTHS, headerStyle);
            int rowIndex = 1;
            for (Account account : accounts) {
                List<UnitMember> own = membersByAccount.getOrDefault(account.getId(), List.of());
                UUID dutyId = dutyByAccount.get(account.getId());
                writeRow(sheet.createRow(rowIndex), bodyStyle,
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
                        names(own.stream().filter(m -> m.getUnitRole() == UnitRole.COMMANDER).toList()),
                        names(own.stream().filter(m -> m.getId().equals(dutyId)).toList()),
                        own.isEmpty() ? "" : String.valueOf(own.size()),
                        account.isLocked() ? "Đã khoá" : (account.isActive() ? "Hoạt động" : "Ngưng hoạt động"),
                        formatDate(account.getCreatedDate()));
                rowIndex++;
            }
            finish(sheet, rowIndex, HEADERS.length);

            if (!members.isEmpty()) {
                Sheet memberSheet = createSheet(workbook, MEMBER_SHEET_NAME, MEMBER_HEADERS, MEMBER_WIDTHS, headerStyle);
                int memberRow = 1;
                for (UnitMember member : members) {
                    Account unit = member.getAccount();
                    String pin = pinOf.apply(member);
                    writeRow(memberSheet.createRow(memberRow), bodyStyle,
                            String.valueOf(memberRow),
                            orEmpty(unit.provinceName()),
                            unit.getAgencyType() != null ? unit.getAgencyType().getLabel() : "",
                            levelLabel(unit.getAgencyType(), unit.getUnitLevel()),
                            unit.fullName(),
                            unit.getEmail(),
                            member.getFullName(),
                            orEmpty(member.getBadgeNumber()),
                            member.getRank() != null ? member.getRank().getLabel() : "",
                            orEmpty(member.getPosition()),
                            member.getUnitRole() != null ? member.getUnitRole().getLabel() : "",
                            member.getId().equals(dutyByAccount.get(unit.getId())) ? "Có" : "",
                            orEmpty(member.getPhone()),
                            !member.isActivated() ? "" : (pin != null ? pin : UNKNOWN_PIN),
                            member.isLocked() ? "Khoá (sai PIN)" : (member.isActivated() ? "Hoạt động" : "Chưa kích hoạt"),
                            formatDate(member.getCreatedAt()));
                    memberRow++;
                }
                finish(memberSheet, memberRow, MEMBER_HEADERS.length);
            }

            workbook.write(out);
            return out.toByteArray();
        } finally {
            workbook.dispose(); // xoá file tạm của SXSSF
            workbook.close();
        }
    }

    /** Tên cấp theo lực lượng: CSGT, PCCC gọi là cấp Đội / cấp Phòng, còn lại theo UnitLevel. */
    static String levelLabel(AgencyType agency, UnitLevel level) {
        if (level == null) return "";
        return agency != null ? agency.levelLabel(level) : level.getLabel();
    }

    private static Sheet createSheet(Workbook workbook, String name, String[] headers, int[] widths, CellStyle style) {
        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        header.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        return sheet;
    }

    private static void writeRow(Row row, CellStyle style, String... values) {
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
            cell.setCellStyle(style);
        }
    }

    private static void finish(Sheet sheet, int rowCount, int columns) {
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(rowCount - 1, 0), 0, columns - 1));
    }

    private static String names(List<UnitMember> members) {
        return members.stream().map(UnitMember::displayName).collect(Collectors.joining(", "));
    }

    private static String formatDate(java.time.LocalDateTime time) {
        return time != null ? time.format(DATE_FORMAT) : "";
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
