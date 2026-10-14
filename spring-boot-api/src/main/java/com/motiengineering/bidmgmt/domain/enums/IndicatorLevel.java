package com.motiengineering.bidmgmt.domain.enums;

/**
 * NO_TARGET means no stage_target row matched - nothing to measure against,
 * not an error. UNKNOWN means a target exists but elapsed time (or the
 * deadline field it's relative to) can't be determined - never report a
 * color in that case.
 */
public enum IndicatorLevel {
    NO_TARGET,
    UNKNOWN,
    GREEN,
    AMBER,
    RED,
    CRITICAL
}
