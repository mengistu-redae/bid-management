package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.BidScout;
import com.motiengineering.bidmgmt.domain.BidScoutId;
import com.motiengineering.bidmgmt.domain.ImportBatch;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.LotAccountOfficerId;
import com.motiengineering.bidmgmt.domain.LotScopeType;
import com.motiengineering.bidmgmt.domain.LotScopeTypeId;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.importer.BidsTrackerRow;
import com.motiengineering.bidmgmt.importer.BidsTrackerSheetParser;
import com.motiengineering.bidmgmt.importer.ParsedMoney;
import com.motiengineering.bidmgmt.importer.ParsingUtils;
import com.motiengineering.bidmgmt.importer.UpcomingBidRow;
import com.motiengineering.bidmgmt.importer.UpcomingBidSheetParser;
import com.motiengineering.bidmgmt.repository.BidLotRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.BidScoutRepository;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.repository.ImportBatchRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.LotScopeTypeRepository;
import com.motiengineering.bidmgmt.util.TextSimilarity;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates both sample-file importers: parse (read-only, for the
 * preview screen) and confirm (actually persists). See
 * ImportPreviewCache for why preview and confirm are two separate calls,
 * and ImportControllerIT / BidsTrackerSheetParserTest etc. for the
 * concrete parsing cases this was built against.
 */
@Service
@RequiredArgsConstructor
public class ImportService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");
    /** Below this title-similarity score, a tracker row with no reference number match is treated as a new bid, not a merge. */
    private static final double TITLE_MATCH_THRESHOLD = 0.45;

    private final BidRepository bidRepository;
    private final BidLotRepository bidLotRepository;
    private final BidScoutRepository bidScoutRepository;
    private final LotAccountOfficerRepository lotAccountOfficerRepository;
    private final LotScopeTypeRepository lotScopeTypeRepository;
    private final DivisionRepository divisionRepository;
    private final OrganizationResolutionService organizationResolutionService;
    private final UserResolutionService userResolutionService;
    private final ChecklistService checklistService;
    private final ActivityLogService activityLogService;
    private final ImportBatchRepository importBatchRepository;

    // ---------------------------------------------------------------- preview

    public List<UpcomingBidRow> parseUpcoming(InputStream xlsx, int defaultYear) throws IOException {
        try (Workbook wb = WorkbookFactory.create(xlsx)) {
            Sheet sheet = wb.getSheetAt(0);
            return UpcomingBidSheetParser.parse(sheet, defaultYear);
        }
    }

    public Map<String, List<BidsTrackerRow>> parseTracker(InputStream xlsx, int defaultYear) throws IOException {
        try (Workbook wb = WorkbookFactory.create(xlsx)) {
            Map<String, List<BidsTrackerRow>> result = new LinkedHashMap<>();
            for (Map.Entry<String, BidStatus> entry : trackerSheetStatuses().entrySet()) {
                Sheet sheet = wb.getSheet(entry.getKey());
                if (sheet != null) {
                    result.put(entry.getKey(), BidsTrackerSheetParser.parse(sheet, entry.getValue(), defaultYear));
                }
            }
            return result;
        }
    }

    private Map<String, BidStatus> trackerSheetStatuses() {
        Map<String, BidStatus> m = new LinkedHashMap<>();
        m.put("Bids on hand", BidStatus.PREPARING);
        m.put("Submitted", BidStatus.SUBMITTED);
        m.put("drop", BidStatus.DROPPED);
        m.put("cancelled", BidStatus.CANCELLED);
        return m;
    }

    // ---------------------------------------------------------------- confirm

    @Transactional
    public ImportBatch confirmUpcoming(List<UpcomingBidRow> rows, String sourceFilename, AppUser importedBy) {
        ImportBatch batch = new ImportBatch();
        batch.setKind("UPCOMING_BIDS");
        batch.setSourceFilename(sourceFilename);
        batch.setImportedBy(importedBy);
        batch.setRowsTotal(rows.size());

        for (UpcomingBidRow row : rows) {
            Organization org = organizationResolutionService.resolveOrCreate(row.organizationRaw, Sector.OTHER);

            Bid bid = new Bid();
            bid.setOrganization(org);
            bid.setTitle(row.cleanTitle);
            bid.setReferenceNumber(row.referenceNumber);
            bid.setScoutedDate(row.scoutedDate);
            bid.setClosingAt(row.closingAt == null ? null : row.closingAt.atZone(ZONE).toInstant());
            bid.setOpeningAt(row.openingAt == null ? null : row.openingAt.atZone(ZONE).toInstant());
            bid.setClarificationDeadline(row.clarificationDeadline == null ? null : row.clarificationDeadline.atStartOfDay(ZONE).toInstant());
            bid.setBidValidityDays(row.bidValidityDays);
            bid.setCreatedBy(importedBy);
            bid = bidRepository.save(bid);

            for (String scoutName : row.scoutNames) {
                AppUser scout = userResolutionService.resolveOrCreate(scoutName, Role.SCOUT);
                bidScoutRepository.save(new BidScout(new BidScoutId(bid.getId(), scout.getId())));
            }

            List<ParsingUtils.LotAmount> lotAmounts = row.bidBondLots.isEmpty()
                    ? List.of(new ParsingUtils.LotAmount("Lot 1", ParsedMoney.empty()))
                    : row.bidBondLots;
            for (ParsingUtils.LotAmount lotAmount : lotAmounts) {
                BidLot lot = new BidLot();
                lot.setBid(bid);
                lot.setLotLabel(lotAmount.label());
                if (row.inferredDivisionCode != null) {
                    divisionRepository.findByCode(row.inferredDivisionCode).ifPresent(lot::setDivision);
                }
                lot.setBidBondAmount(lotAmount.money().amount());
                lot.setBidBondCurrency(lotAmount.money().currency());
                lot.setBidBondValidityDays(row.bidBondValidityDays);
                boolean needsReview = lotAmount.money().needsReview() || row.inferredDivisionCode == null;
                lot.setNeedsReview(needsReview);
                lot.setReviewNote(String.join(" | ", row.warnings));
                lot = bidLotRepository.save(lot);
                bid.getLots().add(lot); // a lot saved via its own repository doesn't auto-join the parent's in-memory collection
                checklistService.seedForLot(lot);
            }

            if (!row.warnings.isEmpty()) {
                batch.setRowsFlagged(batch.getRowsFlagged() + 1);
            }
            batch.setRowsCreated(batch.getRowsCreated() + 1);
        }

        return importBatchRepository.save(batch);
    }

    @Transactional
    public ImportBatch confirmTracker(Map<String, List<BidsTrackerRow>> rowsBySheet, String sourceFilename, AppUser importedBy) {
        ImportBatch batch = new ImportBatch();
        batch.setKind("BIDS_TRACKER");
        batch.setSourceFilename(sourceFilename);
        batch.setImportedBy(importedBy);

        for (Map.Entry<String, List<BidsTrackerRow>> entry : rowsBySheet.entrySet()) {
            for (BidsTrackerRow row : entry.getValue()) {
                batch.setRowsTotal(batch.getRowsTotal() + 1);
                Organization org = organizationResolutionService.resolveOrCreate(row.organizationRaw, Sector.OTHER);

                Bid matched = findMatch(org, row);
                if (matched != null) {
                    mergeIntoBid(matched, row, importedBy);
                    batch.setRowsMerged(batch.getRowsMerged() + 1);
                } else {
                    createFromTrackerRow(org, row, importedBy);
                    batch.setRowsCreated(batch.getRowsCreated() + 1);
                }
                if (!row.warnings.isEmpty()) {
                    batch.setRowsFlagged(batch.getRowsFlagged() + 1);
                }
            }
        }

        return importBatchRepository.save(batch);
    }

    private Bid findMatch(Organization org, BidsTrackerRow row) {
        List<Bid> candidates = bidRepository.findByOrganization_Id(org.getId());
        if (row.referenceNumber != null) {
            for (Bid candidate : candidates) {
                if (row.referenceNumber.equalsIgnoreCase(candidate.getReferenceNumber())) {
                    return candidate;
                }
            }
        }
        // A combined multi-lot title in the Upcoming file (e.g. Awash Bank's
        // "LOT-1 Renewal of ... And LOT-2 Supply and ...") literally contains
        // each lot's own shorter description verbatim once the "LOT-n"
        // prefix is stripped off the tracker row's title - a reliable signal
        // plain edit-distance similarity misses because the lengths differ
        // so much. Checked before falling back to fuzzy similarity.
        String rowTitleNoLotPrefix = stripLotPrefix(row.cleanTitle).toLowerCase();
        if (rowTitleNoLotPrefix.length() >= 15) {
            for (Bid candidate : candidates) {
                if (candidate.getTitle().toLowerCase().contains(rowTitleNoLotPrefix)) {
                    return candidate;
                }
            }
        }

        Bid best = null;
        double bestScore = 0;
        for (Bid candidate : candidates) {
            double score = TextSimilarity.similarity(row.cleanTitle.toLowerCase(), candidate.getTitle().toLowerCase());
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return bestScore >= TITLE_MATCH_THRESHOLD ? best : null;
    }

    private static final java.util.regex.Pattern LOT_PREFIX = java.util.regex.Pattern.compile("(?i)^\\s*lot[\\s-]*[ivx\\d]+\\s*[:\\-]?\\s*");

    private String stripLotPrefix(String title) {
        return LOT_PREFIX.matcher(title.replace("\n", " ")).replaceFirst("").replaceAll("\\s+", " ").trim();
    }

    private void mergeIntoBid(Bid bid, BidsTrackerRow row, AppUser importedBy) {
        Integer rowLotNumber = ParsingUtils.extractLotNumber(row.cleanTitle);
        BidLot targetLot = selectLot(bid, rowLotNumber);

        BidStatus oldStatus = bid.getStatus();
        bid.setStatus(row.sheetStatus);
        for (BidLot lot : bid.getLots()) {
            if (lot.getStatus() == oldStatus) {
                lot.setStatus(row.sheetStatus);
            }
        }
        bidRepository.save(bid);

        if (!row.dealSize.needsReview() && row.dealSize.amount() != null && targetLot.getEstimatedValue() == null) {
            targetLot.setEstimatedValue(row.dealSize.amount());
            targetLot.setEstimatedValueCurrency(row.dealSize.currency());
        }
        if (row.dealSize.needsReview()) {
            targetLot.setNeedsReview(true);
            targetLot.setReviewNote(appendNote(targetLot.getReviewNote(), row.dealSize.reviewNote()));
        }
        for (var scopeType : row.scopeTypes) {
            lotScopeTypeRepository.save(new LotScopeType(new LotScopeTypeId(targetLot.getId(), scopeType)));
        }
        for (String officerName : row.accountOfficerNames) {
            AppUser officer = userResolutionService.resolveOrCreate(officerName, Role.ACCOUNT_OFFICER);
            lotAccountOfficerRepository.save(new LotAccountOfficer(new LotAccountOfficerId(targetLot.getId(), officer.getId())));
        }
        bidLotRepository.save(targetLot);

        addNotesToActivityLog(bid.getId(), row, importedBy);
    }

    private BidLot selectLot(Bid bid, Integer rowLotNumber) {
        if (bid.getLots().size() == 1 || rowLotNumber == null) {
            return bid.getLots().get(0);
        }
        for (BidLot lot : bid.getLots()) {
            if (rowLotNumber.equals(ParsingUtils.extractLotNumber(lot.getLotLabel()))) {
                return lot;
            }
        }
        BidLot fallback = bid.getLots().get(0);
        fallback.setNeedsReview(true);
        fallback.setReviewNote(appendNote(fallback.getReviewNote(), "Tracker row merged ambiguously - verify this is the right lot."));
        return fallback;
    }

    private void createFromTrackerRow(Organization org, BidsTrackerRow row, AppUser importedBy) {
        Bid bid = new Bid();
        bid.setOrganization(org);
        bid.setTitle(row.cleanTitle);
        bid.setReferenceNumber(row.referenceNumber);
        bid.setStatus(row.sheetStatus);
        bid.setClosingAt(row.submissionDeadline == null ? null : row.submissionDeadline.atZone(ZONE).toInstant());
        bid.setCreatedBy(importedBy);
        bid = bidRepository.save(bid);

        BidLot lot = new BidLot();
        lot.setBid(bid);
        lot.setLotLabel("Lot 1");
        lot.setStatus(row.sheetStatus);
        if (row.inferredDivisionCode != null) {
            divisionRepository.findByCode(row.inferredDivisionCode).ifPresent(lot::setDivision);
        }
        if (!row.dealSize.needsReview()) {
            lot.setEstimatedValue(row.dealSize.amount());
            lot.setEstimatedValueCurrency(row.dealSize.currency());
        }
        boolean needsReview = row.dealSize.needsReview() || row.inferredDivisionCode == null;
        lot.setNeedsReview(needsReview);
        lot.setReviewNote(String.join(" | ", row.warnings));
        lot = bidLotRepository.save(lot);
        bid.getLots().add(lot); // a lot saved via its own repository doesn't auto-join the parent's in-memory collection
        checklistService.seedForLot(lot);

        for (var scopeType : row.scopeTypes) {
            lotScopeTypeRepository.save(new LotScopeType(new LotScopeTypeId(lot.getId(), scopeType)));
        }
        for (String officerName : row.accountOfficerNames) {
            AppUser officer = userResolutionService.resolveOrCreate(officerName, Role.ACCOUNT_OFFICER);
            lotAccountOfficerRepository.save(new LotAccountOfficer(new LotAccountOfficerId(lot.getId(), officer.getId())));
        }

        addNotesToActivityLog(bid.getId(), row, importedBy);
    }

    private void addNotesToActivityLog(java.util.UUID bidId, BidsTrackerRow row, AppUser importedBy) {
        if (row.statusNote != null && !row.statusNote.isBlank()) {
            activityLogService.addNote(EntityType.BID, bidId, importedBy, row.statusNote);
        }
        if (row.extraNotes != null && !row.extraNotes.isBlank()) {
            activityLogService.addNote(EntityType.BID, bidId, importedBy, row.extraNotes);
        }
    }

    private String appendNote(String existing, String addition) {
        if (existing == null || existing.isBlank()) {
            return addition;
        }
        return existing + " | " + addition;
    }
}
