package com.aicalendar

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.io.entity.EntityUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.regex.Matcher
import java.util.regex.Pattern
import com.aicalendar.Event

@CompileStatic
class AIService {

    private CalendarService calendarService
    private final String AI_API_ENDPOINT = System.getenv("AI_CALENDAR_API_ENDPOINT") ?: "https://openrouter.ai/api/v1/chat/completions"
    private final String AI_API_KEY = System.getenv("AI_CALENDAR_API_KEY") ?: "YOUR_API_KEY_HERE"

    AIService(CalendarService calendarService) {
        this.calendarService = calendarService
    }

    AIResponsePayload getAIResponse(String userQuery) {
        if (AI_API_KEY == "YOUR_API_KEY_HERE" || AI_API_ENDPOINT.contains("api.example.com")) {
            println "WARN: Using mock AI response. Configure AI_CALENDAR_API_ENDPOINT and AI_CALENDAR_API_KEY environment variables for real AI interaction."
            return getMockAIResponse(userQuery)
        }

        try {
            try (def httpClient = HttpClients.createDefault()) {
                HttpPost request = new HttpPost(AI_API_ENDPOINT)
                request.setHeader("Authorization", "Bearer ${AI_API_KEY}")
                request.setHeader("Content-Type", "application/json")

                def now = LocalDateTime.now()
                def eventsSummary = getCalendarEventsWithIdsAsString()

                def systemMessageContent = """
            You are a helpful calendar assistant. Your goal is to help the user manage their calendar. 
            
            Available Actions:
            1. Create an event: 
               ACTION: CREATE_EVENT title="<event_title>" startTime="<YYYY-MM-DDTHH:MM>" endTime="<YYYY-MM-DDTHH:MM>" description="<event_description>"
               (description is optional; title, startTime, endTime are mandatory)
            2. Update an existing event:
               ACTION: UPDATE_EVENT eventId="<event_id_to_update>" title="<new_title>" startTime="<YYYY-MM-DDTHH:MM>" endTime="<YYYY-MM-DDTHH:MM>" description="<new_description>"
               (eventId is mandatory; title, startTime, endTime, description are optional - only include fields you want to change)
            3. Delete an existing event:
               ACTION: DELETE_EVENT eventId="<event_id_to_delete>"
               (eventId is mandatory)

            When you perform an action, use the specified format and nothing else in that part of your response. 
            You MUST use the correct eventId when updating or deleting an event. Event IDs will be provided when events are listed.
            For all other interactions, or if providing text alongside an action, respond as a helpful assistant normally would.
            
            Current calendar events (with IDs):
            ${eventsSummary}
            """.stripIndent()

                def payload = [
                    model: "deepseek/deepseek-r1-0528:free", 
                    messages: [
                        [role: "system", content: systemMessageContent],
                        [role: "user", content: userQuery]
                    ],
                    stream: false
                ]
                StringEntity entity = new StringEntity(new groovy.json.JsonOutput().toJson(payload))
                request.setEntity(entity)

                try (def response = httpClient.execute(request)) {
                    def responseBody = EntityUtils.toString(response.getEntity())
                    if (response.getCode() >= 200 && response.getCode() < 300) {
                        Map<String, Object> jsonResponse = (Map<String, Object>) new JsonSlurper().parseText(responseBody)
                        String aiTextResponse = ""

                        List choices = (List) jsonResponse.get("choices")
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> firstChoice = (Map<String, Object>) choices.get(0)
                            if (firstChoice != null) {
                                Map<String, Object> message = (Map<String, Object>) firstChoice.get("message")
                                if (message != null && message.get("content") instanceof String) {
                                    aiTextResponse = (String) message.get("content")
                                } else {
                                    return new AIResponsePayload("AI service response format unexpected (missing message content): ${responseBody}", false, false)
                                }
                            } else {
                                return new AIResponsePayload("AI service response format unexpected (empty choice): ${responseBody}", false, false)
                            }
                        } else {
                            return new AIResponsePayload("AI service response format unexpected (no choices): ${responseBody}", false, false)
                        }

                        // Regex to find ACTION command and surrounding text
                        def actionMatcher = (aiTextResponse =~ /(?s)(.*?)(ACTION: (?:CREATE_EVENT|UPDATE_EVENT|DELETE_EVENT)[^\n]*)(.*)/)

                        if (actionMatcher.find()) {
                            String textBeforeAction = actionMatcher.group(1)?.trim()
                            String action = actionMatcher.group(2).trim() // The full ACTION: ... command, stopping at newline
                            String textAfterAction = actionMatcher.group(3)?.trim()

                            println "AIService: Found ACTION command: ${action}"
                            println "AIService: Text before action: '${textBeforeAction}'"
                            println "AIService: Text after action: '${textAfterAction}'"

                            String textForDisplay = textBeforeAction ?: (textAfterAction ?: "Action processed.")
                            if (textForDisplay.isEmpty()) textForDisplay = "Action processed."
                            if (textForDisplay.startsWith("ACTION:")) textForDisplay = "Action processed." // Avoid showing action as text

                            try {
                                // Attempt to parse and execute the action
                                if (action.startsWith("ACTION: CREATE_EVENT")) {
                                    def matcher = action =~ /ACTION: CREATE_EVENT title="([^"]+)" startTime="([^"]+)" endTime="([^"]+)"(?: description="([^"]*)")?/
                                    if (matcher.find()) {
                                        String title = matcher.group(1)
                                        LocalDateTime startTime = LocalDateTime.parse(matcher.group(2), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                        LocalDateTime endTime = LocalDateTime.parse(matcher.group(3), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                        String description = matcher.group(4) ?: ""
                                        Event newEvent = new Event(title, startTime, endTime, description)
                                        calendarService.addEvent(newEvent)
                                        // Use the AI's conversational text if available and sensible, otherwise generate one.
                                        String confirmationText = (textForDisplay != "Action processed.") ? textForDisplay : "OK. I've added '${title}' to your calendar."
                                        return new AIResponsePayload(confirmationText, true, false, newEvent)
                                    } else {
                                        return new AIResponsePayload("I tried to create an event, but the command format was incorrect. Command: ${action}", false, false)
                                    }
                                } else if (action.startsWith("ACTION: UPDATE_EVENT")) {
                                    def matcher = aiTextResponse =~ /ACTION: UPDATE_EVENT eventId="([^"]+)"(?: title="([^"]*)")?(?: startTime="([^"]*)")?(?: endTime="([^"]*)")?(?: description="([^"]*)")?/
                                    if (matcher.find()) {
                                        String eventId = matcher.group(1)
                                        println "AIService: UPDATE - Extracted eventId: ${eventId}"
                                        Event existingEvent = calendarService.getAllEvents().find { it.id == eventId }
                                        println "AIService: UPDATE - Found existingEvent: ${existingEvent}"
                                        if (!existingEvent) {
                                            return new AIResponsePayload("I tried to update an event, but I couldn't find an event with ID ${eventId}.", false, false)
                                        }
                                        try {
                                            // Create a new event object with potentially updated fields, using existing values as defaults
                                            String newTitle = matcher.group(2) ?: existingEvent.title
                                            LocalDateTime newStartTime = matcher.group(3) ? LocalDateTime.parse(matcher.group(3), DateTimeFormatter.ISO_LOCAL_DATE_TIME) : existingEvent.startTime
                                            LocalDateTime newEndTime = matcher.group(4) ? LocalDateTime.parse(matcher.group(4), DateTimeFormatter.ISO_LOCAL_DATE_TIME) : existingEvent.endTime
                                            String newDescription = matcher.group(5) // if null, keep existing; if empty string, clear it
                                            if (newDescription == null) newDescription = existingEvent.description // keep existing if not provided

                                            Event updatedEventDetails = new Event(newTitle, newStartTime, newEndTime, newDescription, eventId) // Pass ID to maintain it
                                            println "AIService: UPDATE - Constructed updatedEventDetails: ${updatedEventDetails}"

                                            boolean success = calendarService.updateEvent(eventId, updatedEventDetails)
                                            println "AIService: UPDATE - CalendarService.updateEvent success: ${success}"
                                            if (success) {
                                                String confirmationText = "OK. I've updated the event '${updatedEventDetails.title}'."
                                                AIResponsePayload payloadToReturn = new AIResponsePayload(confirmationText, false, true, updatedEventDetails)
                                                println "AIService: UPDATE - Returning payload: ${payloadToReturn} (eventModified=${payloadToReturn.eventModified})"
                                                return payloadToReturn
                                            } else {
                                                return new AIResponsePayload("I tried to update event ID ${eventId}, but something went wrong.", false, false)
                                            }
                                        } catch (DateTimeParseException e) { // Specific to UPDATE_EVENT date parsing
                                            return new AIResponsePayload("I tried to update an event, but the date/time format was incorrect: ${e.message}", false, false)
                                        }
                                    } else {
                                        return new AIResponsePayload("I tried to update an event, but the command format was incorrect.", false, false)
                                    }
                                } else if (action.startsWith("ACTION: DELETE_EVENT")) {
                                    def matcher = action =~ /ACTION: DELETE_EVENT eventId="([^"]+)"/
                                    if (matcher.find()) {
                                        String eventId = matcher.group(1)
                                        println "AIService: DELETE - Extracted eventId: ${eventId}"
                                        boolean success = calendarService.deleteEvent(eventId)
                                        println "AIService: DELETE - CalendarService.deleteEvent success: ${success}"
                                        if (success) {
                                            String confirmationText = "OK. I've deleted the event with ID ${eventId}."
                                            AIResponsePayload payloadToReturn = new AIResponsePayload(confirmationText, false, true)
                                            println "AIService: DELETE - Returning payload: ${payloadToReturn} (eventModified=${payloadToReturn.eventModified})"
                                            return payloadToReturn
                                        } else {
                                            return new AIResponsePayload("I tried to delete event ID ${eventId}, but I couldn't find it or something went wrong.", false, false)
                                        }
                                    } else {
                                        return new AIResponsePayload("I tried to delete an event, but the command format was incorrect.", false, false)
                                    }
                                } else {
                                    // Unknown action or malformed
                                    println "AIService: Unknown or malformed action: ${action}"
                                    return new AIResponsePayload("I received an action I didn't understand or it was formatted incorrectly: ${action}", false, false)
                                }
                            } catch (DateTimeParseException e) {
                                // This catch is for general DateTimeParseExceptions during action processing (e.g., from CREATE_EVENT)
                                e.printStackTrace()
                                return new AIResponsePayload("There was an error with the date/time format for an event action: ${e.getMessage()}. Original AI response: ${aiTextResponse}", false, false)
                            } catch (Exception e) {
                                // General catch for other errors during action processing
                                e.printStackTrace()
                                return new AIResponsePayload("Error processing AI action: ${e.getMessage()}. Original AI response: ${aiTextResponse}", false, false)
                            }
                        } else {
                            // No ACTION: command found anywhere in the response
                            println "AIService: No ACTION command found in AI Response (full text): ${aiTextResponse}"
                            return new AIResponsePayload(aiTextResponse, false, false)
                        }

                    } else {
                        return new AIResponsePayload("Error communicating with AI service: ${response.getReasonPhrase()} - ${responseBody}", false, false)
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace()
            return new AIResponsePayload("Error connecting to AI service: ${e.message}. Falling back to mock response.\n${getMockAIResponse(userQuery).textResponse}", false, false)
        }
    }

    private String getCalendarEventsWithIdsAsString() {
        List<Event> events = calendarService.getAllEvents()
        if (events.isEmpty()) {
            return "No events scheduled."
        }
        return events.collect { event ->
            "- ID: ${event.id}, Title: ${event.title}, Start: ${event.startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}, End: ${event.endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}${event.description ? ', Desc: ' + event.description : ''}"
        }.join("\n")
    }

    AIResponsePayload getMockAIResponse(String userQuery) {
        def query = userQuery.toLowerCase()
        String textResponse
        boolean eventCreated = false
        boolean eventModified = false
        Event anEvent = null

        if (query.contains("create event") || query.contains("add event")) {
            if (query.contains("meeting tomorrow at 10am")) {
                LocalDateTime startTime = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0)
                LocalDateTime endTime = startTime.plusHours(1)
                anEvent = new Event("Team Meeting", startTime, endTime, "Discuss project updates")
                calendarService.addEvent(anEvent)
                textResponse = "ACTION: CREATE_EVENT title=\"Team Meeting\" startTime=\"${startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}\" endTime=\"${endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}\" description=\"Discuss project updates\"\nOK, I've added 'Team Meeting' to your calendar for tomorrow at 10 AM."
                eventCreated = true
            } else {
                textResponse = "I can help with that. What are the details of the event? (Mock response)"
            }
        } else if (query.contains("what's on my calendar")) {
            List<Event> events = calendarService.getAllEvents()
            if (events.isEmpty()) {
                textResponse = "Your calendar is empty. (Mock response)"
            } else {
                textResponse = "Here are your upcoming events (Mock response):\n" +
                               events.collect { "- ID: ${it.id}, ${it.title} on ${it.startTime.toLocalDate()} from ${it.startTime.toLocalTime()} to ${it.endTime.toLocalTime()}" }.join("\n")
            }
        } else {
            textResponse = "I'm a mock AI. I can only create a 'Team Meeting tomorrow at 10am' or show events. You said: ${userQuery}"
        }
        return new AIResponsePayload(textResponse, eventCreated, eventModified, anEvent)
    }
}
