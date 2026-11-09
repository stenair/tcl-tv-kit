package local.tvbuttons;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Button → action mappings. The built-in list is {@code R.array.mappings} (strings.xml). A file pushed to the TV,
 * {@code /sdcard/Android/data/local.tvbuttons/files/mappings.txt}, REPLACES it when present, so another TV with other
 * HDMI ports, buttons or cameras needs no rebuild. Same format as strings.xml, one {@code <keyCode>=<target>} per line;
 * '#' starts a comment. The service re-reads the file when it changes (checked on each button press).
 */
final class Mappings {
    private static final String TAG = "TvButtons";
    static final String FILE_NAME = "mappings.txt";

    private Mappings() {
    }

    static File file(Context context) {
        File dir = context.getExternalFilesDir(null);
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    /** The file's stamp (0 when absent), to notice a pushed or deleted file. */
    static long stamp(Context context) {
        File f = file(context);
        return f != null && f.isFile() ? f.lastModified() ^ f.length() : 0;
    }

    /** The mappings in force: the pushed file when present, else the built-in list. Keeps file order. */
    static Map<String, String> load(Context context) {
        File f = file(context);
        if (f != null && f.isFile()) {
            List<String> lines = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                for (String line; (line = r.readLine()) != null; ) lines.add(line);
                Map<String, String> m = parse(lines);
                Log.i(TAG, "mappings: " + m.size() + " from " + FILE_NAME);
                return m;
            } catch (Exception e) {
                Log.w(TAG, "could not read " + FILE_NAME + ", using the built-in mappings: " + e);
            }
        }
        List<String> builtIn = new ArrayList<>();
        Collections.addAll(builtIn, context.getResources().getStringArray(R.array.mappings));
        Map<String, String> m = parse(builtIn);
        Log.i(TAG, "mappings: " + m.size() + " built in");
        return m;
    }

    static boolean fromFile(Context context) {
        File f = file(context);
        return f != null && f.isFile();
    }

    private static Map<String, String> parse(List<String> lines) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String raw : lines) {
            int hash = raw.indexOf('#');
            String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
            int eq = line.indexOf('=');
            if (eq > 0 && eq < line.length() - 1) m.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return Collections.unmodifiableMap(m);
    }

    /** The recorder channels of a {@code camera:<n>} or {@code camera:<n>,<n>,...} target; null for other targets. */
    static int[] cameraChannels(String target) {
        if (target == null || !target.startsWith("camera:")) return null;
        String[] parts = target.substring(7).split(",");
        List<Integer> out = new ArrayList<>();
        for (String p : parts) {
            try {
                int n = Integer.parseInt(p.trim());
                if (n > 0 && !out.contains(n)) out.add(n);
            } catch (NumberFormatException ignored) {
            }
        }
        if (out.isEmpty()) return null;
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    /** Every recorder channel any button shows, in order, for the setup screen. */
    static List<Integer> allCameras(Map<String, String> mappings) {
        TreeSet<Integer> set = new TreeSet<>();
        for (String target : mappings.values()) {
            int[] ch = cameraChannels(target);
            if (ch != null) for (int n : ch) set.add(n);
        }
        return new ArrayList<>(set);
    }

    /** The first channel of every camera button, in mapping order: what "Keep cameras ready" keeps running. */
    static List<Integer> firstCameras(Map<String, String> mappings) {
        List<Integer> out = new ArrayList<>();
        for (String target : mappings.values()) {
            int[] ch = cameraChannels(target);
            if (ch != null && !out.contains(ch[0])) out.add(ch[0]);
        }
        return out;
    }

    /** The first button that shows channel n, by its spoken name, for hints ("yellow"); null when none does. */
    static String buttonForCamera(Map<String, String> mappings, int n) {
        for (Map.Entry<String, String> e : mappings.entrySet()) {
            int[] ch = cameraChannels(e.getValue());
            if (ch != null) for (int c : ch) if (c == n) return buttonName(e.getKey());
        }
        return null;
    }

    /** A key's name as people say it: KEYCODE_PROG_YELLOW → "yellow", 4141 → "button 4141". */
    static String buttonName(String key) {
        if (key.startsWith("KEYCODE_PROG_")) return key.substring(13).toLowerCase(Locale.ROOT);
        if (key.startsWith("KEYCODE_")) return key.substring(8).toLowerCase(Locale.ROOT).replace('_', ' ');
        return "button " + key;
    }
}
