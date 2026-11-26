package logistics.documents.infrastructure.persistence;

import contracts.v1.DocumentStatus;
import contracts.v1.DocumentType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import logistics.documents.domain.SimulatedDocument;
import logistics.documents.domain.SimulatedDocumentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSimulatedDocumentRepository implements SimulatedDocumentRepository {
  private final JdbcTemplate jdbcTemplate;

  private final RowMapper<SimulatedDocument> rowMapper =
      (rs, rowNum) ->
          new SimulatedDocument(
              getUuid(rs, "id"),
              getUuid(rs, "order_id"),
              DocumentType.valueOf(rs.getString("document_type")),
              DocumentStatus.valueOf(rs.getString("status")),
              rs.getBytes("pdf_content"),
              rs.getTimestamp("created_at").toInstant());

  public JdbcSimulatedDocumentRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private static UUID getUuid(ResultSet rs, String col) throws SQLException {
    Object val = rs.getObject(col);
    if (val == null) {
      return null;
    }
    return val instanceof UUID uuid ? uuid : UUID.fromString(val.toString());
  }

  @Override
  public Optional<SimulatedDocument> findById(UUID id) {
    List<SimulatedDocument> rows =
        jdbcTemplate.query(
            """
            SELECT id, order_id, document_type, status, pdf_content, created_at
            FROM documents.simulated_documents
            WHERE id = ?
            """,
            rowMapper,
            id);
    return rows.stream().findFirst();
  }

  @Override
  public Optional<SimulatedDocument> findByOrderAndType(UUID orderId, DocumentType documentType) {
    List<SimulatedDocument> rows =
        jdbcTemplate.query(
            """
            SELECT id, order_id, document_type, status, pdf_content, created_at
            FROM documents.simulated_documents
            WHERE order_id = ? AND document_type = ?
            """,
            rowMapper,
            orderId,
            documentType.name());
    return rows.stream().findFirst();
  }

  @Override
  public void save(SimulatedDocument document) {
    jdbcTemplate.update(
        """
        INSERT INTO documents.simulated_documents
        (id, order_id, document_type, status, pdf_content, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        document.id(),
        document.orderId(),
        document.documentType().name(),
        document.status().name(),
        document.pdfContent(),
        Timestamp.from(document.createdAt()));
  }
}
