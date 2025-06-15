package com.aicalendar

import javafx.scene.layout.Pane
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import javafx.scene.text.Text
import javafx.scene.paint.Color
import javafx.util.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.Duration
import javafx.geometry.VPos
import javafx.scene.control.Tooltip

import com.aicalendar.Event // Make sure Event.groovy is in this package

@groovy.transform.CompileStatic
class DailyTimelineView extends Pane {

    private static final double HOUR_HEIGHT = 60.0
    private static final double LEFT_PADDING = 60.0 // Increased for wider hour labels like "10 AM"
    private static final double RIGHT_PADDING = 15.0
    private static final double TOP_PADDING = 10.0
    private static final double EVENT_BAR_INSET = 5.0 // Small gap between hour line and event bar

    private static final int START_HOUR = 0 // 00:00 (12 AM)
    private static final int END_HOUR = 23 // 23:00 (11 PM)

    private LocalDate currentDate
    private List<Event> currentEvents = []

    public LocalDate getCurrentDate() { // Getter for currentDate
        return this.currentDate
    }

    public DailyTimelineView() {
        setStyle("-fx-background-color: #ffffff;")
        // Calculate min height based on 24 hours
        double calculatedMinHeight = (END_HOUR - START_HOUR + 1) * HOUR_HEIGHT + 2 * TOP_PADDING
        setMinHeight(calculatedMinHeight)
        setPrefHeight(calculatedMinHeight) // Ensure it takes up its calculated space
        
        // Request layout pass whenever the width changes to redraw lines correctly
        widthProperty().addListener({ obs, oldVal, newVal -> requestLayout() } as javafx.beans.value.ChangeListener<Number>)
    }

    public void updateView(LocalDate date, List<Event> events) {
        this.currentDate = date
        this.currentEvents = events ?: new ArrayList<Event>() // Explicitly type empty list
        drawTimelineAndEvents()
    }

    private void drawTimelineAndEvents() {
        if (currentDate == null) return // Don't draw if no date is set

        getChildren().clear()

        // Draw hour lines and labels
        for (int hour = START_HOUR; hour <= END_HOUR + 1; hour++) {
            double yPos = TOP_PADDING + (hour - START_HOUR) * HOUR_HEIGHT

            Line line = new Line(LEFT_PADDING - EVENT_BAR_INSET, yPos, getWidth() - RIGHT_PADDING, yPos)
            line.stroke = Color.LIGHTGRAY
            getChildren().add(line)

            if (hour <= END_HOUR) {
                Text hourText = new Text(LocalTime.of(hour, 0).format(DateTimeFormatter.ofPattern("h a")))
                hourText.x = 10.0d
                hourText.y = yPos + (HOUR_HEIGHT / 2) // Center in the current hour slot
                hourText.textOrigin = VPos.CENTER
                hourText.styleClass.add("timeline-hour-label")
                getChildren().add(hourText)
            }
        }

        // Draw events for the current date
        if (currentEvents != null) {
            for (Event event : currentEvents) {
                // Ensure event is on the currentDate (though list should already be filtered)
                if (!event.startTime.toLocalDate().isEqual(currentDate)) continue

                double eventStartHour = event.startTime.hour + (event.startTime.minute / 60.0)
                double eventY = TOP_PADDING + (eventStartHour - START_HOUR) * HOUR_HEIGHT

                double eventEndHour = event.endTime.hour + (event.endTime.minute / 60.0)
                // Handle events spanning midnight for the current day's view (clamp to 24:00)
                if (event.endTime.toLocalDate().isAfter(currentDate)) {
                    eventEndHour = END_HOUR + 1.0; // End of the 23rd hour
                }
                
                double durationInHours = eventEndHour - eventStartHour
                if (durationInHours <= 0) durationInHours = 0.25; // Min height for very short events

                double eventHeight = durationInHours * HOUR_HEIGHT

                Rectangle eventBar = new Rectangle(
                        LEFT_PADDING,
                        eventY,
                        Math.max(0, getWidth() - LEFT_PADDING - RIGHT_PADDING), // Ensure width is not negative
                        eventHeight
                )
                eventBar.fill = Color.SKYBLUE.deriveColor(0, 1.0d, 1.0d, 0.7d) // Semi-transparent, ensure doubles
                eventBar.stroke = Color.STEELBLUE
                eventBar.arcWidth = 10.0d // Rounded corners, ensure double
                eventBar.arcHeight = 10.0d
                eventBar.styleClass.add("timeline-event-bar")

                Text eventTitle = new Text(String.format("%s %s", event.startTime.format(DateTimeFormatter.ofPattern("HH:mm")), event.title))
                eventTitle.x = LEFT_PADDING + 5
                eventTitle.y = eventY + 15 // Position text inside the bar
                eventTitle.wrappingWidth = Math.max(0, getWidth() - LEFT_PADDING - RIGHT_PADDING - 10)
                eventTitle.styleClass.add("timeline-event-label")

                Tooltip tooltip = new Tooltip(
                    String.format("Title: %s\nStart: %s\nEnd: %s\nDescription: %s",
                        event.title,
                        event.startTime.format(DateTimeFormatter.ofPattern("MMM d, HH:mm")),
                        event.endTime.format(DateTimeFormatter.ofPattern("MMM d, HH:mm")),
                        event.description ?: "N/A"
                    )
                )
                tooltip.styleClass.add("timeline-event-tooltip")

                // Add these lines for diagnostics:
                tooltip.setShowDelay(javafx.util.Duration.millis(100)) // Show quickly
                tooltip.setShowDuration(javafx.util.Duration.seconds(10)) // Stay for 10 seconds
                tooltip.setHideDelay(javafx.util.Duration.millis(200)) // Hide quickly after mouse moves away

                Tooltip.install(eventBar, tooltip)
                Tooltip.install(eventTitle, tooltip)


                getChildren().addAll(eventBar, eventTitle)
            }
        }
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren()
        // Redraw when layout changes (e.g. initial display, window resize)
        drawTimelineAndEvents()
    }
}
