package com.intech.ai.modal;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Attachment {
    private String fileName;
    private String fileType; // EXCEL / PDF
    private String url;      // downloadable url
}
