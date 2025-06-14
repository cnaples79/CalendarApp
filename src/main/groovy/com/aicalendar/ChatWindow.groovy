package com.aicalendar

import groovy.transform.CompileStatic
import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.scene.layout.GridPane
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.Priority
import javafx.scene.control.SplitPane
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import java.time.LocalDateTime
import com.aicalendar.Event
import java.time.format.DateTimeFormatter
import java.time.YearMonth
import java.time.DayOfWeek
import javafx.scene.control.Alert
import javafx.scene.control.DialogPane
import javafx.scene.layout.Region

@CompileStatic
class ChatWindow extends Application {

    private VBox chatMessagesContainer
    private TextField inputField
    private AIService aiService
    private CalendarService calendarService
    private ScrollPane scrollPane // Declare scrollPane as a field
    // Fields for the new monthly calendar view
    private GridPane calendarGrid
    private Label monthYearLabel
    private YearMonth currentYearMonth

    @Override
    void start(Stage primaryStage) throws Exception {
        calendarService = new CalendarService()
        aiService = new AIService(calendarService)

        primaryStage.title = "AI Calendar Chat"

        BorderPane root = new BorderPane()
        root.styleClass.add("root")

        // Chat display area
        chatMessagesContainer = new VBox(5)
        chatMessagesContainer.padding = new Insets(10)
        chatMessagesContainer.alignment = Pos.TOP_LEFT

        scrollPane = new ScrollPane(chatMessagesContainer) // Initialize the field
        scrollPane.fitToWidth = true
        scrollPane.hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        scrollPane.vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED

        // --- New Monthly Calendar View Setup ---
        currentYearMonth = YearMonth.now()

        monthYearLabel = new Label()
        monthYearLabel.styleClass.add("month-year-label")

        Button prevMonthButton = new Button("< Prev")
        prevMonthButton.onAction = { event -> changeMonth(-1) }
        Button nextMonthButton = new Button("Next >")
        nextMonthButton.onAction = { event -> changeMonth(1) }

        HBox monthNavigation = new HBox(10, prevMonthButton, monthYearLabel, nextMonthButton)
        monthNavigation.alignment = Pos.CENTER
        monthNavigation.padding = new Insets(5)

        calendarGrid = new GridPane()
        calendarGrid.styleClass.add("calendar-grid")
        calendarGrid.hgap = 2
        calendarGrid.vgap = 2
        // Make columns equally sized
        for (int i = 0; i < 7; i++) {
            ColumnConstraints colConst = new ColumnConstraints()
            colConst.setPercentWidth((double)(100.0 / 7))
            calendarGrid.getColumnConstraints().add(colConst)
        }

        VBox calendarLayout = new VBox(10, monthNavigation, createDayOfWeekHeader(), calendarGrid)
        calendarLayout.padding = new Insets(10)
        calendarLayout.styleClass.add("calendar-layout")

        populateCalendarGrid()
        // --- End New Monthly Calendar View Setup ---

        // SplitPane to hold chat and new calendar layout
        SplitPane splitPane = new SplitPane()
        splitPane.getItems().addAll(scrollPane, calendarLayout) // Use calendarLayout instead of calendarView
        splitPane.setDividerPositions(0.5d) // Initial 50/50 split

        root.center = splitPane

        // Input area
        inputField = new TextField()
        inputField.promptText = "Ask your AI assistant about your calendar..."
        inputField.onAction = { event -> sendMessage() }

        Button sendButton = new Button("Send")
        sendButton.onAction = { event -> sendMessage() }

        HBox inputArea = new HBox(10, inputField, sendButton)
        inputArea.padding = new Insets(10)
        inputArea.alignment = Pos.CENTER
        root.bottom = inputArea

        Scene scene = new Scene(root, 1200, 700) // Increased width for calendar view
        try {
            String cssPath = getClass().getResource("/com/aicalendar/ui/styles.css")?.toExternalForm()
            if (cssPath != null) {
                scene.stylesheets.add(cssPath)
            } else {
                System.err.println("Could not load stylesheet: styles.css")
            }
        } catch (Exception e) {
            System.err.println("Error loading stylesheet: " + e.getMessage())
            e.printStackTrace()
        }
        
        primaryStage.scene = scene
        primaryStage.show()

        // Initial greeting
        addMessageToChat("AI", "Welcome to your AI Calendar! How can I assist you today?", false)
    }

    private void sendMessage() {
        String userMessage = inputField.text.trim()
        if (userMessage.isEmpty()) return

        addMessageToChat("You", userMessage, true)
        inputField.clear()

        // Simulate AI thinking and get response
        // In a real app, this might be an async call
        Platform.runLater {
            AIResponsePayload payload = aiService.getAIResponse(userMessage)
            addMessageToChat("AI", payload.textResponse, false)
            if (payload.eventCreated) {
                populateCalendarGrid() // Update to call the new method
            }
        }
    }

    private void addMessageToChat(String sender, String message, boolean isUser) {
        Label senderLabel = new Label(sender + ":")
        senderLabel.style = "-fx-font-weight: bold;"
        
        TextArea messageArea = new TextArea(message)
        messageArea.setEditable(false)
        messageArea.setWrapText(true)
        messageArea.setPrefRowCount(message.split("\n").length + 1)
        messageArea.setMinHeight(TextArea.USE_PREF_SIZE)
        messageArea.setMaxHeight(Double.MAX_VALUE)
        
        VBox messageBubble = new VBox(5, senderLabel, messageArea)
        messageBubble.padding = new Insets(8)
        messageBubble.styleClass.add(isUser ? "chat-message-user" : "chat-message-ai")
        
        HBox messageRow = new HBox(messageBubble)
        if (isUser) {
            messageRow.alignment = Pos.CENTER_RIGHT
        } else {
            messageRow.alignment = Pos.CENTER_LEFT
        }
        messageRow.styleClass.add("chat-message-container")

        chatMessagesContainer.children.add(messageRow)
        
        // Auto-scroll to bottom
        // Ensure this runs after layout pass
        Platform.runLater {
            // Use the class member 'scrollPane' directly
            this.scrollPane.setVvalue(1.0d)
        }
    }

    private HBox createDayOfWeekHeader() {
        HBox headerRow = new HBox()
        headerRow.styleClass.add("day-of-week-header")
        String[] dayNames = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
        for (String dayName : dayNames) {
            Label dayLabel = new Label(dayName)
            dayLabel.setMaxWidth(Double.MAX_VALUE)
            HBox.setHgrow(dayLabel, Priority.ALWAYS)
            dayLabel.alignment = Pos.CENTER
            dayLabel.styleClass.add("day-header-label")
            headerRow.getChildren().add(dayLabel)
        }
        return headerRow
    }

    private void changeMonth(long amount) {
        currentYearMonth = currentYearMonth.plusMonths(amount)
        populateCalendarGrid()
    }

    private void populateCalendarGrid() {
        monthYearLabel.text = currentYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        calendarGrid.getChildren().clear() // Clear previous month's days

        List<Event> allEvents = calendarService.getAllEvents() // Get all events for potential display
        println "ChatWindow: populateCalendarGrid - Current Month: ${currentYearMonth}, Events: ${allEvents.size()}"

        java.time.LocalDate firstOfMonth = currentYearMonth.atDay(1)
        int dayOfWeekOfFirst = firstOfMonth.getDayOfWeek().getValue() % 7 // SUN=0, MON=1 .. SAT=6
        int daysInMonth = currentYearMonth.lengthOfMonth()

        int row = 0
        int col = dayOfWeekOfFirst

        for (int day = 1; day <= daysInMonth; day++) {
            VBox dayCell = new VBox()
            dayCell.alignment = Pos.TOP_CENTER
            dayCell.styleClass.add("calendar-day-cell")
            // Add padding inside the cell
            dayCell.setPadding(new Insets(5));

            Label dayLabel = new Label(String.valueOf(day))
            dayLabel.styleClass.add("day-number-label")
            dayCell.getChildren().add(dayLabel)

            final java.time.LocalDate cellDate = currentYearMonth.atDay(day);
            List<Event> eventsOnThisDay = allEvents.findAll { event ->
                event.startTime.toLocalDate().isEqual(cellDate)
            }

            if (!eventsOnThisDay.isEmpty()) {
                Label eventIndicator = new Label("(${eventsOnThisDay.size()} event${eventsOnThisDay.size() > 1 ? 's' : ''})")
                eventIndicator.styleClass.add("event-indicator-label")
                dayCell.getChildren().add(eventIndicator)
            }

            dayCell.setOnMouseClicked { event ->
                showEventsForDay(cellDate)
            }
            // Add a hover effect to indicate clickable cells
            dayCell.setOnMouseEntered { event -> dayCell.style = "-fx-background-color: #e0e0e0;" }
            dayCell.setOnMouseExited { event -> dayCell.style = "" } 

            calendarGrid.add(dayCell, col, row)

            col++
            if (col > 6) {
                col = 0
                row++
            }
        }
        println "ChatWindow: Calendar grid populated for ${currentYearMonth}"
    }

    private void showEventsForDay(java.time.LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay()
        LocalDateTime endOfDay = date.atTime(23, 59, 59)
        List<Event> events = calendarService.getEvents(startOfDay, endOfDay)

        Alert alert = new Alert(Alert.AlertType.INFORMATION)
        alert.title = "Events for ${date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))}"
        alert.headerText = null // No header

        if (events.isEmpty()) {
            alert.contentText = "No events scheduled for this day."
        } else {
            StringBuilder content = new StringBuilder()
            events.each { event ->
                content.append("${event.title}\n")
                content.append("  Start: ${event.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))}\n")
                content.append("  End:   ${event.endTime.format(DateTimeFormatter.ofPattern("HH:mm"))}\n")
                if (event.description != null && !event.description.isEmpty()) {
                    content.append("  Desc:  ${event.description}\n")
                }
                content.append("\n")
            }
            alert.contentText = content.toString()
        }
        // Make dialog resizable and wrap text
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setPrefWidth(400); // Set a preferred width
        dialogPane.setPrefHeight(300); // Set a preferred height
        // Access the content TextArea to enable wrapping (requires lookup)
        TextArea contentTextArea = (TextArea) dialogPane.lookup(".content.label");
        if (contentTextArea != null) {
            contentTextArea.setWrapText(true);
            contentTextArea.setEditable(false);
            contentTextArea.setPrefHeight(Region.USE_COMPUTED_SIZE);
        }

        alert.showAndWait()
    }

    static void main(String[] args) {
        launch(args)
    }
}
