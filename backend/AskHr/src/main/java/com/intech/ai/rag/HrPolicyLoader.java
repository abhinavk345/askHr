package com.intech.ai.rag;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class HrPolicyLoader {

    private static final String SOURCE_KEY = "source";
    private static final String SOURCE_VALUE = "policy2.pdf";

    private final VectorStore vectorStore;

    @Value("classpath:policy2.pdf")
    private Resource policyPdf;

    public HrPolicyLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    public void loadPdfData() {
        try {
            if (!policyPdf.exists()) {
                log.error("pdf NOT FOUND in classpath");
                return;
            }

            // 🔍 Check if already indexed
            if (isAlreadyIndexed()) {
                log.info("HR Policy already exists in VectorStore. Skipping chunk creation.");
                return;
            }

            log.info("Loading HR policy PDF: {}", policyPdf.getFilename());

            TikaDocumentReader reader = new TikaDocumentReader(policyPdf);
            TokenTextSplitter splitter =
                    new TokenTextSplitter(30, 50, 50, 500, true);

            List<Document> documents = reader.get();
            List<Document> splitDocs = splitter.apply(documents);

            // 🏷️ Add metadata to each chunk
            splitDocs.forEach(doc ->
                    doc.getMetadata().put(SOURCE_KEY, SOURCE_VALUE)
            );

            vectorStore.add(splitDocs);

            log.info("HR Policy loaded into VectorStore. Chunks: {}", splitDocs.size());

        } catch (Exception e) {
            log.error("Failed to load HR policy PDF", e);
        }
    }

    private boolean isAlreadyIndexed() {

        SearchRequest request = SearchRequest.builder()
                .query("policy") // any dummy query, filter does the work
                .topK(1)
                .filterExpression(SOURCE_KEY + " == '" + SOURCE_VALUE + "'")
                .build();

        List<Document> existingDocs = vectorStore.similaritySearch(request);

        return !existingDocs.isEmpty();
    }
}
