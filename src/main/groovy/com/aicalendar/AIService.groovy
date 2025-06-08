package com.aicalendar

import groovy.json.JsonSlurper
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.io.entity.EntityUtils
import java.time.LocalDateTime

class AIService {

    private CalendarService calendarService
    // Placeholder for a real AI API endpoint and key
    private final String AI_API_ENDPOINT = System.getenv("AI_CALENDAR_API_ENDPOINT") ?: "https://api.example.com/ai/calendar-chat"
    private final String AI_API_KEY = System.getenv("AI_CALENDAR_API_KEY") ?: "YOUR_API_KEY_HERE"

    AIService(CalendarService calendarService) {
        this.calendarService = calendarService
    }

    String getAIResponse(String userQuery) {
        // For now, simulate AI responses based on keywords
        // In a real app, this would make an HTTP call to an AI service

        if (AI_API_KEY == "YOUR_API_KEY_HERE" || AI_API_ENDPOINT.contains("api.example.com")) {
            println "WARN: Using mock AI response. Configure AI_CALENDAR_API_ENDPOINT and AI_CALENDAR_API_KEY environment variables for real AI interaction."
            return getMockAIResponse(userQuery)
        }

        try {
            try (def httpClient = HttpClients.createDefault()) {
                HttpPost request = new HttpPost(AI_API_ENDPOINT)
                request.setHeader("Authorization", "Bearer ${AI_API_KEY}")
                request.setHeader("Content-Type", "application/json")

                def payload = [
                    query: userQuery,
                    calendarEvents: calendarService.getAllEvents().collect { event ->
                        [
                            title: event.title,
                            startTime: ((LocalDateTime)event.startTime).toString(),
                            endTime: ((LocalDateTime)event.endTime).toString(),
                            description: event.description
                        ]
                    },
                    currentTime: LocalDateTime.now().toString()
                ]
                StringEntity entity = new StringEntity(new groovy.json.JsonOutput().toJson(payload))
                request.setEntity(entity)

                try (def response = httpClient.execute(request)) {
                    def responseBody = EntityUtils.toString(response.getEntity())
                    if (response.getCode() >= 200 && response.getCode() < 300) {
                        def jsonResponse = new JsonSlurper().parseText(responseBody)
                        return jsonResponse.aiSuggestion ?: "AI service did not provide a suggestion."
                    } else {
                        return "Error communicating with AI service: ${response.getReasonPhrase()} - ${responseBody}"
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace()
            return "Error connecting to AI service: ${e.message}. Falling back to mock response.\n${getMockAIResponse(userQuery)}"
        }
    }

    private String getMockAIResponse(String userQuery) {
        def query = userQuery.toLowerCase()
        if (query.contains("hello") || query.contains("hi")) {
            return "Hello! How can I help you with your calendar today?"
        }
        if (query.contains("what's on my calendar") || query.contains("show events")) {
            def events = calendarService.getAllEvents()
            if (events.isEmpty()) {
                return "Your calendar is empty."
            }
            def response = "Here are your upcoming events:\n"
            events.each { event ->
                response += "- ${calendarService.formatEvent(event)}\n"
            }
            return response
        }
        if (query.contains("add event") || query.contains("schedule")) {
            return "Sure, I can help with that. What is the event title, date, time, and description? (Suggestion: You can say 'Schedule a meeting for tomorrow at 2 PM titled Project Sync')"
        }
        if (query.contains("suggest")) {
            return "I can suggest optimal times for new events or help you organize your schedule. What are you trying to plan?"
        }
        if (query.contains("theme")) {
            return "I'm currently rocking a cool black and blue theme! What do you think?"
        }
        return "I'm still learning! I can show your events or help schedule new ones. Try asking 'What's on my calendar?' or 'Schedule a new event.'"
    }

    // TODO: Implement methods to parse AI responses and apply suggestions to the CalendarService
    // For example, if AI suggests creating an event, parse the details and call calendarService.addEvent(...)
}
