package com.intech.ai.controller;

import com.intech.ai.dtos.ChipRequest;
import com.intech.ai.dtos.ChipResponse;
import com.intech.ai.service.ChipSuggestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chips")
public class ChipSuggestionController {

    private final ChipSuggestionService chipSuggestionService;

    public ChipSuggestionController(ChipSuggestionService chipSuggestionService) {
        this.chipSuggestionService = chipSuggestionService;
    }

    @PostMapping
    public ChipResponse getChips(@RequestBody ChipRequest request) {
        return chipSuggestionService.generateChips(request);
    }
}
