package com.tw.clipshare;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SettingsActivity extends AppCompatActivity {

    private Switch secureSwitch;
    private Intent intent;
    private AtomicInteger id;
    private LinearLayout trustList;

    private void addRowToTrusList(boolean addToList, String name) {
        try {
            LayoutInflater layoutInflater = getLayoutInflater();
            View trustServer = layoutInflater.inflate(R.layout.trusted_server, null, false);
            ImageButton delBtn = trustServer.findViewById(R.id.delBtn);
            TextView cnTxt = trustServer.findViewById(R.id.cnTxt);
            EditText cnEdit = trustServer.findViewById(R.id.cnEdit);
            trustServer.setId(id.getAndIncrement());
            List<String> servers = Settings.INSTANCE.getTrustedList();
            if (name != null) cnTxt.setText(name);
            if (addToList) servers.add(cnTxt.getText().toString());
            trustList.addView(trustServer);
            delBtn.setOnClickListener(view1 -> {
                if (servers.remove(cnTxt.getText().toString()))
                    trustList.removeView(trustServer);
            });
            cnTxt.setOnClickListener(view1 -> {
                cnEdit.setText(cnTxt.getText());
                cnTxt.setVisibility(View.GONE);
                cnEdit.setVisibility(View.VISIBLE);
                cnEdit.requestFocus();
            });
            cnEdit.setOnFocusChangeListener((view1, hasFocus) -> {
                if (!hasFocus) {
                    CharSequence oldText = cnTxt.getText();
                    CharSequence newText = cnEdit.getText();
                    cnTxt.setText(newText);
                    cnEdit.setVisibility(View.GONE);
                    cnTxt.setVisibility(View.VISIBLE);
                    if (servers.remove(oldText.toString()))
                        servers.add(newText.toString());
                }
            });
        } catch (Exception ex) {
            Log.d("AddErr", ex.getMessage());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        this.intent = getIntent();
        this.id = new AtomicInteger();
        this.trustList = findViewById(R.id.trustedList);
        ImageButton addBtn = findViewById(R.id.addServerBtn);
        this.secureSwitch = findViewById(R.id.secureSwitch);
        this.secureSwitch.setOnClickListener(view -> Settings.INSTANCE.setSecure(secureSwitch.isChecked()));
        this.secureSwitch.setChecked(Settings.INSTANCE.getSecure());

        List<String> servers = Settings.INSTANCE.getTrustedList();

        for (String server : servers) {
            addRowToTrusList(false, server);
        }

        addBtn.setOnClickListener(view -> addRowToTrusList(true, null));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (this.intent != null) {
            this.setResult(Activity.RESULT_OK, intent);
        }
        this.finish();
    }
}
