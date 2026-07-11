package ru.privatenull.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionComparatorTest {

    @Test
    void comparesNumericPartsInsteadOfLexicographicText() {
        assertTrue(VersionComparator.compare("1.10", "1.9") > 0);
        assertTrue(VersionComparator.compare("v1.4.8", "1.4.7") > 0);
        assertEquals(0, VersionComparator.compare("1.4.8.0", "1.4.8"));
    }

    @Test
    void releaseIsNewerThanSnapshotWithSameNumbers() {
        assertTrue(VersionComparator.compare("1.4.8", "1.4.8-SNAPSHOT") > 0);
        assertTrue(VersionComparator.compare("1.4.8-SNAPSHOT", "1.4.8") < 0);
    }

    @Test
    void malformedPartsDoNotCrashComparison() {
        assertEquals(0, VersionComparator.compare("dev", "unknown"));
    }
}
