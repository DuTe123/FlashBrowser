package com.flashbrowser

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.flashbrowser.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var virtualMouseEnabled = false
    private var virtualKeyboardEnabled = false
    private var isFullscreen = false

    companion object {
        private const val HOME_URL = "https://www.4399.com"
        private const val PREFS_NAME = "FlashBrowserPrefs"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        setupWebView()
        setupToolbar()
        setupBottomBar()

        binding.etAddress.setText(HOME_URL)
        binding.webView.loadUrl(HOME_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings = binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = userAgentString.replace("Mobile", "Mobile FlashBrowser")
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.endsWith(".swf") || url.contains(".swf?")) {
                    openSwfPlayer(url)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                injectRuffle()
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                binding.progressBar.progress = newProgress
            }
        }

        binding.webView.setOnTouchListener { _, event ->
            if (virtualMouseEnabled) {
                handleVirtualMouse(event)
            }
            false
        }
    }

    private fun injectRuffle() {
        val script = """
            (function() {
                if (window.ruffleInjected) return;
                window.ruffleInjected = true;
                
                var script = document.createElement('script');
                script.src = 'https://unpkg.com/@ruffle-rs/ruffle';
                script.onload = function() {
                    if (window.Ruffle) {
                        window.RufflePlayer = window.Ruffle;
                        var flashObjects = document.querySelectorAll('object, embed');
                        flashObjects.forEach(function(obj) {
                            if (obj.type === 'application/x-shockwave-flash' || 
                                obj.data?.endsWith('.swf') || 
                                obj.src?.endsWith('.swf')) {
                                var container = document.createElement('div');
                                container.style.width = '100%';
                                container.style.height = '100%';
                                obj.parentNode?.replaceChild(container, obj);
                                
                                var player = window.RufflePlayer.newest().createPlayer();
                                player.style.width = '100%';
                                player.style.height = '100%';
                                container.appendChild(player);
                                
                                var swfUrl = obj.data || obj.src;
                                if (swfUrl) player.load(swfUrl);
                            }
                        });
                    }
                };
                document.head.appendChild(script);
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(script, null)
    }

    private fun openSwfPlayer(swfUrl: String) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Flash Player</title>
                <style>
                    body { margin: 0; background: #000; display: flex; justify-content: center; align-items: center; height: 100vh; }
                    #player { width: 100%; height: 100%; }
                </style>
                <script src="https://unpkg.com/@ruffle-rs/ruffle"></script>
            </head>
            <body>
                <div id="player"></div>
                <script>
                    window.RufflePlayer = window.Ruffle;
                    var player = window.RufflePlayer.newest().createPlayer();
                    player.style.width = '100%';
                    player.style.height = '100%';
                    document.getElementById('player').appendChild(player);
                    player.load('$swfUrl');
                </script>
            </body>
            </html>
        """.trimIndent()
        binding.webView.loadDataWithBaseURL(swfUrl, html, "text/html", "utf-8", null)
    }

    private fun handleVirtualMouse(event: MotionEvent): Boolean {
        // 虚拟鼠标触摸处理逻辑
        return true
    }

    private fun setupToolbar() {
        binding.etAddress.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.btnGo.setOnClickListener {
                    var url = s.toString().trim()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://www.google.com/search?q=$url"
                    }
                    binding.webView.loadUrl(url)
                    binding.etAddress.clearFocus()
                }
            }
        })

        binding.etAddress.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                binding.btnGo.performClick()
                true
            } else false
        }
    }

    private fun setupBottomBar() {
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack()
        }

        binding.btnForward.setOnClickListener {
            if (binding.webView.canGoForward()) binding.webView.goForward()
        }

        binding.btnRefresh.setOnClickListener {
            binding.webView.reload()
        }

        binding.btnMouse.setOnClickListener {
            virtualMouseEnabled = !virtualMouseEnabled
            Toast.makeText(this, 
                if (virtualMouseEnabled) "虚拟鼠标已开启" else "虚拟鼠标已关闭", 
                Toast.LENGTH_SHORT).show()
            binding.btnMouse.alpha = if (virtualMouseEnabled) 1.0f else 0.5f
        }

        binding.btnKeyboard.setOnClickListener {
            virtualKeyboardEnabled = !virtualKeyboardEnabled
            Toast.makeText(this, 
                if (virtualKeyboardEnabled) "虚拟键盘已开启" else "虚拟键盘已关闭", 
                Toast.LENGTH_SHORT).show()
            binding.btnKeyboard.alpha = if (virtualKeyboardEnabled) 1.0f else 0.5f
        }

        binding.btnFullscreen.setOnClickListener {
            toggleFullscreen()
        }

        binding.btnHome.setOnClickListener {
            binding.webView.loadUrl(HOME_URL)
            binding.etAddress.setText(HOME_URL)
        }
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            supportActionBar?.hide()
            binding.toolbarLayout.visibility = View.GONE
            binding.bottomBar.visibility = View.GONE
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        } else {
            supportActionBar?.show()
            binding.toolbarLayout.visibility = View.VISIBLE
            binding.bottomBar.visibility = View.VISIBLE
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.webView.canGoBack()) {
            binding.webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
