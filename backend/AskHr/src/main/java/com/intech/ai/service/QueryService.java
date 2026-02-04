package com.intech.ai.service;

import com.intech.ai.caches.SemanticCacheEntry;
import com.intech.ai.caches.SemanticResponseCache;
import com.intech.ai.enums.LeaveStep;
import com.intech.ai.enums.UserIntent;
import com.intech.ai.modal.LeaveFlowState;
import com.intech.ai.modal.Ticket;
import com.intech.ai.repository.TicketRepository;
import com.intech.ai.caches.LlmResponseCache;
import com.intech.ai.utility.CosineSimilarityUtil;
import com.intech.ai.utility.FuzzyTextUtil;
import com.intech.ai.utility.IntentDetector;
import com.intech.ai.utility.PromptNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intech.ai.enums.UserIntent.*;

@Service
@RequiredArgsConstructor
public class QueryService {

    private final TicketRepository leaveTicketRepository;
    private final AiChatService aiChatService; // your LLM service
    private final LlmResponseCache llmCache;
    private final SemanticResponseCache semanticCache;
    private final EmbeddingService embeddingService;
    private final TicketExportService ticketExportService;
    private final IntentClassificationService intentService;
    // store conversation state per user
    private final Map<String, LeaveFlowState> leaveFlow = new ConcurrentHashMap<>();
    private final Map<String, UUID> lastTicketContext = new ConcurrentHashMap<>();

    /* ============================================================
       MAIN ENTRY
       ============================================================ */
    public Flux<String> handleUserQuery1(String message, String userId) {

        if (userId != null && leaveFlow.containsKey(userId)) {

            if (IntentDetector.isCancel(message)) {
                leaveFlow.remove(userId);
                return Flux.just("❌ Leave process cancelled. How else can I help?");
            }

            if (IntentDetector.isNewIntent(message)) {
                leaveFlow.remove(userId);
                // continue to fresh intent detection
            } else {
                return handleLeaveCreation(message, userId);
            }
        }

        if (message == null || message.isBlank()) {
            return Flux.just("Please type your query.");
        }

        // 2) Detect leave intent (supports fuzzy + typos)
        if (IntentDetector.isLeavePolicyQuery(message)) {
            // direct policy answer using LLM/RAG
            return aiChatService.askStream(message);
        }

        if (IntentDetector.isLeaveCreationRequest(message)) {
            return handleLeaveCreation(message, userId);
        }

        // Ticket update
        if (IntentDetector.isTicketUpdateRequest(message)) {
            return handleTicketUpdate(message, userId);
        }

        if (IntentDetector.isTicketStatusExportRequest(message)) {
            return handleTicketStatusExport(userId);
        }

        // Ticket status
        if (IntentDetector.isTicketStatusRequest(message)) {
            return handleTicketStatus(message, userId);
        }

        // Ticket delete
        if (IntentDetector.isTicketDeleteRequest(message)) {
            return handleTicketDelete(message, userId);
        }

        // 3) Otherwise treat as general query (policy/LLM)
        //-- return aiChatService.askStream(message);

        // Normalize message for cache key
        String normalizedPrompt = PromptNormalizer.normalize(message);

// ✅ CACHE HIT → DO NOT CALL LLM
        if (llmCache.contains(normalizedPrompt)) {
            return streamLikeLlm(llmCache.get(normalizedPrompt));
        }

        if (IntentDetector.isLeavePolicyQuery(message)) {
            return aiChatService.askStream(message);
        }

        if (!semanticCache.isEmpty()) {
            List<Float> queryEmbedding = embeddingService.embed(normalizedPrompt);
            for (var entry : semanticCache.getAll()) {
                double similarity = CosineSimilarityUtil.similarity(
                        queryEmbedding,
                        entry.getEmbedding()
                );
                // 🔥 Threshold (tune between 0.80 – 0.90)
                if (similarity >= 0.85) {
                    return streamLikeLlm(entry.getResponse());
                }
            }
        }

// ❌ CACHE MISS → CALL LLM
        return aiChatService.askStream(message)
                .collectList()
                .map(parts -> String.join("", parts))
//                .doOnNext(response -> {
//                    if (response != null && !response.isBlank()) {
//                        llmCache.put(normalizedPrompt, response);
//                    }
//                })
                .doOnNext(response -> {
                    llmCache.put(normalizedPrompt, response);

                    List<Float> embedding = embeddingService.embed(normalizedPrompt);
                    semanticCache.put(new SemanticCacheEntry(embedding, response));
                })
                .flatMapMany(Flux::just);
    }

    public Flux<String> handleUserQuery(String message, String userId) {

        if (message == null || message.isBlank()) {
            return Flux.just("Please type your query.");
        }

        // 1️⃣ If user is already in leave flow
        if (userId != null && leaveFlow.containsKey(userId)) {

            if (IntentDetector.isCancel(message)) {
                leaveFlow.remove(userId);
                return Flux.just("❌ Leave process cancelled. How else can I help?");
            }

            // If user continues the same leave conversation → stay in flow
            if (!IntentDetector.isNewIntent(message)) {
                return handleLeaveCreation(message, userId);
            }

            // User wants something else → reset flow
            leaveFlow.remove(userId);
        }

        // 2️⃣ FAST intent detection (LLM, non-streaming)
        UserIntent intent = intentService.detectIntentWithLLM(message);

        // 3️⃣ Route based on intent
        return switch (intent) {
            case GREETING -> Flux.just("Hi 😊 How can I help you today?");
            case LEAVE_CREATE -> handleLeaveCreation(message, userId);
            case LEAVE_STATUS -> handleTicketStatus(message, userId);
            case POLICY_QUERY -> answerPolicyWithCache(message);
            case TICKET_UPDATE -> handleTicketUpdate(message, userId);
            default -> handleGeneralChatWithCache(message);
        };
    }

    private Flux<String> handleTicketStatusExport(String userId) {

        if (userId == null || userId.isBlank()) {
            return Flux.just("Please login to download ticket status report.");
        }

        return Mono.fromCallable(() -> ticketExportService.exportTicketStatusExcel(userId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(fileName -> {

                    String url = "/api/v1/files/" + fileName;

                    // Return response with attachment metadata
                    // (if you still return Flux<String>, then return JSON string)
                    return Flux.just("""
                        {
                          "message": "📄 Ticket status report generated. Click download icon.",
                          "attachment": {
                            "fileName": "%s",
                            "fileType": "EXCEL",
                            "url": "%s"
                          }
                        }
                        """.formatted(fileName, url));
                });
    }

    /* ============================================================
       TICKET UPDATE
       ============================================================ */
    private Flux<String> handleTicketUpdate1(String message, String userId) {

        if (userId == null || userId.isBlank()) {
            return Flux.just("Please login to update ticket.");
        }

        UUID ticketId = extractTicketId(message);
        if (ticketId == null) {
            return Flux.just("Please provide Ticket ID (UUID). Example: update ticket <uuid> because fever");
        }

        String newReason = extractReason(message);
        if (newReason == null || newReason.isBlank()) {
            return Flux.just("Please provide reason. Example: update ticket <uuid> because fever");
        }

        return Mono.fromCallable(() -> leaveTicketRepository.findById(ticketId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(optional -> {

                    if (optional.isEmpty()) {
                        return Flux.just("Ticket not found: " + ticketId);
                    }

                    Ticket ticket = optional.get();

                    if (!userId.equals(ticket.getEmployeeId())) {
                        return Flux.just("❌ You are not authorized to update this ticket.");
                    }

                    if ("APPROVED".equalsIgnoreCase(ticket.getStatus())) {
                        return Flux.just("❌ Approved ticket cannot be updated.");
                    }

                    ticket.setDescription(ticket.getDescription() + ", UpdatedReason=" + newReason);
                    ticket.setUpdatedAt(LocalDateTime.now());


                    return Mono.fromCallable(() -> leaveTicketRepository.save(ticket))
                            .subscribeOn(Schedulers.boundedElastic())
                            .thenMany(Flux.just("✅ Ticket updated successfully: " + ticketId));
                });
    }

    private Flux<String> handleTicketUpdate(String message, String userId) {

        if (userId == null || userId.isBlank()) {
            return Flux.just("Please login to update ticket.");
        }

        if (message == null || message.isBlank()) {
            return Flux.just("Please type your update request.");
        }

        // 1) Resolve ticket id (from message OR context)
        UUID extractedTicketId = extractTicketId(message);

        UUID resolvedTicketId =
                extractedTicketId != null
                        ? extractedTicketId
                        : lastTicketContext.get(userId);

        if (resolvedTicketId == null) {
            return Flux.just("Please provide Ticket ID (UUID). Example: update ticket <uuid> status ACTIVE because testing");
        }

        // store context so next messages like "reason is ..." works
        lastTicketContext.put(userId, resolvedTicketId);

        final UUID ticketId = resolvedTicketId;

        // 2) Extract fields
        String newReason = extractReason(message);          // can be null
        String newStatus = extractTicketStatus(message);    // can be null

        // If neither status nor reason provided -> ask user
        if ((newReason == null || newReason.isBlank()) && (newStatus == null || newStatus.isBlank())) {
            return Flux.just("""
                What would you like to update?
                Examples:
                - update ticket %s status ACTIVE
                - update ticket %s because system issue
                """.formatted(ticketId, ticketId));
        }

        // 3) Update ticket
        return Mono.fromCallable(() -> leaveTicketRepository.findById(ticketId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(optional -> {

                    if (optional.isEmpty()) {
                        return Flux.just("❌ Ticket not found: " + ticketId);
                    }

                    Ticket ticket = optional.get();

                    // authorization check
                    if (!userId.equals(ticket.getEmployeeId())) {
                        return Flux.just("❌ You are not authorized to update this ticket.");
                    }

                    // approved ticket cannot be updated
                    if ("APPROVED".equalsIgnoreCase(ticket.getStatus())) {
                        return Flux.just("❌ Approved ticket cannot be updated.");
                    }

                    // Apply updates
                    boolean updated = false;

                    if (newReason != null && !newReason.isBlank()) {
                        String oldDesc = ticket.getDescription() == null ? "" : ticket.getDescription();
                        ticket.setDescription(oldDesc + (oldDesc.isBlank() ? "" : " | ") + "UpdatedReason=" + newReason.trim());
                        updated = true;
                    }

                    if (newStatus != null && !newStatus.isBlank()) {
                        ticket.setStatus(newStatus.trim().toUpperCase());
                        updated = true;
                    }

                    if (!updated) {
                        return Flux.just("⚠️ Nothing to update for ticket: " + ticketId);
                    }

                    ticket.setUpdatedAt(LocalDateTime.now());

                    return Mono.fromCallable(() -> leaveTicketRepository.save(ticket))
                            .subscribeOn(Schedulers.boundedElastic())
                            .thenMany(Flux.just(buildTicketUpdateSuccessMessage(ticketId, newStatus, newReason)));
                });
    }



    /* ============================================================
       TICKET DELETE
       ============================================================ */
    private Flux<String> handleTicketDelete(String message, String userId) {

        if (userId == null || userId.isBlank()) {
            return Flux.just("Please login to delete ticket.");
        }

        UUID ticketId = extractTicketId(message);
        if (ticketId == null) {
            return Flux.just("Please provide Ticket ID (UUID). Example: delete ticket <uuid>");
        }

        return Mono.fromCallable(() -> leaveTicketRepository.findById(ticketId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(optional -> {

                    if (optional.isEmpty()) {
                        return Flux.just("Ticket not found: " + ticketId);
                    }

                    Ticket ticket = optional.get();

                    if (!userId.equals(ticket.getEmployeeId())) {
                        return Flux.just("❌ You are not authorized to delete this ticket.");
                    }

                    if ("APPROVED".equalsIgnoreCase(ticket.getStatus())) {
                        return Flux.just("❌ Approved ticket cannot be deleted.");
                    }

                    return Mono.fromRunnable(() -> leaveTicketRepository.delete(ticket))
                            .subscribeOn(Schedulers.boundedElastic())
                            .thenMany(Flux.just("🗑 Ticket deleted successfully: " + ticketId));
                });
    }

    /* ============================================================
       TICKET STATUS
       ============================================================ */
    private Flux<String> handleTicketStatus1(String message, String userId) {

        if (userId == null || userId.isBlank()) {
            return Flux.just("Please login to check ticket status.");
        }

        UUID ticketId = extractTicketId(message);
        if (ticketId == null) {
            return Flux.just("Please provide Ticket ID (UUID). Example: status of ticket <uuid>");
        }

        return Mono.fromCallable(() -> leaveTicketRepository.findById(ticketId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(optional -> {

                    if (optional.isEmpty()) {
                        return Flux.just("Ticket not found: " + ticketId);
                    }

                    Ticket ticket = optional.get();

                    if (!userId.equals(ticket.getEmployeeId())) {
                        return Flux.just("❌ You are not authorized to view this ticket.");
                    }

                    return Flux.just("📌 Ticket " + ticketId + " Status: " + ticket.getStatus());
                });
    }

    private Flux<String> handleTicketStatus(String message, String userId) {

        if (userId == null || userId.isBlank()) {
            return Flux.just("Please login to check ticket status.");
        }

        UUID extractedTicketId = extractTicketId(message);

        UUID resolvedTicketId =
                extractedTicketId != null
                        ? extractedTicketId
                        : lastTicketContext.get(userId);

        if (resolvedTicketId == null) {
            return Flux.just("Please provide Ticket ID (UUID). Example: status of ticket <uuid>");
        }

        final UUID ticketId = resolvedTicketId; // ✅ FIX

        return Mono.fromCallable(() -> leaveTicketRepository.findById(ticketId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(optional -> {

                    if (optional.isEmpty()) {
                        return Flux.just("❌ Ticket not found: " + ticketId);
                    }

                    Ticket ticket = optional.get();

                    if (!userId.equals(ticket.getEmployeeId())) {
                        return Flux.just("❌ You are not authorized to view this ticket.");
                    }

                    // ✅ Store context only after validation
                    lastTicketContext.put(userId, ticketId);

                    return Flux.just(
                            "📄 Ticket Status\n" +
                                    "Ticket ID: " + ticket.getId() + "\n" +
                                    "Status: " + ticket.getStatus()
                    );
                });
    }

    /* ============================================================
       LEAVE FLOW HANDLER
       ============================================================ */
    private Flux<String> handleLeaveCreation1(String message, String userId) {

        if (userId == null || userId.isBlank()) {
            return Flux.just("Please login to apply leave.");
        }

        if (message == null || message.isBlank()) {
            return Flux.just("Please type your leave request.");
        }

        LeaveFlowState state = leaveFlow.computeIfAbsent(userId, k -> {
            LeaveFlowState s = new LeaveFlowState();
            s.setStep(LeaveStep.TYPE);
            return s;
        });

        String normalized = FuzzyTextUtil.normalize(message);

        // 1) Cancel always wins
        if (IntentDetector.isCancel(message)
                || normalized.contains("cancel")
                || FuzzyTextUtil.fuzzyTokenMatch(normalized, "cancel", 2)) {
            leaveFlow.remove(userId);
            return Flux.just("❌ Leave request cancelled.");
        }

        // ------------------------------------------------------------
        // GLOBAL EXTRACTION (works at ANY step)
        // ------------------------------------------------------------

        // Leave Type extraction from sentence
        if (state.getLeaveType() == null) {
            String extractedType = parseLeaveType(message);
            if (extractedType != null) {
                state.setLeaveType(extractedType);
            }
        }

        // Date extraction
        LocalDate possibleDate = parseDate(message);
        if (possibleDate != null) {
            if (state.getFromDate() == null) {
                state.setFromDate(possibleDate);
            }
            // if toDate not set, default to same day for now
            if (state.getFromDate() != null && state.getToDate() == null) {
                state.setToDate(state.getFromDate());
            }
        }

        // Duration extraction: "5 days" etc.
        Integer durationDays = IntentDetector.extractDurationDays(message);
        if (durationDays != null && durationDays > 1 && state.getFromDate() != null) {
            state.setToDate(state.getFromDate().plusDays(durationDays - 1));
        }

        // Reason extraction (only if user explicitly provides reason-like phrase)
        if (state.getReason() == null) {
            String possibleReason = extractReason(message);
            if (possibleReason != null && !possibleReason.isBlank()) {
                state.setReason(possibleReason.trim());
            }
        }

        // ------------------------------------------------------------
        // STEP RECOVERY (IMPORTANT)
        // ------------------------------------------------------------
        if (state.getStep() == null) {
            state.setStep(LeaveStep.TYPE);
        }

        // If leave type already detected but still at TYPE, move to date
        if (state.getStep() == LeaveStep.TYPE && state.getLeaveType() != null) {
            state.setStep(state.getFromDate() == null ? LeaveStep.FROM_DATE : LeaveStep.REASON);
        }

        // If user already has leaveType but stuck at LEAVE_TYPE, move forward
        if (state.getStep() == LeaveStep.LEAVE_TYPE && state.getLeaveType() != null) {
            state.setStep(state.getFromDate() == null ? LeaveStep.FROM_DATE : LeaveStep.REASON);
        }

        // ------------------------------------------------------------
        // TYPE (start)
        // ------------------------------------------------------------
        if (state.getStep() == LeaveStep.TYPE) {

            // If leave type missing -> show menu
            if (state.getLeaveType() == null) {
                state.setStep(LeaveStep.LEAVE_TYPE);
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

            // If leaveType already exists, go to date
            state.setStep(LeaveStep.FROM_DATE);
            return Flux.just("Enter From Date (yyyy-MM-dd) OR type 'today' / 'tomorrow'");
        }

        // ------------------------------------------------------------
        // LEAVE_TYPE
        // ------------------------------------------------------------
        if (state.getStep() == LeaveStep.LEAVE_TYPE) {

            if (state.getLeaveType() == null) {
                String type = parseLeaveType(message);
                if (type == null) {
                    return Flux.just("Invalid leave type. Please reply with 1-8 or leave name.");
                }
                state.setLeaveType(type);
            }

            state.setStep(LeaveStep.FROM_DATE);
            return Flux.just("Enter From Date (yyyy-MM-dd) OR type 'today' / 'tomorrow'");
        }

        // ------------------------------------------------------------
        // FROM_DATE
        // ------------------------------------------------------------
        if (state.getStep() == LeaveStep.FROM_DATE) {

            if (state.getFromDate() == null) {
                LocalDate from = parseDate(message);
                if (from == null) {
                    return Flux.just("Invalid date. Please enter yyyy-MM-dd or 'today' / 'tomorrow'");
                }
                state.setFromDate(from);

                // default to one day
                if (state.getToDate() == null) {
                    state.setToDate(from);
                }
            }

            state.setStep(LeaveStep.TO_DATE);
            return Flux.just("Enter To Date (yyyy-MM-dd) OR type 'same' OR duration like '5 days'");
        }

        // ------------------------------------------------------------
        // TO_DATE
        // ------------------------------------------------------------
        if (state.getStep() == LeaveStep.TO_DATE) {

            if (state.getToDate() == null) {

                Integer d = IntentDetector.extractDurationDays(message);

                LocalDate to;
                if (d != null && d > 1) {
                    to = state.getFromDate().plusDays(d - 1);
                } else if (normalized.equals("same") || FuzzyTextUtil.fuzzyTokenMatch(normalized, "same", 1)) {
                    to = state.getFromDate();
                } else {
                    to = parseDate(message);
                }

                if (to == null) {
                    return Flux.just("Invalid date. Please enter yyyy-MM-dd or type 'same' or '5 days'.");
                }

                state.setToDate(to);
            }

            state.setStep(LeaveStep.REASON);
            return Flux.just("Enter reason for leave:");
        }

        // ------------------------------------------------------------
        // REASON
        // ------------------------------------------------------------
        if (state.getStep() == LeaveStep.REASON) {

            if (state.getReason() == null) {
                String reason = message.trim();
                if (reason.isBlank()) {
                    return Flux.just("Please enter a valid reason.");
                }
                state.setReason(reason);
            }

            state.setStep(LeaveStep.CONFIRM);

            return Flux.just("""
                Please confirm leave request:
                Leave Type: %s
                From: %s
                To: %s
                Reason: %s

                Reply: CONFIRM to submit OR CANCEL
                """.formatted(state.getLeaveType(), state.getFromDate(), state.getToDate(), state.getReason()));
        }

        // ------------------------------------------------------------
        // CONFIRM
        // ------------------------------------------------------------
        if (state.getStep() == LeaveStep.CONFIRM) {

            if (!isConfirmMessage(message)) {
                return Flux.just("Please reply CONFIRM to submit or CANCEL to stop.");
            }

            return createLeaveTicketAndReset(state, userId);
        }

        // ------------------------------------------------------------
        // LAST RESCUE (never break)
        // ------------------------------------------------------------
        if (state.getLeaveType() == null) {
            state.setStep(LeaveStep.LEAVE_TYPE);
            return Flux.just("Please select leave type (Need Based / Planned / etc).");
        }
        if (state.getFromDate() == null) {
            state.setStep(LeaveStep.FROM_DATE);
            return Flux.just("Enter From Date (yyyy-MM-dd) OR today/tomorrow");
        }
        if (state.getToDate() == null) {
            state.setStep(LeaveStep.TO_DATE);
            return Flux.just("Enter To Date (yyyy-MM-dd) OR same / '5 days'");
        }
        if (state.getReason() == null) {
            state.setStep(LeaveStep.REASON);
            return Flux.just("Enter reason for leave:");
        }

        state.setStep(LeaveStep.CONFIRM);
        return Flux.just("Please reply CONFIRM to submit or CANCEL to stop.");
    }
    private Flux<String> handleLeaveCreation(String message, String userId) {

        if (userId == null || userId.isBlank()) {
            return Flux.just("Please login to apply leave.");
        }

        if (message == null || message.isBlank()) {
            return Flux.just("Please type your leave request.");
        }

        LeaveFlowState state = leaveFlow.computeIfAbsent(userId, k -> {
            LeaveFlowState s = new LeaveFlowState();
            s.setStep(LeaveStep.TYPE);
            return s;
        });

        String normalized = FuzzyTextUtil.normalize(message);

        // ------------------------------------------------------------
        // 0️⃣ CANCEL always wins
        // ------------------------------------------------------------
        if (IntentDetector.isCancel(message)
                || normalized.contains("cancel")
                || FuzzyTextUtil.fuzzyTokenMatch(normalized, "cancel", 2)) {
            leaveFlow.remove(userId);
            return Flux.just("❌ Leave request cancelled. How else can I help?");
        }

        // ------------------------------------------------------------
        // 1️⃣ GLOBAL SLOT EXTRACTION (order-free)
        // ------------------------------------------------------------

        // Leave Type
        if (state.getLeaveType() == null) {
            String extractedType = parseLeaveType(message);
            if (extractedType != null) {
                state.setLeaveType(extractedType);
            }
        }

        // From date
        LocalDate parsedDate = parseDate(message);
        if (parsedDate != null && state.getFromDate() == null) {
            state.setFromDate(parsedDate);
            if (state.getToDate() == null) {
                state.setToDate(parsedDate); // default single day
            }
        }

        // Duration → To date
        Integer durationDays = IntentDetector.extractDurationDays(message);
        if (durationDays != null && durationDays > 1 && state.getFromDate() != null) {
            state.setToDate(state.getFromDate().plusDays(durationDays - 1));
        }

        // Reason
        if (state.getReason() == null) {
            String possibleReason = extractReason(message);
            if (possibleReason != null && !possibleReason.isBlank()) {
                state.setReason(possibleReason.trim());
            }
        }

        // ------------------------------------------------------------
        // 2️⃣ ⭐ SLOT-FIRST COMPLETENESS CHECK (KEY IMPROVEMENT)
        // ------------------------------------------------------------
        if (isLeaveComplete(state)) {
            state.setStep(LeaveStep.CONFIRM);
            return Flux.just(buildLeaveConfirmation(state));
        }

        // ------------------------------------------------------------
        // 3️⃣ STEP RECOVERY (only for missing info)
        // ------------------------------------------------------------
        if (state.getLeaveType() == null) {
            state.setStep(LeaveStep.LEAVE_TYPE);
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

        if (state.getFromDate() == null) {
            state.setStep(LeaveStep.FROM_DATE);
            return Flux.just("From which date do you want the leave? (today / tomorrow / yyyy-MM-dd)");
        }

        if (state.getToDate() == null) {
            state.setStep(LeaveStep.TO_DATE);
            return Flux.just("Till which date? (same / yyyy-MM-dd / '5 days')");
        }

        if (state.getReason() == null) {
            state.setStep(LeaveStep.REASON);
            return Flux.just("Please tell me the reason for leave.");
        }

        // ------------------------------------------------------------
        // 4️⃣ CONFIRM STEP
        // ------------------------------------------------------------
        if (state.getStep() == LeaveStep.CONFIRM) {

            if (!isConfirmMessage(message)) {
                return Flux.just("Please reply CONFIRM to submit or CANCEL to stop.");
            }

            return createLeaveTicketAndReset(state, userId);
        }

        // ------------------------------------------------------------
        // 5️⃣ LAST SAFE FALLBACK (never break conversation)
        // ------------------------------------------------------------
        return Flux.just("Please provide remaining leave details so I can submit your request.");
    }

    private String buildLeaveConfirmation(LeaveFlowState s) {
        return """
    👍 I got everything:
    • Leave Type: %s
    • From: %s
    • To: %s
    • Reason: %s

    Please confirm by typing CONFIRM or CANCEL.
    """.formatted(
                s.getLeaveType(),
                s.getFromDate(),
                s.getToDate(),
                s.getReason()
        );
    }

    /* ============================================================
       Ticket creation helper
       ============================================================ */
    private Flux<String> createLeaveTicketAndReset(LeaveFlowState state, String userId) {

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
        ticket.setUpdatedAt(LocalDateTime.now());

        //leaveTicketRepository.save(ticket);
        leaveFlow.remove(userId);
        return Mono.fromCallable(() -> leaveTicketRepository.save(ticket))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(saved ->
                        Flux.just("✅ Leave ticket created successfully for Ticket ID: " + saved.getId()
                                        +" Leave Type: "+state.getLeaveType()
                                        +" From: "+state.getFromDate()
                                        +" To: "+ state.getToDate()
                        ));

    }

    /* ============================================================
       Extract reason from sentence
       ============================================================ */
    private String extractReason(String message) {
        if (message == null) return null;

        String m = message.toLowerCase();

        if (m.contains("reason")) {
            int idx = m.indexOf("reason");
            return message.substring(idx + "reason".length()).replace(":", "").trim();
        }
        if (m.contains("because")) {
            int idx = m.indexOf("because");
            return message.substring(idx + "because".length()).trim();
        }
        if (m.contains("due to")) {
            int idx = m.indexOf("due to");
            return message.substring(idx + "due to".length()).trim();
        }
        return null;
    }

    /* ============================================================
       Confirm detection with fuzzy
       ============================================================ */
    private boolean isConfirmMessage(String message) {
        if (message == null) return false;

        String m = FuzzyTextUtil.normalize(message);

        return m.equals("confirm")
                || m.contains("confirm")
                || m.contains("confirmed")
                || m.contains("confirming")
                || FuzzyTextUtil.fuzzyTokenMatch(m, "confirm", 2);
    }

    /* ============================================================
       Leave type parsing (with fuzzy + synonyms)
       ============================================================ */
    private String parseLeaveType(String message) {
        if (message == null) return null;

        String m = FuzzyTextUtil.normalize(message);

        // 1) number mapping (highest priority if user selects from menu)
        if (m.equals("1")) return "Need Based Leave";
        if (m.equals("2")) return "Planned Leave";
        if (m.equals("3")) return "Paternity Leave";
        if (m.equals("4")) return "Maternity Leave";
        if (m.equals("5")) return "Project Leave";
        if (m.equals("6")) return "Leave Without Pay";
        if (m.equals("7")) return "Election Leave";
        if (m.equals("8")) return "Birthday Leave";

        // 2) strict keyword match (before fuzzy!)
        if (m.contains("paternity") || m.contains("father leave") || m.contains("dad leave")) {
            return "Paternity Leave";
        }
        if (m.contains("maternity") || m.contains("mother leave")) {
            return "Maternity Leave";
        }
        if (m.contains("planned") || m.contains("plan leave")) {
            return "Planned Leave";
        }
        if (m.contains("need based") || m.contains("needbase") || m.contains("nbl")) {
            return "Need Based Leave";
        }
        if (m.contains("project")) {
            return "Project Leave";
        }
        if (m.contains("without pay") || m.contains("lwp")) {
            return "Leave Without Pay";
        }
        if (m.contains("election")) {
            return "Election Leave";
        }
        if (m.contains("birthday")) {
            return "Birthday Leave";
        }

        // 3) fuzzy match ONLY if above didn't match
        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("paternity", "father leave"), 1))
            return "Paternity Leave";

        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("maternity", "mother leave"), 1))
            return "Maternity Leave";

        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("planned", "plan leave", "pl"), 1))
            return "Planned Leave";

        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("need based", "need base", "needbased", "nbl"), 1))
            return "Need Based Leave";

        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("project"), 1))
            return "Project Leave";

        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("without pay", "lwp"), 1))
            return "Leave Without Pay";

        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("election"), 1))
            return "Election Leave";

        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("birthday"), 1))
            return "Birthday Leave";

        return null;
    }


    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                    // yyyy-MM-dd
            DateTimeFormatter.ofPattern("yyyy-M-d"),             // yyyy-M-d
            DateTimeFormatter.ofPattern("d/M/yyyy"),             // d/M/yyyy
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),           // dd/MM/yyyy
            DateTimeFormatter.ofPattern("M-d-yyyy"),             // M-d-yyyy
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),           // MM-dd-yyyy
            DateTimeFormatter.ofPattern("d-M-yyyy"),             // d-M-yyyy
            DateTimeFormatter.ofPattern("dd-MM-yyyy")            // dd-MM-yyyy
    );

    private LocalDate parseDate(String message) {
        if (message == null || message.isBlank()) return null;

        String m = FuzzyTextUtil.normalize(message);

        // 1) today / tomorrow (token based)
        if (FuzzyTextUtil.hasToken(m, "today") || FuzzyTextUtil.fuzzyTokenMatch(m, "today", 1)) {
            return LocalDate.now();
        }

        if (FuzzyTextUtil.hasToken(m, "tomorrow")
                || FuzzyTextUtil.fuzzyTokenMatch(m, "tomorrow", 2)
                || FuzzyTextUtil.fuzzyTokenMatch(m, "tomorow", 2)
                || FuzzyTextUtil.hasToken(m, "tmrw")) {
            return LocalDate.now().plusDays(1);
        }

        // 2) next monday / next tuesday ...
        LocalDate nextDow = parseNextDayOfWeek(m);
        if (nextDow != null) return nextDow;

        // 3) extract date-like substring using regex (handles commas/colons etc.)
        // examples: "2026-02-01," "date:2026-2-1" "1/2/2026"
        Pattern p = Pattern.compile("(\\d{4}-\\d{1,2}-\\d{1,2})|(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})");
        Matcher matcher = p.matcher(m);

        while (matcher.find()) {
            String raw = matcher.group().trim();

            for (DateTimeFormatter f : DATE_FORMATS) {
                try {
                    return LocalDate.parse(raw, f);
                } catch (DateTimeParseException ignored) {
                }
            }
        }

        // 4) month name date: "15 feb" / "15 feb 2026"
        LocalDate monthNameDate = parseMonthNameDate(m);
        if (monthNameDate != null) return monthNameDate;

        return null;
    }



    private LocalDate parseNextDayOfWeek(String normalizedMessage) {

        if (normalizedMessage == null) return null;

        // Must contain "next"
        if (!normalizedMessage.contains("next")) return null;

        java.time.DayOfWeek dow = null;

        if (normalizedMessage.contains("monday")) dow = java.time.DayOfWeek.MONDAY;
        else if (normalizedMessage.contains("tuesday")) dow = java.time.DayOfWeek.TUESDAY;
        else if (normalizedMessage.contains("wednesday")) dow = java.time.DayOfWeek.WEDNESDAY;
        else if (normalizedMessage.contains("thursday")) dow = java.time.DayOfWeek.THURSDAY;
        else if (normalizedMessage.contains("friday")) dow = java.time.DayOfWeek.FRIDAY;
        else if (normalizedMessage.contains("saturday")) dow = java.time.DayOfWeek.SATURDAY;
        else if (normalizedMessage.contains("sunday")) dow = java.time.DayOfWeek.SUNDAY;

        if (dow == null) return null;

        LocalDate today = LocalDate.now();

        // calculate next occurrence
        int todayValue = today.getDayOfWeek().getValue(); // 1-7
        int targetValue = dow.getValue();

        int diff = targetValue - todayValue;
        if (diff <= 0) diff += 7;

        // because user said "next monday", ensure it always means future week
        diff += 7;

        return today.plusDays(diff);
    }
    private LocalDate parseMonthNameDate(String normalizedMessage) {
        if (normalizedMessage == null) return null;

        // Examples:
        // "15 feb"
        // "15 feb 2026"
        // "leave on 15 feb because sick"
        var pattern = java.util.regex.Pattern.compile(
                "\\b(\\d{1,2})\\s+(jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)\\b(\\s+(\\d{4}))?"
        );

        var matcher = pattern.matcher(normalizedMessage);

        if (!matcher.find()) return null;

        int day = Integer.parseInt(matcher.group(1));
        String monthText = matcher.group(2);
        String yearText = matcher.group(4);

        int year = (yearText != null) ? Integer.parseInt(yearText) : LocalDate.now().getYear();

        int month = switch (monthText) {
            case "jan", "january" -> 1;
            case "feb", "february" -> 2;
            case "mar", "march" -> 3;
            case "apr", "april" -> 4;
            case "may" -> 5;
            case "jun", "june" -> 6;
            case "jul", "july" -> 7;
            case "aug", "august" -> 8;
            case "sep", "sept", "september" -> 9;
            case "oct", "october" -> 10;
            case "nov", "november" -> 11;
            case "dec", "december" -> 12;
            default -> -1;
        };

        if (month == -1) return null;

        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }


    private UUID extractTicketId(String message) {
        if (message == null) return null;

        Pattern pattern = Pattern.compile(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
        );

        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return UUID.fromString(matcher.group());
        }
        return null;
    }


    private Flux<String> streamLikeLlm(String response) {
        return Flux.fromIterable(
                response.chars()
                        .mapToObj(c -> String.valueOf((char) c))
                        .toList()
        ).delayElements(Duration.ofMillis(10));
    }

    private String extractTicketStatus(String message) {
        if (message == null) return null;

        String m = message.toUpperCase();

        if (m.contains("ACTIVE")) return "ACTIVE";
        if (m.contains("INACTIVE")) return "INACTIVE";
        if (m.contains("CLOSED")) return "CLOSED";
        if (m.contains("OPEN")) return "OPEN";
        if (m.contains("CREATED")) return "CREATED";

        return null;
    }

    private String buildTicketUpdateSuccessMessage(UUID ticketId, String newStatus, String newReason) {

        StringBuilder sb = new StringBuilder("✅ Ticket updated successfully: " + ticketId);

        if (newStatus != null && !newStatus.isBlank()) {
            sb.append("\nStatus: ").append(newStatus.toUpperCase());
        }

        if (newReason != null && !newReason.isBlank()) {
            sb.append("\nReason: ").append(newReason.trim());
        }

        return sb.toString();
    }

    private Flux<String> handleGeneralChatWithCache(String message) {

        String normalizedPrompt = PromptNormalizer.normalize(message);

        // 1️⃣ Exact cache hit
        if (llmCache.contains(normalizedPrompt)) {
            return streamLikeLlm(llmCache.get(normalizedPrompt));
        }

        // 2️⃣ Semantic cache hit
        if (!semanticCache.isEmpty()) {

            List<Float> queryEmbedding = embeddingService.embed(normalizedPrompt);

            for (SemanticCacheEntry entry : semanticCache.getAll()) {

                double similarity = CosineSimilarityUtil.similarity(
                        queryEmbedding,
                        entry.getEmbedding()
                );

                if (similarity >= 0.88) { // slightly stricter for chat
                    return streamLikeLlm(entry.getResponse());
                }
            }
        }

        // 3️⃣ Cache miss → streaming LLM
        return aiChatService.askStream(message)
                .collectList()
                .map(parts -> String.join("", parts))
                .doOnNext(response -> {

                    if (response != null && !response.isBlank()) {

                        llmCache.put(normalizedPrompt, response);

                        List<Float> embedding = embeddingService.embed(normalizedPrompt);
                        semanticCache.put(new SemanticCacheEntry(embedding, response));
                    }
                })
                .flatMapMany(Flux::just);
    }

    private Flux<String> answerPolicyWithCache(String message) {

        String normalizedPrompt = PromptNormalizer.normalize(message);

        // 1️⃣ Exact cache hit (fastest)
        if (llmCache.contains(normalizedPrompt)) {
            return streamLikeLlm(llmCache.get(normalizedPrompt));
        }

        // 2️⃣ Semantic cache hit (similar meaning)
        if (!semanticCache.isEmpty()) {

            List<Float> queryEmbedding = embeddingService.embed(normalizedPrompt);

            for (SemanticCacheEntry entry : semanticCache.getAll()) {

                double similarity = CosineSimilarityUtil.similarity(
                        queryEmbedding,
                        entry.getEmbedding()
                );

                // tune threshold if needed
                if (similarity >= 0.85) {
                    return streamLikeLlm(entry.getResponse());
                }
            }
        }

        // 3️⃣ Cache miss → synchronous LLM call (FASTER than streaming)
        String response = aiChatService.askSync(message);

        // 4️⃣ Save in caches
        if (response != null && !response.isBlank()) {

            llmCache.put(normalizedPrompt, response);

            List<Float> embedding = embeddingService.embed(normalizedPrompt);
            semanticCache.put(new SemanticCacheEntry(embedding, response));
        }

        return Flux.just(response);
    }

    private boolean isLeaveComplete(LeaveFlowState s) {
        return s.getLeaveType() != null
                && s.getFromDate() != null
                && s.getToDate() != null
                && s.getReason() != null;
    }

}
