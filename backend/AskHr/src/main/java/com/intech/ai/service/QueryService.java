package com.intech.ai.service;

import com.intech.ai.caches.ConversationStateStore;
import com.intech.ai.caches.SemanticCacheEntry;
import com.intech.ai.caches.SemanticResponseCache;
import com.intech.ai.enums.LeaveStep;
import com.intech.ai.enums.PendingIntent;
import com.intech.ai.modal.LeaveFlowState;
import com.intech.ai.modal.Ticket;
import com.intech.ai.repository.TicketRepository;
import com.intech.ai.caches.LlmResponseCache;
import com.intech.ai.utility.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j;
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

@Log
@Service
@RequiredArgsConstructor
public class QueryService {

    private final TicketRepository leaveTicketRepository;
    private final AiChatService aiChatService; // your LLM service
    private final LlmResponseCache llmCache;
    private final SemanticResponseCache semanticCache;
    private final EmbeddingService embeddingService;
    private final PolicyService policyService;
    private final ConversationStateStore conversationStateStore;
    // store conversation state per user
    private final Map<String, LeaveFlowState> leaveFlow = new ConcurrentHashMap<>();
    private final Map<String, UUID> lastTicketContext = new ConcurrentHashMap<>();

    /* ============================================================
       MAIN ENTRY
       ============================================================ */
    public Flux<String> handleUserQuery(String message, String userId) {

        // ---------------------------------------------------------
        // 0️⃣ Basic Validation
        // ---------------------------------------------------------
        if (message == null || message.isBlank()) {
            return Flux.just("Please type your query.");
        }

        message = message.trim();

        if (userId != null) {
            userId = userId.trim().toLowerCase();
        }

        // ---------------------------------------------------------
        // 1️⃣ Pending Ticket ID Flow
        // ---------------------------------------------------------
        if (userId != null &&
                conversationStateStore.getPendingIntent(userId) == PendingIntent.AWAITING_TICKET_ID) {

            if (!TicketUtil.containsTicketId(message)) {
                return Flux.just("Please provide a valid ticket ID (UUID).");
            }

            conversationStateStore.clear(userId);
            return handleTicketStatus(TicketUtil.extractTicketId(message), userId);
        }

        // ---------------------------------------------------------
        // 2️⃣ Active Leave Flow (NEVER re-check intent)
        // ---------------------------------------------------------
        if (userId != null && leaveFlow.containsKey(userId)) {
            return handleLeaveCreation(message, userId);
        }

        // ---------------------------------------------------------
        // 3️⃣ Fresh Leave Request
        // ---------------------------------------------------------
        if (IntentDetector.isLeaveCreationRequest(message)) {

            // Only start fresh flow if no active flow
            if (!leaveFlow.containsKey(userId)) {
                return handleLeaveCreation(message, userId);
            }

            // If already in flow → continue
            return handleLeaveCreation(message, userId);
        }

        // ---------------------------------------------------------
        // 4️⃣ Ticket Status
        // ---------------------------------------------------------
        if (IntentDetector.isTicketStatusRequest(message)) {

            if (!TicketUtil.containsTicketId(message)) {
                conversationStateStore.setPendingIntent(userId, PendingIntent.AWAITING_TICKET_ID);
                return Flux.just("Please provide your ticket ID.");
            }

            return handleTicketStatus(TicketUtil.extractTicketId(message), userId);
        }

        // ---------------------------------------------------------
        // 5️⃣ Ticket Update
        // ---------------------------------------------------------
        if (IntentDetector.isTicketUpdateRequest(message)) {
            return handleTicketUpdate(message, userId);
        }

        // ---------------------------------------------------------
        // 6️⃣ Ticket Delete
        // ---------------------------------------------------------
        if (IntentDetector.isTicketDeleteRequest(message)) {
            return handleTicketDelete(message, userId);
        }

        // ---------------------------------------------------------
        // 7️⃣ Policy Query (RAG)
        // ---------------------------------------------------------
        if (IntentDetector.isLeavePolicyQuery(message)) {
            final String finalMessage = message;
            return Mono.fromCallable(() -> policyService.answerPolicy(finalMessage))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapMany(this::streamLikeLlm);
        }

        // ---------------------------------------------------------
        // 8️⃣ General Query → LLM + Cache
        // ---------------------------------------------------------

        if (leaveFlow.containsKey(userId)) {
            return aiChatService.askStream(message);
        }
        String normalizedPrompt = PromptNormalizer.normalize(message);
        // ---------- Exact Cache ----------
//        if (llmCache.contains(normalizedPrompt)) {
//            return streamLikeLlm(llmCache.get(normalizedPrompt));
//        }
//
//        // ---------- Semantic Cache (non-blocking) ----------
//        if (!semanticCache.isEmpty()) {
//
//            try {
//                List<Float> queryEmbedding = embeddingService.embed(normalizedPrompt);
//
//                for (SemanticCacheEntry entry : semanticCache.getAll()) {
//
//                    double similarity = CosineSimilarityUtil.similarity(
//                            queryEmbedding,
//                            entry.getEmbedding()
//                    );
//
//                    if (similarity >= 0.85) {
//                        return streamLikeLlm(entry.getResponse());
//                    }
//                }
//            } catch (Exception ignored) {}
//        }

        // ---------- LLM Streaming ----------
        return aiChatService.askStream(message)

                .doOnNext(chunk ->
                        log.info("LLM chunk: {}"+ chunk)
                )

                // collect AFTER stream finishes
                .collectList()

                .flatMapMany(parts -> {

                    String fullResponse = String.join("", parts);

//                    if (!fullResponse.isBlank()) {
//
//                        // 1️⃣ Exact cache immediately
//                        llmCache.put(normalizedPrompt, fullResponse);
//
//                        // 2️⃣ Background embedding (non-blocking)
//                        Mono.fromRunnable(() -> {
//                                    List<Float> embedding =
//                                            embeddingService.embed(normalizedPrompt);
//
//                                    semanticCache.put(
//                                            new SemanticCacheEntry(embedding, fullResponse)
//                                    );
//                                })
//                                .subscribeOn(Schedulers.boundedElastic())
//                                .subscribe();
//                    }

                    // stream response back to client
                    return streamLikeLlm(fullResponse);
                });
    }

    /* ============================================================
       TICKET UPDATE
       ============================================================ */
    private Flux<String> handleTicketUpdate(String message, String userId) {

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

        message = message.trim();
        LeaveFlowState parsed = tryParseFullLeaveRequest(message);
        if (parsed != null) {

            leaveFlow.put(userId, parsed);
            parsed.setStep(LeaveStep.CONFIRM);

            return Flux.just("""
        Please confirm leave request:

        Leave Type: %s
        From: %s
        To: %s
        Reason: %s

        Reply: CONFIRM to submit OR CANCEL
        """.formatted(
                    parsed.getLeaveType(),
                    parsed.getFromDate(),
                    parsed.getToDate(),
                    parsed.getReason()
            ));
        }
        String normalized = FuzzyTextUtil.normalize(message);

        // 🔒 SAFE STATE FETCH (no computeIfAbsent)
        LeaveFlowState state = leaveFlow.get(userId);

        if (state == null) {
            state = new LeaveFlowState();
            state.setStep(LeaveStep.LEAVE_TYPE);
            leaveFlow.put(userId, state);

            return Flux.just("""
        Please select Leave Type:
        1. Need Based Leave
        2. Planned Leave
        3. Paternity Leave
        4. Maternity Leave
        5. Project Leave
        6. Leave Without Pay
        7. Election Leave
        8. Birthday Leave
        Reply with number or leave type name.
        """);
        }

        // 🔥 CANCEL always wins
        if (IntentDetector.isCancel(message)
                || normalized.contains("cancel")
                || FuzzyTextUtil.fuzzyTokenMatch(normalized, "cancel", 2)) {

            leaveFlow.remove(userId);
            return Flux.just("❌ Leave request cancelled.");
        }

        LeaveStep currentStep = state.getStep();

        switch (currentStep) {

            // =====================================================
            // STEP 1 → TYPE
            // =====================================================
            /*case TYPE -> {

                state.setStep(LeaveStep.LEAVE_TYPE);

                return Flux.just("""
                    Please select Leave Type:
                    1. Need Based Leave
                    2. Planned Leave
                    3. Paternity Leave
                    4. Maternity Leave
                    5. Project Leave
                    6. Leave Without Pay
                    7. Election Leave
                    8. Birthday Leave
                    Reply with number or leave type name.
                    """);
            }*/
            case TYPE -> {
                state.setStep(LeaveStep.LEAVE_TYPE);
                return handleLeaveCreation("", userId);
            }
            // =====================================================
            // STEP 2 → LEAVE TYPE SELECTION
            // =====================================================
            case LEAVE_TYPE -> {

                String type = parseLeaveType(message);

                if (type == null) {
                    return Flux.just("Invalid leave type. Please reply with 1-8 or leave name.");
                }

                state.setLeaveType(type);
                state.setStep(LeaveStep.FROM_DATE);

                return Flux.just("Enter From Date (yyyy-MM-dd) OR type 'today' / 'tomorrow'");
            }

            // =====================================================
            // STEP 3 → FROM DATE
            // =====================================================
            case FROM_DATE -> {

                LocalDate from = parseDate(message);

                if (from == null) {
                    return Flux.just("Invalid date. Please enter yyyy-MM-dd or 'today' / 'tomorrow'");
                }

                state.setFromDate(from);
                state.setStep(LeaveStep.TO_DATE);

                return Flux.just("Enter To Date (yyyy-MM-dd) OR type 'same' OR duration like '5 days'");
            }

            // =====================================================
            // STEP 4 → TO DATE
            // =====================================================
            case TO_DATE -> {

                LocalDate to = null;

                Integer duration = IntentDetector.extractDurationDays(message);

                if (duration != null && duration > 1) {
                    to = state.getFromDate().plusDays(duration - 1);
                } else if (normalized.equals("same")) {
                    to = state.getFromDate();
                } else {
                    to = parseDate(message);
                }

                if (to == null) {
                    return Flux.just("Invalid date. Please enter yyyy-MM-dd or type 'same' or '5 days'.");
                }

                if (to.isBefore(state.getFromDate())) {
                    return Flux.just("To date cannot be before From date.");
                }

                state.setToDate(to);
                state.setStep(LeaveStep.REASON);

                return Flux.just("Enter reason for leave:");
            }

            // =====================================================
            // STEP 5 → REASON
            // =====================================================
            case REASON -> {

                if (message.isBlank()) {
                    return Flux.just("Please enter a valid reason.");
                }

                state.setReason(message);
                state.setStep(LeaveStep.CONFIRM);

                return Flux.just("""
                    Please confirm leave request:

                    Leave Type: %s
                    From: %s
                    To: %s
                    Reason: %s

                    Reply: CONFIRM to submit OR CANCEL
                    """.formatted(
                        state.getLeaveType(),
                        state.getFromDate(),
                        state.getToDate(),
                        state.getReason()
                ));
            }

            // =====================================================
            // STEP 6 → CONFIRM
            // =====================================================
            case CONFIRM -> {

                if (!isConfirmMessage(message)) {
                    return Flux.just("Please reply CONFIRM to submit or CANCEL to stop.");
                }

                // 🔒 Create ticket and reset safely
                state.setStep(LeaveStep.COMPLETED);
                return createLeaveTicketAndReset(state, userId)
                        .doFinally(signal -> leaveFlow.remove(userId));
            }

            default -> {
                leaveFlow.remove(userId);
                return Flux.just("Something went wrong. Let's start again.");
            }
        }
    }

    private LeaveFlowState tryParseFullLeaveRequest(String message) {

        String normalized = message.toLowerCase();

        // Must contain action verb
        if (!normalized.contains("create")
                && !normalized.contains("apply")
                && !normalized.contains("request")) {
            return null;
        }

        String type = parseLeaveType(message);
        LocalDate from = parseDate(message);
        Integer duration = IntentDetector.extractDurationDays(message);
        String reason = extractReason(message);

        if (type == null || from == null) {
            return null;
        }

        LocalDate to = from;

        if (duration != null && duration > 1) {
            to = from.plusDays(duration - 1);
        }

        if (reason == null || reason.isBlank()) {
            return null;
        }

        LeaveFlowState state = new LeaveFlowState();
        state.setLeaveType(type);
        state.setFromDate(from);
        state.setToDate(to);
        state.setReason(reason);

        return state;
    }



    /* ============================================================
       Ticket creation helper
       ============================================================ */
    private Flux<String> createLeaveTicketAndReset1(LeaveFlowState state, String userId) {

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

        leaveFlow.remove(userId);
        conversationStateStore.clear(userId);
        return Mono.fromCallable(() -> leaveTicketRepository.save(ticket))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(saved ->
                        Flux.just("✅ Leave ticket created successfully.\n"
                                + "Ticket ID: " + saved.getId()));
    }

    private Flux<String> createLeaveTicketAndReset(LeaveFlowState state, String userId) {

        return Mono.fromCallable(() -> {

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

                    return leaveTicketRepository.save(ticket);
                })
                .subscribeOn(Schedulers.boundedElastic())

                .flatMapMany(savedTicket -> {

                    // ✅ Reset state ONLY after successful save
                    leaveFlow.remove(userId);
                    conversationStateStore.clear(userId);

                    return Flux.just(
                            "✅ Leave ticket created successfully.\n" +
                                    "Ticket ID: " + savedTicket.getId()
                    );
                })

                .onErrorResume(ex -> {

                    log.info("Leave ticket creation failed for user {}"+
                            userId+" : " +ex);

                    return Flux.just(
                            "❌ Failed to create leave ticket. Please try again."
                    );
                });
    }


    /* ============================================================
       Extract reason from sentence
       ============================================================ */
    private String extractReason1(String message) {
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
    private String extractReason(String message) {

        if (message == null || message.isBlank()) {
            return null;
        }

        String original = message.trim();
        String lower = original.toLowerCase();

        // ----------------------------------------------------
        // 1️⃣ Explicit reason keywords
        // ----------------------------------------------------
        String[] triggers = {
                "reason",
                "because",
                "due to",
                "as i have",
                "as i am",
                "since i have",
                "since i am",
                "for"
        };

        for (String trigger : triggers) {

            if (lower.contains(trigger)) {

                int idx = lower.indexOf(trigger);
                String extracted = original.substring(idx + trigger.length())
                        .replace(":", "")
                        .trim();

                if (!extracted.isBlank()) {
                    return extracted;
                }
            }
        }

        // ----------------------------------------------------
        // 2️⃣ Health-related fallback detection
        // ----------------------------------------------------
        String[] healthKeywords = {
                "fever",
                "cold",
                "cough",
                "headache",
                "sick",
                "ill",
                "not feeling well",
                "medical",
                "hospital",
                "injury"
        };

        for (String keyword : healthKeywords) {
            if (lower.contains(keyword)) {
                return original.trim();
            }
        }

        // ----------------------------------------------------
        // 3️⃣ If sentence ends with meaningful phrase
        // Example:
        // "create leave tomorrow suffering from fever"
        // ----------------------------------------------------
        if (lower.matches(".*(suffering from|having|with).*")) {
            return original.trim();
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


    private Flux<String> streamLikeLlm(String fullResponse) {

        if (fullResponse == null || fullResponse.isBlank()) {
            return Flux.empty();
        }

        // split into small chunks (words feel more natural than chars)
        String[] tokens = fullResponse.split(" ");

        return Flux.fromArray(tokens)
                .delayElements(Duration.ofMillis(30 + new Random().nextInt(40)))// typing effect
                .map(token -> token + " ");
    }


}
