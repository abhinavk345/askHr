package com.intech.ai.rag;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class HRPolicyLoader {
    private final VectorStore vectorStore;

    @Value("classpath:policy.pdf")
    private Resource policyPdf;

    public HRPolicyLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    public void checkPdfLoaded() {
        System.out.println("Filename: " + policyPdf.getFilename());
        System.out.println("URI: " + policyPdf);
    }

    @PostConstruct
    public void loadPdfData(){
        TikaDocumentReader tikaDocumentReader=new TikaDocumentReader(policyPdf);
        TokenTextSplitter splitter = new TokenTextSplitter(500,50,50,50,true);
        List<Document> documentList = tikaDocumentReader.get();
        List<Document> splitDocs = splitter.apply(documentList);
        vectorStore.add(splitDocs);
    }
}
