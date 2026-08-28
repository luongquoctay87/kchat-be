package com.kchat.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidOrderTest {

    @Test
    void matchesPostgresOrderForKnownFailingPair() {
        UUID e497 = UUID.fromString("497e5d92-1bb5-4952-a1f8-639e029ee672");
        UUID f335 = UUID.fromString("f3354492-3f71-4f56-b30d-810a5ddd3ed2");

        assertTrue(UuidOrder.compare(f335, e497) > 0, "PostgreSQL treats f335 as larger");
        assertTrue(f335.compareTo(e497) < 0, "Java compareTo treats f335 as smaller");

        assertEquals(e497, UuidOrder.smaller(f335, e497));
        assertEquals(f335, UuidOrder.larger(f335, e497));
    }

    @Test
    void smallerAndLargerAreConsistent() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(UuidOrder.compare(UuidOrder.smaller(a, b), UuidOrder.larger(a, b)) <= 0);
    }
}
