package org.practice.fundgateway.knowledge.document;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.practice.fundgateway.knowledge.chunk.KnowledgeChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用 PostgreSQL 保存文档版本元数据，DDL 由人工审核后单独执行。 */
public class PostgresDocumentVersionRepository implements DocumentVersionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    /** 注入 JDBC 模板。 */
    public PostgresDocumentVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        if (jdbcTemplate.getDataSource() == null) {
            throw new IllegalArgumentException("文档仓储需要可用的数据源");
        }
        this.transactionTemplate = new TransactionTemplate(
                new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
    }

    /** 按文档和版本读取元数据。 */
    @Override
    public Optional<DocumentVersionRecord> find(String documentId, String version) {
        List<DocumentVersionRecord> records = jdbcTemplate.query("select document_id, version, file_path, file_sha256, status from knowledge.knowledge_documents where document_id=? and version=?", rowMapper(), documentId, version);
        return records.stream().findFirst();
    }

    /** 按 SHA-256 查找重复内容。 */
    @Override
    public Optional<DocumentVersionRecord> findBySha256(String sha256) {
        List<DocumentVersionRecord> records = jdbcTemplate.query("select document_id, version, file_path, file_sha256, status from knowledge.knowledge_documents where file_sha256=?", rowMapper(), sha256);
        return records.stream().findFirst();
    }

    /** 返回全部文档版本。 */
    @Override
    public List<DocumentVersionRecord> findAll() {
        return jdbcTemplate.query("select document_id, version, file_path, file_sha256, status from knowledge.knowledge_documents order by created_at", rowMapper());
    }

    /** 返回指定文档的全部版本。 */
    @Override
    public List<DocumentVersionRecord> findVersions(String documentId) {
        return jdbcTemplate.query("select document_id, version, file_path, file_sha256, status from knowledge.knowledge_documents where document_id=? order by created_at", rowMapper(), documentId);
    }

    /** 插入不可覆盖的文档版本。 */
    @Override
    public DocumentVersionRecord save(DocumentVersionRecord record) {
        return transactionTemplate.execute(status -> {
            jdbcTemplate.update("insert into knowledge.knowledge_documents "
                            + "(document_id, version, file_path, file_sha256, status, created_at, updated_at) "
                            + "values (?, ?, ?, ?, ?, now(), now())",
                    record.source().documentId(), record.source().version(), record.source().file().toString(),
                    record.source().fileSha256(), record.status().name());
            saveElements(record);
            saveChunks(record);
            return record;
        });
    }

    /** 更新状态，不允许覆盖文档来源和版本内容。 */
    @Override
    public DocumentVersionRecord updateStatus(String documentId, String version, DocumentIndexStatus status) {
        jdbcTemplate.update("update knowledge.knowledge_documents set status=?, updated_at=now() where document_id=? and version=?", status.name(), documentId, version);
        return find(documentId, version).orElseThrow(() -> new IllegalArgumentException("文档版本不存在"));
    }

    /** 保存解析元素，保证应用重启后仍能查看完整解析结果。 */
    private void saveElements(DocumentVersionRecord record) {
        jdbcTemplate.batchUpdate("insert into knowledge.knowledge_document_elements "
                        + "(document_id, version, sequence_no, element_type, section_path, raw_text, cleaned_text, "
                        + "table_index, row_index) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                record.elements(), 200, (statement, element) -> {
                    statement.setString(1, record.source().documentId());
                    statement.setString(2, record.source().version());
                    statement.setInt(3, element.sequence());
                    statement.setString(4, element.type().name());
                    statement.setString(5, element.sectionPath());
                    statement.setString(6, element.rawText());
                    statement.setString(7, element.cleanedText());
                    statement.setInt(8, element.tableIndex());
                    statement.setInt(9, element.rowIndex());
                });
    }

    /** 保存待向量化分片，索引任务可在进程重启后继续执行。 */
    private void saveChunks(DocumentVersionRecord record) {
                jdbcTemplate.batchUpdate("insert into knowledge.knowledge_document_chunks "
                        + "(chunk_id, document_id, version, section_path, content, first_sequence, last_sequence, "
                        + "table_index, row_index, metadata) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                record.chunks(), 200, (statement, chunk) -> {
                    statement.setString(1, chunk.chunkId());
                    statement.setString(2, record.source().documentId());
                    statement.setString(3, record.source().version());
                    statement.setString(4, chunk.sectionPath());
                    statement.setString(5, chunk.text());
                    statement.setInt(6, chunk.firstSequence());
                    statement.setInt(7, chunk.lastSequence());
                    statement.setInt(8, chunk.tableIndex());
                    statement.setInt(9, chunk.rowIndex());
                    statement.setString(10, metadataJson(chunk));
                });
    }

    /** 保存格式和来源定位元数据，供重启后的分片预览继续展示。 */
    private String metadataJson(KnowledgeChunk chunk) {
        return "{\"format\":\"" + DocumentFormat.from(chunk.source().file().getFileName().toString())
                + "\",\"locator\":\"" + chunk.locator().replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    /** 将数据库行及其解析子记录转换为完整领域记录。 */
    private org.springframework.jdbc.core.RowMapper<DocumentVersionRecord> rowMapper() {
        return (resultSet, rowNumber) -> {
            String documentId = resultSet.getString("document_id");
            String version = resultSet.getString("version");
            DocumentSource source = new DocumentSource(documentId, version,
                    Path.of(resultSet.getString("file_path")), resultSet.getString("file_sha256"));
            return new DocumentVersionRecord(source, DocumentIndexStatus.valueOf(resultSet.getString("status")),
                    loadElements(documentId, version), loadChunks(source));
        };
    }

    /** 按原始顺序恢复文档段落和表格行。 */
    private List<DocumentElement> loadElements(String documentId, String version) {
        return jdbcTemplate.query("select sequence_no, element_type, section_path, raw_text, cleaned_text, "
                        + "table_index, row_index from knowledge.knowledge_document_elements "
                        + "where document_id=? and version=? order by sequence_no",
                (resultSet, rowNumber) -> new DocumentElement(
                        DocumentElement.ElementType.valueOf(resultSet.getString("element_type")),
                        resultSet.getInt("sequence_no"), resultSet.getString("section_path"),
                        resultSet.getString("raw_text"), resultSet.getString("cleaned_text"),
                        resultSet.getInt("table_index"), resultSet.getInt("row_index")),
                documentId, version);
    }

    /** 按来源顺序恢复全部待向量化分片。 */
    private List<KnowledgeChunk> loadChunks(DocumentSource source) {
        return jdbcTemplate.query("select chunk_id, section_path, content, first_sequence, last_sequence, "
                        + "table_index, row_index from knowledge.knowledge_document_chunks "
                        + "where document_id=? and version=? order by first_sequence, chunk_id",
                (resultSet, rowNumber) -> new KnowledgeChunk(resultSet.getString("chunk_id"), source,
                        resultSet.getString("section_path"), resultSet.getString("content"),
                        resultSet.getInt("first_sequence"), resultSet.getInt("last_sequence"),
                        resultSet.getInt("table_index"), resultSet.getInt("row_index")),
                source.documentId(), source.version());
    }
}
