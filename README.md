# AI Calendar Groovy App

A fully functional AI-powered calendar application built with Groovy and JavaFX that allows users to manage events through both manual interactions and natural language AI commands.

## Features

### Core Calendar Management
- **Monthly Grid View**: Navigate through months with previous/next buttons
- **Event Display**: View events directly in calendar cells with detailed information
- **Day Selection**: Click on any day to view detailed events for that date

### Manual Event Management (CRUD)
- **Create Events**: Add new events via "New Event" dialog with title, date/time, and description
- **View Events**: Click on calendar days to see all events for that date
- **Edit Events**: Modify existing events through dedicated edit dialogs
- **Delete Events**: Remove events with confirmation dialogs
- **Real-time Updates**: Calendar view automatically refreshes after any changes

### AI-Powered Event Management
- **Natural Language Commands**: Interact with your calendar using conversational AI
- **AI Event Creation**: Ask the AI to create events (e.g., "Schedule a meeting tomorrow at 2 PM")
- **AI Event Updates**: Modify existing events through AI commands (e.g., "Change the meeting time to 3 PM")
- **AI Event Deletion**: Remove events via AI (e.g., "Delete the doctor appointment on June 16th")
- **Intelligent Parsing**: AI responses are parsed for embedded ACTION commands even within conversational text

### User Interface
- **Chat Interface**: Dedicated chat window for AI interactions
- **Daily Timeline View**: Scrollable hourly view of events for a selected day, with tooltips for full event details.
- **Modern Design**: Clean, intuitive interface with proper dialog management
- **Event Details**: Rich event information display with titles, times, and descriptions

## Tech Stack
- **Groovy**: Primary programming language
- **JavaFX**: Desktop UI framework
- **Gradle**: Build and dependency management
- **Apache HttpClient**: HTTP communication for AI API calls
- **OpenRouter AI**: AI service integration (configurable)

## Project Structure

```
src/main/groovy/com/aicalendar/
├── App.groovy              # Main application entry point
├── CalendarService.groovy  # Core calendar data management
├── ChatWindow.groovy       # Main UI with calendar grid, chat, and timeline integration
├── DailyTimelineView.groovy # UI component for the daily timeline
├── AIService.groovy        # AI integration and command parsing
├── Event.groovy           # Event data model (POGO)
└── AIResponsePayload.groovy # AI response wrapper
```

## Configuration

Set these environment variables for AI functionality:
- `AI_CALENDAR_API_ENDPOINT`: AI service endpoint (defaults to OpenRouter)
- `AI_CALENDAR_API_KEY`: Your API key for the AI service

## AI Command Format

The AI uses specific action formats for event management:
- **Create**: `ACTION: CREATE_EVENT title="Event Title" startTime="YYYY-MM-DDTHH:MM" endTime="YYYY-MM-DDTHH:MM" description="Optional description"`
- **Update**: `ACTION: UPDATE_EVENT eventId="event-id" title="New Title" startTime="YYYY-MM-DDTHH:MM" endTime="YYYY-MM-DDTHH:MM" description="New description"`
- **Delete**: `ACTION: DELETE_EVENT eventId="event-id"`

## Running the Application

```bash
gradle run
```

## Key Features Implemented
- Full CRUD operations for calendar events
- AI-driven event management with natural language processing
- Monthly calendar grid view with navigation
- Real-time calendar updates after any modification
- Comprehensive error handling and user feedback
- Event validation and proper date/time formatting
- Embedded ACTION command parsing from conversational AI responses





Made with ❤️ from Charlotte.
