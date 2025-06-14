package com.aicalendar

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import groovy.transform.CompileStatic

@CompileStatic
class CalendarService {

    private List<Event> events = []
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    CalendarService() {
        // Initialize with some mock events
        events.add(new Event("Team Meeting", LocalDateTime.now().plusDays(1).withHour(10).withMinute(0), LocalDateTime.now().plusDays(1).withHour(11).withMinute(0), "Discuss project updates"))
        events.add(new Event("Doctor Appointment", LocalDateTime.now().plusDays(2).withHour(14).withMinute(30), LocalDateTime.now().plusDays(2).withHour(15).withMinute(0), "Annual check-up"))
        events.add(new Event("Lunch with Alex", LocalDateTime.now().plusHours(3), LocalDateTime.now().plusHours(4), "Catch up at The Italian Place"))
        events.add(new Event("Groovy Project Work", LocalDateTime.now().plusDays(1).withHour(15).withMinute(0), LocalDateTime.now().plusDays(1).withHour(18).withMinute(0), "Focus session on AI Calendar app"))
        println "CalendarService: Initialized with ${events.size()} mock events."
    }

    void addEvent(String title, LocalDateTime startTime, LocalDateTime endTime, String description) {
        Event newEvent = new Event(title, startTime, endTime, description)
        events.add(newEvent)
        println "CalendarService: Added event: ${newEvent}"
        println "CalendarService: Total events now: ${events.size()}"
    }

    void addEvent(Event event) { // Overloaded method to directly add an Event object
        events.add(event)
        println "CalendarService: Added event object: ${event}"
        println "CalendarService: Total events now: ${events.size()}"
    }

    List<Event> getEvents(LocalDateTime from, LocalDateTime to) {
        return events.findAll { Event event -> // Explicitly type event here
            !(event.startTime.isAfter(to) || event.endTime.isBefore(from))
        }
    }

    List<Event> getAllEvents() {
        println "CalendarService: getAllEvents called. Returning ${events.size()} events: ${events}"
        return new ArrayList<>(events) // Return a copy
    }

    boolean updateEvent(String eventId, Event eventWithNewDetails) {
        int eventIndex = events.findIndexOf { it.id == eventId }
        if (eventIndex != -1) {
            Event existingEvent = events.get(eventIndex)
            existingEvent.title = eventWithNewDetails.title
            existingEvent.startTime = eventWithNewDetails.startTime
            existingEvent.endTime = eventWithNewDetails.endTime
            existingEvent.description = eventWithNewDetails.description
            println "CalendarService: Updated event ID ${eventId}: ${existingEvent}"
            return true
        } else {
            println "CalendarService: Update failed. Event ID ${eventId} not found."
            return false
        }
    }

    boolean deleteEvent(String eventId) {
        boolean removed = events.removeIf { it.id == eventId }
        if (removed) {
            println "CalendarService: Deleted event ID ${eventId}"
        } else {
            println "CalendarService: Delete failed. Event ID ${eventId} not found."
        }
        return removed
    }

    String formatEvent(Event event) {
        "'${event.title}' from ${event.startTime.format(formatter)} to ${event.endTime.format(formatter)}. Description: ${event.description ?: 'N/A'}"
    }

    // TODO: Add methods to modify and delete events
    // TODO: Integrate with a real calendar API (Google Calendar, Outlook, etc.)
}
