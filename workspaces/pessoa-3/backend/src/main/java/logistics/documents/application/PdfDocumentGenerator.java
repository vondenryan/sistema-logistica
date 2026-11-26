package logistics.documents.application;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import contracts.v1.AddressSnapshot;
import contracts.v1.ContractException;
import contracts.v1.DocumentType;
import contracts.v1.OrderSnapshot;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PdfDocumentGenerator {
  public static final String ACADEMIC_WATERMARK =
      "SIMULAÇÃO ACADÊMICA — SEM VALIDADE FISCAL";

  public byte[] generate(
      UUID documentId, DocumentType type, OrderSnapshot order, Instant createdAt) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Document pdf = new Document(PageSize.A4, 48, 48, 48, 48);
      PdfWriter.getInstance(pdf, out);
      pdf.open();

      Font watermarkFont =
          FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(185, 28, 28));
      Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
      Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);
      Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);

      Paragraph bannerTop = new Paragraph(ACADEMIC_WATERMARK, watermarkFont);
      bannerTop.setAlignment(Element.ALIGN_CENTER);
      bannerTop.setSpacingAfter(16f);
      pdf.add(bannerTop);

      String docTitle =
          type == DocumentType.NFE_SIMULATED
              ? "NF-e Simulada (Documento Auxiliar Fictício)"
              : "CT-e Simulado (Conhecimento de Transporte Fictício)";
      Paragraph title = new Paragraph(docTitle, titleFont);
      title.setAlignment(Element.ALIGN_CENTER);
      title.setSpacingAfter(20f);
      pdf.add(title);

      pdf.add(new Paragraph("Identificação do Documento", sectionFont));
      pdf.add(new Paragraph("ID do Documento: " + documentId, bodyFont));
      pdf.add(new Paragraph("Tipo: " + type.name(), bodyFont));
      pdf.add(new Paragraph("ID do Pedido: " + order.orderId(), bodyFont));
      pdf.add(new Paragraph("Emitido em (UTC): " + createdAt, bodyFont));
      pdf.add(new Paragraph(" "));

      pdf.add(new Paragraph("Dados do Transporte e Frete", sectionFont));
      pdf.add(new Paragraph("Destinatário: " + safe(order.recipientName()), bodyFont));
      pdf.add(new Paragraph("Origem: " + formatAddress(order.origin()), bodyFont));
      pdf.add(new Paragraph("Destino: " + formatAddress(order.destination()), bodyFont));
      pdf.add(new Paragraph("Peso Total (kg): " + order.totalWeightKg(), bodyFont));
      pdf.add(
          new Paragraph(
              "Valor do Frete (" + order.currency() + "): " + order.freightAmount(), bodyFont));
      pdf.add(new Paragraph(" "));

      Paragraph footer =
          new Paragraph(
              ACADEMIC_WATERMARK
                  + " — Documento gerado exclusivamente para fins acadêmicos, sem chave SEFAZ ou validade jurídica.",
              watermarkFont);
      footer.setAlignment(Element.ALIGN_CENTER);
      footer.setSpacingBefore(24f);
      pdf.add(footer);

      pdf.close();
      return out.toByteArray();
    } catch (Exception ex) {
      throw new ContractException("INTERNAL_ERROR");
    }
  }

  private static String safe(String val) {
    return val != null ? val : "-";
  }

  private static String formatAddress(AddressSnapshot addr) {
    if (addr == null) {
      return "-";
    }
    return addr.line1() + " - " + addr.city() + "/" + addr.state() + " (" + addr.country() + ")";
  }
}
