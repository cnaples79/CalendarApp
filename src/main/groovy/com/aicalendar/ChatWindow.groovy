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
import javafx.scene.control.ButtonBar
import javafx.scene.control.DatePicker
import javafx.scene.control.Dialog
import javafx.scene.control.DialogPane
import javafx.scene.control.ButtonType
import java.time.format.DateTimeParseException
import javafx.scene.layout.Region
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue

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
    private Stage primaryStage // To be used as owner for dialogs

    @Override
    void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage // Store the primary stage
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

        Button newEventButton = new Button("New Event")
        newEventButton.onAction = { event -> showCreateEventDialog() }

        HBox monthNavigation = new HBox(10, prevMonthButton, monthYearLabel, nextMonthButton, newEventButton)
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
            println "ChatWindow.sendMessage: AIResponsePayload - eventCreated=${payload.eventCreated}, eventModified=${payload.eventModified}" // Log flags
            if (payload.eventCreated || payload.eventModified) {
                populateCalendarGrid() // Refresh calendar if event created or modified
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
                println "ChatWindow: Day cell clicked for date: ${cellDate}"
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

    private void showCreateEventDialog() {
        println "DEBUG: showCreateEventDialog() called"
        Dialog<Event> dialog = new Dialog<>()
        dialog.title = "Create New Event"
        dialog.headerText = "Enter the details for the new event."
        dialog.initOwner(this.primaryStage) // Set owner

        // Set the button types
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE)
        dialog.dialogPane.buttonTypes.addAll(saveButtonType, ButtonType.CANCEL)

        // Create labels and fields
        GridPane grid = new GridPane()
        grid.hgap = 10
        grid.vgap = 10
        grid.padding = new Insets(20, 150, 10, 10)

        TextField titleField = new TextField()
        titleField.promptText = "Event Title"
        DatePicker startDatePicker = new DatePicker(java.time.LocalDate.now())
        TextField startTimeField = new TextField()
        startTimeField.promptText = "HH:mm" // e.g., 14:30
        DatePicker endDatePicker = new DatePicker(java.time.LocalDate.now())
        TextField endTimeField = new TextField()
        endTimeField.promptText = "HH:mm"
        TextArea descriptionArea = new TextArea()
        descriptionArea.promptText = "Event Description"
        descriptionArea.setWrapText(true)

        grid.add(new Label("Title:"), 0, 0)
        grid.add(titleField, 1, 0)
        grid.add(new Label("Start Date:"), 0, 1)
        grid.add(startDatePicker, 1, 1)
        grid.add(new Label("Start Time (HH:mm):"), 0, 2)
        grid.add(startTimeField, 1, 2)
        grid.add(new Label("End Date:"), 0, 3)
        grid.add(endDatePicker, 1, 3)
        grid.add(new Label("End Time (HH:mm):"), 0, 4)
        grid.add(endTimeField, 1, 4)
        grid.add(new Label("Description:"), 0, 5)
        grid.add(descriptionArea, 1, 5)
        GridPane.setVgrow(descriptionArea, Priority.ALWAYS)

        dialog.dialogPane.content = grid

        // Enable/Disable save button depending on whether title is empty
        javafx.scene.Node saveButton = dialog.dialogPane.lookupButton(saveButtonType)
        saveButton.disable = true
        titleField.textProperty().addListener(new ChangeListener<String>() {
            @Override
            void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                saveButton.disable = newValue.trim().isEmpty()
            }
        })

        // Convert the result to an event object when the save button is clicked.
        dialog.setResultConverter(dialogButton -> {
            println "DEBUG: CreateDialog - setResultConverter called. Button: ${dialogButton?.buttonData}"
            if (dialogButton == saveButtonType) {
                try {
                    java.time.LocalDate startDate = startDatePicker.getValue()
                    java.time.LocalTime startTime = java.time.LocalTime.parse(startTimeField.getText(), DateTimeFormatter.ofPattern("HH:mm"))
                    java.time.LocalDate endDate = endDatePicker.getValue()
                    java.time.LocalTime endTime = java.time.LocalTime.parse(endTimeField.getText(), DateTimeFormatter.ofPattern("HH:mm"))

                    LocalDateTime startDateTime = LocalDateTime.of(startDate, startTime)
                    LocalDateTime endDateTime = LocalDateTime.of(endDate, endTime)

                    if (endDateTime.isBefore(startDateTime)) {
                        throw new IllegalArgumentException("End date/time must be after start date/time.")
                    }

                    Event newEvent = new Event(titleField.getText(), startDateTime, endDateTime, descriptionArea.getText())
                    println "DEBUG: CreateDialog - Event to be returned from converter: ${newEvent}"
                    return newEvent
                } catch (DateTimeParseException e) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR)
                        alert.title = "Invalid Time Format"
                        alert.headerText = "Please enter time in HH:mm format (e.g., 09:00 or 15:30)."
                        alert.contentText = e.getMessage()
                        alert.showAndWait()
                    })
                    println "DEBUG: CreateDialog - DateTimeParseException, returning null from converter"
                    return null
                } catch (IllegalArgumentException e) {
                     Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR)
                        alert.title = "Invalid Date/Time Logic"
                        alert.headerText = e.getMessage()
                        alert.showAndWait()
                    })
                    println "DEBUG: CreateDialog - IllegalArgumentException, returning null from converter"
                    return null
                }
            }
            println "DEBUG: CreateDialog - Button was not saveButtonType, returning null from converter"
            return null
        })

        println "DEBUG: CreateDialog - About to call dialog.showAndWait()"
        Optional<Event> result = dialog.showAndWait()
        println "DEBUG: CreateDialog - dialog.showAndWait() returned. Result present: ${result.isPresent()}"

        result.ifPresent(event -> {
            println "DEBUG: CreateDialog - Event result is present: ${event}"
            calendarService.addEvent(event)
            populateCalendarGrid()
            println "ChatWindow: Event created via dialog: ${event}"
        })
    }

        private void showEventsForDay(java.time.LocalDate date) {
            println "ChatWindow: showEventsForDay called for date: ${date}"
            LocalDateTime startOfDay = date.atStartOfDay()
            LocalDateTime endOfDay = date.atTime(23, 59, 59)
            List<Event> events = calendarService.getEvents(startOfDay, endOfDay)

            Dialog dialog = new Dialog<>()
            dialog.initOwner(this.primaryStage) // Set owner
            dialog.title = "Events on ${date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))}"
            dialog.headerText = null
            dialog.dialogPane.buttonTypes.add(ButtonType.CLOSE)

            VBox eventsVBox = new VBox(10)
            eventsVBox.padding = new Insets(10)
            eventsVBox.alignment = Pos.TOP_LEFT

            if (events.isEmpty()) {
                eventsVBox.children.add(new Label("No events scheduled for this day."))
            } else {
                events.each { Event event ->
                    VBox eventBox = new VBox(5)
                    eventBox.style = "-fx-border-color: lightgray; -fx-border-width: 1; -fx-padding: 5;"
                    Label titleLabel = new Label("${event.title} (${event.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${event.endTime.format(DateTimeFormatter.ofPattern("HH:mm"))})")
                    titleLabel.style = "-fx-font-weight: bold;"
                    Label descLabel = new Label("Description: ${event.description ?: 'N/A'}")
                    descLabel.wrapText = true
                    Label idLabel = new Label("(ID: ${event.id})")
                    idLabel.style = "-fx-font-size: 0.8em; -fx-text-fill: gray;"

                    Button editButton = new Button("Edit")
                    editButton.onAction = { evt -> 
                        dialog.close() // Close current dialog
                        // Schedule the edit dialog to open on the JavaFX Application Thread
                        Platform.runLater(() -> showEditEventDialog(event))
                    }
                    Button deleteButton = new Button("Delete")
                    deleteButton.onAction = { evt ->
                        Alert confirmDeleteAlert = new Alert(Alert.AlertType.CONFIRMATION)
                        confirmDeleteAlert.title = "Confirm Deletion"
                        confirmDeleteAlert.headerText = "Delete Event: ${event.title}?"
                        confirmDeleteAlert.contentText = "Are you sure you want to delete this event? This action cannot be undone."
                        Optional<ButtonType> result = confirmDeleteAlert.showAndWait()
                        if (result.isPresent() && result.get() == ButtonType.OK) {
                            calendarService.deleteEvent(event.id)
                            populateCalendarGrid()
                            dialog.close() // Close the events list dialog
                            // Optionally, show a notification that event was deleted or re-open day view
                        }
                    }
                    HBox buttonBox = new HBox(10, editButton, deleteButton)
                    buttonBox.alignment = Pos.CENTER_RIGHT
                    eventBox.children.addAll(titleLabel, descLabel, idLabel, buttonBox)
                    eventsVBox.children.add(eventBox)
                }
            }
            
            ScrollPane scrollableEvents = new ScrollPane(eventsVBox)
            scrollableEvents.setFitToWidth(true)
            scrollableEvents.setPrefHeight(300) // Set a preferred height for scrollable area

            dialog.dialogPane.content = scrollableEvents
            dialog.dialogPane.setPrefSize(500, 400) // Set a preferred size for the dialog
            dialog.showAndWait()
        }

        private void showEditEventDialog(Event eventToEdit) {
            println "DEBUG: showEditEventDialog() called for event: ${eventToEdit}"
            Dialog<Event> dialog = new Dialog<>()
            dialog.title = "Edit Event"
            dialog.headerText = "Update the details for '${eventToEdit.title}'."
            dialog.initOwner(this.primaryStage) // Set owner

            ButtonType saveButtonType = new ButtonType("Save Changes", ButtonBar.ButtonData.OK_DONE)
            dialog.dialogPane.buttonTypes.addAll(saveButtonType, ButtonType.CANCEL)

            GridPane grid = new GridPane()
            grid.hgap = 10
            grid.vgap = 10
            grid.padding = new Insets(20, 150, 10, 10)


            TextField titleField = new TextField()
            titleField.setText(eventToEdit.title)
            titleField.setEditable(true)

            DatePicker startDatePicker = new DatePicker()
            startDatePicker.setValue(eventToEdit.startTime.toLocalDate())
            startDatePicker.setEditable(true)

            TextField startTimeField = new TextField()
            startTimeField.setText(eventToEdit.startTime.format(DateTimeFormatter.ofPattern("HH:mm")))
            startTimeField.promptText = "HH:mm"
            startTimeField.setEditable(true)

            DatePicker endDatePicker = new DatePicker()
            endDatePicker.setValue(eventToEdit.endTime.toLocalDate())
            endDatePicker.setEditable(true)

            TextField endTimeField = new TextField()
            endTimeField.setText(eventToEdit.endTime.format(DateTimeFormatter.ofPattern("HH:mm")))
            endTimeField.promptText = "HH:mm"
            endTimeField.setEditable(true)

            TextArea descriptionArea = new TextArea()
            descriptionArea.setText(eventToEdit.description)
            descriptionArea.setWrapText(true)
            descriptionArea.setEditable(true)

            grid.add(new Label("Title:"), 0, 0)
            grid.add(titleField, 1, 0)
            grid.add(new Label("Start Date:"), 0, 1)
            grid.add(startDatePicker, 1, 1)
            grid.add(new Label("Start Time (HH:mm):"), 0, 2)
            grid.add(startTimeField, 1, 2)
            grid.add(new Label("End Date:"), 0, 3)
            grid.add(endDatePicker, 1, 3)
            grid.add(new Label("End Time (HH:mm):"), 0, 4)
            grid.add(endTimeField, 1, 4)
            grid.add(new Label("Description:"), 0, 5)
            grid.add(descriptionArea, 1, 5)
            GridPane.setVgrow(descriptionArea, Priority.ALWAYS)

            // Diagnostic Test Field
            TextField diagnosticTestField = new TextField()
            diagnosticTestField.setPromptText("Can you edit this?")
            diagnosticTestField.setEditable(true) // Explicitly set editable for safety
            grid.add(new Label("Diagnostic Test:"), 0, 6)
            grid.add(diagnosticTestField, 1, 6)

            dialog.dialogPane.content = grid

            javafx.scene.Node saveButton = dialog.dialogPane.lookupButton(saveButtonType)
            saveButton.disable = titleField.text.trim().isEmpty()
            titleField.textProperty().addListener(new ChangeListener<String>() {
                @Override
                void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                    saveButton.disable = newValue.trim().isEmpty()
                }
            })

            dialog.setResultConverter(dialogButton -> {
                println "DEBUG: EditDialog - setResultConverter called. Button: ${dialogButton?.buttonData}"
                if (dialogButton == saveButtonType) {
                    try {
                        java.time.LocalDate startDate = startDatePicker.getValue()
                        java.time.LocalTime startTime = java.time.LocalTime.parse(startTimeField.getText(), DateTimeFormatter.ofPattern("HH:mm"))
                        java.time.LocalDate endDate = endDatePicker.getValue()
                        java.time.LocalTime endTime = java.time.LocalTime.parse(endTimeField.getText(), DateTimeFormatter.ofPattern("HH:mm"))

                        LocalDateTime startDateTime = LocalDateTime.of(startDate, startTime)
                        LocalDateTime endDateTime = LocalDateTime.of(endDate, endTime)

                        if (endDateTime.isBefore(startDateTime)) {
                            throw new IllegalArgumentException("End date/time must be after start date/time.")
                        }
                        // Create a new event object with the original ID but new details
                        Event updatedEvent = new Event(titleField.getText(), startDateTime, endDateTime, descriptionArea.getText(), eventToEdit.id)
                        println "DEBUG: EditDialog - Event to be returned from converter: ${updatedEvent}"
                        return updatedEvent
                    } catch (DateTimeParseException e) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR)
                            alert.title = "Invalid Time Format"
                            alert.headerText = "Please enter time in HH:mm format (e.g., 09:00 or 15:30)."
                            alert.contentText = e.getMessage()
                            alert.showAndWait()
                        })
                        println "DEBUG: EditDialog - DateTimeParseException, returning null from converter"
                        return null
                    } catch (IllegalArgumentException e) {
                         Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR)
                            alert.title = "Invalid Date/Time Logic"
                            alert.headerText = e.getMessage()
                            alert.showAndWait()
                        })
                        println "DEBUG: EditDialog - IllegalArgumentException, returning null from converter"
                        return null
                    }
                }
                println "DEBUG: EditDialog - Button was not saveButtonType, returning null from converter"
                return null
            })

            println "DEBUG: EditDialog - About to call dialog.showAndWait()"
            Optional<Event> result = dialog.showAndWait()
            println "DEBUG: EditDialog - dialog.showAndWait() returned. Result present: ${result.isPresent()}"

            result.ifPresent(updatedEventData -> {
                println "DEBUG: EditDialog - Event result is present: ${updatedEventData}"
                boolean success = calendarService.updateEvent(eventToEdit.id, updatedEventData)
                if (success) {
                    populateCalendarGrid()
                    println "ChatWindow: Event updated via dialog: ${updatedEventData}"
                } else {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR)
                        alert.title = "Update Failed"
                        alert.headerText = "Could not update the event (ID: ${eventToEdit.id}). It might have been deleted."
                        alert.showAndWait()
                    })
                }
            })
        }

    static void main(String[] args) {
        launch(args)
    }
}
