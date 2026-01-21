package com.intech.ai.utility;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HRUtility {

    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "(?i)^(hi|hello|hey|good morning|good afternoon|good evening).*$"
    );

    // HR intent detection
    private static final Pattern HR_PATTERN = Pattern.compile(
            "(?i).*(" +
                    "hr|human resources|employee|employment|" +
                    "leave|leaves|pl|nbl|lwp|maternity|paternity|medical|" +
                    "holiday|attendance|working hours|shift|" +
                    "policy|code of conduct|disciplinary|" +
                    "probation|promotion|resignation|notice period|" +
                    "harassment|posh|benefits|grievance|approval" +
                    ").*"
    );

    public static boolean isGreeting(String message) {
        return GREETING_PATTERN.matcher(message.trim()).matches();
    }

    public static boolean isHRMessage(String message) {
        return HR_PATTERN.matcher(message).matches();
    }


    public static String joinDocuments(List<Document> docs) {
        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
