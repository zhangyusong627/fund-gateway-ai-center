package org.practice.fundgateway.knowledge.embedding;

import java.io.IOException;
import java.nio.file.Path;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;

/** 使用本地 BGE tokenizer 将中文查询转换为模型输入 token。 */
public class LocalBgeTokenizer implements AutoCloseable {

    private final HuggingFaceTokenizer tokenizer;

    /** 从模型目录加载 tokenizer.json。 */
    public LocalBgeTokenizer(Path modelDirectory) throws IOException {
        this.tokenizer = HuggingFaceTokenizer.newInstance(modelDirectory.resolve("tokenizer.json"));
    }

    /** 返回查询文本的 token 编号，供后续模型推理使用。 */
    public long[] encode(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("查询文本不能为空");
        }
        Encoding encoding = tokenizer.encode(text);
        return encoding.getIds();
    }

    /** 返回与 token 对齐的注意力掩码，1 表示真实 token，0 表示填充位置。 */
    public long[] attentionMask(String text) {
        return tokenizer.encode(text).getAttentionMask();
    }

    /** 释放 tokenizer 的本地资源。 */
    @Override
    public void close() {
        tokenizer.close();
    }
}
