package com.omnishelf.engine.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.*;
import com.omnishelf.engine.config.ShopConfig;
import com.omnishelf.engine.exception.PdfGenerationException;
import com.omnishelf.engine.model.Bill;
import com.omnishelf.engine.model.BillItem;
import com.omnishelf.engine.model.ProductVariant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
@RequiredArgsConstructor
public class PdfService {

    @Value("${pdf.host-base-url}")
    private String pdfHostBaseUrl;

    private final ShopConfig shopConfig;

    private static final DeviceRgb BRAND_BLUE  = new DeviceRgb(30, 90, 160);
    private static final DeviceRgb LIGHT_GRAY  = new DeviceRgb(245, 245, 245);
    private static final DeviceRgb TABLE_HEADER = new DeviceRgb(220, 235, 255);
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    public byte[] generateGstInvoice(Bill bill) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter   writer   = new PdfWriter(baos);
            PdfDocument pdfDoc   = new PdfDocument(writer);
            Document    document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(36, 36, 36, 36);

            PdfFont bold    = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

            // ── Header ─────────────────────────────────────────────────────────
            Table header = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                .useAllAvailableWidth();

            Cell shopCell = new Cell()
                .add(new Paragraph(shopConfig.getName()).setFont(bold).setFontSize(18).setFontColor(BRAND_BLUE))
                .add(new Paragraph(shopConfig.getAddress()).setFont(regular).setFontSize(9))
                .add(new Paragraph("Ph: " + shopConfig.getPhone()).setFont(regular).setFontSize(9))
                .add(new Paragraph("GSTIN: " + shopConfig.getGstin()).setFont(bold).setFontSize(9))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
            header.addCell(shopCell);

            String dateStr = bill.getConfirmedAt() != null
                ? bill.getConfirmedAt().format(DATE_FMT)
                : java.time.LocalDateTime.now().format(DATE_FMT);

            Cell invoiceCell = new Cell()
                .add(new Paragraph("TAX INVOICE").setFont(bold).setFontSize(14).setFontColor(BRAND_BLUE)
                    .setTextAlignment(TextAlignment.RIGHT))
                .add(new Paragraph("Invoice No: " + bill.getBillNumber())
                    .setFont(bold).setFontSize(10).setTextAlignment(TextAlignment.RIGHT))
                .add(new Paragraph("Date: " + dateStr)
                    .setFont(regular).setFontSize(9).setTextAlignment(TextAlignment.RIGHT))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
            header.addCell(invoiceCell);
            document.add(header);

            // ── Customer ────────────────────────────────────────────────────────
            if (bill.getCustomerName() != null) {
                document.add(new Paragraph(" "));
                Paragraph billedTo = new Paragraph("Billed To: " + bill.getCustomerName())
                    .setFont(bold).setFontSize(10);
                document.add(billedTo);
                if (bill.getCustomer() != null && bill.getCustomer().getGstin() != null) {
                    document.add(new Paragraph("GSTIN: " + bill.getCustomer().getGstin())
                        .setFont(regular).setFontSize(9));
                }
            }

            document.add(new Paragraph(" "));

            // ── Line Items Table ─────────────────────────────────────────────────
            float[] cols = {5, 30, 12, 10, 12, 12, 12, 10};
            Table itemsTable = new Table(UnitValue.createPercentArray(cols)).useAllAvailableWidth();

            String[] tableHeaders = {"#", "Description", "HSN", "Qty", "Unit Price", "Taxable", "GST", "Total"};
            for (String h : tableHeaders) {
                itemsTable.addHeaderCell(new Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(9))
                    .setBackgroundColor(TABLE_HEADER)
                    .setTextAlignment(TextAlignment.CENTER));
            }

            int rowNum = 1;
            for (BillItem item : bill.getItems()) {
                ProductVariant v = item.getVariant();
                String desc = v.getProduct().getBrand() + " " + v.getProduct().getName()
                    + buildVariantNote(v);

                boolean shade = rowNum % 2 == 0;
                addRow(itemsTable, bold, regular, shade,
                    String.valueOf(rowNum++),
                    desc,
                    v.getHsnCode() != null ? v.getHsnCode() : "-",
                    String.valueOf(item.getQuantity()),
                    "₹" + fmt(item.getUnitPrice()),
                    "₹" + fmt(item.getLineTotal()),
                    "₹" + fmt(item.getGstAmount())
                        + " (" + v.getGstRatePercent().intValue() + "%)",
                    "₹" + fmt(item.getLineTotal().add(item.getGstAmount()))
                );
            }

            document.add(itemsTable);
            document.add(new Paragraph(" "));

            // ── GST Summary Box ─────────────────────────────────────────────────
            Table totals = new Table(UnitValue.createPercentArray(new float[]{70, 30}))
                .useAllAvailableWidth();

            // Left: GST breakdown
            Table gstBreak = new Table(UnitValue.createPercentArray(new float[]{50, 25, 25}))
                .useAllAvailableWidth();
            addGstHeader(gstBreak, bold, "GST Component", "Rate", "Amount");
            addGstRow(gstBreak, regular, "CGST", "9%", "₹" + fmt(bill.getCgst()));
            addGstRow(gstBreak, regular, "SGST", "9%", "₹" + fmt(bill.getSgst()));

            Cell gstCell = new Cell().add(gstBreak)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
            totals.addCell(gstCell);

            // Right: totals
            Table totalsSub = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                .useAllAvailableWidth();
            addTotalRow(totalsSub, regular, "Subtotal", "₹" + fmt(bill.getSubtotal()), false);
            if (bill.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                addTotalRow(totalsSub, regular, "Discount", "-₹" + fmt(bill.getDiscountAmount()), false);
                addTotalRow(totalsSub, regular, "Taxable Amount", "₹" + fmt(bill.getTaxableAmount()), false);
            }
            addTotalRow(totalsSub, regular, "CGST", "₹" + fmt(bill.getCgst()), false);
            addTotalRow(totalsSub, regular, "SGST", "₹" + fmt(bill.getSgst()), false);
            addTotalRow(totalsSub, bold, "GRAND TOTAL", "₹" + fmt(bill.getGrandTotal()), true);

            totals.addCell(new Cell().add(totalsSub)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
            document.add(totals);

            // ── Footer ─────────────────────────────────────────────────────────
            document.add(new Paragraph(" "));
            document.add(new Paragraph("This is a computer-generated invoice. No signature required.")
                .setFont(regular).setFontSize(8)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Powered by OmniShelf AI Billing • " + shopConfig.getName())
                .setFont(regular).setFontSize(7)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER));

            document.close();
            log.info("PDF generated for bill {}: {} bytes", bill.getBillNumber(), baos.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("PDF generation failed for bill {}: {}", bill.getBillNumber(), e.getMessage(), e);
            throw new PdfGenerationException("PDF generation failed for " + bill.getBillNumber(), e);
        }
    }

    // ── Table helpers ──────────────────────────────────────────────────────────

    private void addRow(Table t, PdfFont bold, PdfFont regular,
                        boolean shade, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            Cell c = new Cell()
                .add(new Paragraph(cells[i]).setFont(i == 1 ? regular : regular).setFontSize(9))
                .setTextAlignment(i == 0 || i >= 3 ? TextAlignment.CENTER : TextAlignment.LEFT);
            if (shade) c.setBackgroundColor(LIGHT_GRAY);
            t.addCell(c);
        }
    }

    private void addGstHeader(Table t, PdfFont bold, String... vals) {
        for (String v : vals) t.addHeaderCell(new Cell()
            .add(new Paragraph(v).setFont(bold).setFontSize(8))
            .setBackgroundColor(TABLE_HEADER));
    }

    private void addGstRow(Table t, PdfFont regular, String... vals) {
        for (String v : vals) t.addCell(new Cell()
            .add(new Paragraph(v).setFont(regular).setFontSize(9)));
    }

    private void addTotalRow(Table t, PdfFont font, String label,
                              String value, boolean highlight) {
        Cell lc = new Cell().add(new Paragraph(label).setFont(font).setFontSize(9))
            .setTextAlignment(TextAlignment.RIGHT)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
        Cell vc = new Cell().add(new Paragraph(value).setFont(font).setFontSize(9))
            .setTextAlignment(TextAlignment.RIGHT)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
        if (highlight) {
            lc.setBackgroundColor(TABLE_HEADER).setFontSize(11);
            vc.setBackgroundColor(TABLE_HEADER).setFontSize(11);
        }
        t.addCell(lc); t.addCell(vc);
    }

    private String buildVariantNote(ProductVariant v) {
        StringBuilder sb = new StringBuilder();
        if (v.getColor()   != null) sb.append(" ").append(v.getColor());
        if (v.getSize()    != null) sb.append(" Sz").append(v.getSize());
        if (v.getStorage() != null) sb.append(" ").append(v.getStorage());
        return sb.toString();
    }

    private String fmt(BigDecimal val) {
        return val == null ? "0.00" : String.format("%.2f", val);
    }
}
