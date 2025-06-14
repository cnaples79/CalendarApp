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

@CompileStatic
class AIResponsePayload {
    String textResponse
    boolean eventCreated
    Map<String, Object> createdEventDetails // Optional

    AIResponsePayload(String textResponse, boolean eventCreated = false, Map<String, Object> createdEventDetails = null) {
        this.textResponse = textResponse
        this.eventCreated = eventCreated
        this.createdEventDetails = createdEventDetails
    }
}

@CompileStatic
class AIService {

    private CalendarService calendarService
    private final String AI_API_ENDPOINT = System.getenv("AI_CALENDAR_API_ENDPOINT") ?: "https://openrouter.ai/api/v1/chat/completions"
    private final String AI_API_KEY = System.getenv("AI_CALENDAR_API_KEY") ?: "YOUR_API_KEY_HERE"

    private static final Pattern CREATE_EVENT_PATTERN = Pattern.compile(
        "ACTION: CREATE_EVENT title=\"(.*?)\" startTime=\"(.*?)\" endTime=\"(.*?)\"(?: description=\"(.*?)\")?"
    )

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
                def eventsSummary = calendarService.getAllEvents().collect { event ->
                    "- ${event.title} from ${((LocalDateTime)event.startTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)} to ${((LocalDateTime)event.endTime).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)} (${event.description ?: 'No description'})"
                }.join('\n')
                if (eventsSummary.isEmpty()) {
                    eventsSummary = "User's calendar is currently empty."
                }

                def systemMessageContent = """Current time is ${now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}. User's calendar events:
${eventsSummary}
You are an AI assistant helping the user manage their calendar. Be concise.
When you decide to create a calendar event based on the user's request, you MUST include a line in your response formatted EXACTLY as follows (do not surround it with backticks or any other formatting):
ACTION: CREATE_EVENT title="<event_title>" startTime="<YYYY-MM-DDTHH:MM>" endTime="<YYYY-MM-DDTHH:MM>" description="<event_description>"
Replace <event_title>, <YYYY-MM-DDTHH:MM> (for both start and end times, using 24-hour format), and <event_description> with the actual details.
The description is optional. If no description, you can omit the description part entirely (e.g., ACTION: CREATE_EVENT title="Meeting" startTime="2024-07-01T10:00" endTime="2024-07-01T11:00") or provide an empty one (description="").
Ensure date and time formats are strictly YYYY-MM-DDTHH:MM. Provide your normal conversational response before or after this ACTION line.
Do not add any extra fields to the ACTION line.
"""

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
                                    return new AIResponsePayload("AI service response format unexpected (missing message content): ${responseBody}", false)
                                }
                            } else {
                                return new AIResponsePayload("AI service response format unexpected (empty choice): ${responseBody}", false)
                            }
                        } else {
                            return new AIResponsePayload("AI service response format unexpected (no choices): ${responseBody}", false)
                        }

                        Matcher matcher = CREATE_EVENT_PATTERN.matcher(aiTextResponse)
                        if (matcher.find()) {
                            try {
                                String title = matcher.group(1)
                                String startTimeStr = matcher.group(2)
                                String endTimeStr = matcher.group(3)
                                String description = matcher.group(4) ?: ""

                                LocalDateTime startTime = LocalDateTime.parse(startTimeStr) // Assumes YYYY-MM-DDTHH:MM
                                LocalDateTime endTime = LocalDateTime.parse(endTimeStr)   // Assumes YYYY-MM-DDTHH:MM
                                
                                calendarService.addEvent(title, startTime, endTime, description)
                                String confirmationMessage = "OK, I've added '${title}' to your calendar from ${startTime.format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))} to ${endTime.format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))}."
                                
                                String cleanedAiTextResponse = aiTextResponse.replace(matcher.group(0), "").trim()
                                if (cleanedAiTextResponse.isEmpty() && !confirmationMessage.isEmpty()) {
                                    cleanedAiTextResponse = confirmationMessage
                                } else if (!cleanedAiTextResponse.isEmpty() && !confirmationMessage.isEmpty()) {
                                    cleanedAiTextResponse = confirmationMessage + "\n" + cleanedAiTextResponse
                                } else if (cleanedAiTextResponse.isEmpty() && confirmationMessage.isEmpty()) {
                                    cleanedAiTextResponse = "Event created."
                                }
                                Map<String, Object> eventDetailsMap = new HashMap<String, Object>()
                                eventDetailsMap.put("title", title)
                                eventDetailsMap.put("startTime", startTime)
                                eventDetailsMap.put("endTime", endTime)
                                eventDetailsMap.put("description", description)
                                return new AIResponsePayload(cleanedAiTextResponse, true, eventDetailsMap)
                            } catch (DateTimeParseException e) {
                                e.printStackTrace()
                                return new AIResponsePayload("AI tried to create an event, but there was an error with the date/time format: ${e.getMessage()}. Original AI response: ${aiTextResponse}", false)
                            } catch (Exception e) {
                                e.printStackTrace()
                                return new AIResponsePayload("Error processing AI action to create event: ${e.getMessage()}. Original AI response: ${aiTextResponse}", false)
                            }
                        }
                        return new AIResponsePayload(aiTextResponse, false)
                    } else {
                        return new AIResponsePayload("Error communicating with AI service: ${response.getReasonPhrase()} - ${responseBody}", false)
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace()
            return new AIResponsePayload("Error connecting to AI service: ${e.message}. Falling back to mock response.\n${getMockAIResponse(userQuery).textResponse}", false)
        }
    }

    AIResponsePayload getMockAIResponse(String userQuery) {
        def query = userQuery.toLowerCase()
        String textResponse
        if (query.contains("hello") || query.contains("hi")) {
            textResponse = "Hello! How can I help you with your calendar today?"
        } else if (query.contains("what's on my calendar") || query.contains("show events")) {
            def events = calendarService.getAllEvents()
            if (events.isEmpty()) {
                textResponse = "Your calendar is empty."
            } else {
                textResponse = "Here are your upcoming events:\n"
                events.each { event ->
                    textResponse += "- ${calendarService.formatEvent(event)}\n"
                }
            }
        } else if (query.contains("add event") || query.contains("schedule")) {
            textResponse = "Sure, I can help with that. What is the event title, date (YYYY-MM-DD), start time (HH:MM), end time (HH:MM), and description? For example: 'Schedule a meeting for 2024-07-15 at 14:00 until 15:00 titled Project Sync about our progress.'"
        } else if (query.contains("suggest")) {
            textResponse = "I can suggest optimal times for new events or help you organize your schedule. What are you trying to plan?"
        } else if (query.contains("theme")) {
            textResponse = "I'm currently rocking a cool black and blue theme! What do you think?"
        } else {
            textResponse = "I'm still learning! I can show your events or help schedule new ones. Try asking 'What's on my calendar?' or 'Schedule a new event.'"
        }
        return new AIResponsePayload(textResponse, false)
    }
}
