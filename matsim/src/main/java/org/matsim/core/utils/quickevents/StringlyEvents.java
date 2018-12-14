package org.matsim.core.utils.quickevents;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.ArrayList;
import java.util.List;

@JacksonXmlRootElement(localName = "events")
public class StringlyEvents {
    @JacksonXmlProperty(localName = "version", isAttribute = true)
    public String version;

    @JacksonXmlProperty(localName = "event")
    @JacksonXmlElementWrapper(useWrapping = false)
    public List<StringlyEvent> events = new ArrayList<>();

    @JsonSetter
    public void setEvent(StringlyEvent event) {
        this.events.add(event);
    }
}
