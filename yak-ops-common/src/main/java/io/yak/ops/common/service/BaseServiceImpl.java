package io.yak.ops.common.service;

import io.yak.ops.common.utils.DateUtils;

import java.util.Date;

/**
 * Shared base class for application service implementations.
 *
 * <p>Only module-independent helpers belong here. API-specific permissions,
 * response objects and status codes remain in the API adapter.
 */
public abstract class BaseServiceImpl {

    /**
     * Checks whether a description exceeds the platform's 255-character limit.
     */
    public boolean checkDescriptionLength(String description) {
        return description != null && description.codePointCount(0, description.length()) > 255;
    }

    /**
     * Parses a date string and rejects invalid values.
     */
    protected Date transformDate(String dateStr) {
        Date date = DateUtils.stringToDate(dateStr);
        if (date == null) {
            throw new IllegalArgumentException("dateStr: [" + dateStr + "] is invalid");
        }
        return date;
    }
}
