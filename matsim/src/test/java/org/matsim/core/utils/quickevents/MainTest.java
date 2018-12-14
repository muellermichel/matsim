package org.matsim.core.utils.quickevents;

import org.junit.Test;

public class MainTest {

    @Test
    public static void main(String[] args) {
        EventValidationTest.testEventLogReadWrite();
        EventValidationTest.testEventLogTimingError();
        EventValidationTest.testEventLogError();
//        EventValidationTest.testEventLogReadWriteBerlin();
    }
}
