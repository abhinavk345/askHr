package com.intech.ai.jaeger;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HrChatService {

    private final Tracer tracer;

    public String handleChat(String message) {

        Span span = tracer.nextSpan()
                .name("hr.chat.processing")
                .tag("user.message", message)
                .start();

        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {

            // call Spring AI here
            return "response";

        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
