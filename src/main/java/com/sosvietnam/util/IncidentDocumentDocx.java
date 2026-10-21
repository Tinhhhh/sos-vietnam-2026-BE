package com.sosvietnam.util;

import com.sosvietnam.model.entity.IncidentDocument;
import com.sosvietnam.model.payload.enums.CitizenSignChannel;
import com.sosvietnam.model.payload.enums.SignatureType;
import lombok.experimental.UtilityClass;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Function;

/**
 * File Word "Phiếu thông báo và tiếp nhận thông tin ứng phó sự cố khẩn cấp", thể thức Nghị định 30/2020/NĐ-CP
 * (port từ scripts/generate_report_docx.py của bản gốc): A4, lề 20/20/25/20 mm, Times New Roman 13.
 */
@UtilityClass
public class IncidentDocumentDocx {

    private static final String FONT = "Times New Roman";
    private static final DateTimeFormatter SIGNED_AT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final int CM = 567; // twips / cm

    public byte[] build(IncidentDocument d, String gmapsUrl, Function<UUID, String> nameOf) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            page(doc);
            header(doc, d);

            XWPFParagraph title = para(doc, ParagraphAlignment.CENTER, 0, 4);
            run(title, "PHIẾU THÔNG BÁO VÀ TIẾP NHẬN THÔNG TIN", 14.5, true, false);
            title.createRun().addBreak();
            run(title, "ỨNG PHÓ SỰ CỐ KHẨN CẤP", 14.5, true, false);
            XWPFParagraph code = para(doc, ParagraphAlignment.CENTER, 0, 12);
            run(code, "Mã tiếp nhận: " + d.getIncidentId() + " | Thời gian: " + nz(d.getIncidentTime()), 12, false, true);

            section(doc, "A. THÔNG TIN CHUNG (ĐƠN VỊ TIẾP NHẬN)");
            line(doc, "- Họ tên cán bộ tiếp nhận: ", nz(d.getOfficerName())
                    + (blank(d.getOfficerTitle()) ? "" : " (" + d.getOfficerTitle() + ")"));
            line(doc, "- Đơn vị tiếp nhận: ", nz(d.getUnitName()));
            line(doc, "- Địa chỉ cơ quan / Trụ sở công tác: ", nz(d.getUnitAddress()));
            line(doc, "- Số điện thoại liên hệ của đơn vị: ", nz(d.getUnitPhone()));

            section(doc, "B. THÔNG TIN ĐẾN (NGƯỜI BÁO & HIỆN TRƯỜNG SỰ CỐ)");
            line(doc, "- Tên người gọi / Người báo tin: ", nz(d.getReporterName()));
            String target = nz(d.getTargetType());
            line(doc, "- Thuộc đối tượng: ", box("Người dân".equals(target)) + " Người dân    "
                    + box("Nhân viên cơ sở".equals(target)) + " Nhân viên cơ sở    "
                    + box("Lực lượng ứng phó".equals(target)) + " Lực lượng ứng phó");
            line(doc, "- Số điện thoại người gọi: ", nz(d.getReporterPhone()));
            line(doc, "- Giờ gọi / Báo tin: ", nz(d.getIncidentTime()));
            line(doc, "- Vị trí xảy ra sự cố (Địa chỉ thực tế): ", nz(d.getIncidentAddress()));
            line(doc, "- Tọa độ định vị GPS: ", nz(d.getCoordinates()));
            if (gmapsUrl != null) line(doc, "- Đường dẫn vị trí Google Maps: ", gmapsUrl);
            line(doc, "- Loại sự cố & Mô tả chi tiết: ", nz(d.getDescription()));
            line(doc, "- Có ảnh hưởng tới người dân: ", yesNo(d.getAffectsCitizens()));
            line(doc, "- Tình huống có yêu cầu trợ giúp: ", yesNo(d.getNeedsHelp()));
            line(doc, "- Yêu cầu trợ giúp cụ thể / Lực lượng điều động: ", nz(d.getHelpRequest()));
            line(doc, "- Lời khuyên / Khuyến cáo đã hướng dẫn người dân: ", nz(d.getAdviceGiven()));
            line(doc, "- Kết quả xử lý: ", nz(d.getResolutionResult()));

            signatures(doc, d, nameOf);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được file Word", e);
        }
    }

    // ---------------------------------------------------------------- bố cục

    private static void page(XWPFDocument doc) {
        CTSectPr sect = doc.getDocument().getBody().addNewSectPr();
        CTPageSz size = sect.addNewPgSz();
        size.setW(BigInteger.valueOf(11906));
        size.setH(BigInteger.valueOf(16838));
        CTPageMar mar = sect.addNewPgMar();
        mar.setTop(BigInteger.valueOf(2 * CM));
        mar.setBottom(BigInteger.valueOf(2 * CM));
        mar.setLeft(BigInteger.valueOf((int) (2.5 * CM)));
        mar.setRight(BigInteger.valueOf(2 * CM));
    }

    /** Bảng 2 cột không viền: trái = cơ quan + số văn bản, phải = quốc hiệu + địa danh, ngày tháng */
    private static void header(XWPFDocument doc, IncidentDocument d) {
        XWPFTable table = doc.createTable(1, 2);
        noBorders(table);
        XWPFTableCell left = table.getRow(0).getCell(0);
        XWPFTableCell right = table.getRow(0).getCell(1);
        width(left, (int) (7.2 * CM));
        width(right, (int) (9.3 * CM));

        XWPFParagraph up = cellPara(left, true);
        run(up, nz(d.getHeaderUpper()), nz(d.getHeaderUpper()).length() > 28 ? 11.5 : 12, false, false);
        XWPFParagraph low = cellPara(left, false);
        run(low, nz(d.getHeaderLower()), nz(d.getHeaderLower()).length() > 24 ? 11.5 : 12.5, true, false);
        rule(left, (int) (7.2 * CM), 4 * CM);
        XWPFParagraph num = cellPara(left, false);
        run(num, "Số: " + d.getDocNumber(), 12, false, false);

        XWPFParagraph nation = cellPara(right, true);
        run(nation, "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", 12.5, true, false);
        XWPFParagraph motto = cellPara(right, false);
        run(motto, "Độc lập - Tự do - Hạnh phúc", 13.5, true, false);
        rule(right, (int) (9.3 * CM), (int) (4.8 * CM));
        XWPFParagraph date = cellPara(right, false);
        date.setSpacingBefore(80);
        run(date, nz(d.getPlaceDate()), 13.5, false, true);

        para(doc, ParagraphAlignment.LEFT, 0, 6);
    }

    private static void signatures(XWPFDocument doc, IncidentDocument d, Function<UUID, String> nameOf) {
        para(doc, ParagraphAlignment.LEFT, 6, 0);
        XWPFTable table = doc.createTable(1, 2);
        noBorders(table);
        XWPFTableCell left = table.getRow(0).getCell(0);
        XWPFTableCell right = table.getRow(0).getCell(1);
        width(left, (int) (8.25 * CM));
        width(right, (int) (8.25 * CM));

        signatureBox(doc, left, "NGƯỜI BÁO TIN / NGƯỜI DÂN", "(Ký, ghi rõ họ tên)",
                d.getCitizenSignType(), d.getCitizenSignature(), d.getCitizenSignName(), d.getReporterName(),
                d.getCitizenSignedAt(),
                d.getCitizenSignChannel() == CitizenSignChannel.OFFICER_PROXY
                        ? "Cán bộ " + nz(nameOf.apply(d.getCitizenProxyBy())) + " ký hộ, lý do: " + nz(d.getCitizenProxyReason())
                        : null);
        signatureBox(doc, right, "CÁN BỘ / CHỈ HUY TIẾP NHẬN", "(Ký, đóng dấu và ghi rõ họ tên)",
                d.getOfficerSignType(), d.getOfficerSignature(), d.getOfficerSignName(), d.getOfficerName(),
                d.getOfficerSignedAt(), null);
    }

    private static void signatureBox(XWPFDocument doc, XWPFTableCell cell, String role, String hint,
                                     SignatureType type, String image, String signName, String fallbackName,
                                     LocalDateTime signedAt, String note) {
        XWPFParagraph p = cellPara(cell, true);
        run(p, role, 13, true, false);
        XWPFParagraph h = cellPara(cell, false);
        run(h, hint, 11, false, true);

        XWPFParagraph sig = cellPara(cell, false);
        sig.setSpacingBefore(120);
        sig.setSpacingAfter(120);
        if (signedAt != null && type == SignatureType.DRAW && image != null && picture(sig, image)) {
            // ảnh chữ ký đã chèn
        } else if (signedAt != null) {
            XWPFRun r = run(sig, "(Chữ ký điện tử: " + nz(signName) + ")", 12, true, true);
            r.setColor("1E3A8A");
        } else {
            sig.createRun().addBreak();
            sig.createRun().addBreak();
        }

        XWPFParagraph name = cellPara(cell, false);
        run(name, nz(signName != null ? signName : fallbackName), 13, true, false);
        if (signedAt != null) {
            XWPFParagraph at = cellPara(cell, false);
            run(at, "Thời gian ký: " + signedAt.format(SIGNED_AT), 10, false, true);
        }
        if (note != null) {
            XWPFParagraph n = cellPara(cell, false);
            run(n, "(" + note + ")", 10, false, true);
        }
    }

    /** Chèn ảnh PNG từ data URL, rộng tối đa 4.5 cm, giữ tỉ lệ */
    private static boolean picture(XWPFParagraph p, String dataUrl) {
        try {
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1));
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) return false;
            double widthCm = 4.5;
            double heightCm = widthCm * img.getHeight() / Math.max(1, img.getWidth());
            if (heightCm > 2.2) {
                widthCm = widthCm * 2.2 / heightCm;
                heightCm = 2.2;
            }
            p.createRun().addPicture(new ByteArrayInputStream(bytes), Document.PICTURE_TYPE_PNG, "signature.png",
                    Units.toEMU(widthCm * 28.3465), Units.toEMU(heightCm * 28.3465));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- tiện ích

    private static void section(XWPFDocument doc, String text) {
        XWPFParagraph p = para(doc, ParagraphAlignment.LEFT, 8, 4);
        run(p, text, 13.5, true, false);
    }

    private static void line(XWPFDocument doc, String label, String value) {
        XWPFParagraph p = para(doc, ParagraphAlignment.BOTH, 0, 3);
        p.setIndentationLeft((int) (0.5 * CM));
        run(p, label, 13, true, false);
        run(p, value, 13, false, false);
    }

    private static XWPFParagraph para(XWPFDocument doc, ParagraphAlignment align, int beforePt, int afterPt) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(align);
        p.setSpacingBefore(beforePt * 20);
        p.setSpacingAfter(afterPt * 20);
        return p;
    }

    private static XWPFParagraph cellPara(XWPFTableCell cell, boolean first) {
        XWPFParagraph p = first ? cell.getParagraphs().get(0) : cell.addParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(40);
        return p;
    }

    private static XWPFRun run(XWPFParagraph p, String text, double size, boolean bold, boolean italic) {
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(size);
        r.setBold(bold);
        r.setItalic(italic);
        r.setText(text == null ? "" : text);
        return r;
    }

    /** Đường kẻ ngang ở giữa ô: viền dưới của 1 đoạn trống, thụt 2 bên cho đủ độ dài */
    private static void rule(XWPFTableCell cell, int cellWidth, int lineWidth) {
        XWPFParagraph p = cell.addParagraph();
        int indent = Math.max(0, (cellWidth - lineWidth) / 2);
        p.setIndentationLeft(indent);
        p.setIndentationRight(indent);
        p.setSpacingAfter(60);
        p.setBorderBottom(Borders.SINGLE);
        XWPFRun r = p.createRun();
        r.setFontSize(2);
    }

    private static void noBorders(XWPFTable table) {
        table.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        table.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        table.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        table.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        table.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        table.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
    }

    private static void width(XWPFTableCell cell, int twips) {
        CTTcPr pr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTblWidth w = pr.isSetTcW() ? pr.getTcW() : pr.addNewTcW();
        w.setType(STTblWidth.DXA);
        w.setW(BigInteger.valueOf(twips));
    }

    private static String box(boolean on) {
        return on ? "[X]" : "[ ]";
    }

    private static String yesNo(Boolean value) {
        return box(Boolean.TRUE.equals(value)) + " Có      " + box(Boolean.FALSE.equals(value)) + " Không";
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
