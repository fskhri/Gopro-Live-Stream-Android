package com.gopro.rebuild.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.gopro.rebuild.ui.viewmodel.TiktokStreamViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "TiktokLoginScreen"
private const val LOGIN_URL =
    "https://www.tiktok.com/login?is_modal=1&hide_toggle_login_signup=1&enter_method=live_studio&enter_from=live_studio&lang=en"
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TiktokLoginScreen(
    onBack: () -> Unit,
    viewModel: TiktokStreamViewModel = hiltViewModel()
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var isLoading by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Memuat halaman login TikTok...") }
    val scope = rememberCoroutineScope()

    fun getAllCookies(): String {
        val cm = CookieManager.getInstance()
        val c1 = cm.getCookie("https://www.tiktok.com") ?: ""
        val c2 = cm.getCookie("https://webcast.tiktok.com") ?: ""
        val c3 = cm.getCookie("https://tiktok.com") ?: ""
        return (c1.split(";") + c2.split(";") + c3.split(";"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("; ")
    }

    fun extractCookies(currentUrl: String) {
        val cookies = getAllCookies()
        Log.d(TAG, "extractCookies ($currentUrl): len=${cookies.length}")

        if (cookies.contains("sessionid") || cookies.contains("sid_tt")) {
            statusText = "Login terdeteksi! Menyimpan cookies..."
            viewModel.importCookies(cookies)

            if (cookies.contains("sessionid")) {
                statusText = "Berhasil login dengan sessionid!"
                scope.launch {
                    delay(1000)
                    onBack()
                }
            } else {
                Log.d(TAG, "sid_tt found without sessionid, loading foryou...")
                webView?.loadUrl("https://www.tiktok.com/foryou")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Login TikTok LIVE Studio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val cookies = getAllCookies()
                        if (cookies.isNotEmpty()) {
                            viewModel.importCookies(cookies)
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Selesai Simpan", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { webView?.loadUrl("https://www.tiktok.com/foryou") }) {
                        Icon(Icons.Default.Home, contentDescription = "Foryou")
                    }
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = DESKTOP_UA
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true

                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                statusText = "Memuat: $url"
                                if (url != null && (url.contains("foryou") || url.contains("@") || url.contains("live"))) {
                                    extractCookies(url)
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                statusText = "Selesai memuat"
                                if (url != null && !url.contains("login") && !url.contains("signup")) {
                                    extractCookies(url)
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val u = request?.url?.toString() ?: return false
                                if (u.startsWith("http://") || u.startsWith("https://")) {
                                    return false
                                }
                                return true
                            }
                        }

                        loadUrl(LOGIN_URL)
                        webView = this
                    }
                }
            )

            if (isLoading) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(statusText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
