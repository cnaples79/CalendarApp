package com.aicalendar

import groovy.transform.CompileStatic

@CompileStatic
class AIResponsePayload {
    String textResponse
    boolean eventCreated
    boolean eventModified
    Event event // Can be null if no specific event is associated with the response

    AIResponsePayload(String textResponse, boolean eventCreated, boolean eventModified, Event event = null) {
        this.textResponse = textResponse
        this.eventCreated = eventCreated
        this.eventModified = eventModified
        this.event = event
    }
}
