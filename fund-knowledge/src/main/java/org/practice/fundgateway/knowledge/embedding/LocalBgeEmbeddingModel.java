package org.practice.fundgateway.knowledge.embedding;

import java.io.IOException;
import java.nio.file.Path;

import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

/** 使用 DJL 在本地加载 BGE 模型并生成归一化向量。 */
public class LocalBgeEmbeddingModel implements AutoCloseable {

    private static final int DIMENSION = 512;
    private final ZooModel<NDList, NDList> model;
    private final LocalBgeTokenizer tokenizer;

    /** 从本地模型目录加载 BGE 模型。 */
    public LocalBgeEmbeddingModel(Path modelDirectory) throws IOException {
        Criteria<NDList, NDList> criteria = Criteria.builder()
                .setTypes(NDList.class, NDList.class)
                .optModelPath(modelDirectory)
                .optModelName("model.onnx")
                .optTranslator(new BgeTranslator())
                .optEngine("OnnxRuntime")
                .build();
        try {
            this.model = criteria.loadModel();
        } catch (Exception exception) {
            throw new IOException("无法加载本地 BGE 模型", exception);
        }
        this.tokenizer = new LocalBgeTokenizer(modelDirectory);
    }

    /** 生成一个 512 维归一化查询向量。 */
    public float[] embed(String text) throws TranslateException {
        long[] ids = tokenizer.encode(text);
        long[] attentionMask = tokenizer.attentionMask(text);
        try (Predictor<NDList, NDList> predictor = model.newPredictor()) {
            NDList output = predictor.predict(new NDList(idsToArray(ids), idsToArray(attentionMask)));
            NDArray hidden = output.get(0);
            NDArray mask = idsToArray(attentionMask).expandDims(1);
            NDArray pooled = hidden.mul(mask).sum(new int[] {0}).div(mask.sum());
            float[] vector = pooled.toFloatArray();
            double norm = 0;
            for (float value : vector) {
                norm += value * value;
            }
            norm = Math.sqrt(norm);
            if (norm == 0) {
                throw new IllegalStateException("BGE 输出向量长度为零");
            }
            for (int index = 0; index < vector.length; index++) {
                vector[index] = (float) (vector[index] / norm);
            }
            if (vector.length != DIMENSION) {
                throw new IllegalStateException("BGE 向量维度不是 512，实际为 " + vector.length);
            }
            return vector;
        }
    }

    /** 将 token 编号转换为模型输入数组。 */
    private NDArray idsToArray(long[] ids) {
        NDManager manager = model.getNDManager();
        return manager.create(ids);
    }

    /** 释放模型和 tokenizer 资源。 */
    @Override
    public void close() {
        tokenizer.close();
        model.close();
    }

    /** 把模型输出转换成句向量的最小转换器。 */
    private static final class BgeTranslator implements Translator<NDList, NDList> {
        @Override
        public NDList processInput(TranslatorContext context, NDList input) {
            NDArray ids = input.get(0);
            NDArray mask = input.get(1);
            NDArray tokenTypes = ids.zerosLike();
            return new NDList(ids, mask, tokenTypes);
        }

        @Override
        public NDList processOutput(TranslatorContext context, NDList list) {
            return list;
        }
    }
}
