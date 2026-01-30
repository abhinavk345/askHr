package com.intech.ai.service;

import com.intech.ai.modal.LeaveFlowState;
import com.intech.ai.modal.LeaveRequest;
import com.intech.ai.modal.Ticket;
import com.intech.ai.repository.LeaveRequestRepository;
import com.intech.ai.repository.TicketRepository;
import com.intech.ai.utility.IntentDetector;
import com.intech.ai.utility.LeavePolicyCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryService {

    private final VectorStore vectorStore;
    private final AiChatService aiChatService;
    private final TicketRepository leaveTicketRepository;

    /* ================= CACHE ================= */

    private final Map<String, CachedFlux> queryCache = new ConcurrentHashMap<>();
    private final Map<String, LeaveFlowState> leaveFlow = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofMinutes(15);

    /* ================= ENTRY POINT ================= */

    public Flux<String> handleUserQuery(String message, String userId) {

        // 0️⃣ If leave flow already started for this user → continue it
        if (userId != null && leaveFlow.containsKey(userId)) {
            return handleLeaveCreation(message, userId);
        }

        // 1️⃣ New leave request
        if (IntentDetector.isLeaveCreationRequest(message)) {
            return handleLeaveCreation(message, userId);
        }

        // 2️⃣ Leave policy summary list
        if (IntentDetector.isLeavePolicySummary(message)) {
            return Flux.just(LeavePolicyCatalog.summary());
        }

        // 3️⃣ Default: policy RAG
        return handlePolicyQuery(message, userId);
    }



    private Flux<String> handleLeaveCreation1(String message, String userId) {

        String extractPrompt = "...";

        return Flux.create(sink ->
                aiChatService.ask(extractPrompt)
                        .thenAccept(date -> {

                            if ("UNKNOWN".equalsIgnoreCase(date.trim())) {
                                sink.next("Please specify the leave date.");
                                sink.complete();
                                return;
                            }

                            Ticket ticket = new Ticket();
                            ticket.setEmployeeId(userId);
                            ticket.setStatus("CREATED");
                            ticket.setCreatedAt(LocalDateTime.now());

                            leaveTicketRepository.save(ticket);

                            sink.next(
                                    "✅ Leave ticket created successfully\n" +
                                            "Ticket ID: " + ticket.getId()
                            );
                            sink.complete();
                        })
                        .exceptionally(ex -> {
                            sink.error(ex);
                            return null;
                        })
        );
    }

    private Flux<String> handleLeaveCreation2(String message, String userId) {

        String lower = message.toLowerCase();

        // 🔐 SAFETY GATE — no DB write without confirmation
        if (!lower.contains("confirm")) {
            return Flux.just(
                    "I can create a leave ticket for you.\n" +
                            "Please confirm by replying:\n" +
                            "**confirm leave for <date>**"
            );
        }

        // Only confirmed requests reach here
        return createLeaveTicket(message, userId);
    }

    private Flux<String> handleLeaveCreation(String message, String userId) {

        if (userId == null || userId.isBlank()) {
            return Flux.just("Please login to apply leave.");
        }

        LeaveFlowState state = leaveFlow.computeIfAbsent(userId, k -> {
            LeaveFlowState s = new LeaveFlowState();
            s.setStep("TYPE");
            return s;
        });

        String lower = message.toLowerCase().trim();

        // Cancel support
        if (lower.contains("cancel")) {
            leaveFlow.remove(userId);
            return Flux.just("❌ Leave request cancelled.");
        }

        // Step: TYPE
        if ("TYPE".equals(state.getStep())) {
            state.setStep("FROM_DATE");
            return Flux.just("""
                Please select Leave Type:</br>
                1. Need Based Leave</br>
                2. Planned Leave</br>
                3. Paternity Leave</br>
                4. Maternity Leave</br>
                5. Project Leave</br>
                6. Leave Without Pay</br>
                7. Election Leave</br>
                8. Birthday Leave</br>
                Reply with number or leave type name.
                """);
        }

        // Capture leave type
        if ("FROM_DATE".equals(state.getStep()) && state.getLeaveType() == null) {
            String type = parseLeaveType(message);
            if (type == null) {
                return Flux.just("Invalid leave type. Please reply with 1-8 or leave name.");
            }
            state.setLeaveType(type);
            return Flux.just("Enter From Date (yyyy-MM-dd) OR type 'tomorrow'");
        }

        // From date
        if ("FROM_DATE".equals(state.getStep()) && state.getFromDate() == null) {
            LocalDate from = parseDate(message);
            if (from == null) return Flux.just("Invalid date. Please enter yyyy-MM-dd or 'tomorrow'");
            state.setFromDate(from);
            state.setStep("TO_DATE");
            return Flux.just("Enter To Date (yyyy-MM-dd) OR type 'same'");
        }

        // To date
        if ("TO_DATE".equals(state.getStep()) && state.getToDate() == null) {
            LocalDate to = message.equalsIgnoreCase("same") ? state.getFromDate() : parseDate(message);
            if (to == null) return Flux.just("Invalid date. Please enter yyyy-MM-dd or 'same'");
            state.setToDate(to);
            state.setStep("REASON");
            return Flux.just("Enter reason for leave:");
        }

        // Reason
        if ("REASON".equals(state.getStep()) && state.getReason() == null) {
            state.setReason(message.trim());
            state.setStep("CONFIRM");

            return Flux.just("""
                Please confirm leave request:
                Leave Type: %s
                From: %s
                To: %s
                Reason: %s
                                
                Reply: CONFIRM to submit OR CANCEL
                """.formatted(state.getLeaveType(), state.getFromDate(), state.getToDate(), state.getReason()));
        }

        // Confirm
        if ("CONFIRM".equals(state.getStep())) {
            if (!lower.equals("confirm")) {
                return Flux.just("Please reply CONFIRM to submit or CANCEL to stop.");
            }

            // ✅ Now create ticket only after all details
            Ticket ticket = new Ticket();
            ticket.setEmployeeId(userId);
            ticket.setCategory("LEAVE");
            ticket.setDescription(
                    "LeaveType=" + state.getLeaveType() +
                            ", From=" + state.getFromDate() +
                            ", To=" + state.getToDate() +
                            ", Reason=" + state.getReason()
            );
            ticket.setStatus("CREATED");
            ticket.setCreatedAt(LocalDateTime.now());
            leaveTicketRepository.save(ticket);

            leaveFlow.remove(userId);

            return Flux.just("""
                ✅ Leave ticket created successfully
                Ticket ID: %s
                Leave Type: %s
                From: %s
                To: %s
                """.formatted(ticket.getId(), state.getLeaveType(), state.getFromDate(), state.getToDate()));
        }

        return Flux.just("Something went wrong in leave flow. Type CANCEL and retry.");
    }

    /* ================= POLICY (RAG) HANDLER ================= */

    private Flux<String> createLeaveTicket(String message, String userId) {

        String extractPrompt = """
        Extract leave date from the text.
        Return date in YYYY-MM-DD format only.
        If no date is found, return UNKNOWN.

        Text:
        %s
        """.formatted(message);

        return Flux.create(sink ->
                aiChatService.ask(extractPrompt)
                        .thenAccept(date -> {

                            String cleanedDate = date.trim();

                            if ("UNKNOWN".equalsIgnoreCase(cleanedDate)) {
                                sink.next("Please specify the leave date.");
                                sink.complete();
                                return;
                            }

                            Ticket ticket = new Ticket();
                            ticket.setEmployeeId(userId);
                           // ticket.setLeaveDate(cleanedDate);
                            ticket.setStatus("CREATED");
                            ticket.setCreatedAt(LocalDateTime.now());

                            leaveTicketRepository.save(ticket);

                            sink.next(
                                    "✅ Leave ticket created successfully\n" +
                                            "Ticket ID: " + ticket.getId() + "\n" +
                                            "Leave Date: " + ticket.getCreatedAt()
                            );
                            sink.complete();
                        })
                        .exceptionally(ex -> {
                            sink.error(ex);
                            return null;
                        })
        );
    }

    public Flux<String> handlePolicyQuery(String message, String userId) {

        String lowerMessage = message.toLowerCase().trim();
        boolean detailed = lowerMessage.contains("explain")
                || lowerMessage.contains("details")
                || lowerMessage.contains("elaborate")
                || lowerMessage.contains("why");

        String cacheKey = buildCacheKey(message, userId, detailed);
        CachedFlux cached = queryCache.get(cacheKey);

        if (cached != null && !cached.isExpired()) {
            return cached.flux().delayElements(Duration.ofMillis(30));
        }

        Flux<String> flux = Flux.defer(() -> {

            List<Document> documents = vectorStore.similaritySearch(message);

            // 🚨 RAG Guard (prevents hallucination)
            if (documents.isEmpty() || documents.get(0).getScore() < 0.75) {
                return Flux.just("I don’t have information on this.");
            }

            String context = buildContext(documents);
            String prompt = buildPrompt(message, context, detailed);

            Flux<String> response = aiChatService.askStream(prompt)
                    .map(s -> s.replaceAll("\\n+", "\n"));

            if (detailed) {
                response = response.startWith("Let me check that for you...");
            }

            return response;
        }).cache();

        queryCache.put(cacheKey, new CachedFlux(flux, Instant.now(), ttl));
        return flux;
    }

    /* ================= PROMPT ================= */

    private String buildPrompt(String message, String context, boolean detailed) {

        if (!detailed) {
            return """
            You are an HR assistant for INTECH INDIA.

            STRICT RULES:
            - Answer ONLY using the CONTEXT below
            - If the answer is NOT clearly present in the CONTEXT, say:
              "I don’t have information on this."
            - Do NOT guess
            - Do NOT assume
            - Keep the answer VERY SHORT
            - Max 3 bullet points

            CONTEXT:
            %s

            QUESTION:
            %s

            ANSWER:
            """.formatted(context, message);
        }

        return """
        You are an HR assistant for INTECH INDIA.

        STRICT RULES:
        - Answer ONLY using the CONTEXT below
        - If the answer is NOT clearly present in the CONTEXT, say:
          "I don’t have information on this."
        - Do NOT guess
        - Do NOT assume
        - Use bullet points
        - Explain clearly

        CONTEXT:
        %s

        QUESTION:
        %s

        ANSWER:
        """.formatted(context, message);
    }

    /* ================= HELPERS ================= */

    private String buildContext(List<Document> docs) {
        return docs.stream()
                .limit(2)
                .map(d -> d.getText().substring(0, Math.min(300, d.getText().length())))
                .collect(Collectors.joining("\n\n"));
    }

    private String buildCacheKey(String message, String userId, boolean detailed) {
        return message.toLowerCase().trim()
                + "::" + (userId != null ? userId : "anon")
                + "::" + (detailed ? "DETAILED" : "SHORT");
    }

    /* ================= CACHE HOLDER ================= */

    private static class CachedFlux {
        private final Flux<String> flux;
        private final Instant createdAt;
        private final Duration ttl;

        CachedFlux(Flux<String> flux, Instant createdAt, Duration ttl) {
            this.flux = flux;
            this.createdAt = createdAt;
            this.ttl = ttl;
        }

        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plus(ttl));
        }

        Flux<String> flux() {
            return flux;
        }
    }
    private String parseLeaveType(String message) {
        String m = message.trim().toLowerCase();

        return switch (m) {
            case "1", "need based leave" ,"nbl" -> "Need Based Leave";
            case "2", "planned leave","pl" -> "Planned Leave";
            case "3", "paternity leave" -> "Paternity Leave";
            case "4", "maternity leave" -> "Maternity Leave";
            case "5", "project leave" -> "Project Leave";
            case "6", "leave without pay", "lwp" -> "Leave Without Pay";
            case "7", "election leave" -> "Election Leave";
            case "8", "birthday leave" -> "Birthday Leave";
            default -> null;
        };
    }

    private LocalDate parseDate(String message) {
        String m = message.trim().toLowerCase();

        try {
            if (m.equals("tomorrow")) return LocalDate.now().plusDays(1);
            if (m.equals("today")) return LocalDate.now();
            return LocalDate.parse(message.trim());
        } catch (Exception e) {
            return null;
        }
    }

}