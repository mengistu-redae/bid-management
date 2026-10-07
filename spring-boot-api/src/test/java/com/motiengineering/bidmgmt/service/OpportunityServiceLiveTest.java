package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.Opportunity;
import com.motiengineering.bidmgmt.domain.enums.Currency;
import com.motiengineering.bidmgmt.dto.CreateOpportunityRequest;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises "Convert to Bid" against a real Postgres (not mocks) - this is
 * exactly the kind of bug a mocked BidService wouldn't catch: converting an
 * opportunity 500'd live with "null value in column created_at of relation
 * bids", caused by mutating a Bid entity returned from a cross-service call
 * and relying on implicit dirty-checking instead of an explicit save. See
 * ImportServiceLiveFilesTest for why this points at the compose Postgres's
 * dedicated bidmgmt_test database rather than Testcontainers.
 */
@SpringBootTest
@Transactional
class OpportunityServiceLiveTest {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5434/bidmgmt_test");
        registry.add("spring.datasource.username", () -> "bidmgmt");
        registry.add("spring.datasource.password", () -> "bidmgmt");
        registry.add("bidmgmt.seed.enabled", () -> "false");
    }

    @Autowired
    private OpportunityService opportunityService;
    @Autowired
    private DivisionRepository divisionRepository;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void convertingAnOpportunityCreatesABidLinkedBackToIt() {
        Division security = divisionRepository.findByCode("SECURITY").orElseThrow();
        var director = appUserRepository.findAll().stream().findFirst().orElseThrow();

        CreateOpportunityRequest request = new CreateOpportunityRequest(
                null, "Live Test Bank", "BANK", "Live test opportunity",
                new BigDecimal("5000000"), "ETB", null,
                List.of(security.getCode()), List.of("Dell"), null, null);
        Opportunity opportunity = opportunityService.create(request, director);

        Bid bid = opportunityService.convertToBid(opportunity.getId(), director);
        entityManager.flush(); // force Hibernate to actually attempt the INSERTs now, inside this still-rolled-back-at-the-end test transaction

        assertThat(bid.getId()).isNotNull();
        assertThat(bid.getCreatedAt()).isNotNull();
        assertThat(bid.getTitle()).isEqualTo("Live test opportunity");
        assertThat(bid.getOpportunity().getId()).isEqualTo(opportunity.getId());
        assertThat(bid.getLots()).hasSize(1);
        assertThat(bid.getLots().get(0).getEstimatedValue()).isEqualByComparingTo("5000000");
        assertThat(bid.getLots().get(0).getEstimatedValueCurrency()).isEqualTo(Currency.ETB);

        Opportunity reloaded = opportunityService.get(opportunity.getId());
        assertThat(reloaded.getStage().name()).isEqualTo("CONVERTED_TO_BID");
        assertThat(reloaded.getConvertedBid().getId()).isEqualTo(bid.getId());
    }
}
