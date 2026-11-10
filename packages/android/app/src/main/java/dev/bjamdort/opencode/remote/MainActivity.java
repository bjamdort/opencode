package dev.bjamdort.opencode.remote;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;
import androidx.webkit.WebViewAssetLoader;
import java.net.URI;

public final class MainActivity extends Activity {
    private static final int FILE_REQUEST = 1;
    private WebView web;
    private LinearLayout error;
    private ValueCallback<Uri[]> files;

    @SuppressLint("SetJavaScriptEnabled") // The bundled OpenCode UI needs JS; there is no native JS bridge.
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(24, 24, 24));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets keyboard = insets.getInsets(WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
                return WindowInsets.CONSUMED;
            });
        }

        // A virtual loopback origin is already accepted by OpenCode's remote API CORS policy.
        // WebView intercepts these requests; no localhost listener or OpenCode server is started.
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(BundledSite.HOME.getAuthority())
                .setHttpAllowed(true)
                .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(24, 24, 24));
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                URI asset = BundledSite.asset(request.getUrl().toString(), request.isForMainFrame());
                if (asset == null) return null;
                WebResourceResponse response = loader.shouldInterceptRequest(Uri.parse(asset.toString()));
                if (response != null && asset.getPath().endsWith(".wasm")) response.setMimeType("application/wasm");
                return response;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                if ("opencode-notifications:settings".equals(request.getUrl().toString())
                        && BundledSite.asset(view.getUrl(), false) != null) {
                    PushSettings.show(MainActivity.this);
                    return true;
                }
                if (BundledSite.asset(request.getUrl().toString(), false) != null) return false;
                String scheme = request.getUrl().getScheme();
                if ("http".equals(scheme) || "https".equals(scheme) || "mailto".equals(scheme)) {
                    openExternal(request.getUrl());
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError failure) {
                if (request.isForMainFrame()) error.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError failure) {
                handler.cancel();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (files != null) files.onReceiveValue(null);
                files = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*")
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try {
                    startActivityForResult(Intent.createChooser(intent, getString(R.string.choose_file)), FILE_REQUEST);
                } catch (ActivityNotFoundException unavailable) {
                    files.onReceiveValue(null);
                    files = null;
                }
                return true;
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        error = new LinearLayout(this);
        error.setOrientation(LinearLayout.VERTICAL);
        error.setGravity(Gravity.CENTER);
        error.setBackgroundColor(Color.rgb(24, 24, 24));
        TextView message = new TextView(this);
        message.setText(R.string.bundle_error);
        message.setTextColor(Color.WHITE);
        message.setGravity(Gravity.CENTER);
        error.addView(message);
        Button retry = new Button(this);
        retry.setText(R.string.reload);
        retry.setOnClickListener(view -> {
            error.setVisibility(View.GONE);
            web.loadUrl(BundledSite.HOME.toString());
        });
        error.addView(retry);
        error.setVisibility(View.GONE);
        root.addView(error, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::back);
        }
        if (notificationIntent(getIntent())) return;
        if (state != null && web.restoreState(state) != null) return;
        web.loadUrl(BundledSite.HOME.toString());
    }

    private boolean notificationIntent(Intent intent) {
        String target = PushRoute.href(intent.getStringExtra("server_url"), intent.getStringExtra("session_id"));
        if (target == null) return false;
        intent.removeExtra("server_url");
        intent.removeExtra("session_id");
        web.loadUrl(target);
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        notificationIntent(intent);
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != PushSettings.PERMISSION_REQUEST) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            PushSettings.enable(this);
            return;
        }
        Toast.makeText(this, R.string.push_permission_denied, Toast.LENGTH_LONG).show();
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException unavailable) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    private void back() {
        if (web.canGoBack()) {
            web.goBack();
            return;
        }
        moveTaskToBack(true);
    }

    @SuppressLint("GestureBackNavigation") // API 33+ uses the OnBackInvokedCallback registered in onCreate.
    @Override
    public void onBackPressed() {
        back();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != FILE_REQUEST || files == null) return;
        files.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
        files = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        web.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (files != null) files.onReceiveValue(null);
        web.destroy();
        super.onDestroy();
    }
}
