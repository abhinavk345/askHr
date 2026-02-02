package com.intech.ai.service;

import com.intech.ai.modal.LeaveFlowState;
import com.intech.ai.modal.Ticket;
import com.intech.ai.repository.TicketRepository;
import com.intech.ai.utility.FuzzyTextUtil;
import com.intech.ai.utility.IntentDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
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

        if (message == null || message.isBlank()) {
            return Flux.just("Please type your query.");
        }

        // 1) If leave flow already started -> continue
        if (userId != null && leaveFlow.containsKey(userId)) {
            return handleLeaveCreation(message, userId);
        }

        // 2) Detect leave intent (supports fuzzy + typos)
        if (IntentDetector.isLeaveCreationRequest(message)) {
            return handleLeaveCreation(message, userId);
        }

        // 3) Otherwise treat as general query (policy/LLM)
        return aiChatService.askStream(message);
    }

    /* ============================================================
       LEAVE FLOW HANDLER
       ============================================================ */
    private Flux<String> handleLeaveCreation(String message, String userId) {

        if (userId == null || userId.isBlank()) {
            return Flux.just("Please login to apply leave.");
        }

        LeaveFlowState state = leaveFlow.computeIfAbsent(userId, k -> {
            LeaveFlowState s = new LeaveFlowState();
            s.setStep("TYPE");
            return s;
        });

        String lower = FuzzyTextUtil.normalize(message);

        // Cancel support
        if (lower.contains("cancel") || FuzzyTextUtil.fuzzyTokenMatch(lower, "cancel", 2)) {
            leaveFlow.remove(userId);
            return Flux.just("❌ Leave request cancelled.");
        }

        /* ============================================================
           Step: TYPE
           ============================================================ */
        if ("TYPE".equals(state.getStep())) {

            // ---- Extract info from the same sentence (single-line support)
            String extractedType = parseLeaveType(message);
            if (extractedType != null) state.setLeaveType(extractedType);

            LocalDate possibleDate = parseDate(message);
            if (possibleDate != null) {
                state.setFromDate(possibleDate);
                state.setToDate(possibleDate);
            }

            String possibleReason = extractReason(message);
            if (possibleReason != null && !possibleReason.isBlank()) {
                state.setReason(possibleReason);
            }

            // if user already confirmed in same message
            boolean confirmedInMessage = isConfirmMessage(message);

            // If all details already present -> go confirm / create ticket
            if (state.getLeaveType() != null
                    && state.getFromDate() != null
                    && state.getToDate() != null
                    && state.getReason() != null) {

                state.setStep("CONFIRM");

                if (confirmedInMessage) {
                    // directly create ticket
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

        /* ============================================================
           Step: FROM_DATE (first capture leave type)
           ============================================================ */
        if ("FROM_DATE".equals(state.getStep()) && state.getLeaveType() == null) {

            String type = parseLeaveType(message);
            if (type == null) {
                return Flux.just("Invalid leave type. Please reply with 1-8 or leave name.");
            }

            state.setLeaveType(type);

            // user may have provided date in same message
            LocalDate possibleDate = parseDate(message);
            if (possibleDate != null) {
                state.setFromDate(possibleDate);
                state.setToDate(possibleDate);
                state.setStep("REASON");
                return Flux.just("Got it 👍 Leave for " + possibleDate + ". Please enter reason for leave:");
            }

            return Flux.just("Enter From Date (yyyy-MM-dd) OR type 'today' / 'tomorrow'");
        }

        /* ============================================================
           Step: FROM_DATE (capture from date)
           ============================================================ */
        if ("FROM_DATE".equals(state.getStep()) && state.getFromDate() == null) {

            LocalDate from = parseDate(message);
            if (from == null) {
                return Flux.just("Invalid date. Please enter yyyy-MM-dd or 'today' / 'tomorrow'");
            }

            state.setFromDate(from);
            state.setStep("TO_DATE");

            return Flux.just("Enter To Date (yyyy-MM-dd) OR type 'same'");
        }

        /* ============================================================
           Step: TO_DATE (capture to date + validation)
           ============================================================ */
        if ("TO_DATE".equals(state.getStep()) && state.getToDate() == null) {

            LocalDate to = lower.equals("same") || FuzzyTextUtil.fuzzyTokenMatch(lower, "same", 1)
                    ? state.getFromDate()
                    : parseDate(message);

            if (to == null) {
                return Flux.just("Invalid date. Please enter yyyy-MM-dd or 'same'");
            }

            // ✅ Validation: ToDate cannot be earlier than FromDate
            if (to.isBefore(state.getFromDate())) {
                return Flux.just("❌ To Date cannot be earlier than From Date. Please enter a valid To Date (yyyy-MM-dd) or type 'same'.");
            }

            state.setToDate(to);
            state.setStep("REASON");

            return Flux.just("Enter reason for leave:");
        }

        /* ============================================================
           Step: REASON
           ============================================================ */
        if ("REASON".equals(state.getStep()) && state.getReason() == null) {

            String reason = message.trim();
            if (reason.isBlank()) {
                return Flux.just("Please enter a valid reason.");
            }

            state.setReason(reason);
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

        /* ============================================================
           Step: CONFIRM
           ============================================================ */
        if ("CONFIRM".equals(state.getStep())) {

            if (!isConfirmMessage(message)) {
                return Flux.just("Please reply CONFIRM to submit or CANCEL to stop.");
            }

            return createLeaveTicketAndReset(state, userId);
        }

        return Flux.just("Something went wrong in leave flow. Type CANCEL and retry.");
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
       Date parsing (supports today/tomorrow + typo "tomorow")
       ============================================================ */
    private LocalDate parseDate(String message) {
        if (message == null) return null;

        String m = FuzzyTextUtil.normalize(message);

        if (m.contains("today") || FuzzyTextUtil.fuzzyTokenMatch(m, "today", 1)) {
            return LocalDate.now();
        }

        // tomorrow typo support
        if (m.contains("tomorrow") || FuzzyTextUtil.fuzzyTokenMatch(m, "tomorrow", 2)
                || FuzzyTextUtil.fuzzyTokenMatch(m, "tomorow", 2)) {
            return LocalDate.now().plusDays(1);
        }

        // yyyy-MM-dd
        try {
            // extract first yyyy-mm-dd pattern if exists
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
}
