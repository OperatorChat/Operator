/*
 * Operator: chat for keypad phones.
 * Copyright (C) 2026 Spanorak (https://www.reddit.com/user/Spanorak)
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version. It is distributed WITHOUT ANY WARRANTY; see
 * the GNU General Public License (LICENSE) for details.
 */
package chat.operator.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import chat.operator.app.R
import chat.operator.app.databinding.ActivityMainBinding
import chat.operator.app.notify.OperatorNotifier
import chat.operator.core.matrix.MatrixRepository
import kotlinx.coroutines.launch

/**
 * Single-activity host with a stack of [Screen]s. The soft-key bar at the
 * bottom always shows what the left and right keys do.
 *
 * Keypad phones: left soft = MENU, right soft = BACK, centre = ENTER (the
 * framework turns ENTER/DPAD_CENTER into a click on the focused view).
 */
class MainActivity : AppCompatActivity(), ScreenHost {

    companion object {
        /** Set before recreate() from the Appearance screen so the new activity returns there. */
        @Volatile
        var reopenAppearanceAfterRecreate = false
        /** Which Appearance row was changed, so focus returns to it after the recreate. */
        @Volatile
        var reopenAppearanceRow = 0
    }

    private lateinit var binding: ActivityMainBinding
    private val stack = ArrayDeque<Screen>()

    // Photo picking / capture: the result goes to whichever screen asked.
    private var onPhotoPicked: ((android.net.Uri?) -> Unit)? = null
    private var cameraOutput: android.net.Uri? = null
    /** Room a photo is being chosen for, kept across an activity recreate. */
    var pendingPhotoRoom: String? = null
    /** A photo that arrived after a recreate, for the chat screen to pick up. */
    var pendingPhotoUri: android.net.Uri? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingPhotoRoom?.let { outState.putString("pendingPhotoRoom", it) }
        cameraOutput?.let { outState.putString("cameraOutput", it.toString()) }
    }
    private val pickPhoto = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        android.util.Log.i("Photo", "gallery result: ${if (uri == null) "none" else "image from ${uri.authority}"}; callback ${if (onPhotoPicked == null) "LOST" else "present"}")
        onPhotoPicked?.invoke(uri); onPhotoPicked = null
    }
    private val takePhoto = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
        android.util.Log.i("Photo", "camera result: ${if (ok) "saved" else "cancelled or failed"}; callback ${if (onPhotoPicked == null) "LOST" else "present"}")
        onPhotoPicked?.invoke(if (ok) cameraOutput else null); onPhotoPicked = null
    }

    private var onPermissionResult: ((Boolean) -> Unit)? = null
    private val requestPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        onPermissionResult?.invoke(granted); onPermissionResult = null
    }

    /** Asks for one runtime permission; [callback] gets the answer. */
    fun requestPermission(permission: String, callback: (Boolean) -> Unit) {
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) { callback(true); return }
        onPermissionResult = callback
        requestPermission.launch(permission)
    }

    /** Android 13+: notifications need the user's permission; older versions grant it. */
    fun requestNotificationPermission(callback: (Boolean) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < 33) { callback(true); return }
        onPermissionResult = callback
        requestPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Opens the phone's gallery picker; [callback] gets the chosen image or null. */
    fun pickPhoto(callback: (android.net.Uri?) -> Unit) {
        onPhotoPicked = callback
        runCatching { pickPhoto.launch("image/*") }.onFailure { callback(null); onPhotoPicked = null }
    }

    /** Opens the phone's camera app; [callback] gets the captured image or null. */
    fun takePhoto(callback: (android.net.Uri?) -> Unit) {
        onPhotoPicked = callback
        val dir = java.io.File(cacheDir, "camera").apply { mkdirs() }
        val file = java.io.File(dir, "capture-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", file)
        cameraOutput = uri
        runCatching { takePhoto.launch(uri) }.onFailure { callback(null); onPhotoPicked = null }
    }

    override val activity: MainActivity get() = this
    override val scope: LifecycleCoroutineScope get() = lifecycleScope

    override fun onCreate(savedInstanceState: Bundle?) {
        Appearance.applyTo(this)
        super.onCreate(savedInstanceState)
        android.util.Log.i("Photo", "activity created (restored=${savedInstanceState != null})")
        // Survive a recreate while the camera/gallery is in front: the pending room is kept.
        savedInstanceState?.getString("cameraOutput")?.let { cameraOutput = android.net.Uri.parse(it) }
        savedInstanceState?.getString("pendingPhotoRoom")?.let { room ->
            pendingPhotoRoom = room
            onPhotoPicked = { uri -> uri?.let { pendingPhotoUri = it } }
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        paintSystemBars()
        route(intent)
    }

    override fun onResume() {
        super.onResume()
        // The sync loop is screen- and process-lifecycle dependent in the ported
        // code; coming to the foreground is the one moment we know it must run.
        lifecycleScope.launch {
            if (MatrixRepository.ensureClient() != null) MatrixRepository.applySyncModeForScreenState()
        }
        stack.lastOrNull()?.onResume()
    }

    /**
     * Turning the phone sideways (auto-rotate on) while the chat list is up
     * previews the highlighted chat without opening it, so nothing is marked
     * read; turning it upright again closes the preview. The layout itself
     * reflows in place (configChanges in the manifest), so focus survives.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val landscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val top = stack.lastOrNull()
        when {
            landscape && top is ChatListScreen && Appearance.peek(this) ->
                top.currentRoom()?.let { push(PeekScreen(this, it.id, it.name)) }
            !landscape && top is PeekScreen -> pop()
        }
    }

    /**
     * Sideways, the D-pad's physical "up" points left or right. Remap the four
     * directions so they follow the screen as held: with the top of the phone to
     * the left (rotation 90) physical up means left; to the right (270) it means right.
     */
    private var remappingKey = false
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!remappingKey && resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            val rotation = (if (android.os.Build.VERSION.SDK_INT >= 30) display?.rotation else @Suppress("DEPRECATION") windowManager.defaultDisplay.rotation) ?: android.view.Surface.ROTATION_0
            val mapped = when (rotation) {
                android.view.Surface.ROTATION_90 -> when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> KeyEvent.KEYCODE_DPAD_LEFT
                    KeyEvent.KEYCODE_DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_UP
                    KeyEvent.KEYCODE_DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_RIGHT
                    KeyEvent.KEYCODE_DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_DOWN
                    else -> null
                }
                android.view.Surface.ROTATION_270 -> when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> KeyEvent.KEYCODE_DPAD_RIGHT
                    KeyEvent.KEYCODE_DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_DOWN
                    KeyEvent.KEYCODE_DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_LEFT
                    KeyEvent.KEYCODE_DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_UP
                    else -> null
                }
                else -> null
            }
            if (mapped != null) {
                remappingKey = true
                try {
                    return super.dispatchKeyEvent(
                        KeyEvent(event.downTime, event.eventTime, event.action, mapped, event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source),
                    )
                } finally {
                    remappingKey = false
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val roomId = intent.getStringExtra(OperatorNotifier.EXTRA_ROOM_ID) ?: return
        if (stack.lastOrNull() is ChatListScreen || stack.lastOrNull() is ChatScreen) {
            openRoomFromNotification(roomId)
        }
    }

    /** Decide the first screen: a restored session goes straight to the chats. */
    private fun route(intent: Intent?) {
        replaceAll(ConnectingScreen(this))
        lifecycleScope.launch {
            val client = MatrixRepository.ensureClient()
            if (client == null) {
                replaceAll(WelcomeScreen(this@MainActivity))
                return@launch
            }
            // An unapproved phone goes back to the approval screen first (Back skips it).
            val approved = runCatching { MatrixRepository.e2eeState().verified }.getOrDefault(false)
            when {
                !approved -> replaceAll(RecoveryKeyScreen(this@MainActivity))
                Onboarding.shouldRemind(this@MainActivity) -> replaceAll(PhoneSetupScreen(this@MainActivity, onboarding = true))
                else -> replaceAll(ChatListScreen(this@MainActivity))
            }
            if (reopenAppearanceAfterRecreate) {
                reopenAppearanceAfterRecreate = false
                push(SettingsScreen(this@MainActivity))
                push(AppearanceScreen(this@MainActivity))
                return@launch
            }
            val roomId = intent?.getStringExtra(OperatorNotifier.EXTRA_ROOM_ID)
                ?: MatrixRepository.takeNotifyRoom()
            if (roomId != null) openRoomFromNotification(roomId)
        }
    }

    /**
     * The status bar and navigation bar take the theme's paper colour with icons
     * tinted to match, so the whole screen reads as one surface.
     */
    private fun paintSystemBars() {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(chat.operator.app.R.attr.opBackground, tv, true)
        val colour = tv.data
        window.statusBarColor = colour
        window.navigationBarColor = colour
        val light = androidx.core.graphics.ColorUtils.calculateLuminance(colour) > 0.5
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    private fun openRoomFromNotification(roomId: String) {
        while (stack.size > 1) popSilently()
        push(ChatScreen(this, roomId))
    }

    // ---- ScreenHost ----

    override fun push(screen: Screen) {
        stack.lastOrNull()?.onHide()
        stack.addLast(screen)
        show(screen)
    }

    override fun pop() {
        if (stack.size <= 1) {
            finish()
            return
        }
        popSilently()
        show(stack.last())
    }

    private fun popSilently() {
        stack.removeLast().onHide()
    }

    override fun replaceAll(screen: Screen) {
        stack.forEach { it.onHide() }
        stack.clear()
        stack.addLast(screen)
        show(screen)
    }

    override fun setSoftKeys(left: CharSequence?, right: CharSequence?) {
        binding.softKeys.setLabels(left, right)
    }

    private fun show(screen: Screen) {
        val previous = binding.screenHost.getChildAt(binding.screenHost.childCount - 1)
        binding.screenHost.removeAllViews()
        if (screen.isOverlay) {
            // Keep the screen beneath in view (static; its work paused in onHide) and dim it with
            // the sheet. It must not take focus, or the D-pad would move through it behind the sheet.
            val beneath = stack.getOrNull(stack.size - 2)
            if (beneath != null && beneath.isCreated) {
                (beneath.view as? android.view.ViewGroup)?.let { group ->
                    if (group.getTag(R.id.saved_focusability) == null) group.setTag(R.id.saved_focusability, group.descendantFocusability)
                    group.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
                binding.screenHost.addView(beneath.view)
            }
        }
        val view = if (screen.isCreated) screen.view else screen.create(binding.screenHost)
        (view as? android.view.ViewGroup)?.let { group ->
            // Back on top: let its children take focus again.
            (group.getTag(R.id.saved_focusability) as? Int)?.let { saved ->
                group.descendantFocusability = saved
                group.setTag(R.id.saved_focusability, null)
            }
        }
        binding.screenHost.addView(view)
        if (previous != null && previous !== view && !screen.isOverlay && Appearance.motion(this)) {
            // A short fade-and-rise; cheap enough for the phone's chip, off in Appearance if unwanted.
            view.alpha = 0f
            view.translationY = resources.getDimension(chat.operator.app.R.dimen.gap)
            view.animate().alpha(1f).translationY(0f).setDuration(140).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
        screen.onShow()
    }

    // ---- keys ----

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val current = stack.lastOrNull() ?: return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> current.onMenu() || super.onKeyDown(keyCode, event)
            KeyEvent.KEYCODE_CALL -> current.onCall() || super.onKeyDown(keyCode, event)
            else -> current.onKey(keyCode) || super.onKeyDown(keyCode, event)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val current = stack.lastOrNull()
        if (current != null && current.onBack()) return
        pop()
    }
}
