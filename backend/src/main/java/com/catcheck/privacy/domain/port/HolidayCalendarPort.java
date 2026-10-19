package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.HolidayCalendarEntry;

import java.util.Set;

public interface HolidayCalendarPort {

    Set<HolidayCalendarEntry> allHolidays();
}
