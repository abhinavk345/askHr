package com.intech.ai.rag;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class HrPolicyLoader {

    private final VectorStore vectorStore;

    @Value("classpath:policy.pdf")
    private Resource policyPdf;


    public HrPolicyLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    public void loadPdfData() {
        try {
            if (!policyPdf.exists()) {
                log.error("policy.pdf NOT FOUND in classpath");
                return;
            }

            log.info("Loading HR policy PDF: {}", policyPdf.getFilename());

            TikaDocumentReader reader = new TikaDocumentReader(policyPdf);
            TokenTextSplitter splitter =
                    new TokenTextSplitter(500, 50, 50, 50, true);

            List<Document> documents = reader.get();
            List<Document> splitDocs = splitter.apply(documents);

            vectorStore.add(splitDocs);

            log.info(" HR Policy loaded into VectorStore. Chunks: {}", splitDocs.size());

        } catch (Exception e) {
            log.error("Failed to load HR policy PDF", e);

        }
    }
}
