package com.flashbrowser

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.flashbrowser.databinding.ActivityMainBinding
import java.io.ByteArrayInputStream

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

        binding.webView.addJavascriptInterface(JsBridge(), "Android")

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                
                // 拦截跳转到Flash提示页的请求
                if (url.contains("oldseer.htm") || url.contains("flashhelp")) {
                    Toast.makeText(this@MainActivity, "已拦截Flash提示页，尝试直接加载游戏", Toast.LENGTH_SHORT).show()
                    // 返回上一页，回到游戏页面
                    if (view?.canGoBack() == true) {
                        view.goBack()
                    }
                    return true
                }
                
                if (url.endsWith(".swf") || url.contains(".swf?")) {
                    openSwfPlayer(url)
                    return true
                }
                
                view?.loadUrl(url)
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                // 拦截HTML响应，在里面注入我们的JS
                val url = request?.url?.toString() ?: return null
                
                // 只拦截HTML页面，不拦截其他资源
                if (!url.endsWith(".html") && !url.endsWith(".htm") && !url.contains(".htm?") && !url.contains(".html?") && url.contains("4399.com")) {
                    // 让它正常加载
                    return null
                }
                
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.etAddress.setText(url)
                // 在页面开始加载时就注入Flash伪装
                injectFlashSpoofEarly()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                injectFlashSpoof()
                injectRuffle()
                extractSwfAndNotify()
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

    private fun injectFlashSpoofEarly() {
        // 在页面开始加载时就注入Flash伪装，用evaluateJavascript可能太早
        // 我们用一个延时的方式，确保页面DOM创建后立即执行
        val script = """
            (function() {
                // 提前设置Flash伪装，在页面脚本执行前就生效
                var fakeFlash = {
                    name: 'Shockwave Flash',
                    description: 'Shockwave Flash 33.0 r0',
                    version: '33.0.0.387',
                    filename: 'NPSWF32_33_0_0_387.dll',
                    length: 1
                };
                
                try {
                    Object.defineProperty(navigator, 'plugins', {
                        get: function() {
                            var arr = [fakeFlash];
                            arr.namedItem = function(name) { 
                                return name === 'Shockwave Flash' ? fakeFlash : null; 
                            };
                            arr.refresh = function() {};
                            return arr;
                        },
                        configurable: true
                    });
                } catch(e) {}
                
                try {
                    Object.defineProperty(navigator, 'mimeTypes', {
                        get: function() {
                            var arr = [{ type: 'application/x-shockwave-flash', suffixes: 'swf', description: 'Shockwave Flash', enabledPlugin: fakeFlash }];
                            arr.namedItem = function(name) { 
                                return name === 'application/x-shockwave-flash' ? arr[0] : null; 
                            };
                            return arr;
                        },
                        configurable: true
                    });
                } catch(e) {}
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(script, null)
    }

    private fun injectFlashSpoof() {
        val script = """
            (function() {
                var fakeFlash = {
                    name: 'Shockwave Flash',
                    description: 'Shockwave Flash 33.0 r0',
                    version: '33.0.0.387',
                    filename: 'NPSWF32_33_0_0_387.dll',
                    length: 1
                };
                
                try {
                    Object.defineProperty(navigator, 'plugins', {
                        get: function() {
                            var arr = [fakeFlash];
                            arr.namedItem = function(name) { 
                                return name === 'Shockwave Flash' ? fakeFlash : null; 
                            };
                            arr.refresh = function() {};
                            return arr;
                        }
                    });
                } catch(e) {}
                
                try {
                    Object.defineProperty(navigator, 'mimeTypes', {
                        get: function() {
                            var arr = [{ type: 'application/x-shockwave-flash', suffixes: 'swf', description: 'Shockwave Flash', enabledPlugin: fakeFlash }];
                            arr.namedItem = function(name) { 
                                return name === 'application/x-shockwave-flash' ? arr[0] : null; 
                            };
                            return arr;
                        }
                    });
                } catch(e) {}
                
                // 伪装ActiveXObject (IE风格检测)
                try {
                    window.ActiveXObject = function(progID) {
                        if (progID.indexOf('ShockwaveFlash') > -1) {
                            return {
                                GetVariable: function(varName) { return '33.0.0.387'; },
                                FlashVersion: function() { return '33,0,0,387'; }
                            };
                        }
                        throw new Error('ActiveXObject not available');
                    };
                } catch(e) {}
                
                // 伪装window.FlashObject
                try {
                    window.FlashObject = function() {
                        return {
                            SetVariable: function() {},
                            GetVariable: function() { return '33.0.0.387'; }
                        };
                    };
                } catch(e) {}
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(script, null)
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
                        
                        // 替换所有object和embed标签
                        var objs = document.querySelectorAll('object, embed');
                        objs.forEach(function(obj) {
                            var swfUrl = obj.data || obj.src;
                            if (swfUrl && (swfUrl.indexOf('.swf') > -1)) {
                                var container = document.createElement('div');
                                container.style.width = '100%';
                                container.style.height = '600px';
                                container.style.background = '#000';
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

    private fun extractSwfAndNotify() {
        val script = """
            (function() {
                var html = document.documentElement.outerHTML;
                var swfRegex = /(https?:\/\/[^"'\s<>]+\.swf[^"'\s<>]*)/gi;
                var matches = html.match(swfRegex);
                if (matches && matches.length > 0) {
                    Android.onSwfFound(matches[0]);
                }
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(script, null)
    }

    private fun openSwfPlayer(swfUrl: String) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                <title>Flash Player</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { background: #000; width: 100vw; height: 100vh; overflow: hidden; }
                    #player { width: 100vw; height: 100vh; }
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
        toggleFullscreen()
    }

    private fun handleVirtualMouse(event: MotionEvent): Boolean {
        return true
    }

    private fun setupToolbar() {
        binding.btnGo.setOnClickListener {
            var input = binding.etAddress.text.toString().trim()
            if (input.isEmpty()) return@setOnClickListener
            
            var url = input
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                if (url.contains(".") && !url.contains(" ")) {
                    url = "https://$url"
                } else {
                    url = "https://www.baidu.com/s?wd=$url"
                }
            }
            binding.webView.loadUrl(url)
            binding.etAddress.clearFocus()
        }

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
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            supportActionBar?.show()
            binding.toolbarLayout.visibility = View.VISIBLE
            binding.bottomBar.visibility = View.VISIBLE
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    inner class JsBridge {
        @JavascriptInterface
        fun onSwfFound(swfUrl: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "找到SWF游戏，正在加载...", Toast.LENGTH_SHORT).show()
                openSwfPlayer(swfUrl)
            }
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
