package dev.community.wordcraft;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Unofficial WordCraft Android wrapper.
 * The upstream Rust application is the *bundled* WebAssembly release, not a remote website.
 */
public final class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String PREFIX = "/wordcraft/";
    private static final String START_URL = "https://" + HOST + PREFIX + "index.html?webgl";
    private static final int FILE_CHOOSER = 6201;

    private WebView webView;
    private ValueCallback<Uri[]> pendingChooser;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(28, 40, 58));
        getWindow().setNavigationBarColor(Color.rgb(28, 40, 58));
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(230, 230, 230));
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setDefaultTextEncodingName("UTF-8");

        // Only our packaged HTML may invoke the download bridge; external links open elsewhere.
        webView.addJavascriptInterface(new AndroidDownloads(), "WordCraftAndroid");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost())) return null;
                String path = uri.getPath();
                if (path == null || !path.startsWith(PREFIX)) return notFound();
                String relative = path.substring(PREFIX.length());
                if (relative.isEmpty()) relative = "index.html";
                if (relative.contains("..") || relative.indexOf('\\') >= 0 || relative.startsWith("/")) {
                    return notFound();
                }
                try {
                    InputStream stream = getAssets().open("wordcraft/" + relative);
                    return new WebResourceResponse(mime(relative), encoding(relative), stream);
                } catch (IOException e) {
                    return notFound();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && HOST.equals(uri.getHost())) return false;
                if (request.isForMainFrame()) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception e) {
                        toast("Cannot open external link");
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url.startsWith("https://" + HOST + PREFIX)) installBlobDownloadInterceptor();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pendingChooser != null) pendingChooser.onReceiveValue(null);
                pendingChooser = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER);
                    return true;
                } catch (ActivityNotFoundException e) {
                    pendingChooser.onReceiveValue(null);
                    pendingChooser = null;
                    toast("No file picker installed");
                    return false;
                }
            }
        });
        webView.setDownloadListener((url, agent, disposition, type, size) -> {
            String filename = safeName(URLUtil.guessFileName(url, disposition, type));
            if (url.startsWith("blob:")) {
                fetchBlob(url, filename);
            } else if (url.startsWith("data:")) {
                webView.evaluateJavascript("WordCraftAndroid.saveDataUrl(" + JSONObject.quote(filename)
                        + "," + JSONObject.quote(url) + ")", null);
            } else if (url.startsWith("https://")) {
                try {
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    req.setTitle(filename);
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                    ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                } catch (Exception e) {
                    toast("Download failed: " + e.getMessage());
                }
            }
        });
        setContentView(webView);
        webView.loadUrl(START_URL);
    }

    private void installBlobDownloadInterceptor() {
        // eframe's Save/Export uses JS-created <a download> links and blob URLs.
        // Android WebView cannot send those to DownloadManager directly.
        String js = "(function(){if(window.__wcAndroidInstalled)return;window.__wcAndroidInstalled=true;"
                + "document.addEventListener('click',function(e){"
                + "var a=e.target.closest&&e.target.closest('a[download]');"
                + "if(!a||!a.href||!a.href.startsWith('blob:'))return;"
                + "e.preventDefault();e.stopImmediatePropagation();"
                + "window.__wcAndroidSave(a.href,a.download||'document.docx');"
                + "},true);"
                + "window.__wcAndroidSave=async function(u,n){try{"
                + "const b=await(await fetch(u)).blob();const r=new FileReader();"
                + "r.onloadend=function(){WordCraftAndroid.saveDataUrl(n,r.result)};"
                + "r.readAsDataURL(b);"
                + "}catch(e){WordCraftAndroid.reportError(String(e))}};"
                + "})();";
        webView.evaluateJavascript(js, null);
    }

    private void fetchBlob(String url, String filename) {
        String js = "window.__wcAndroidSave(" + JSONObject.quote(url) + ","
                + JSONObject.quote(filename) + ")";
        webView.evaluateJavascript(js, null);
    }

    private static String safeName(String filename) {
        if (filename == null || filename.trim().isEmpty()) return "document.docx";
        String clean = filename.replaceAll("[^a-zA-Z0-9._() -]", "_");
        return clean.length() > 140 ? clean.substring(0, 140) : clean;
    }

    private static WebResourceResponse notFound() {
        WebResourceResponse response = new WebResourceResponse("text/plain", "UTF-8",
                new ByteArrayInputStream("Not found".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        response.setStatusCodeAndReasonPhrase(404, "Not Found");
        return response;
    }

    private static String mime(String filename) {
        String low = filename.toLowerCase(Locale.ROOT);
        if (low.endsWith(".wasm")) return "application/wasm";
        if (low.endsWith(".js") || low.endsWith(".mjs")) return "text/javascript";
        if (low.endsWith(".html")) return "text/html";
        if (low.endsWith(".css")) return "text/css";
        if (low.endsWith(".json") || low.endsWith(".webmanifest")) return "application/json";
        if (low.endsWith(".svg")) return "image/svg+xml";
        if (low.endsWith(".png")) return "image/png";
        if (low.endsWith(".ico")) return "image/x-icon";
        if (low.endsWith(".woff2")) return "font/woff2";
        if (low.endsWith(".woff")) return "font/woff";
        if (low.endsWith(".ttf")) return "font/ttf";
        if (low.endsWith(".txt") || low.endsWith(".md")) return "text/plain";
        return "application/octet-stream";
    }

    private static String encoding(String filename) {
        String type = mime(filename);
        return type.startsWith("text/") || type.equals("application/json") ? "UTF-8" : null;
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    private final class AndroidDownloads {
        @JavascriptInterface
        public void saveDataUrl(String filename, String dataUrl) {
            new Thread(() -> {
                try {
                    int comma = dataUrl.indexOf(',');
                    if (comma < 0 || !dataUrl.substring(0, comma).contains(";base64")) {
                        throw new IOException("Expected a base64-encoded document");
                    }
                    String meta = dataUrl.substring(5, comma);
                    String contentType = meta.split(";", 2)[0];
                    if (contentType.isEmpty()) contentType = "application/octet-stream";
                    byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName(filename));
                    values.put(MediaStore.MediaColumns.MIME_TYPE, contentType);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/WordCraft");
                    Uri outputUri = getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (outputUri == null) throw new IOException("Cannot create download");
                    try (OutputStream out = getContentResolver().openOutputStream(outputUri)) {
                        if (out == null) throw new IOException("Cannot open download");
                        out.write(bytes);
                    }
                    toast("Saved to Downloads/WordCraft/" + safeName(filename));
                } catch (Exception e) {
                    toast("Save failed: " + e.getMessage());
                }
            }, "wordcraft-save").start();
        }

        @JavascriptInterface
        public void reportError(String message) {
            toast("Export failed: " + message);
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == FILE_CHOOSER && pendingChooser != null) {
            pendingChooser.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            pendingChooser = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (pendingChooser != null) {
            pendingChooser.onReceiveValue(null);
            pendingChooser = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("WordCraftAndroid");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
