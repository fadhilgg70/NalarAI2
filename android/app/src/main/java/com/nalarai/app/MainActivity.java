package com.nalarai.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 4101;
    private static final int CAMERA_PERMISSION_REQUEST = 4102;

    private WebView webView;
    private LinearLayout offlinePanel;
    private ValueCallback<Uri[]> filePathCallback;
    private PermissionRequest pendingPermissionRequest;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterImmersiveMode();
        buildLayout();
        configureWebView();
        registerNetworkListener();

        if (savedInstanceState == null) {
            webView.loadUrl(BuildConfig.WEB_APP_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
        updateConnectivityUi();
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        offlinePanel = new LinearLayout(this);
        offlinePanel.setOrientation(LinearLayout.VERTICAL);
        offlinePanel.setPadding(36, 28, 36, 28);
        offlinePanel.setBackgroundColor(0xEE111827);
        offlinePanel.setVisibility(View.GONE);

        TextView message = new TextView(this);
        message.setText("Tidak ada koneksi internet. Periksa jaringan lalu coba lagi.");
        message.setTextColor(0xFFFFFFFF);
        message.setTextSize(15);
        offlinePanel.addView(message);

        Button retry = new Button(this);
        retry.setText("Coba lagi");
        retry.setOnClickListener(v -> {
            updateConnectivityUi();
            if (isOnline()) webView.reload();
        });
        offlinePanel.addView(retry);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        panelParams.gravity = android.view.Gravity.CENTER;
        panelParams.setMargins(24, 24, 24, 24);
        root.addView(offlinePanel, panelParams);
        setContentView(root);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    // Keep web navigation inside the app.
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                updateConnectivityUi();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame() && !isOnline()) {
                    offlinePanel.setVisibility(View.VISIBLE);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                          FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Tidak dapat membuka pemilih file", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean wantsVideo = false;
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) wantsVideo = true;
                    }
                    if (!wantsVideo || Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                            || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        pendingPermissionRequest = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
                    }
                });
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                request.addRequestHeader("User-Agent", userAgent);
                request.setMimeType(mimeType);
                request.setTitle("Unduhan NalarAI");
                request.setDescription("Mengunduh file...");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType));
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                Toast.makeText(this, "Unduhan dimulai", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Unduhan gagal dimulai", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void registerNetworkListener() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    runOnUiThread(() -> {
                        offlinePanel.setVisibility(View.GONE);
                        webView.evaluateJavascript(
                                "window.dispatchEvent(new Event('online'));", null);
                    });
                }
                @Override public void onLost(Network network) {
                    runOnUiThread(() -> {
                        offlinePanel.setVisibility(View.VISIBLE);
                        webView.evaluateJavascript(
                                "window.dispatchEvent(new Event('offline'));", null);
                    });
                }
            };
            try {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            } catch (Exception ignored) { }
        } else {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    runOnUiThread(() -> offlinePanel.setVisibility(View.GONE));
                }
                @Override public void onLost(Network network) {
                    runOnUiThread(() -> offlinePanel.setVisibility(View.VISIBLE));
                }
            };
            try { connectivityManager.registerNetworkCallback(request, networkCallback); }
            catch (Exception ignored) { }
        }
    }

    private boolean isOnline() {
        if (connectivityManager == null) return false;
        android.net.Network active = connectivityManager.getActiveNetwork();
        if (active == null) return false;
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(active);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void updateConnectivityUi() {
        if (offlinePanel != null) offlinePanel.setVisibility(isOnline() ? View.GONE : View.VISIBLE);
    }

    private void enterImmersiveMode() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST && pendingPermissionRequest != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
            } else {
                pendingPermissionRequest.deny();
            }
            pendingPermissionRequest = null;
        }
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript(
            "(function(){"
            + "var ids=['lens-modal','converter-modal','settings-modal'];"
            + "for(var i=0;i<ids.length;i++){var e=document.getElementById(ids[i]);"
            + "if(e && !e.classList.contains('hidden')){"
            + "if(ids[i]==='lens-modal' && typeof closeLensModal==='function') closeLensModal();"
            + "else if(ids[i]==='converter-modal' && typeof closeConverterModal==='function') closeConverterModal();"
            + "else if(ids[i]==='settings-modal' && typeof closeSettingsModal==='function') closeSettingsModal();"
            + "return 'closed';}}"
            + "var side=document.getElementById('history-sidebar');"
            + "if(side && !side.classList.contains('-translate-x-full')){"
            + "if(typeof toggleSidebar==='function') toggleSidebar(false);return 'closed';}"
            + "return 'none';})()", value -> {
                if ("\"none\"".equals(value)) {
                    if (webView.canGoBack()) webView.goBack();
                    else superOnBackPressed();
                }
            });
    }

    private void superOnBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); }
            catch (Exception ignored) { }
        }
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
