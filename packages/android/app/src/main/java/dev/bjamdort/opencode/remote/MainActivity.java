package dev.bjamdort.opencode.remote;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;
import java.net.URI;

public final class MainActivity extends Activity {
    private static final String[] SERVERS = {"laptop", "desktop"};
    private static final int[] LABELS = {R.string.laptop, R.string.desktop};
    private static final int FILE_REQUEST = 1;
    private SharedPreferences preferences;
    private WebView web;
    private Button serverButton;
    private TextView error;
    private URI server;
    private ValueCallback<Uri[]> files;
    private final String[] pending = new String[SERVERS.length];
    private boolean clearHistory;
    private int selected;

    @SuppressLint("SetJavaScriptEnabled") // OpenCode is a web app; no native JavaScript bridge is exposed.
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("servers", MODE_PRIVATE);
        selected = preferences.getInt("selected", 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
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

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        serverButton = new Button(this);
        serverButton.setAllCaps(false);
        serverButton.setTextColor(Color.WHITE);
        serverButton.setOnClickListener(view -> showServers());
        toolbar.addView(serverButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button reload = new Button(this);
        reload.setText(R.string.reload);
        reload.setAllCaps(false);
        reload.setTextColor(Color.WHITE);
        reload.setOnClickListener(view -> {
            if (server == null) {
                editServers();
                return;
            }
            error.setVisibility(View.GONE);
            web.reload();
        });
        toolbar.addView(reload);
        root.addView(toolbar);

        FrameLayout content = new FrameLayout(this);
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
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                URI target;
                try {
                    target = ServerAddress.parse(request.getUrl().toString());
                } catch (IllegalArgumentException invalid) {
                    // Do not hand arbitrary intent:, file:, or javascript: URLs to Android.
                    return true;
                }
                if (server != null && ServerAddress.sameOrigin(server, target)) return false;
                openBrowser(request.getUrl());
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError failure) {
                if (request.isForMainFrame()) showError(R.string.connection_error);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError failure) {
                handler.cancel();
                showError(R.string.certificate_error);
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
                LinearLayout fields = fields();
                EditText username = input(fields, R.string.username, "opencode", false);
                EditText password = input(fields, R.string.password, "", true);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(getString(R.string.sign_in, host))
                        .setView(scroll(fields))
                        .setPositiveButton(R.string.sign_in_action, (dialog, which) ->
                                handler.proceed(username.getText().toString(), password.getText().toString()))
                        .setNegativeButton(R.string.cancel, (dialog, which) -> handler.cancel())
                        .setOnCancelListener(dialog -> handler.cancel())
                        .show();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (clearHistory && server != null && url.startsWith(ServerAddress.home(server))) {
                    view.clearHistory();
                    clearHistory = false;
                }
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
        content.addView(web, new FrameLayout.LayoutParams(-1, -1));
        error = new TextView(this);
        error.setTextColor(Color.WHITE);
        error.setBackgroundColor(Color.rgb(24, 24, 24));
        error.setGravity(Gravity.CENTER);
        error.setPadding(dp(24), dp(24), dp(24), dp(24));
        error.setVisibility(View.GONE);
        content.addView(error, new FrameLayout.LayoutParams(-1, -1));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::back);
        }
        serverButton.setText(R.string.servers);
        String address = preferences.getString(SERVERS[selected], "");
        if (address.isEmpty()) {
            editServers();
            return;
        }
        server = ServerAddress.parse(address);
        serverButton.setText(LABELS[selected]);
        if (state != null && web.restoreState(state) != null) return;
        connect(selected, address);
    }

    private void connect(int index, String address) {
        server = ServerAddress.parse(address);
        selected = index;
        preferences.edit().putInt("selected", index).apply();
        serverButton.setText(LABELS[index]);
        error.setVisibility(View.GONE);
        // Clear after the new page commits so Back cannot return to the other machine.
        web.stopLoading();
        clearHistory = true;
        pending[index] = null;
        web.loadUrl(server.toString());
    }

    private void showServers() {
        PopupMenu menu = new PopupMenu(this, serverButton);
        for (int index = 0; index < SERVERS.length; index++) {
            final int slot = index;
            String address = pending[slot] != null ? pending[slot] : preferences.getString(SERVERS[slot], "");
            menu.getMenu().add(LABELS[slot]).setOnMenuItemClickListener(item -> {
                if (address.isEmpty()) {
                    editServers();
                    return true;
                }
                connect(slot, address);
                return true;
            });
        }
        menu.getMenu().add(R.string.edit_servers).setOnMenuItemClickListener(item -> {
            editServers();
            return true;
        });
        menu.getMenu().add(R.string.open_browser).setOnMenuItemClickListener(item -> {
            // Pairing links belong to this WebView; never send its one-time code to another app.
            if (server != null) openBrowser(Uri.parse(ServerAddress.home(server)));
            return true;
        });
        menu.show();
    }

    private void editServers() {
        LinearLayout fields = fields();
        TextView help = new TextView(this);
        help.setText(R.string.setup_help);
        fields.addView(help);
        EditText[] inputs = new EditText[SERVERS.length];
        for (int index = 0; index < SERVERS.length; index++) {
            inputs[index] = input(fields, LABELS[index], pending[index] != null ? pending[index] : preferences.getString(SERVERS[index], ""), false);
            inputs[index].setHint(R.string.url_hint);
            inputs[index].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            inputs[index].setTextDirection(View.TEXT_DIRECTION_LTR);
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.server_setup)
                .setView(scroll(fields))
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            URI[] addresses = new URI[SERVERS.length];
            for (int index = 0; index < SERVERS.length; index++) {
                String value = inputs[index].getText().toString().trim();
                if (value.isEmpty()) continue;
                try {
                    addresses[index] = ServerAddress.parse(value);
                } catch (IllegalArgumentException invalid) {
                    inputs[index].setError(getString(R.string.invalid_url));
                    return;
                }
            }
            if (addresses[0] == null && addresses[1] == null) {
                inputs[0].setError(getString(R.string.missing_url));
                return;
            }
            SharedPreferences.Editor edit = preferences.edit();
            for (int index = 0; index < SERVERS.length; index++) {
                edit.putString(SERVERS[index], addresses[index] == null ? "" : ServerAddress.home(addresses[index]));
                pending[index] = addresses[index] == null ? null : addresses[index].toString();
            }
            edit.apply();
            int next = addresses[selected] != null ? selected : 1 - selected;
            connect(next, addresses[next].toString());
            dialog.dismiss();
        }));
        dialog.show();
    }

    private LinearLayout fields() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(8), dp(24), dp(8));
        return layout;
    }

    private ScrollView scroll(LinearLayout fields) {
        ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);
        return scroll;
    }

    private EditText input(LinearLayout layout, int label, String value, boolean password) {
        TextView title = new TextView(this);
        title.setText(label);
        layout.addView(title);
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(value);
        field.setContentDescription(getString(label));
        field.setInputType(InputType.TYPE_CLASS_TEXT | (password
                ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_NORMAL));
        layout.addView(field, new LinearLayout.LayoutParams(-1, -2));
        return field;
    }

    private void showError(int message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }

    private void openBrowser(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException unavailable) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void back() {
        if (!clearHistory && web.canGoBack()) {
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
