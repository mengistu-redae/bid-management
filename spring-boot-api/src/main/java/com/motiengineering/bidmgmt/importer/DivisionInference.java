package com.motiengineering.bidmgmt.importer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Best-effort division guess from keywords in a tender title, per the
 * brief's examples. Always just a suggestion - the importer preview and the
 * bid detail screen both let a human confirm or change it, since these
 * keyword lists can never be exhaustive (e.g. the EEU tender below matches
 * no keyword at all and needs a manual pick).
 */
public final class DivisionInference {

    private DivisionInference() {
    }

    // Checked in this order; the first division whose keywords appear wins
    // when a title (rarely) matches more than one list's keywords.
    private static final Map<String, List<String>> KEYWORDS_BY_DIVISION_CODE = new LinkedHashMap<>();

    static {
        KEYWORDS_BY_DIVISION_CODE.put("SECURITY", List.of("siem", "edr", "pam", "firewall", "fortiweb", "waf", "nac", "vulnerability", "threat intelligence"));
        KEYWORDS_BY_DIVISION_CODE.put("NETWORK", List.of("switch", "router", "apic", "wi-fi", "wifi"));
        KEYWORDS_BY_DIVISION_CODE.put("SERVER_STORAGE", List.of("server", "storage", "exadata", "vmware", "backup", "tape", "hci", "veeam", "red hat", "openshift", "oracle"));
        KEYWORDS_BY_DIVISION_CODE.put("DATACENTER_FACILITY", List.of("ups", "cooling", "rack", "generator", "pdu"));
    }

    /** Returns the division code, or null if no keyword matched (the caller should ask a human). */
    public static String inferDivisionCode(String text) {
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : KEYWORDS_BY_DIVISION_CODE.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }
}
