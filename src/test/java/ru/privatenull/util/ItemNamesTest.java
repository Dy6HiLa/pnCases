package ru.privatenull.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemNamesTest {

    @Test
    void humanizesUnknownMaterialNames() {
        assertEquals("Nether quartz ore", ItemNames.humanizeMaterial("NETHER_QUARTZ_ORE"));
    }
}
