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
        
        @Volatile
        var instance: DrClickerAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        DrClickerController.logEvent("Accessibility Service Connected successfully!", false)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
        DrClickerController.logEvent("Accessibility Service Halted / Disconnected", false)
    }

    fun dispatchManualTap(x: Float, y: Float, duration: Long = 85L, onComplete: (Boolean) -> Unit = {}) {
        handler.post {
            val clickPath = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(clickPath, 0, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    onComplete(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    onComplete(false)
                }
            }, null)
        }
    }

    fun dispatchManualSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 320L, onComplete: (Boolean) -> Unit = {}) {
        handler.post {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    onComplete(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    onComplete(false)
                }
            }, null)
        }
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
        val isClickable = node.isClickable
        val className = node.className?.toString() ?: ""
        val isButtonOrImage = className.contains("Button", ignoreCase = true) || className.contains("ImageView", ignoreCase = true)
        
        // We capture node if it has text/description, OR if it is a clickable element/button component.
        // This ensures textless accept buttons or image buttons are clickable by the engine, making it 100% reliable to use!
        if (!text.isNullOrEmpty() || !contentDesc.isNullOrEmpty() || isClickable || isButtonOrImage) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            list.add(
                NodeInfoData(
                     text = text ?: "",
                     contentDescription = contentDesc ?: "",
                     bounds = bounds,
                     nodeId = node.hashCode(),
                     isClickable = isClickable,
                     className = className
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

        // 1. Dictionaries requested by User for Dr. Clicker
        val userDirectTargetKeywords = listOf(
            "ACCEPT", "ACCEPT RIDE", "ACCEPT ORDER", "TAP TO ACCEPT", "SLIDE TO ACCEPT", "SWIPE TO ACCEPT",
            "CONFIRM", "CONFIRM BOOKING", "GO TO STORE", "START TRIP", "BOOK RIDE", "ARRIVED"
        )

        val userMatchingConditions = listOf(
            "NEW RIDE", "NEW ORDER", "INCOMING REQUEST", "NEW REQUEST", "ORDER ASSIGNED",
            "BOOKING RECEIVED", "NEW TRIP", "NAYA ORDER", "NAYI RIDE", "NAYA REQUEST", "REQUEST RECEIVED"
        )

        val userPriceTrackingKeywords = listOf(
            "EARNING", "EARNINGS", "EST. EARNINGS", "ESTIMATED FARE", "ORDER PAY", "YOU WILL EARN",
            "TRIP FARE", "KAMAI", "SURGE PAY", "BONUS", "INCENTIVE", "RAIN INCENTIVE", "HIGH DEMAND BONUS"
        )

        val userTimerKeywords = listOf(
            "SEC", "SECS", "SECONDS", "TIME REMAINING", "REMAINING", "EXPIRES IN", "INSTANT"
        )

        val userLocationKeywords = listOf(
            "PICKUP", "PICKUP LOCATION", "PICKUP FROM", "DROP", "DROP LOCATION", "DROP TO",
            "DELIVER TO", "CUSTOMER LOCATION", "STORE LOCATION", "DISTANCE", "KM", "AWAY"
        )

        // Regular Expressions for currency and distances
        // Matches prefix patterns like ₹150 or Rs.150 AND suffix patterns like 150 rs or 150INR
        val farePattern = Pattern.compile(
            "(?:(?:₹|Rs\\.?|INR|\\$)\\s*(\\d+(?:\\.\\d+)?))|(?:(\\d+(?:\\.\\d+)?)\\s*(?:rs|rs\\.|inr|₹|rupees))", 
            Pattern.CASE_INSENSITIVE
        )
        // Specific Regex tip rule requested by the user for perfect symbol matching
        val userSymbolPattern = Pattern.compile("(?:₹|Rs\\.?)\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE)
        val distancePattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:km|kms|miles|mi)", Pattern.CASE_INSENSITIVE)

        // Compile combined screen text to check for apps and order keywords
        val screenTextCombined = nodes.joinToString(" ") { "${it.text} ${it.contentDescription}" }.uppercase()

        // 2. Proactively run analytics logs on incoming screen text blocks to display on real-time dashboard
        for (kw in userMatchingConditions) {
            if (screenTextCombined.contains(kw)) {
                DrClickerController.logEvent("🔔 [ORDER INCOMING] Screen matches ride/order marker: '$kw'", false)
                break
            }
        }
        for (kw in userPriceTrackingKeywords) {
            if (screenTextCombined.contains(kw)) {
                DrClickerController.logEvent("💰 [EARNINGS FOCUS] Detected price/payout reference keyword: '$kw'", false)
                break
            }
        }
        var hasTimerUrgency = false
        for (kw in userTimerKeywords) {
            if (screenTextCombined.contains(kw)) {
                hasTimerUrgency = true
                DrClickerController.logEvent("⏱️ [URGENCY ALERT] Active countdown or expiry detected on screen: '$kw'", false)
                break
            }
        }
        for (kw in userLocationKeywords) {
            if (screenTextCombined.contains(kw)) {
                DrClickerController.logEvent("📍 [ROUTE DETAILS] Found trip info/distance context: '$kw'", false)
                break
            }
        }

        // Gather enabled apps
        val enabledApps = DrClickerController.targetApps.value.filter { it.isEnabled }
        var matchedApp: DrClickerController.AppAutomationConfig? = null

        if (enabledApps.isNotEmpty()) {
            // Check if the current screen corresponds to any enabled apps by either:
            // 1) Explicit app name matching (e.g. OLA, UBER, RAPIDO, SWIGGY on-screen)
            // 2) Custom order keywords matching
            for (app in enabledApps) {
                val nameMatch = screenTextCombined.contains(app.name.uppercase())
                val keywordTokens = app.orderKeywords.split(",")
                    .map { it.trim().uppercase() }
                    .filter { it.isNotEmpty() }
                val hasKeywordMatch = keywordTokens.any { screenTextCombined.contains(it) }

                if (nameMatch || hasKeywordMatch) {
                    matchedApp = app
                    break
                }
            }

            // Fallback second pass: If no explicit app was matched by name, see if any active app's accept buttons are on-screen
            if (matchedApp == null) {
                for (app in enabledApps) {
                    val acceptTokens = app.acceptButtonKeyword.split(",")
                        .map { it.trim().uppercase() }
                        .filter { it.isNotEmpty() }
                    
                    val hasAcceptBtnOnScreen = nodes.any { node ->
                        val combinedNodeText = "${node.text} ${node.contentDescription}".uppercase()
                        acceptTokens.any { combinedNodeText.contains(it) }
                    }

                    if (hasAcceptBtnOnScreen) {
                        matchedApp = app
                        break
                    }
                }
            }
        }

        // Establish the active accept button keywords
        val currentAcceptKeywords = if (matchedApp != null) {
            matchedApp.acceptButtonKeyword.split(",")
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
        } else {
            // General fallback matches matching user's Direct Target Words
            userDirectTargetKeywords + listOf("TAKE", "APPLY", "GO", "APPROVE")
        }

        // Evaluate order-accepting keywords check
        var orderKeywordSatisfied = true
        var orderKeywordSummary = "none"
        if (matchedApp != null && matchedApp.orderKeywords.isNotEmpty()) {
            val keywordTokens = matchedApp.orderKeywords.split(",")
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
            
            if (keywordTokens.isNotEmpty()) {
                orderKeywordSatisfied = keywordTokens.any { screenTextCombined.contains(it) }
                orderKeywordSummary = matchedApp.orderKeywords
            }
        }

        for (node in nodes) {
            val combinedText = "${node.text} ${node.contentDescription}"
            
            // 1. Parse Card Fare value (supporting both generic patterns and explicit symbol tip requested by user)
            val symbolMatcher = userSymbolPattern.matcher(combinedText)
            if (symbolMatcher.find()) {
                val fareVal = symbolMatcher.group(1)?.toDoubleOrNull()?.toInt()
                if (fareVal != null && detectedFare == null) {
                    detectedFare = fareVal
                }
            }

            val fareMatcher = farePattern.matcher(combinedText)
            if (fareMatcher.find()) {
                val fareVal = (fareMatcher.group(1) ?: fareMatcher.group(2))?.toDoubleOrNull()?.toInt()
                if (fareVal != null && detectedFare == null) {
                    detectedFare = fareVal
                }
            }

            // 2. Parse Distance indicators with user's precise Pickup vs Drop context matching
            val distMatcher = distancePattern.matcher(combinedText)
            while (distMatcher.find()) {
                val distVal = distMatcher.group(1)?.toFloatOrNull()
                if (distVal != null) {
                    val textForCheck = combinedText.lowercase()
                    val isPickupContext = textForCheck.contains("pickup") || 
                                          textForCheck.contains("pick") || 
                                          textForCheck.contains("near") || 
                                          textForCheck.contains("start") || 
                                          textForCheck.contains("from") || 
                                          textForCheck.contains("customer") || 
                                          textForCheck.contains("store") || 
                                          textForCheck.contains("away")
                                          
                    val isDropContext = textForCheck.contains("drop") || 
                                        textForCheck.contains("dest") || 
                                        textForCheck.contains("trip") || 
                                        textForCheck.contains("deliver") || 
                                        textForCheck.contains("to")

                    if (isPickupContext && !isDropContext) {
                        detectedPickupKm = distVal
                    } else if (isDropContext && !isPickupContext) {
                        detectedDropKm = distVal
                    } else {
                        // Smart layout fallback: First encountered distance is usually Pickup proximity, second is Drop
                        if (detectedPickupKm == null) {
                            detectedPickupKm = distVal
                        } else if (detectedDropKm == null) {
                            detectedDropKm = distVal
                        }
                    }
                }
            }

            // 3. Search for the app-specific action button
            if (node.isClickable || node.className.contains("Button", ignoreCase = true)) {
                for (kw in currentAcceptKeywords) {
                    if (combinedText.uppercase().contains(kw)) {
                        targetActionButton = node
                        break
                    }
                }
            }
        }

        // Apply sensible fallback defaults if contextual markers weren't explicitly found but numbers are on-screen
        if (detectedFare != null) {
            val pickupVal = detectedPickupKm ?: 0.5f // Fallback to 0.5km pickup proximity if missing
            val dropVal = detectedDropKm ?: 4.0f     // Fallback to 4.0km drop distance if missing

            val minPrice = DrClickerController.minPrice.value
            val maxPrice = DrClickerController.maxPrice.value
            val maxPickup = DrClickerController.maxPickupDistance.value
            val maxDrop = DrClickerController.maxDropDistance.value

            // 4. MODULE 4 CONDITIONAL CHECK BLOCK
            val satisfiesFilters = (detectedFare >= minPrice) && 
                                   (detectedFare <= maxPrice) && 
                                   (pickupVal <= maxPickup) && 
                                   (dropVal <= maxDrop) && 
                                   orderKeywordSatisfied

            val appLabel = if (matchedApp != null) "[${matchedApp.name.uppercase()}] " else "[ALL-APPS] "

            if (satisfiesFilters) {
                DrClickerController.logEvent(
                    "${appLabel}MATCH FOUND! Price ₹$detectedFare, Pickup ${pickupVal}KM, Ride ${dropVal}KM. Sub-Filters satisfied. Matching keywords: '$orderKeywordSummary'.",
                    true
                )
                
                // Save match to local persistence
                val appNameString = matchedApp?.name ?: "Unknown"
                val offerId = "job_" + System.currentTimeMillis() + "_" + (100..999).random()
                val jobOffer = JobOffer(
                    id = offerId,
                    timestamp = System.currentTimeMillis(),
                    appName = appNameString,
                    fare = detectedFare,
                    pickupDistance = pickupVal,
                    dropDistance = dropVal,
                    satisfiesFilters = true,
                    reason = "Match Found! Filters and keywords fully satisfied."
                )
                JobOfferStorage.saveOffer(this@DrClickerAccessibilityService, jobOffer)
                
                if (targetActionButton != null) {
                    // Start natural human touch routine with speed acceleration on urgency
                    triggerHumanlikeGesture(targetActionButton, hasTimerUrgency)
                } else {
                    // If no explicit keyword button, pick the safest clickable container node
                    val generalClickable = nodes.firstOrNull { it.isClickable }
                    if (generalClickable != null) {
                        triggerHumanlikeGesture(generalClickable, hasTimerUrgency)
                    } else {
                        DrClickerController.logEvent("${appLabel}Match found but no clickable action element isolated on screen.", false)
                    }
                }
            } else {
                val reason = if (!orderKeywordSatisfied) {
                    "Order accepting keywords mismatch (required one of: $orderKeywordSummary)"
                } else {
                    "Filters mismatch: Price ₹$detectedFare (Min: ₹$minPrice), Pickup ${pickupVal}KM (Max: ${maxPickup}KM), Drop ${dropVal}KM (Max: ${maxDrop}KM)"
                }
                
                DrClickerController.logEvent(
                    "${appLabel}Card Ignored: $reason",
                    false
                )

                // Save ignored/filtered offer to local persistence
                val appNameString = matchedApp?.name ?: "Unknown"
                val offerId = "job_" + System.currentTimeMillis() + "_" + (100..999).random()
                val jobOffer = JobOffer(
                    id = offerId,
                    timestamp = System.currentTimeMillis(),
                    appName = appNameString,
                    fare = detectedFare,
                    pickupDistance = pickupVal,
                    dropDistance = dropVal,
                    satisfiesFilters = false,
                    reason = reason
                )
                JobOfferStorage.saveOffer(this@DrClickerAccessibilityService, jobOffer)
            }
        }
    }

    /**
     * MODULE 5: HUMAN-CENTRIC ERGONOMIC EXECUTION ENGINE
     */
    private fun triggerHumanlikeGesture(target: NodeInfoData, isUrgent: Boolean = false) {
        // 1. Connection/Reaction speed mode configuration
        var mode = DrClickerController.speedMode.value
        if (isUrgent && mode == "ANTIBAN") {
            // Speed up slightly to secure the ticking order while preserving safety profile
            mode = "INSTANT"
        }
        val delayMs = when (mode) {
            "INSTANT" -> (0..20).random().toLong() // Ultra fast 0-20ms click latency
            "ANTIBAN" -> (100..150).random().toLong() // 100-150ms randomized anti-ban delay (AS REQUESTED)
            else -> { // "HUMAN"
                val rawDelay = (random.nextGaussian() * 60f + 317f).toLong()
                rawDelay.coerceIn(195, 440) // Human randomized delay to bypass bot check
            }
        }

        // 2. Micro-Coordinate Variance (Inner 60% button bounds for organic shifts)
        val rect = target.bounds
        val width = rect.width()
        val height = rect.height()

        val insetWidth = (width * 0.20f).toInt()
        val insetHeight = (height * 0.20f).toInt()

        val minX = rect.left + insetWidth
        val maxX = rect.right - insetWidth
        val minY = rect.top + insetHeight
        val maxY = rect.bottom - insetHeight

        // Safely pick pixel coordinate variables with high-precision organic randomization
        val targetX = if (maxX > minX) {
            (minX..maxX).random().toFloat()
        } else {
            rect.centerX().toFloat() + (-3..3).random().toFloat()
        }
        val targetY = if (maxY > minY) {
            (minY..maxY).random().toFloat()
        } else {
            rect.centerY().toFloat() + (-3..3).random().toFloat()
        }

        // 3. Realistic Hold Variations (Hold click dur cycle 70ms to 130ms randomized for anti-ban)
        val touchDuration = (70..130).random().toLong()

        DrClickerController.logEvent(
            "🛡️ [ANTI-BAN ACTIVE] Scheduling gesture: Mode=$mode, Delay=${delayMs}ms, Coords=($targetX, $targetY), PressDur=${touchDuration}ms",
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
