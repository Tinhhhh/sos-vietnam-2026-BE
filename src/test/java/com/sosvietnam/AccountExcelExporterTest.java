package com.sosvietnam;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.util.AccountExcelExporter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccountExcelExporterTest {

    private final AccountExcelExporter exporter = new AccountExcelExporter();

    private static List<String> cells(Row row) {
        List<String> values = new ArrayList<>();
        row.forEach(c -> values.add(c.getStringCellValue()));
        return values;
    }

    @Test
    void export_writesHeaderAndOneRowPerAccount_andOnlyKnownPasswords() throws Exception {
        Role officer = Role.builder().roleName("OFFICER").build();
        Account doiPccc = Account.builder()
                .email("pccc.tanan@sos.vn").password("$2a$10$hash-must-not-leak")
                .lastName("Trực Ban").firstName("Đội PCCC & CNCH Khu Vực Tân An")
                .agencyType(AgencyType.FIRE).unitLevel(UnitLevel.WARD)
                .province(TestGeo.province("Cần Thơ")).ward(TestGeo.ward("Phường Tân An")).role(officer).build();
        Account caXa = Account.builder()
                .email("congan.tanan@sos.vn").password("$2a$10$other")
                .lastName("Trực Ban").firstName("Công An Phường Tân An")
                .agencyType(AgencyType.POLICE).unitLevel(UnitLevel.WARD)
                .province(TestGeo.province("Cần Thơ")).ward(TestGeo.ward("Phường Tân An")).role(officer).build();

        // Only the seed password of doiPccc is known; caXa changed theirs
        byte[] xlsx = exporter.export(List.of(doiPccc, caXa), a -> a == doiPccc ? "Seed@123" : null);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            List<String> header = cells(sheet.getRow(0));
            assertThat(header).contains("Email Đăng Nhập", "Cấp Đơn Vị", "Mật Khẩu");

            List<String> first = cells(sheet.getRow(1));
            assertThat(first).contains("1", "Cần Thơ", "Cảnh Sát PCCC & CNCH", "Cấp Đội", "pccc.tanan@sos.vn", "Seed@123", "OFFICER");
            assertThat(cells(sheet.getRow(2))).contains("Công An", "Cấp Xã/Phường", AccountExcelExporter.UNKNOWN_PASSWORD);

            sheet.forEach(row -> row.forEach(c -> assertThat(c.getStringCellValue()).doesNotContain("$2a$")));
        }
    }

    @Test
    void agencyLevels_matchTheUnitStructure() {
        assertThat(AgencyType.POLICE.supports(UnitLevel.WARD)).isTrue();
        assertThat(AgencyType.CSGT.supports(UnitLevel.WARD)).isTrue();
        assertThat(AgencyType.CSGT.levelLabel(UnitLevel.WARD)).isEqualTo("Cấp Đội");
        assertThat(AgencyType.POLICE.getIntakeLevel()).isEqualTo(UnitLevel.WARD);
        assertThat(AgencyType.CSGT.getIntakeLevel()).isEqualTo(UnitLevel.PROVINCE);
        assertThat(AgencyType.FIRE.isEscalates()).isFalse();
        assertThat(AgencyType.HOSPITAL.receivesIncidents()).isFalse();
        assertThat(AgencyType.FIRE.supports(UnitLevel.WARD)).isTrue();
        assertThat(AgencyType.HOSPITAL.getLevels()).containsExactly(UnitLevel.PROVINCE);
    }
}
