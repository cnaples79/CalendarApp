package com.aicalendar

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CalendarService {

    private List<Map<String, Object>> events = []
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    CalendarService() {
        // Initialize with some mock events
        addEvent("Team Meeting", LocalDateTime.now().plusDays(1).withHour(10).withMinute(0), LocalDateTime.now().plusDays(1).withHour(11).withMinute(0), "Discuss project updates")
        addEvent("Doctor Appointment", LocalDateTime.now().plusDays(2).withHour(14).withMinute(30), LocalDateTime.now().plusDays(2).withHour(15).withMinute(0), "Annual check-up")
        addEvent("Lunch with Alex", LocalDateTime.now().plusHours(3), LocalDateTime.now().plusHours(4), "Catch up at The Italian Place")
        addEvent("Groovy Project Work", LocalDateTime.now().plusDays(1).withHour(15).withMinute(0), LocalDateTime.now().plusDays(1).withHour(18).withMinute(0), "Focus session on AI Calendar app")
    }

    void addEvent(String title, LocalDateTime startTime, LocalDateTime endTime, String description) {
        Map<String, Object> newEvent = [
            title: title,
            startTime: startTime,
            endTime: endTime,
            description: description
        ]
        events.add(newEvent)
        println "CalendarService: Added event: ${newEvent}"
        println "CalendarService: Total events now: ${events.size()}"
    }

    List<Map<String, Object>> getEvents(LocalDateTime from, LocalDateTime to) {
        return events.findAll { event ->
            def eventStart = (LocalDateTime) event.startTime
            def eventEnd = (LocalDateTime) event.endTime
            !(eventStart.isAfter(to) || eventEnd.isBefore(from))
        }
    }

    List<Map<String, Object>> getAllEvents() {
        println "CalendarService: getAllEvents called. Returning ${events.size()} events: ${events}"
        return new ArrayList<>(events) // Return a copy
    }

    String formatEvent(Map<String, Object> event) {
        "'${event.title}' from ${((LocalDateTime)event.startTime).format(formatter)} to ${((LocalDateTime)event.endTime).format(formatter)}. Description: ${event.description ?: 'N/A'}"
    }

    // TODO: Add methods to modify and delete events
    // TODO: Integrate with a real calendar API (Google Calendar, Outlook, etc.)
}
