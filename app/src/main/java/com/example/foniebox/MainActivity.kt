package com.example.foniebox

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("foniebox", Context.MODE_PRIVATE) }
    private val scanner = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var folderText: TextView
    private lateinit var countText: TextView
    private lateinit var durationInput: EditText
    private lateinit var fadeInput: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView

    private var selectedTreeUri: Uri? = null
    private var mp3Count: Int = 0
    private var isPlaying: Boolean = false

    private val statusRefresh = object : Runnable {
        override fun run() {
            if (isPlaying) {
                requestPlaybackStatus()
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private val playbackStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != PlaybackService.ACTION_STATUS) return

            isPlaying = intent.getBooleanExtra(PlaybackService.EXTRA_IS_PLAYING, false)
            if (isPlaying) {
                val mode = intent.getStringExtra(PlaybackService.EXTRA_MODE).orEmpty()
                val remainingMs = intent.getLongExtra(PlaybackService.EXTRA_REMAINING_MS, 0L)
                statusText.text = when (mode) {
                    PlaybackService.MODE_FADEOUT -> "Fading out... ${formatDuration(remainingMs)} until Stop"
                    else -> "Playing... ${formatDuration(remainingMs)} until Fade-out"
                }
            } else {
                statusText.text = "Stopped"
            }
            updateButtonState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedTreeUri = prefs.getString(KEY_FOLDER_URI, null)?.let(Uri::parse)
        buildUi()
        requestNotificationPermission()
        refreshFolderState()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(PlaybackService.ACTION_STATUS)
        val receiverFlags = if (Build.VERSION.SDK_INT >= 33) {
            RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        registerReceiver(playbackStatusReceiver, filter, receiverFlags)
        requestPlaybackStatus()
    }

    override fun onPause() {
        handler.removeCallbacks(statusRefresh)
        unregisterReceiver(playbackStatusReceiver)
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_OPEN_FOLDER || resultCode != RESULT_OK) return

        val uri = data?.data ?: return
        if ((data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) return
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

        selectedTreeUri = uri
        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
        refreshFolderState()
    }

    override fun onDestroy() {
        scanner.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 56, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val title = TextView(this).apply {
            text = "Foniebox"
            textSize = 30f
            gravity = Gravity.CENTER
        }
        root.addView(title, matchWrap())

        folderText = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 36, 0, 16)
        }
        root.addView(folderText, matchWrap())

        val chooseButton = Button(this).apply {
            text = "Choose MP3 Folder"
            setOnClickListener { openFolderPicker() }
        }
        root.addView(chooseButton, matchWrap())

        countText = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 24)
        }
        root.addView(countText, matchWrap())

        durationInput = numberInput("Playback minutes", prefs.getInt(KEY_DURATION_MINUTES, 30))
        root.addView(label("Playback time, minutes"))
        root.addView(durationInput, matchWrap())

        fadeInput = numberInput("Fade minutes", prefs.getInt(KEY_FADE_MINUTES, 10))
        root.addView(label("Fade-out time, minutes"))
        root.addView(fadeInput, matchWrap())

        startButton = Button(this).apply {
            text = "Start"
            setOnClickListener { startPlayback() }
        }
        root.addView(startButton, spacedButtonParams())

        stopButton = Button(this).apply {
            text = "Stop"
            isEnabled = false
            setOnClickListener { stopPlayback() }
        }
        root.addView(stopButton, matchWrap())

        statusText = TextView(this).apply {
            text = "Stopped"
            textSize = 15f
            setPadding(0, 24, 0, 0)
        }
        root.addView(statusText, matchWrap())

        val fonieboxImage = ImageView(this).apply {
            setImageResource(resources.getIdentifier("foniebox", "drawable", packageName))
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Foniebox"
            setPadding(0, 24, 0, 0)
            maxHeight = 420
        }
        root.addView(fonieboxImage, matchWrap())

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun label(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 14f
            setPadding(0, 12, 0, 4)
        }
    }

    private fun numberInput(hintValue: String, value: Int): EditText {
        return EditText(this).apply {
            hint = hintValue
            setText(value.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.CENTER
            setSelectAllOnFocus(true)
        }
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_OPEN_FOLDER)
    }

    private fun refreshFolderState() {
        val treeUri = selectedTreeUri
        startButton.isEnabled = false
        mp3Count = 0

        if (treeUri == null) {
            folderText.text = "No folder selected"
            countText.text = "Choose a folder containing MP3 files."
            return
        }

        folderText.text = "Selected folder"
        countText.text = "Scanning..."

        scanner.execute {
            val count = Mp3Scanner.findMp3s(contentResolver, treeUri).size
            runOnUiThread {
                mp3Count = count
                countText.text = if (count == 1) "1 MP3 found" else "$count MP3s found"
                updateButtonState()
            }
        }
    }

    private fun startPlayback() {
        val treeUri = selectedTreeUri ?: return
        if (mp3Count <= 0) return

        val durationMinutes = positiveInt(durationInput.text.toString(), 30)
        val fadeMinutes = positiveInt(fadeInput.text.toString(), 10)
        prefs.edit()
            .putInt(KEY_DURATION_MINUTES, durationMinutes)
            .putInt(KEY_FADE_MINUTES, fadeMinutes)
            .apply()

        val intent = Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_START
            putExtra(PlaybackService.EXTRA_TREE_URI, treeUri.toString())
            putExtra(PlaybackService.EXTRA_DURATION_MINUTES, durationMinutes)
            putExtra(PlaybackService.EXTRA_FADE_MINUTES, fadeMinutes)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isPlaying = true
        showInitialPlayingStatus(durationMinutes, fadeMinutes)
        updateButtonState()
        handler.removeCallbacks(statusRefresh)
        handler.postDelayed(statusRefresh, 250L)
    }

    private fun stopPlayback() {
        startService(Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_STOP
        })
        isPlaying = false
        statusText.text = "Stopped"
        updateButtonState()
        handler.removeCallbacks(statusRefresh)
    }

    private fun requestPlaybackStatus() {
        startService(Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_STATUS_REQUEST
        })
    }

    private fun showInitialPlayingStatus(durationMinutes: Int, fadeMinutes: Int) {
        val playbackMs = durationMinutes * 60_000L
        statusText.text = "Playing... ${formatDuration(playbackMs)} until Fade-out"
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    private fun positiveInt(value: String, fallback: Int): Int {
        return value.toIntOrNull()?.takeIf { it > 0 } ?: fallback
    }

    private fun updateButtonState() {
        startButton.isEnabled = mp3Count > 0 && !isPlaying
        stopButton.isEnabled = isPlaying
    }

    private fun formatDuration(milliseconds: Long): String {
        val totalSeconds = (milliseconds.coerceAtLeast(0L) + 999L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun matchWrap(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun spacedButtonParams(): LinearLayout.LayoutParams {
        return matchWrap().apply { topMargin = 36 }
    }

    companion object {
        private const val REQUEST_OPEN_FOLDER = 100
        private const val REQUEST_NOTIFICATIONS = 101
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_DURATION_MINUTES = "duration_minutes"
        private const val KEY_FADE_MINUTES = "fade_minutes"
    }
}

data class Mp3Track(val uri: Uri, val name: String)

object Mp3Scanner {
    private const val MIME_DIRECTORY = "vnd.android.document/directory"

    fun findMp3s(contentResolver: android.content.ContentResolver, treeUri: Uri): List<Mp3Track> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val tracks = mutableListOf<Mp3Track>()
        scanDocument(contentResolver, treeUri, rootId, tracks)
        return tracks
    }

    private fun scanDocument(
        contentResolver: android.content.ContentResolver,
        treeUri: Uri,
        documentId: String,
        tracks: MutableList<Mp3Track>
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex) ?: ""
                val mimeType = cursor.getString(mimeIndex) ?: ""

                if (mimeType == MIME_DIRECTORY) {
                    scanDocument(contentResolver, treeUri, childId, tracks)
                } else if (name.endsWith(".mp3", ignoreCase = true) || mimeType == "audio/mpeg") {
                    tracks += Mp3Track(DocumentsContract.buildDocumentUriUsingTree(treeUri, childId), name)
                }
            }
        }
    }
}
