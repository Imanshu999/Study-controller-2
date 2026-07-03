package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerView(
    videoId: String,
    modifier: Modifier = Modifier
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(80f) } // 0f to 100f
    var isReady by remember { mutableStateOf(false) }

    val htmlData = remember(videoId) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                body, html { margin: 0; padding: 0; width: 100%; height: 100%; background-color: #000; overflow: hidden; }
                #player { width: 100%; height: 100%; }
            </style>
        </head>
        <body>
            <div id="player"></div>
            <script>
                var tag = document.createElement('script');
                tag.src = "https://www.youtube.com/iframe_api";
                var firstScriptTag = document.getElementsByTagName('script')[0];
                firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                var player;
                function onYouTubeIframeAPIReady() {
                    player = new YT.Player('player', {
                        height: '100%',
                        width: '100%',
                        videoId: '$videoId',
                        playerVars: {
                            'playsinline': 1,
                            'controls': 1, // Keep iframe controls enabled for convenience
                            'rel': 0,
                            'showinfo': 0,
                            'modestbranding': 1
                        },
                        events: {
                            'onReady': onPlayerReady,
                            'onStateChange': onPlayerStateChange
                        }
                    });
                }

                function onPlayerReady(event) {
                    window.AndroidBridge.onPlayerReady();
                }

                function onPlayerStateChange(event) {
                    window.AndroidBridge.onPlayerStateChange(event.data);
                }

                function playVideo() {
                    if (player && player.playVideo) player.playVideo();
                }

                function pauseVideo() {
                    if (player && player.pauseVideo) player.pauseVideo();
                }

                function seekBy(seconds) {
                    if (player && player.getCurrentTime && player.seekTo) {
                        var currentTime = player.getCurrentTime();
                        player.seekTo(currentTime + seconds, true);
                    }
                }

                function setVolume(level) {
                    if (player && player.setVolume) player.setVolume(level);
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    class WebAppInterface(private val getWebView: () -> WebView?) {
        @JavascriptInterface
        fun onPlayerReady() {
            isReady = true
            // Sync initial volume
            val view = getWebView()
            view?.post {
                view.evaluateJavascript("setVolume($volume);", null)
            }
        }

        @JavascriptInterface
        fun onPlayerStateChange(state: Int) {
            // 1 = playing, 2 = paused, 0 = ended
            isPlaying = (state == 1)
        }
    }

    LaunchedEffect(videoId, webView) {
        webView?.let { view ->
            isReady = false
            isPlaying = false
            view.loadDataWithBaseURL("https://www.youtube.com", htmlData, "text/html", "utf-8", null)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Embed Player WebView
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.domStorageEnabled = true
                        
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        
                        addJavascriptInterface(WebAppInterface { webView }, "AndroidBridge")
                        loadDataWithBaseURL("https://www.youtube.com", htmlData, "text/html", "utf-8", null)
                        webView = this
                    }
                },
                update = { view ->
                    webView = view
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Seek Back Button
            IconButton(
                onClick = {
                    webView?.evaluateJavascript("seekBy(-10);", null)
                },
                enabled = isReady,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Rewind 10 seconds",
                    tint = if (isReady) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Play/Pause Button
            Button(
                onClick = {
                    if (isPlaying) {
                        webView?.evaluateJavascript("pauseVideo();", null)
                        isPlaying = false
                    } else {
                        webView?.evaluateJavascript("playVideo();", null)
                        isPlaying = true
                    }
                },
                enabled = isReady,
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause video" else "Play video"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isPlaying) "Pause" else "Play")
            }

            // Seek Forward Button
            IconButton(
                onClick = {
                    webView?.evaluateJavascript("seekBy(10);", null)
                },
                enabled = isReady,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Forward 10 seconds",
                    tint = if (isReady) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Volume Controller
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Volume icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )
            Slider(
                value = volume,
                onValueChange = { newVolume ->
                    volume = newVolume
                    webView?.evaluateJavascript("setVolume(${newVolume.toInt()});", null)
                },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${volume.toInt()}%",
                style = MaterialTheme.styleScheme.bodyMedium,
                modifier = Modifier
                    .width(44.dp)
                    .padding(start = 12.dp)
            )
        }
    }
}

// Support bodyMedium mapping for MaterialTheme style safely
private val MaterialTheme.styleScheme: Typography
    @Composable
    get() = typography
