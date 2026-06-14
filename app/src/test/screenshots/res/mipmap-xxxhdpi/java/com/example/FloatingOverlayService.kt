package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

class FloatingOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val CHANNEL_ID = "DrClickerOverlayChannel"
        private const val NOTIFICATION_ID = 2673
        private const val TAG = "FloatingOverlayService"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var floatingWindowParams: WindowManager.LayoutParams

    private val targetViewsMap = mutableMapOf<Int, ComposeView>()
    private val swipeEndViewsMap = mutableMapOf<Int, ComposeView>()

    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job())
    private var playbackJob: kotlinx.coroutines.Job? = null

    // Manual Lifecycle & SavedState registry for Compose support inside standard Services
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        
        DrClickerController.initialize(this) // Ensure controller is ready
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        
        setupFloatingWindow()

        // Start listening to the visual targets Flow to render them dynamically
        serviceScope.launch {
            DrClickerController.visualTargets.collect { targets ->
                updateTargetOverlays(targets)
            }
        }

        // Start the automated target execution loop
        startPlaybackLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dr.Clicker Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the overlay assistant active for delivery route matching"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val clickIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, clickIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dr.Clicker Active")
            .setContentText("Tap to configure filters or manage background scanning")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (true) {
                if (DrClickerController.isScanning.value) {
                    val targets = DrClickerController.visualTargets.value
                    if (targets.isNotEmpty()) {
                        for (t in targets) {
                            if (!DrClickerController.isScanning.value) break

                            // Fetch accessibility service instance
                            val service = DrClickerAccessibilityService.instance
                            if (service != null) {
                                val metrics = resources.displayMetrics
                                val screenWidth = metrics.widthPixels
                                val screenHeight = metrics.heightPixels

                                val pxX = (t.xPercent * screenWidth)
                                val pxY = (t.yPercent * screenHeight)

                                DrClickerController.logEvent("🎯 [TARGET MATCH] Simulating tap #${t.id} (${if (t.isSwipe) "Swipe" else "Tap"}) at (${pxX.toInt()}, ${pxY.toInt()})", true)

                                if (t.isSwipe) {
                                    val endPxX = (t.endXPercent * screenWidth)
                                    val endPxY = (t.endYPercent * screenHeight)
                                    service.dispatchManualSwipe(pxX, pxY, endPxX, endPxY, 320L)
                                } else {
                                    // Inject a visual-comfort jitter offset if configured
                                    val jitter = DrClickerController.randomJitter.value
                                    val finalX = if (jitter > 0) pxX + (-jitter..jitter).random() else pxX
                                    val finalY = if (jitter > 0) pxY + (-jitter..jitter).random() else pxY
                                    service.dispatchManualTap(finalX, finalY, 80L)
                                }
                            } else {
                                DrClickerController.logEvent("⚠️ Visual macro clicker is pending: Grant Accessibility Permission first", false)
                            }

                            // Wait for the specific target delay plus central interval configurations
                            val waitTime = (t.delayMs + DrClickerController.clickInterval.value).toLong().coerceAtLeast(100L)
                            kotlinx.coroutines.delay(waitTime)
                        }
                    } else {
                        // Allow screen matching automatic text scan to run separately, keep loop idle shortly
                        kotlinx.coroutines.delay(1000L)
                    }
                } else {
                    kotlinx.coroutines.delay(500L)
                }
            }
        }
    }

    private fun updateTargetOverlays(targets: List<DrClickerController.VisualTargetPoint>) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Remove views that are no longer in the active targets list
        val activeIds = targets.map { it.id }.toSet()
        val currentViewIds = targetViewsMap.keys.toList()
        for (id in currentViewIds) {
            if (!activeIds.contains(id)) {
                removeTargetView(id)
            }
        }

        // Create or update overlays for each visual target
        for (t in targets) {
            createOrUpdateTargetView(t, screenWidth, screenHeight)
        }
    }

    private fun createOrUpdateTargetView(t: DrClickerController.VisualTargetPoint, screenWidth: Int, screenHeight: Int) {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 1. Primary Target Cursor (Point / Start Badge)
        val viewX = (t.xPercent * screenWidth).toInt() - 50 // center of 100px window
        val viewY = (t.yPercent * screenHeight).toInt() - 50

        val targetView = targetViewsMap[t.id]
        if (targetView == null) {
            val params = WindowManager.LayoutParams(
                100,
                100,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = viewX.coerceIn(0, screenWidth - 100)
                y = viewY.coerceIn(0, screenHeight - 100)
            }

            val targetComposeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingOverlayService)
                setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
                setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                })
                setContent {
                    VisualPointBadge(
                        label = if (t.isSwipe) "S${t.id}" else "${t.id}",
                        isSwipe = t.isSwipe,
                        isStart = true,
                        onDrag = { dx, dy ->
                            params.x = (params.x + dx).coerceIn(0, screenWidth - 100)
                            params.y = (params.y + dy).coerceIn(0, screenHeight - 100)
                            try {
                                windowManager.updateViewLayout(this, params)
                            } catch (e: Exception) {}

                            val centerX = params.x + 50
                            val centerY = params.y + 50
                            DrClickerController.updateVisualTargetPosition(
                                this@FloatingOverlayService,
                                t.id,
                                centerX.toFloat() / screenWidth,
                                centerY.toFloat() / screenHeight
                            )
                        }
                    )
                }
            }

            try {
                windowManager.addView(targetComposeView, params)
                targetViewsMap[t.id] = targetComposeView
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inject visual target window: ${e.message}")
            }
        } else {
            // Update position if updated from controller externally
            val params = targetView.layoutParams as WindowManager.LayoutParams
            if (Math.abs(params.x + 50 - viewX - 50) > 5 || Math.abs(params.y + 50 - viewY - 50) > 5) {
                params.x = viewX.coerceIn(0, screenWidth - 100)
                params.y = viewY.coerceIn(0, screenHeight - 100)
                try {
                    windowManager.updateViewLayout(targetView, params)
                } catch (e: Exception) {}
            }
        }

        // 2. Swipe Target End Cursor (Orange badge indicating path exit)
        if (t.isSwipe) {
            val swipeEndX = (t.endXPercent * screenWidth).toInt() - 50
            val swipeEndY = (t.endYPercent * screenHeight).toInt() - 50

            val swipeEndView = swipeEndViewsMap[t.id]
            if (swipeEndView == null) {
                val paramsEnd = WindowManager.LayoutParams(
                    100,
                    100,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = swipeEndX.coerceIn(0, screenWidth - 100)
                    y = swipeEndY.coerceIn(0, screenHeight - 100)
                }

                val endComposeView = ComposeView(this).apply {
                    setViewTreeLifecycleOwner(this@FloatingOverlayService)
                    setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
                    setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                        override val viewModelStore = ViewModelStore()
                    })
                    setContent {
                        VisualPointBadge(
                            label = "E${t.id}",
                            isSwipe = true,
                            isStart = false,
                            onDrag = { dx, dy ->
                                paramsEnd.x = (paramsEnd.x + dx).coerceIn(0, screenWidth - 100)
                                paramsEnd.y = (paramsEnd.y + dy).coerceIn(0, screenHeight - 100)
                                try {
                                    windowManager.updateViewLayout(this, paramsEnd)
                                } catch (e: Exception) {}

                                val centerX = paramsEnd.x + 50
                                val centerY = paramsEnd.y + 50
                                DrClickerController.updateVisualTargetSwipeEndPosition(
                                    this@FloatingOverlayService,
                                    t.id,
                                    centerX.toFloat() / screenWidth,
                                    centerY.toFloat() / screenHeight
                                )
                            }
                        )
                    }
                }

                try {
                    windowManager.addView(endComposeView, paramsEnd)
                    swipeEndViewsMap[t.id] = endComposeView
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to inject swipe target end window: ${e.message}")
                }
            } else {
                val paramsEnd = swipeEndView.layoutParams as WindowManager.LayoutParams
                if (Math.abs(paramsEnd.x + 50 - swipeEndX - 50) > 5 || Math.abs(paramsEnd.y + 50 - swipeEndY - 50) > 5) {
                    paramsEnd.x = swipeEndX.coerceIn(0, screenWidth - 100)
                    paramsEnd.y = swipeEndY.coerceIn(0, screenHeight - 100)
                    try {
                        windowManager.updateViewLayout(swipeEndView, paramsEnd)
                    } catch (e: Exception) {}
                }
            }
        } else {
            // Remove end marker if changed from Swipe to Tap
            if (swipeEndViewsMap.containsKey(t.id)) {
                try {
                    windowManager.removeView(swipeEndViewsMap[t.id])
                } catch (e: Exception) {}
                swipeEndViewsMap.remove(t.id)
            }
        }
    }

    private fun removeTargetView(id: Int) {
        if (targetViewsMap.containsKey(id)) {
            try {
                windowManager.removeView(targetViewsMap[id])
            } catch (e: Exception) {}
            targetViewsMap.remove(id)
        }
        if (swipeEndViewsMap.containsKey(id)) {
            try {
                windowManager.removeView(swipeEndViewsMap[id])
            } catch (e: Exception) {}
            swipeEndViewsMap.remove(id)
        }
    }

    private fun setupFloatingWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission not granted")
            stopSelf()
            return
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        floatingWindowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 250
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            })
            setContent {
                OverlayDashboard(
                    onDrag = { dx, dy ->
                        floatingWindowParams.x += dx
                        floatingWindowParams.y += dy
                        try {
                            windowManager.updateViewLayout(composeView, floatingWindowParams)
                        } catch (e: Exception) {}
                    },
                    onOpenSettings = {
                        val intent = Intent(this@FloatingOverlayService, SettingsActivity::class.java).apply {
                            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        this@FloatingOverlayService.startActivity(intent)
                    },
                    onHide = {
                        stopSelf()
                    }
                )
            }
        }

        try {
            windowManager.addView(composeView, floatingWindowParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating window overlay: ${e.message}", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        playbackJob?.cancel()
        serviceScope.cancel()

        // Clean up individual visual target views
        targetViewsMap.keys.toList().forEach { removeTargetView(it) }

        if (::composeView.isInitialized) {
            try {
                windowManager.removeView(composeView)
            } catch (e: Exception) {}
        }
        super.onDestroy()
    }
}

@Composable
fun VisualPointBadge(
    label: String,
    isSwipe: Boolean,
    isStart: Boolean,
    onDrag: (Int, Int) -> Unit
) {
    val baseColor = if (isSwipe) {
        if (isStart) Color(0xFFF59E0B) else Color(0xFFD97706)
    } else {
        Color(0xFFFF3B30)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(baseColor.copy(alpha = 0.25f))
        )
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(baseColor)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun OverlayDashboard(
    onDrag: (Int, Int) -> Unit,
    onOpenSettings: () -> Unit,
    onHide: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isScanning by DrClickerController.isScanning.collectAsState()
    val visualTargets by DrClickerController.visualTargets.collectAsState()

    var isCollapsed by remember { mutableStateOf(false) }

    val activeBg = Color(0xEEFAF6F0)
    val cardColor = Color(0xFFFFFFFF)
    val primaryText = Color(0xFF5D4037)
    val buttonGreen = Color(0xFF10B981)
    val buttonBgText = Color(0xFFFFFFFF)
    val accentOrange = Color(0xFFF59E0B)

    if (isCollapsed) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(activeBg)
                .border(1.5.dp, primaryText.copy(alpha = 0.4f), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                    }
                }
                .clickable { isCollapsed = false },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Logo",
                modifier = Modifier.size(38.dp)
            )
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(if (isScanning) buttonGreen else Color.Red)
                    .border(1.5.dp, Color.White, CircleShape)
            )
        }
    } else {
        Card(
            modifier = Modifier
                .width(225.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                    }
                },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, primaryText.copy(alpha = 0.25f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Toolbar Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "Logo",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DR.CLICKER",
                            color = primaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = primaryText.copy(alpha = 0.6f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { isCollapsed = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Minimize",
                                tint = Color.Gray,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Automation Status bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isScanning) buttonGreen.copy(alpha = 0.08f) else Color.LightGray.copy(alpha = 0.15f))
                        .padding(vertical = 4.dp, horizontal = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isScanning) buttonGreen else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isScanning) "SCANNING & PLAYBACK RUNNING" else "SYSTEM IDLE / WAITING",
                        color = if (isScanning) buttonGreen else Color.Gray,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- GIG PROFESSIONAL TACTICAL TOOLBAR ---
                Text(
                    text = "VISUAL TARGET PLACEMENT",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = primaryText.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                )

                // Row of add/remove clicker tools
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Button to Add Click targets
                    Button(
                        onClick = { DrClickerController.addVisualTarget(context, isSwipe = false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryText, contentColor = buttonBgText),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Point", modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Click", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Button to Add Swipe targets
                    Button(
                        onClick = { DrClickerController.addVisualTarget(context, isSwipe = true) },
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentOrange, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Send, contentDescription = "Add Swipe", modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Swipe", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Button to clear individual targets
                    Button(
                        onClick = { DrClickerController.removeLastVisualTarget(context) },
                        modifier = Modifier
                            .size(28.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray.copy(alpha = 0.4f), contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("-1", fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }

                    Button(
                        onClick = { DrClickerController.clearAllVisualTargets(context) },
                        modifier = Modifier
                            .size(28.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEF2F2), contentColor = Color.Red),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🗑️", fontSize = 10.sp)
                    }
                }

                if (visualTargets.isNotEmpty()) {
                    Text(
                        text = "Active Visual Points: ${visualTargets.size} configured",
                        fontSize = 8.sp,
                        color = primaryText,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Master Toggle (Play / Pause clicking engine)
                Button(
                    onClick = { DrClickerController.setScanning(!isScanning) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) Color.Red else buttonGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isScanning) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = "Toggle Clicker",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isScanning) "STOP SYSTEM" else "START SCANNING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Close overlay completely
                Button(
                    onClick = onHide,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "DISMISS ASSISTANT OVERLAY",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}
