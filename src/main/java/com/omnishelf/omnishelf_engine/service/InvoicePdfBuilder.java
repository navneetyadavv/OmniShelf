package com.omnishelf.omnishelf_engine.service;

import com.omnishelf.omnishelf_engine.config.ShopConfig;
import com.omnishelf.omnishelf_engine.model.Bill;
import com.omnishelf.omnishelf_engine.model.BillItem;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.omnishelf.omnishelf_engine.exception.PdfGenerationException;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class InvoicePdfBuilder {

    // Brand color — deep indigo, change to your shop's color
    private static final Color BRAND_COLOR    = new DeviceRgb(41, 57, 132);
    private static final Color HEADER_BG      = new DeviceRgb(245, 246, 250);
    private static final Color LIGHT_GRAY     = new DeviceRgb(220, 220, 220);
    private static final Color TOTAL_ROW_BG   = new DeviceRgb(41, 57, 132);

    private final ShopConfig shopConfig;

    public InvoicePdfBuilder(ShopConfig shopConfig) {
        this.shopConfig = shopConfig;
    }

    /**
     * Generates a complete GST invoice PDF entirely in memory.
     * Returns raw bytes — no disk writes at any point.
     */
    public byte[] buildInvoice(Bill bill) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            PdfWriter writer    = new PdfWriter(buffer);
            PdfDocument pdfDoc  = new PdfDocument(writer);
            Document document   = new Document(pdfDoc, PageSize.A4);
            document.setMargins(36, 36, 36, 36);

            PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // ── 1. Header — shop name + bill details side by side ──────
            addHeader(document, bill, bold, regular);

            // ── 2. Divider ─────────────────────────────────────────────
            addDivider(document);

            // ── 3. Customer block ──────────────────────────────────────
            addCustomerBlock(document, bill, bold, regular);

            // ── 4. Item table ──────────────────────────────────────────
            addItemTable(document, bill, bold, regular);

            // ── 5. Totals block ────────────────────────────────────────
            addTotalsBlock(document, bill, bold, regular);

            // ── 6. Footer ─────────────────────────────────────────────
            addFooter(document, bold, regular);

            document.close();
            log.info("PDF generated for bill {} — {} bytes",
                bill.getBillNumber(), buffer.size());
            return buffer.toByteArray();

        } catch (Exception e) {
            log.error("PDF generation failed for bill {}: {}",
                bill.getBillNumber(), e.getMessage(), e);
            throw new PdfGenerationException("Failed to generate invoice PDF", e);
        }
    }

    // ── Section builders ──────────────────────────────────────────────

    private void addHeader(Document doc, Bill bill,
                            PdfFont bold, PdfFont regular) {
        // Two-column table: shop info left, bill meta right
        Table header = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
            .setWidth(UnitValue.createPercentValue(100))
            .setBorder(Border.NO_BORDER);

        // Left cell — shop branding
        Cell leftCell = new Cell().setBorder(Border.NO_BORDER)
            .setBackgroundColor(HEADER_BG)
            .setPadding(12);

        leftCell.add(new Paragraph(shopConfig.getName())
            .setFont(bold).setFontSize(18)
            .setFontColor(BRAND_COLOR));
        leftCell.add(new Paragraph(shopConfig.getAddress())
            .setFont(regular).setFontSize(9)
            .setFontColor(ColorConstants.DARK_GRAY).setMarginTop(2));
        leftCell.add(new Paragraph("Ph: " + shopConfig.getPhone())
            .setFont(regular).setFontSize(9)
            .setFontColor(ColorConstants.DARK_GRAY));
        leftCell.add(new Paragraph("GSTIN: " + shopConfig.getGstin())
            .setFont(bold).setFontSize(9)
            .setFontColor(ColorConstants.DARK_GRAY));

        // Right cell — bill number + date
        Cell rightCell = new Cell().setBorder(Border.NO_BORDER)
            .setBackgroundColor(HEADER_BG)
            .setPadding(12)
            .setTextAlignment(TextAlignment.RIGHT);

        rightCell.add(new Paragraph("TAX INVOICE")
            .setFont(bold).setFontSize(14)
            .setFontColor(BRAND_COLOR));
        rightCell.add(new Paragraph("Bill No: " + bill.getBillNumber())
            .setFont(bold).setFontSize(10)
            .setFontColor(ColorConstants.DARK_GRAY).setMarginTop(4));
        rightCell.add(new Paragraph("Date: " + bill.getConfirmedAt()
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")))
            .setFont(regular).setFontSize(9)
            .setFontColor(ColorConstants.DARK_GRAY));

        header.addCell(leftCell);
        header.addCell(rightCell);
        doc.add(header);
    }

    private void addDivider(Document doc) {
        doc.add(new LineSeparator(
            new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(1.5f))
            .setMarginTop(4).setMarginBottom(8));
    }

    private void addCustomerBlock(Document doc, Bill bill,
                                   PdfFont bold, PdfFont regular) {
        if (bill.getCustomerName() == null
                || bill.getCustomerName().isBlank()) return;

        Table custTable = new Table(UnitValue.createPercentArray(new float[]{100}))
            .setWidth(UnitValue.createPercentValue(100))
            .setBorder(Border.NO_BORDER)
            .setMarginBottom(10);

        Cell cell = new Cell()
            .setBorder(new SolidBorder(LIGHT_GRAY, 0.5f))
            .setBackgroundColor(HEADER_BG)
            .setPadding(8);
        cell.add(new Paragraph("Bill To:")
            .setFont(bold).setFontSize(9)
            .setFontColor(ColorConstants.DARK_GRAY));
        cell.add(new Paragraph(bill.getCustomerName())
            .setFont(bold).setFontSize(11)
            .setFontColor(ColorConstants.BLACK));
        custTable.addCell(cell);
        doc.add(custTable);
    }

    private void addItemTable(Document doc, Bill bill,
                               PdfFont bold, PdfFont regular) {
        // 5 columns: #, Description, Qty, Unit price, Amount
        Table table = new Table(UnitValue.createPercentArray(
                new float[]{5, 45, 10, 20, 20}))
            .setWidth(UnitValue.createPercentValue(100))
            .setMarginBottom(0);

        // Column headers
        String[] headers = {"#", "Description", "Qty", "Unit Price", "Amount"};
        for (String h : headers) {
            table.addHeaderCell(new Cell()
                .setBackgroundColor(BRAND_COLOR)
                .setBorder(Border.NO_BORDER)
                .setPadding(7)
                .add(new Paragraph(h)
                    .setFont(bold).setFontSize(9)
                    .setFontColor(ColorConstants.WHITE)
                    .setTextAlignment(h.equals("#") || h.equals("Qty")
                        ? TextAlignment.CENTER : TextAlignment.LEFT)));
        }

        // Item rows — alternating row shading
        int rowNum = 1;
        Color altRow = new DeviceRgb(248, 249, 252);

        for (BillItem item : bill.getItems()) {
            Color rowBg = (rowNum % 2 == 0) ? altRow
                : ColorConstants.WHITE;

            String sku  = item.getVariant().getSku();
            String desc = formatDescription(item);

            addItemCell(table, String.valueOf(rowNum),
                TextAlignment.CENTER, rowBg, regular, 9);
            addItemCell(table, desc,
                TextAlignment.LEFT, rowBg, regular, 9);
            addItemCell(table, String.valueOf(item.getQuantity()),
                TextAlignment.CENTER, rowBg, regular, 9);
            addItemCell(table, "₹" + formatMoney(item.getUnitPrice()),
                TextAlignment.LEFT, rowBg, regular, 9);
            addItemCell(table, "₹" + formatMoney(item.getLineTotal()),
                TextAlignment.LEFT, rowBg, bold, 9);
            rowNum++;
        }

        doc.add(table);
    }

    private void addTotalsBlock(Document doc, Bill bill,
                                 PdfFont bold, PdfFont regular) {
        // Right-aligned totals table
        Table totals = new Table(UnitValue.createPercentArray(new float[]{60, 20, 20}))
            .setWidth(UnitValue.createPercentValue(100))
            .setBorder(Border.NO_BORDER)
            .setMarginTop(0);

        // Empty left cell spans subtotal + tax rows
        totals.addCell(new Cell(2, 1).setBorder(Border.NO_BORDER));

        // Subtotal row
        totals.addCell(labelCell("Subtotal", regular, ColorConstants.BLACK));
        totals.addCell(valueCell("₹" + formatMoney(bill.getTotalAmount()),
            regular, ColorConstants.BLACK));

        // GST row
        totals.addCell(labelCell("GST (18%)", regular, ColorConstants.DARK_GRAY));
        totals.addCell(valueCell("₹" + formatMoney(bill.getTaxAmount()),
            regular, ColorConstants.DARK_GRAY));

        // Grand total — full-width colored row
        Cell grandLabel = new Cell()
            .setBorder(Border.NO_BORDER)
            .setBackgroundColor(BRAND_COLOR).setPadding(8)
            .add(new Paragraph("GRAND TOTAL")
                .setFont(bold).setFontSize(11)
                .setFontColor(ColorConstants.WHITE));
        Cell grandValue = new Cell()
            .setBorder(Border.NO_BORDER)
            .setBackgroundColor(BRAND_COLOR).setPadding(8)
            .add(new Paragraph("₹" + formatMoney(bill.getGrandTotal()))
                .setFont(bold).setFontSize(11)
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.RIGHT));

        totals.addCell(new Cell().setBorder(Border.NO_BORDER)
            .setBackgroundColor(BRAND_COLOR));
        totals.addCell(grandLabel);
        totals.addCell(grandValue);

        doc.add(totals);
    }

    private void addFooter(Document doc, PdfFont bold, PdfFont regular) {
        doc.add(new LineSeparator(
            new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(0.5f))
            .setMarginTop(16).setMarginBottom(8));

        doc.add(new Paragraph(
            "This is a computer-generated invoice. No signature required.")
            .setFont(regular).setFontSize(8)
            .setFontColor(ColorConstants.GRAY)
            .setTextAlignment(TextAlignment.CENTER));

        doc.add(new Paragraph("Thank you for your business!")
            .setFont(bold).setFontSize(9)
            .setFontColor(BRAND_COLOR)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginTop(2));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String formatDescription(BillItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getVariant().getProduct().getBrand())
          .append(" ")
          .append(item.getVariant().getProduct().getName());

        if (item.getVariant().getColor() != null)
            sb.append(" — ").append(item.getVariant().getColor());
        if (item.getVariant().getSize() != null)
            sb.append(", Size ").append(item.getVariant().getSize());

        return sb.toString();
    }

    private String formatMoney(BigDecimal amount) {
        return String.format("%,.2f", amount);
    }

    private void addItemCell(Table table, String text,
                              TextAlignment align, Color bg,
                              PdfFont font, float size) {
        table.addCell(new Cell()
            .setBackgroundColor(bg)
            .setBorderLeft(Border.NO_BORDER)
            .setBorderRight(Border.NO_BORDER)
            .setBorderTop(Border.NO_BORDER)
            .setBorderBottom(new SolidBorder(LIGHT_GRAY, 0.3f))
            .setPadding(6)
            .add(new Paragraph(text)
                .setFont(font).setFontSize(size)
                .setTextAlignment(align)));
    }

    private Cell labelCell(String text, PdfFont font, com.itextpdf.kernel.colors.Color color) {
        return new Cell()
            .setBorder(Border.NO_BORDER)
            .setBorderBottom(new SolidBorder(LIGHT_GRAY, 0.3f))
            .setPaddingTop(5).setPaddingBottom(5).setPaddingLeft(8)
            .add(new Paragraph(text)
                .setFont(font).setFontSize(9).setFontColor(color));
    }

    private Cell valueCell(String text, PdfFont font, com.itextpdf.kernel.colors.Color color) {
        return new Cell()
            .setBorder(Border.NO_BORDER)
            .setBorderBottom(new SolidBorder(LIGHT_GRAY, 0.3f))
            .setPaddingTop(5).setPaddingBottom(5).setPaddingRight(8)
            .add(new Paragraph(text)
                .setFont(font).setFontSize(9).setFontColor(color)
                .setTextAlignment(TextAlignment.RIGHT));
    }

    public byte[] buildCancellationNote(Bill bill) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            PdfWriter writer    = new PdfWriter(buffer);
            PdfDocument pdfDoc  = new PdfDocument(writer);
            Document document   = new Document(pdfDoc, PageSize.A4);
            document.setMargins(36, 36, 36, 36);

            PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // Reuse all existing section builders
            addHeader(document, bill, bold, regular);
            addDivider(document);
            addCustomerBlock(document, bill, bold, regular);
            addItemTable(document, bill, bold, regular);
            addTotalsBlock(document, bill, bold, regular);

            // Cancellation stamp
            document.add(new Paragraph("\n"));
            Paragraph stamp = new Paragraph("CANCELLED")
                .setFont(bold).setFontSize(48)
                .setFontColor(new DeviceRgb(163, 45, 45))
                .setTextAlignment(TextAlignment.CENTER)
                .setOpacity(0.25f);
            document.add(stamp);

            // Cancellation details
            document.add(new Paragraph(
                "Cancelled on: " + bill.getCancelledAt()
                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")))
                .setFont(regular).setFontSize(9)
                .setFontColor(ColorConstants.DARK_GRAY)
                .setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph(
                "All stock has been restored to inventory.")
                .setFont(regular).setFontSize(9)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER));

            addFooter(document, bold, regular);
            document.close();
            return buffer.toByteArray();

        } catch (Exception e) {
            throw new PdfGenerationException(
                "Failed to generate cancellation note", e);
        }
    }
}