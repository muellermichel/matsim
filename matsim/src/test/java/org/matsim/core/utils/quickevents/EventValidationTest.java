package org.matsim.core.utils.quickevents;

public class EventValidationTest {
    public static void testEventLogReadWrite() {
        try {
            StringlyEvents events = StringlyEventlogTool.readXMLFile("sample-events.xml");
            StringlyEventlogTool.writeXMLFile("sample-events-output.xml", events);
            StringlyEvents newEvents = StringlyEventlogTool.readXMLFile("sample-events-output.xml");
            StringlyEventlogTool.validate(newEvents, events, true);
            StringlyEventlogTool.printEventTypesAndExamples(events);
        }
        catch (ValidationException e) {
            throw new RuntimeException(e);
        }
        System.out.println("sample-events.xml can be read and represented correctly");
    }

    public static void testEventLogTimingError() {
        try {
            StringlyEventlogTool.validate(
                StringlyEventlogTool.readXMLFile("sample-events-timing-error.xml"),
                StringlyEventlogTool.readXMLFile("sample-events.xml"),
                true
            );
            throw new RuntimeException("timing error was not caught");
        }
        catch (ValidationException e) {
            System.out.println("sample-events-timing-error.xml caught correctly: " + e.getMessage());
        }

        try {
            StringlyEventlogTool.validate(
                StringlyEventlogTool.readXMLFile("sample-events-timing-error.xml"),
                StringlyEventlogTool.readXMLFile("sample-events.xml"),
                false
            );
            System.out.println("timing error was correctly omitted");
        }
        catch (ValidationException e) {
            throw new RuntimeException("timing error was caught where it shouldn't have: " + e.getMessage());
        }
    }

    public static void testEventLogError() {
        try {
            StringlyEventlogTool.validate(
                StringlyEventlogTool.readXMLFile("sample-events-error.xml"),
                StringlyEventlogTool.readXMLFile("sample-events.xml"),
                false
            );
            throw new RuntimeException("error was not caught");
        }
        catch (ValidationException e) {
            System.out.println("sample-events-error.xml caught correctly: " + e.getMessage());
        }
    }

    public static void testEventLogReadWriteBerlin() {
        try {
            System.out.println("parsing berlin");
            StringlyEvents events = StringlyEventlogTool.readGzipXMLFile("berlin-v5.1-1pct.0.events.xml.gz");
            System.out.println("writing out berlin");
            StringlyEventlogTool.writeXMLFile("berlin-events-output.xml", events);
            System.out.println("parsing berlin again");
            StringlyEvents newEvents = StringlyEventlogTool.readXMLFile("berlin-events-output.xml");
            System.out.println("validating the two versions");
            StringlyEventlogTool.validate(newEvents, events, true);
            StringlyEventlogTool.printEventTypesAndExamples(events);
        }
        catch (ValidationException e) {
            throw new RuntimeException(e);
        }
        System.out.println("berlin-v5.1-1pct.0.events.xml.gz can be read and represented correctly");
    }
}
