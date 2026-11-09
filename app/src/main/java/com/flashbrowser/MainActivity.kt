package com.flashbrowser

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.flashbrowser.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var virtualMouseEnabled = false
    private var virtualKeyboardEnabled = false
    private var isFullscreen = false

    companion object {
        private const val HOME_URL = "https://www.4399.com"
        private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            userAgentString = DESKTOP_UA
            databaseEnabled = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                // 所有链接都在当前WebView打开
                if (url.endsWith(".swf") || url.contains(".swf?")) {
                    openSwfPlayer(url)
                    return true
                }
                view?.loadUrl(url)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.etAddress.setText(url)
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
                        
                        // 查找所有Flash对象
                        var flashObjects = document.querySelectorAll('object, embed, [class*="flash"], [id*="flash"]');
                        flashObjects.forEach(function(obj) {
                            var swfUrl = obj.data || obj.src;
                            if (swfUrl && (swfUrl.endsWith('.swf') || swfUrl.indexOf('.swf?') > -1)) {
                                var container = document.createElement('div');
                                container.style.width = '100%';
                                container.style.height = '100%';
                                obj.parentNode?.replaceChild(container, obj);
                                
                                var player = window.RufflePlayer.newest().createPlayer();
                                player.style.width = '100%';
                                player.style.height = '100%';
                                container.appendChild(player);
                                
                                player.load(swfUrl);
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
        return true
    }

    private fun setupToolbar() {
        // 前往按钮
        binding.btnGo.setOnClickListener {
            var input = binding.etAddress.text.toString().trim()
            if (input.isEmpty()) return@setOnClickListener
            
            var url = input
            // 如果不是网址，用百度搜索
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                // 如果看起来像域名
                if (url.contains(".") && !url.contains(" ")) {
                    url = "https://$url"
                } else {
                    // 用百度搜索
                    url = "https://www.baidu.com/s?wd=$url"
                }
            }
            binding.webView.loadUrl(url)
            binding.etAddress.clearFocus()
        }

        // 回车键
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
