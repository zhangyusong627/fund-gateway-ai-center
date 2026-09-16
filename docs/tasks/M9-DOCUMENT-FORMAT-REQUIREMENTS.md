# M9 任务卡：多格式文档解析与可追溯定位

- 状态：解析与控制台第一版已实现，持久化字段和 ER 同步待评估
- 目标：在不破坏现有 DOCX → 分片 → RAG → 评测链路的前提下，增加 DOC、PDF、XLS、XLSX 支持。
- 当前依据：本任务卡只冻结实施范围；具体实现必须继续遵守 ADR-009、ADR-011、ADR-012 和现有 DDL 边界。

## 业务目标

让用户可以上传 DOC、DOCX、PDF、XLS、XLSX 文档，系统能够统一解析为可排序、可分片、可索引、可引用的文档元素，并在分片浏览和 RAG 评测中展示准确来源定位。

## 第一版范围

支持以下格式：

- DOCX：沿用现有 Apache POI XWPF 解析能力；
- DOC：使用 Apache POI HWPF 解析常见正文和表格；
- XLS：使用 Apache POI HSSF 解析工作表、单元格和数据行；
- XLSX：使用 Apache POI XSSF 解析工作表、单元格和数据行；
- PDF：使用 Apache PDFBox 解析可搜索文本，并保留页码和文本块顺序。

第一版不支持：扫描 PDF OCR、复杂 PDF 表格还原、图片文字识别、PPT/PPTX、自动内容纠错和真实生产文档接入。

## 输入 / 输出

输入：

- 原始文件；
- `documentId`；
- 文档版本；
- 文件扩展名和 MIME 类型。

输出：

- 统一的 `ParsedDocument`；
- 有序 `DocumentElement` 集合；
- 格式化的 `DocumentLocator`；
- 带来源定位的 `KnowledgeChunk`；
- 可用于 pgvector、RAG 引用和评测标准证据选择的元数据。

## 统一解析设计

```text
上传文件
  → 文件格式检测
  → DocumentParserRegistry 选择解析器
  → ParsedDocument / DocumentElement / DocumentLocator
  → DocumentChunker
  → pgvector、检索、引用和评测
```

新增 `DocumentParser` 统一接口，具体实现包括：

- `DocxDocumentParser`；
- `DocDocumentParser`；
- `ExcelDocumentParser`；
- `PdfDocumentParser`。

应用服务不得根据扩展名编写解析分支；扩展名、MIME 和文件头不匹配时必须拒绝上传。

## 定位规范

统一定位对象至少支持：

| 格式 | 定位信息 |
|---|---|
| DOC / DOCX | 章节、段落序号、表格序号、行号 |
| PDF | 页码、文本块序号 |
| XLS / XLSX | Sheet 名、行号、列号、单元格范围 |

`locator` 必须可读、稳定，并能在分片浏览页面中直接展示。分片元数据同时保存格式专属字段，例如 `pageNumber`、`sheetName`、`cellRange`。

## 数据模型影响

实现前先完成 DDL 影响评估，不直接执行迁移。原则如下：

- `knowledge_documents` 增加文档格式字段，或确认使用现有元数据承载；
- `knowledge_document_elements` 和 `knowledge_chunks` 保留兼容现有字段；
- 新增定位字段时必须说明与现有 `section_path`、`table_index`、`row_index`、`locator` 的关系；
- DBML、ER 图、字段注释和初始化 DDL 必须同步；
- 数据库 Schema 变更单独取得授权后再实施。

## 实施批次

### M9-A：解析抽象与 DOCX 回归

- 定义 `DocumentParser`、`DocumentParserRegistry` 和 `DocumentLocator`；
- 将现有 DOCX 解析迁移到统一接口；
- 保持既有分片、chunkId、locator、RAG 和评测结果兼容；
- 增加解析器选择和格式不匹配测试。

**实现状态**：已完成。已增加统一解析接口、格式注册表、`DocumentLocator`、DOC 解析器、Office 解析器和 DOCX 回归；应用服务不再直接选择具体 DOCX 解析器。

### M9-B：DOC、XLS、XLSX

- 接入 HWPF、HSSF、XSSF；
- 处理正文、表格、Sheet、表头、数据行和单元格坐标；
- 覆盖公式展示值、合并单元格、空行和多 Sheet；
- 增加每种格式的最小合成 fixture。

**实现状态**：已完成。已接入 Apache POI HWPF、HSSF/XSSF 兼容入口，并保留 Sheet 名、行号和表格行文本。

### M9-C：可搜索 PDF

- 接入 PDFBox；
- 按页和文本块输出元素；
- 对加密 PDF、无文本 PDF、损坏 PDF 和扫描 PDF 给出明确失败结果；
- 不在本批次承诺 PDF 表格结构化。

**实现状态**：已完成。已接入 PDFBox 3.0.5，按页提取可搜索文本并在页内按自然换行和 1200 字符上限拆分文本块；空白或扫描类无文本 PDF 明确失败。

### M9-D：控制台、持久化、评测与文档

- 上传控件支持五种格式；
- 后端保存原始扩展名，不再固定保存为 `source.docx`；
- 文档列表显示格式；
- 分片浏览显示页码、Sheet、单元格范围等定位；
- 评测集标准证据选择兼容新 locator；
- 同步 DDL、DBML、ER 图、README、PRD 和架构文档。

**实现状态**：控制台上传控件、后端扩展名校验、原始扩展名保存、上传预览 locator 和评测分片浏览的格式/locator 展示已完成；正式格式字段、DDL/DBML/ER 仍无需立即变更，待独立评估。本轮未执行数据库迁移。

## 验收标准

- DOC、DOCX、PDF、XLS、XLSX 均能完成上传、解析和分片预览；
- 每种格式至少有一份合成样例和自动化测试；
- 错误扩展名、损坏文件、空文件和不支持的扫描 PDF 有明确失败结果；
- 每个可检索分片都有稳定 `chunkId` 和格式匹配的 `locator`；
- 分片浏览能够展示 PDF 页码或 Excel Sheet/单元格范围；
- RAG 检索、引用和评测能够使用新格式文档；
- 原有 DOCX 流程、已有索引和评测回归通过；
- `mvn -B verify`、`git diff --check` 和必要的 XML/DDL/Compose 检查通过；
- 不宣称支持 OCR、复杂 PDF 表格或生产级文档解析。

## 不在本次范围

- 不执行数据库迁移，除非另行确认；
- 不引入 Python、外部 OCR 服务或新的微服务；
- 不修改现有文档分片算法的业务语义；
- 不删除已有 DOCX 数据、评测集或学习证据；
- 不以“能读取文本”替代“来源定位可追溯”。
