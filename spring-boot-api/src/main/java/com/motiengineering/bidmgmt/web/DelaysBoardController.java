package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.dto.DelayBoardRowDto;
import com.motiengineering.bidmgmt.service.DelaysBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/delays-board")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DelaysBoardController {

    private final DelaysBoardService delaysBoardService;

    @GetMapping
    public List<DelayBoardRowDto> list(
            @RequestParam(required = false) UUID divisionId,
            @RequestParam(required = false) UUID officerId,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) String oemBrand,
            @RequestParam(required = false) String waitingOnParty,
            @RequestParam(required = false) String level) {
        var filter = new DelaysBoardService.Filter(divisionId, officerId, organizationId, oemBrand, waitingOnParty, level);
        return delaysBoardService.list(filter);
    }
}
