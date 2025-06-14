package com.aicalendar

import groovy.transform.CompileStatic
import java.time.LocalDateTime

@CompileStatic
class Event {
    String title
    LocalDateTime startTime
    LocalDateTime endTime
    String description

    Event(String title, LocalDateTime startTime, LocalDateTime endTime, String description) {
        this.title = title
        this.startTime = startTime
        this.endTime = endTime
        this.description = description
    }

    // Groovy automatically provides getters and setters for properties
    // For @CompileStatic, explicit getters might be preferred by some tools or for clarity,
    // but are not strictly necessary for PropertyValueFactory if properties are public or Groovy's convention is recognized.
    // We'll rely on Groovy's automatic getters for now.

    @Override
    String toString() {
        return "Event{title='${title}', startTime=${startTime}, endTime=${endTime}, description='${description}'}"
    }
}
