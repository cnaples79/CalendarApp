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
import javafx.scene.control.TableView
import javafx.scene.control.TableColumn
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.control.SplitPane
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@CompileStatic
class ChatWindow extends Application {

    private VBox chatMessagesContainer
    private TextField inputField
    private AIService aiService
    private CalendarService calendarService
    private ScrollPane scrollPane // Declare scrollPane as a field
    private TableView<Map<String, Object>> calendarView

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
        // Calendar View Table
        calendarView = new TableView<Map<String, Object>>()
        TableColumn<Map<String, Object>, String> titleCol = new TableColumn<>("Title")
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"))

        DateTimeFormatter tableDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        TableColumn<Map<String, Object>, LocalDateTime> startCol = new TableColumn<>("Start Time")
        startCol.setCellValueFactory(new PropertyValueFactory<>("startTime"))
        startCol.setCellFactory({ column ->
            return new javafx.scene.control.TableCell<Map<String, Object>, LocalDateTime>() {
                @Override
                protected void updateItem(LocalDateTime item, boolean empty) {
                    super.updateItem(item, empty)
                    if (item == null || empty) {
                        setText(null)
                    } else {
                        setText(item.format(tableDateTimeFormatter))
                    }
                }
            }
        })

        TableColumn<Map<String, Object>, LocalDateTime> endCol = new TableColumn<>("End Time")
        endCol.setCellValueFactory(new PropertyValueFactory<>("endTime"))
        endCol.setCellFactory({ column ->
            return new javafx.scene.control.TableCell<Map<String, Object>, LocalDateTime>() {
                @Override
                protected void updateItem(LocalDateTime item, boolean empty) {
                    super.updateItem(item, empty)
                    if (item == null || empty) {
                        setText(null)
                    } else {
                        setText(item.format(tableDateTimeFormatter))
                    }
                }
            }
        })

        TableColumn<Map<String, Object>, String> descCol = new TableColumn<>("Description")
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"))

        calendarView.getColumns().addAll(titleCol, startCol, endCol, descCol)
        // Make columns take up available width
        titleCol.prefWidthProperty().bind(calendarView.widthProperty().multiply(0.25d))
        startCol.prefWidthProperty().bind(calendarView.widthProperty().multiply(0.25d))
        endCol.prefWidthProperty().bind(calendarView.widthProperty().multiply(0.25d))
        descCol.prefWidthProperty().bind(calendarView.widthProperty().multiply(0.25d))

        refreshCalendarView()

        // SplitPane to hold chat and calendar
        SplitPane splitPane = new SplitPane()
        splitPane.getItems().addAll(scrollPane, calendarView)
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
            String aiResponse = aiService.getAIResponse(userMessage)
            addMessageToChat("AI", aiResponse, false)
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

    private void refreshCalendarView() {
        ObservableList<Map<String, Object>> events = FXCollections.observableArrayList(calendarService.getAllEvents())
        calendarView.setItems(events)
    }

    static void main(String[] args) {
        launch(args)
    }
}
