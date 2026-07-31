package com.example.myapp;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        WebView webView = findViewById(R.id.webView);
        WebSettings webSettings = webView.getSettings();

        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setAllowFileAccessFromFileURLs(false);
        
        if (true) {
            webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        } else {
            webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        }

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
    }
}