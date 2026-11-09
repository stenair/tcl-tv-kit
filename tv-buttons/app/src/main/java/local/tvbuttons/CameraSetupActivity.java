package local.tvbuttons;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The camera login, typed once on the TV with the remote, plus a name for every camera a button shows (one row per
 * recorder channel found in the mappings, so more cameras need no change here). The login stays in this app's
 * private storage (other apps and the debugging shell can't read it) and is never logged. Each row's Try button
 * previews that camera.
 */
public final class CameraSetupActivity extends Activity {
    private EditText host, user, pass;
    private TextView state;
    private final List<Integer> channels = new ArrayList<>();
    private final List<EditText> names = new ArrayList<>();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.camera_setup);
        host = findViewById(R.id.camera_host);
        user = findViewById(R.id.camera_user);
        pass = findViewById(R.id.camera_pass);
        state = findViewById(R.id.camera_state);

        SharedPreferences p = getSharedPreferences(CameraOverlay.PREFS, MODE_PRIVATE);
        host.setText(p.getString("host", ""));
        user.setText(p.getString("user", ""));
        pass.setText(p.getString("pass", ""));

        ButtonService s = ButtonService.running();
        Map<String, String> mappings = s != null ? s.mappings() : Mappings.load(this);
        LinearLayout rows = findViewById(R.id.camera_rows);
        LayoutInflater inflater = getLayoutInflater();
        for (int ch : Mappings.allCameras(mappings)) {
            View row = inflater.inflate(R.layout.camera_row, rows, false);
            String button = Mappings.buttonForCamera(mappings, ch);
            ((TextView) row.findViewById(R.id.camera_row_label)).setText(
                    getString(R.string.camera_name_label, ch, button != null ? button : "?"));
            EditText name = row.findViewById(R.id.camera_row_name);
            name.setText(p.getString("name" + ch, ""));
            name.setHint(getString(R.string.camera_name_hint, ch));
            Button tryIt = row.findViewById(R.id.camera_row_try);
            tryIt.setOnClickListener(v -> save(ch));
            channels.add(ch);
            names.add(name);
            rows.addView(row);
        }
        if (channels.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(R.string.camera_none_mapped);
            none.setTextColor(0xB3FFFFFF);
            rows.addView(none);
        }

        Switch ready = findViewById(R.id.camera_ready);
        ready.setChecked(p.getBoolean(CameraOverlay.PREF_READY, false));
        ready.setOnCheckedChangeListener((b, on) -> {
            getSharedPreferences(CameraOverlay.PREFS, MODE_PRIVATE).edit().putBoolean(CameraOverlay.PREF_READY, on).apply();
            ButtonService s2 = ButtonService.running();
            if (s2 != null) s2.applyReady();
            showState(getString(on ? R.string.camera_ready_on : R.string.camera_ready_off));
        });

        findViewById(R.id.camera_save).setOnClickListener(v -> save(0));
        ((TextView) findViewById(R.id.camera_source)).setText(Mappings.fromFile(this)
                ? R.string.camera_source_file : R.string.camera_source_builtin);
        (pass.getText().length() == 0 ? pass : findViewById(R.id.camera_save)).requestFocus();
        showState(null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ButtonService s = ButtonService.running();
        if (s != null) s.closeCamera(); // a preview never outlives this screen
    }

    /** Saves the form; with a camera number, also shows that camera's overlay as a preview. */
    private void save(int previewCamera) {
        String h = host.getText().toString().trim();
        if (h.isEmpty()) {
            showState(getString(R.string.camera_need_host));
            return;
        }
        SharedPreferences.Editor e = getSharedPreferences(CameraOverlay.PREFS, MODE_PRIVATE).edit()
                .putString("host", h)
                .putString("user", user.getText().toString().trim())
                .putString("pass", pass.getText().toString());
        for (int i = 0; i < channels.size(); i++) {
            e.putString("name" + channels.get(i), names.get(i).getText().toString().trim());
        }
        e.apply();
        ButtonService s = ButtonService.running();
        if (s == null) {
            showState(getString(R.string.camera_no_service));
            return;
        }
        if (previewCamera == 0) {
            showState(getString(R.string.camera_saved));
        } else {
            showState(getString(R.string.camera_previewing));
            s.showCamera(previewCamera);
        }
    }

    private void showState(String message) {
        if (message != null) {
            state.setText(message);
        } else if (ButtonService.running() == null) {
            state.setText(R.string.camera_no_service);
        } else {
            state.setText("");
        }
    }
}
