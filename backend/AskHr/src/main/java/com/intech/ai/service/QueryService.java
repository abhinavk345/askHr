package com.intech.ai.service;

import com.intech.ai.enums.LeaveStep;
import com.intech.ai.modal.LeaveFlowState;
import com.intech.ai.modal.Ticket;
import com.intech.ai.repository.TicketRepository;
import com.intech.ai.utility.FuzzyTextUtil;
import com.intech.ai.utility.IntentDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class QueryService {

    private final TicketRepository leaveTicketRepository;
    private final AiChatService aiChatService; // your LLM service

    // store conversation state per user
    private final Map<String, LeaveFlowState> leaveFlow = new ConcurrentHashMap<>();

    /* ============================================================
       MAIN ENTRY
       ============================================================ */
    public Flux<String> handleUserQuery(String message, String userId) {

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

        // 1) If leave flow already started -> continue
        if (userId != null && leaveFlow.containsKey(userId)) {

            // 1️⃣ Cancel always wins
            if (IntentDetector.isCancel(message)) {
                leaveFlow.remove(userId);
                return Flux.just("❌ Leave process cancelled. How else can I help?");
            }

            // 2️⃣ New intent overrides leave flow
            if (IntentDetector.isNewIntent(message)) {
                leaveFlow.remove(userId);
                // continue below to fresh intent detection
            } else {
                return handleLeaveCreation(message, userId);
            }
        }

        // 2) Detect leave intent (supports fuzzy + typos)
        if (IntentDetector.isLeaveCreationRequest(message)) {
            return handleLeaveCreation(message, userId);
        }

        // Ticket status
        if (IntentDetector.isTicketStatusRequest(message)) {
            return handleTicketStatus(message, userId);
        }

        // Ticket delete
        if (IntentDetector.isTicketDeleteRequest(message)) {
            return handleTicketDelete(message, userId);
        }

        // Ticket update
        if (IntentDetector.isTicketUpdateRequest(message)) {
            return handleTicketUpdate(message, userId);
        }

        // 3) Otherwise treat as general query (policy/LLM)
        return aiChatService.askStream(message);
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
    private Flux<String> handleTicketStatus(String message, String userId) {

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

    /* ============================================================
       LEAVE FLOW HANDLER
       ============================================================ */
    private Flux<String> handleLeaveCreation1(String message, String userId) {

        if (userId == null || userId.isBlank()) {
            return Flux.just("Please login to apply leave.");
        }

        LeaveFlowState state = leaveFlow.computeIfAbsent(userId, k -> {
            LeaveFlowState s = new LeaveFlowState();
            s.setStep(LeaveStep.TYPE);
            return s;
        });

        String lower = FuzzyTextUtil.normalize(message);

        // DEBUG (keep for 1-2 days)
        System.out.println("LEAVE_FLOW_DEBUG userId=" + userId
                + " step=" + state.getStep()
                + " leaveType=" + state.getLeaveType()
                + " from=" + state.getFromDate()
                + " to=" + state.getToDate()
                + " reason=" + state.getReason()
                + " msg=" + message);

        // Cancel support
        if (lower.contains("cancel") || FuzzyTextUtil.fuzzyTokenMatch(lower, "cancel", 2)) {
            leaveFlow.remove(userId);
            return Flux.just("❌ Leave request cancelled.");
        }

        // ------------------------------------------------------------
        // GLOBAL EXTRACTION (works at any step)
        // ------------------------------------------------------------
        // If user typed leave type anytime
        if (state.getLeaveType() == null) {
            String extractedType = parseLeaveType(message);
            if (extractedType != null) {
                state.setLeaveType(extractedType);
            }
        }

        // If user typed date anytime
        LocalDate possibleDate = parseDate(message);
        if (possibleDate != null && state.getFromDate() == null) {
            state.setFromDate(possibleDate);
        }
        if (possibleDate != null && state.getToDate() == null && state.getFromDate() != null) {
            // default one-day leave
            state.setToDate(state.getFromDate());
        }

        // If user typed reason anytime
        if (state.getReason() == null) {
            String possibleReason = extractReason(message);
            if (possibleReason != null && !possibleReason.isBlank()) {
                state.setReason(possibleReason);
            }
        }

        // ------------------------------------------------------------
        // STEP RECOVERY (very important)
        // If step is wrong / skipped, fix it based on missing fields
        // ------------------------------------------------------------
        if (state.getStep() == null) {
            state.setStep(LeaveStep.TYPE);
        }

        // If user already has leaveType but step is still LEAVE_TYPE, move forward
        if ("LEAVE_TYPE".equals(state.getStep())) {
            state.setStep(state.getFromDate() == null ? LeaveStep.FROM_DATE : LeaveStep.REASON);
        }

        // ------------------------------------------------------------
        // TYPE (start)
        // ------------------------------------------------------------
        if ("TYPE".equals(state.getStep())) {

            // if all details already present -> confirm / create
            if (state.getLeaveType() != null
                    && state.getFromDate() != null
                    && state.getToDate() != null
                    && state.getReason() != null) {

                state.setStep(LeaveStep.CONFIRM);

                if (isConfirmMessage(message)) {
                    return createLeaveTicketAndReset(state, userId);
                }

                return Flux.just("""
                Please confirm leave request:
                Leave Type: %s
                From: %s
                To: %s
                Reason: %s

                Reply: CONFIRM to submit OR CANCEL
                """.formatted(state.getLeaveType(), state.getFromDate(), state.getToDate(), state.getReason()));
            }

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
        if ("LEAVE_TYPE".equals(state.getStep())) {

            // If already captured globally
            if (state.getLeaveType() == null) {
                String type = parseLeaveType(message);
                if (type == null) {
                    return Flux.just("Invalid leave type. Please reply with 1-8 or leave name.");
                }
                state.setLeaveType(type);
            }

            // If user also gave date, jump ahead
            if (state.getFromDate() != null) {
                state.setStep(LeaveStep.REASON);
                return Flux.just("Got it 👍 Leave for " + state.getFromDate() + ". Please enter reason for leave:");
            }

            state.setStep(LeaveStep.FROM_DATE);
            return Flux.just("Enter From Date (yyyy-MM-dd) OR type 'today' / 'tomorrow'");
        }

        // ------------------------------------------------------------
        // FROM_DATE
        // ------------------------------------------------------------
        if ("FROM_DATE".equals(state.getStep())) {

            if (state.getFromDate() == null) {
                LocalDate from = parseDate(message);
                if (from == null) {
                    return Flux.just("Invalid date. Please enter yyyy-MM-dd or 'today' / 'tomorrow'");
                }
                state.setFromDate(from);
                // default to one day
                if (state.getToDate() == null) state.setToDate(from);
            }

            state.setStep(LeaveStep.TO_DATE);
            return Flux.just("Enter To Date (yyyy-MM-dd) OR type 'same'");
        }

        // ------------------------------------------------------------
        // TO_DATE
        // ------------------------------------------------------------
        if ("TO_DATE".equals(state.getStep())) {

            if (state.getToDate() == null) {
                LocalDate to = lower.equals("same") || FuzzyTextUtil.fuzzyTokenMatch(lower, "same", 1)
                        ? state.getFromDate()
                        : parseDate(message);

                if (to == null) {
                    return Flux.just("Invalid date. Please enter yyyy-MM-dd or 'same'");
                }
                state.setToDate(to);
            }

            // validate
            if (state.getToDate() != null && state.getFromDate() != null
                    && state.getToDate().isBefore(state.getFromDate())) {
                state.setToDate(null);
                return Flux.just("❌ To Date cannot be earlier than From Date. Please enter valid To Date (yyyy-MM-dd) or type 'same'.");
            }

            state.setStep(LeaveStep.REASON);
            return Flux.just("Enter reason for leave:");
        }

        // ------------------------------------------------------------
        // REASON
        // ------------------------------------------------------------
        if ("REASON".equals(state.getStep())) {

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
        if ("CONFIRM".equals(state.getStep())) {

            if (!isConfirmMessage(message)) {
                return Flux.just("Please reply CONFIRM to submit or CANCEL to stop.");
            }

            return createLeaveTicketAndReset(state, userId);
        }

        // ------------------------------------------------------------
        // LAST RESCUE (never break the user)
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
            return Flux.just("Enter To Date (yyyy-MM-dd) OR same");
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

        LeaveFlowState state = leaveFlow.computeIfAbsent(userId, k -> {
            LeaveFlowState s = new LeaveFlowState();
            s.setStep(LeaveStep.TYPE);
            return s;
        });

        String lower = FuzzyTextUtil.normalize(message);

        // DEBUG (keep for 1-2 days)
        System.out.println("LEAVE_FLOW_DEBUG userId=" + userId
                + " step=" + state.getStep()
                + " leaveType=" + state.getLeaveType()
                + " from=" + state.getFromDate()
                + " to=" + state.getToDate()
                + " reason=" + state.getReason()
                + " msg=" + message);

        // Cancel support
        if (lower.contains("cancel") || FuzzyTextUtil.fuzzyTokenMatch(lower, "cancel", 2)) {
            leaveFlow.remove(userId);
            return Flux.just("❌ Leave request cancelled.");
        }

        // ------------------------------------------------------------
        // GLOBAL EXTRACTION (works at any step)
        // ------------------------------------------------------------
        if (state.getLeaveType() == null) {
            String extractedType = parseLeaveType(message);
            if (extractedType != null) {
                state.setLeaveType(extractedType);
            }
        }

        LocalDate possibleDate = parseDate(message);
        if (possibleDate != null) {
            if (state.getFromDate() == null) state.setFromDate(possibleDate);
            if (state.getToDate() == null && state.getFromDate() != null) state.setToDate(state.getFromDate());
        }

        if (state.getReason() == null) {
            String possibleReason = extractReason(message);
            if (possibleReason != null && !possibleReason.isBlank()) {
                state.setReason(possibleReason);
            }
        }

        // ------------------------------------------------------------
        // STEP RECOVERY
        // ------------------------------------------------------------
        if (state.getStep() == null) {
            state.setStep(LeaveStep.TYPE);
        }

        // If user already has leaveType but step is still TYPE, move forward
        if (state.getStep() == LeaveStep.TYPE && state.getLeaveType() != null) {
            state.setStep(state.getFromDate() == null ? LeaveStep.FROM_DATE : LeaveStep.REASON);
        }

        // ------------------------------------------------------------
        // TYPE
        // ------------------------------------------------------------
        if (state.getStep() == LeaveStep.TYPE) {

            if (state.getLeaveType() != null
                    && state.getFromDate() != null
                    && state.getToDate() != null
                    && state.getReason() != null) {

                state.setStep(LeaveStep.CONFIRM);

                if (isConfirmMessage(message)) {
                    return createLeaveTicketAndReset(state, userId);
                }

                return Flux.just("""
                Please confirm leave request:
                Leave Type: %s
                From: %s
                To: %s
                Reason: %s

                Reply: CONFIRM to submit OR CANCEL
                """.formatted(state.getLeaveType(), state.getFromDate(), state.getToDate(), state.getReason()));
            }

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

            if (state.getFromDate() != null) {
                state.setStep(LeaveStep.REASON);
                return Flux.just("Got it 👍 Leave for " + state.getFromDate() + ". Please enter reason for leave:");
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
                if (state.getToDate() == null) state.setToDate(from);
            }

            state.setStep(LeaveStep.TO_DATE);
            return Flux.just("Enter To Date (yyyy-MM-dd) OR type 'same'");
        }

        // ------------------------------------------------------------
        // TO_DATE
        // ------------------------------------------------------------
        if (state.getStep() == LeaveStep.TO_DATE) {

            if (state.getToDate() == null) {
                LocalDate to = lower.equals("same") || FuzzyTextUtil.fuzzyTokenMatch(lower, "same", 1)
                        ? state.getFromDate()
                        : parseDate(message);

                if (to == null) {
                    return Flux.just("Invalid date. Please enter yyyy-MM-dd or 'same'");
                }
                state.setToDate(to);
            }

            if (state.getToDate().isBefore(state.getFromDate())) {
                state.setToDate(null);
                return Flux.just("❌ To Date cannot be earlier than From Date. Please enter valid To Date (yyyy-MM-dd) or type 'same'.");
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
        // LAST RESCUE
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
            return Flux.just("Enter To Date (yyyy-MM-dd) OR same");
        }
        if (state.getReason() == null) {
            state.setStep(LeaveStep.REASON);
            return Flux.just("Enter reason for leave:");
        }

        state.setStep(LeaveStep.CONFIRM);
        return Flux.just("Please reply CONFIRM to submit or CANCEL to stop.");
    }


//    private Flux<String> handleLeaveCreation1(String message, String userId) {
//
//        if (userId == null || userId.isBlank()) {
//            return Flux.just("Please login to apply leave.");
//        }
//
//        LeaveFlowState state = leaveFlow.computeIfAbsent(userId, k -> {
//            LeaveFlowState s = new LeaveFlowState();
//            s.setStep("TYPE");
//            return s;
//        });
//
//        String lower = FuzzyTextUtil.normalize(message);
//
//        // Cancel support
//        if (lower.contains("cancel") || FuzzyTextUtil.fuzzyTokenMatch(lower, "cancel", 2)) {
//            leaveFlow.remove(userId);
//            return Flux.just("❌ Leave request cancelled.");
//        }
//
//        /* ============================================================
//           Step: TYPE (start)
//           ============================================================ */
//        if ("TYPE".equals(state.getStep())) {
//
//            // Try extracting leave details directly from same sentence
//            String extractedType = parseLeaveType(message);
//            if (extractedType != null) state.setLeaveType(extractedType);
//
//            LocalDate possibleDate = parseDate(message);
//            if (possibleDate != null) {
//                state.setFromDate(possibleDate);
//                state.setToDate(possibleDate);
//            }
//
//            String possibleReason = extractReason(message);
//            if (possibleReason != null && !possibleReason.isBlank()) {
//                state.setReason(possibleReason);
//            }
//
//            boolean confirmedInMessage = isConfirmMessage(message);
//
//            // If all details already present -> confirm / create
//            if (state.getLeaveType() != null
//                    && state.getFromDate() != null
//                    && state.getToDate() != null
//                    && state.getReason() != null) {
//
//                state.setStep("CONFIRM");
//
//                if (confirmedInMessage) {
//                    return createLeaveTicketAndReset(state, userId);
//                }
//
//                return Flux.just("""
//                    Please confirm leave request:
//                    Leave Type: %s
//                    From: %s
//                    To: %s
//                    Reason: %s
//
//                    Reply: CONFIRM to submit OR CANCEL
//                    """.formatted(state.getLeaveType(), state.getFromDate(), state.getToDate(), state.getReason()));
//            }
//
//            // If leave type missing -> show menu
//            state.setStep("LEAVE_TYPE"); // ✅ FIXED
//            return Flux.just("""
//                Please select Leave Type:</br>
//                1. Need Based Leave</br>
//                2. Planned Leave</br>
//                3. Paternity Leave</br>
//                4. Maternity Leave</br>
//                5. Project Leave</br>
//                6. Leave Without Pay</br>
//                7. Election Leave</br>
//                8. Birthday Leave</br>
//                Reply with number or leave type name.
//                """);
//        }
//
//        /* ============================================================
//           Step: LEAVE_TYPE (capture leave type)
//           ============================================================ */
//        if ("LEAVE_TYPE".equals(state.getStep()) && state.getLeaveType() == null) {
//
//            String type = parseLeaveType(message);
//            if (type == null) {
//                return Flux.just("Invalid leave type. Please reply with 1-8 or leave name.");
//            }
//
//            state.setLeaveType(type);
//
//            // if user also typed date in same message
//            LocalDate possibleDate = parseDate(message);
//            if (possibleDate != null) {
//                state.setFromDate(possibleDate);
//                state.setToDate(possibleDate);
//                state.setStep("REASON");
//                return Flux.just("Got it 👍 Leave for " + possibleDate + ". Please enter reason for leave:");
//            }
//
//            state.setStep("FROM_DATE");
//            return Flux.just("Enter From Date (yyyy-MM-dd) OR type 'today' / 'tomorrow'");
//        }
//
//        /* ============================================================
//           Step: FROM_DATE
//           ============================================================ */
//        if ("FROM_DATE".equals(state.getStep()) && state.getFromDate() == null) {
//
//            LocalDate from = parseDate(message);
//            if (from == null) {
//                return Flux.just("Invalid date. Please enter yyyy-MM-dd or 'today' / 'tomorrow'");
//            }
//
//            state.setFromDate(from);
//            state.setStep("TO_DATE");
//
//            return Flux.just("Enter To Date (yyyy-MM-dd) OR type 'same'");
//        }
//
//        /* ============================================================
//           Step: TO_DATE (validation)
//           ============================================================ */
//        if ("TO_DATE".equals(state.getStep()) && state.getToDate() == null) {
//
//            LocalDate to = lower.equals("same") || FuzzyTextUtil.fuzzyTokenMatch(lower, "same", 1)
//                    ? state.getFromDate()
//                    : parseDate(message);
//
//            if (to == null) {
//                return Flux.just("Invalid date. Please enter yyyy-MM-dd or 'same'");
//            }
//
//            if (to.isBefore(state.getFromDate())) {
//                return Flux.just("❌ To Date cannot be earlier than From Date. Please enter valid To Date (yyyy-MM-dd) or type 'same'.");
//            }
//
//            state.setToDate(to);
//            state.setStep("REASON");
//
//            return Flux.just("Enter reason for leave:");
//        }
//
//        /* ============================================================
//           Step: REASON
//           ============================================================ */
//        if ("REASON".equals(state.getStep()) && state.getReason() == null) {
//
//            String reason = message.trim();
//            if (reason.isBlank()) {
//                return Flux.just("Please enter a valid reason.");
//            }
//
//            state.setReason(reason);
//            state.setStep("CONFIRM");
//
//            return Flux.just("""
//                Please confirm leave request:
//                Leave Type: %s
//                From: %s
//                To: %s
//                Reason: %s
//
//                Reply: CONFIRM to submit OR CANCEL
//                """.formatted(state.getLeaveType(), state.getFromDate(), state.getToDate(), state.getReason()));
//        }
//
//        /* ============================================================
//           Step: CONFIRM
//           ============================================================ */
//        if ("CONFIRM".equals(state.getStep())) {
//
//            if (!isConfirmMessage(message)) {
//                return Flux.just("Please reply CONFIRM to submit or CANCEL to stop.");
//            }
//
//            return createLeaveTicketAndReset(state, userId);
//        }
//
//        return Flux.just("Something went wrong in leave flow. Type CANCEL and retry.");
//    }

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

        // direct mappings
        if (m.equals("1")) return "Need Based Leave";
        if (m.equals("2")) return "Planned Leave";
        if (m.equals("3")) return "Paternity Leave";
        if (m.equals("4")) return "Maternity Leave";
        if (m.equals("5")) return "Project Leave";
        if (m.equals("6")) return "Leave Without Pay";
        if (m.equals("7")) return "Election Leave";
        if (m.equals("8")) return "Birthday Leave";

        // fuzzy synonyms
        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("need based", "need base", "needbased", "nbl"), 2))
            return "Need Based Leave";

        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("planned", "plan leave", "pl"), 2))
            return "Planned Leave";

        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("paternity", "father leave"), 2))
            return "Paternity Leave";

        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("maternity", "mother leave"), 2))
            return "Maternity Leave";

        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("project"), 2))
            return "Project Leave";

        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("without pay", "lwp"), 2))
            return "Leave Without Pay";

        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("election"), 2))
            return "Election Leave";

        if (FuzzyTextUtil.fuzzyContainsAny(m, List.of("birthday"), 2))
            return "Birthday Leave";

        return null;
    }

    /* ============================================================
       Date parsing (supports today/tomorrow + typo)
       ============================================================ */
    private LocalDate parseDate1(String message) {
        if (message == null) return null;

        String m = FuzzyTextUtil.normalize(message);

        if (m.contains("today") || FuzzyTextUtil.fuzzyTokenMatch(m, "today", 1)) {
            return LocalDate.now();
        }

        if (m.contains("tomorrow") || FuzzyTextUtil.fuzzyTokenMatch(m, "tomorrow", 2)
                || FuzzyTextUtil.fuzzyTokenMatch(m, "tomorow", 2)) {
            return LocalDate.now().plusDays(1);
        }

        try {
            String[] tokens = m.split(" ");
            for (String t : tokens) {
                if (t.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    return LocalDate.parse(t, DateTimeFormatter.ISO_LOCAL_DATE);
                }
            }
        } catch (DateTimeParseException ignored) {
        }

        return null;
    }
    private LocalDate parseDate(String message) {
        if (message == null || message.isBlank()) return null;

        String m = FuzzyTextUtil.normalize(message);

        // -------------------------
        // 1) today / tomorrow
        // -------------------------
        if (m.contains("today") || FuzzyTextUtil.fuzzyTokenMatch(m, "today", 1)) {
            return LocalDate.now();
        }

        if (m.contains("tomorrow")
                || FuzzyTextUtil.fuzzyTokenMatch(m, "tomorrow", 2)
                || FuzzyTextUtil.fuzzyTokenMatch(m, "tomorow", 2)
                || m.contains("tmrw")) {
            return LocalDate.now().plusDays(1);
        }

        // -------------------------
        // 2) next monday / next tuesday ...
        // -------------------------
        LocalDate nextDow = parseNextDayOfWeek(m);
        if (nextDow != null) return nextDow;

        // -------------------------
        // 3) Try to extract explicit date patterns
        // Supports:
        // yyyy-MM-dd
        // dd/MM/yyyy
        // MM-dd-yyyy
        // dd MMM yyyy / dd MMM
        // -------------------------
        String[] tokens = m.split("\\s+");

        // (a) yyyy-MM-dd
        for (String t : tokens) {
            if (t.matches("\\d{4}-\\d{2}-\\d{2}")) {
                try {
                    return LocalDate.parse(t, DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (DateTimeParseException ignored) {}
            }
        }

        // (b) dd/MM/yyyy
        for (String t : tokens) {
            if (t.matches("\\d{2}/\\d{2}/\\d{4}")) {
                try {
                    DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                    return LocalDate.parse(t, f);
                } catch (DateTimeParseException ignored) {}
            }
        }

        // (c) MM-dd-yyyy
        for (String t : tokens) {
            if (t.matches("\\d{2}-\\d{2}-\\d{4}")) {
                try {
                    DateTimeFormatter f = DateTimeFormatter.ofPattern("MM-dd-yyyy");
                    return LocalDate.parse(t, f);
                } catch (DateTimeParseException ignored) {}
            }
        }

        // (d) "15 feb" or "15 feb 2026"
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

        var matcher = java.util.regex.Pattern
                .compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                .matcher(message);

        if (matcher.find()) {
            return UUID.fromString(matcher.group());
        }
        return null;
    }
}
