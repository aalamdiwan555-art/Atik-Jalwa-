package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Random
import java.util.regex.Pattern

class DrClickerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()

    companion object {
        private const val TAG = "DrClickerAccessibility"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Only run scan loop if scanning active state is true
        if (!DrClickerController.isScanning.value) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            val rootNode = rootInActiveWindow ?: return
            
            // Step 1: Thread-safe Main UI capture of Layout data
            // We read node texts and rect bounds on the main thread to prevent thread allocation safety issues
            val screenNodes = mutableListOf<NodeInfoData>()
            traverseTree(rootNode, screenNodes)
            rootNode.recycle()

            if (screenNodes.isEmpty()) return

            // Step 2: Offload parsing, filtering and coordinate generation to Asynchronous background thread pool
            serviceScope.launch {
                parseAndTriggerMatching(screenNodes)
            }
        }
    }

    override fun onInterrupt() {
        DrClickerController.logEvent("Accessibility Service Interrupted by system", false)
    }

    /**
     * Traverses the active window hierarchy and extracts essential flat text and boundaries.
     */
    private fun traverseTree(node: AccessibilityNodeInfo?, list: MutableList<NodeInfoData>) {
        if (node == null) return
        
        val text = node.text?.toString()?.trim()
        val contentDesc = node.contentDescription?.toString()?.trim()
        
        if (!text.isNullOrEmpty() || !contentDesc.isNullOrEmpty()) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            list.add(
                NodeInfoData(
                     text = text ?: "",
                     contentDescription = contentDesc ?: "",
                     bounds = bounds,
                     nodeId = node.hashCode(),
                     isClickable = node.isClickable,
                     className = node.className?.toString() ?: ""
                )
            )
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverseTree(child, list)
            child?.recycle()
        }
    }

    /**
     * Light-weight flat representation of AccessibilityNodeInfo values to safely pass across background threads.
     */
    data class NodeInfoData(
        val text: String,
        val contentDescription: String,
        val bounds: Rect,
        val nodeId: Int,
        val isClickable: Boolean,
        val className: String
    )

    /**
     * Asynchronous parser: Identifies currency fares, pickup distance, drop distance, and action triggers.
     */
    private fun parseAndTriggerMatching(nodes: List<NodeInfoData>) {
        var detectedFare: Int? = null
        var detectedPickupKm: Float? = null
        var detectedDropKm: Float? = null
        var targetActionButton: NodeInfoData? = null

        // Regular Expressions for currency and distances
        val farePattern = Pattern.compile("(?:₹|Rs\\.?|INR|\\$)\\s*(\\d+(?:\\.\\d+)?)")
        val distancePattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:km|kms|miles|mi)", Pattern.CASE_INSENSITIVE)

        // Standard Accept keywords
        val actionKeywords = listOf("ACCEPT", "TAKE", "START", "CONFIRM", "APPLY", "BOOK", "GO", "APPROVE")

        for (node in nodes) {
            val combinedText = "${node.text} ${node.contentDescription}"
            
            // 1. Parse Card Fare value
            val fareMatcher = farePattern.matcher(combinedText)
            if (fareMatcher.find()) {
                val fareVal = fareMatcher.group(1)?.toDoubleOrNull()?.toInt()
                if (fareVal != null && detectedFare == null) {
                    detectedFare = fareVal
                }
            }

            // 2. Parse Distance indicators
            val distMatcher = distancePattern.matcher(combinedText)
            while (distMatcher.find()) {
                val distVal = distMatcher.group(1)?.toFloatOrNull()
                if (distVal != null) {
                    // Context check to isolate Pickup vs Destination Distance
                    val lowerText = combinedText.lowercase()
                    if (lowerText.contains("pick") || lowerText.contains("near") || lowerText.contains("start")) {
                        detectedPickupKm = distVal
                    } else if (lowerText.contains("drop") || lowerText.contains("dest") || lowerText.contains("trip") || lowerText.contains("deliver")) {
                        detectedDropKm = distVal
                    } else {
                        // Smart layout fallback: First encountered distance is usually Pickup, second is Drop
                        if (detectedPickupKm == null) {
                            detectedPickupKm = distVal
                        } else if (detectedDropKm == null) {
                            detectedDropKm = distVal
                        }
                    }
                }
            }

            // 3. Search for the action button
            if (node.isClickable || node.className.contains("Button", ignoreCase = true)) {
                for (kw in actionKeywords) {
                    if (combinedText.uppercase().contains(kw)) {
                        targetActionButton = node
                        break
                    }
                }
            }
        }

        // Apply sensible fallback defaults if contextual markers weren't explicitly found but numbers are on-screen
        if (detectedFare != null) {
            val pickupVal = detectedPickupKm ?: 1.0f // Fallback to 1km pickup proximity if missing
            val dropVal = detectedDropKm ?: 5.0f     // Fallback to 5.0km drop distance if missing

            val minPrice = DrClickerController.minPrice.value
            val maxPrice = DrClickerController.maxPrice.value
            val maxPickup = DrClickerController.maxPickupDistance.value
            val maxDrop = DrClickerController.maxDropDistance.value

            // 4. MODULE 4 CONDITIONAL CHECK BLOCK
            val satisfiesFilters = (detectedFare >= minPrice) && 
                                   (detectedFare <= maxPrice) && 
                                   (pickupVal <= maxPickup) && 
                                   (dropVal <= maxDrop)

            if (satisfiesFilters) {
                DrClickerController.logEvent(
                    "MATCH FOUND! Card: Price ₹$detectedFare, Pickup ${pickupVal}KM, Ride ${dropVal}KM. Satisfies filters (Min: ₹$minPrice, Max: ${maxPickup}KM Pickup, ${maxDrop}KM Drop)",
                    true
                )
                
                if (targetActionButton != null) {
                    // Start natural human touch routine
                    triggerHumanlikeGesture(targetActionButton)
                } else {
                    // If no explicit keyword button, pick the safest clickable container node
                    val generalClickable = nodes.firstOrNull { it.isClickable }
                    if (generalClickable != null) {
                        triggerHumanlikeGesture(generalClickable)
                    } else {
                        DrClickerController.logEvent("Match found but no clickable action element isolated on screen.", false)
                    }
                }
            } else {
                // Ignore layouts with silent background log tracking
                DrClickerController.logEvent(
                    "Card evaluated and Ignored: Price ₹$detectedFare (Min: ₹$minPrice), Pickup ${pickupVal}KM (Max: ${maxPickup}KM), Drop ${dropVal}KM (Max: ${maxDrop}KM)",
                    false
                )
            }
        }
    }

    /**
     * MODULE 5: HUMAN-CENTRIC ERGONOMIC EXECUTION ENGINE
     */
    private fun triggerHumanlikeGesture(target: NodeInfoData) {
        // 1. Dynamic Reaction Latency (Gaussian distribution curve, mean=317ms, stdDev=60ms, bounded [195ms, 440ms])
        val rawDelay = (random.nextGaussian() * 60f + 317f).toLong()
        val delayMs = rawDelay.coerceIn(195, 440)

        // 2. Micro-Coordinate Variance (Inner 70% button bounds)
        val rect = target.bounds
        val width = rect.width()
        val height = rect.height()

        val insetWidth = (width * 0.15f).toInt()
        val insetHeight = (height * 0.15f).toInt()

        val minX = rect.left + insetWidth
        val maxX = rect.right - insetWidth
        val minY = rect.top + insetHeight
        val maxY = rect.bottom - insetHeight

        // Safely pick pixel coordinate variables
        val targetX = if (maxX > minX) (minX..maxX).random().toFloat() else rect.centerX().toFloat()
        val targetY = if (maxY > minY) (minY..maxY).random().toFloat() else rect.centerY().toFloat()

        // 3. Realistic Hold Variations (Hold click dur cycle 60ms to 110ms)
        val touchDuration = (60..110).random().toLong()

        DrClickerController.logEvent(
            "Scheduling natural tap: Delay=${delayMs}ms, Coordinates=($targetX, $targetY), PressDuration=${touchDuration}ms",
            true
        )

        // Dispatches gesture after normal human reaction pause
        handler.postDelayed({
            // Construct Swipe or Tap standard behavior
            // We implement high-precision slide mechanisms if a slide operation fits the workflow
            val isSlideToAccept = target.text.uppercase().contains("SLIDE") || 
                                  target.contentDescription.uppercase().contains("SLIDE")

            val gesture = if (isSlideToAccept) {
                // Generate a Bezier Curve slide swipe gesture instead of straight lines
                buildBezierSwipeGesture(targetX, targetY, touchDuration)
            } else {
                // Human click gesture
                val clickPath = Path().apply { moveTo(targetX, targetY) }
                val stroke = GestureDescription.StrokeDescription(clickPath, 0, touchDuration)
                GestureDescription.Builder().addStroke(stroke).build()
            }

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    DrClickerController.logEvent("Gesture completed successfully", true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    DrClickerController.logEvent("Gesture injection rejected/cancelled by system", false)
                }
            }, null)

        }, delayMs)
    }

    /**
     * MODULE 5, PART 4: BEZIER-CURVED SWIPE SOLVER WITH TREMOR & VARIABLE VELOCITY
     */
    private fun buildBezierSwipeGesture(startX: Float, startY: Float, holdTime: Long): GestureDescription {
        val path = Path()
        
        // Target swipe length: Slide rightwards across target button or screen zone
        val endX = startX + 350f + (-20..20).random()
        val endY = startY + (-12..12).random() // Realistic natural hand slant

        // Calculate a mid control point for Bezier displacement
        val ctrlX = (startX + endX) / 2
        val ctrlY = startY - 45f + (-15..15).random() // Tremor micro-vibrations

        path.moveTo(startX, startY)
        
        // Build path using 20 Bezier steps to allow variable velocity simulation
        val steps = 20
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            // Quadratic Bezier interpolation formula: B(t) = (1-t)^2*P0 + 2*(1-t)*t*P1 + t^2*P2
            val x = (1 - t) * (1 - t) * startX + 2 * (1 - t) * t * ctrlX + t * t * endX
            val y = (1 - t) * (1 - t) * startY + 2 * (1 - t) * t * ctrlY + t * t * endY
            path.lineTo(x, y)
        }

        val swipeTime = holdTime + 220L // Allow more generous travel time for swipes
        val stroke = GestureDescription.StrokeDescription(path, 0, swipeTime)
        
        DrClickerController.logEvent("Bezier Glide path drawn over 20 coordinates, duration = ${swipeTime}ms", true)
        return GestureDescription.Builder().addStroke(stroke).build()
    }
}
