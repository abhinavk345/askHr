package com.intech.ai.modal;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatResponse {
    private String message;
    private Attachment attachment;  // null if no file
}
