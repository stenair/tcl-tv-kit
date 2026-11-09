package local.tvbuttons;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A still JPEG of one camera from the recorder, shown while the live stream connects (Dahua:
 * {@code http://<recorder>/cgi-bin/snapshot.cgi?channel=<n>}). Dahua answers with an HTTP Digest login, which
 * Android's HttpURLConnection doesn't do, so it's done here: one request for the challenge, one with the answer, on
 * the same kept-alive connection. Nothing here is logged; the caller logs only timings and status codes.
 */
final class Snapshot {
    private static final int TIMEOUT_MS = 2500;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final Pattern PARAM = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]*))");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** HTTP status of the last failure, for the caller's log (0 = network error). */
    static final class Failure extends IOException {
        final int status;

        Failure(int status) {
            super("HTTP " + status);
            this.status = status;
        }
    }

    private Snapshot() {
    }

    /** Where the time went on the last fetch (no address or login in it), for the caller's log. */
    static volatile String lastTiming = "";

    /** The still, decoded no wider than about {@code targetWidth} pixels. */
    static Bitmap fetch(String host, int channel, String user, String pass, int targetWidth) throws IOException {
        long t0 = SystemClock.uptimeMillis();
        String path = "/cgi-bin/snapshot.cgi?channel=" + channel;
        URL url = new URL("http://" + host + path);
        HttpURLConnection c = open(url, null);
        int code = c.getResponseCode();
        long tChallenge = SystemClock.uptimeMillis() - t0;
        if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            String challenge = c.getHeaderField("WWW-Authenticate");
            drain(c);
            if (challenge == null || !challenge.regionMatches(true, 0, "Digest", 0, 6)) throw new Failure(code);
            c = open(url, digest(challenge, user, pass, "GET", path));
            code = c.getResponseCode();
        }
        long tAnswer = SystemClock.uptimeMillis() - t0;
        if (code != HttpURLConnection.HTTP_OK) {
            drain(c);
            throw new Failure(code);
        }
        byte[] jpeg;
        try (InputStream in = c.getInputStream()) {
            jpeg = readAll(in);
        }
        long tBody = SystemClock.uptimeMillis() - t0;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, bounds);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        while (bounds.outWidth / (opts.inSampleSize * 2) >= targetWidth) opts.inSampleSize *= 2;
        Bitmap b = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, opts);
        if (b == null) throw new Failure(-1);
        lastTiming = "challenge " + tChallenge + " ms, recorder answered " + tAnswer + " ms, body " + tBody + " ms ("
                + jpeg.length / 1024 + " KB, " + bounds.outWidth + "x" + bounds.outHeight + "), decoded "
                + (SystemClock.uptimeMillis() - t0) + " ms";
        return b;
    }

    private static HttpURLConnection open(URL url, String authorization) throws IOException {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setUseCaches(false);
        c.setInstanceFollowRedirects(false);
        if (authorization != null) c.setRequestProperty("Authorization", authorization);
        return c;
    }

    /** RFC 7616 Digest, MD5, with qop=auth when offered (Dahua offers it). */
    static String digest(String challenge, String user, String pass, String method, String uri) {
        Map<String, String> p = new HashMap<>();
        Matcher m = PARAM.matcher(challenge.substring(6));
        while (m.find()) p.put(m.group(1).toLowerCase(Locale.ROOT), m.group(2) != null ? m.group(2) : m.group(3));
        String realm = p.getOrDefault("realm", ""), nonce = p.getOrDefault("nonce", "");
        String qop = p.get("qop"), opaque = p.get("opaque");
        String ha1 = md5(user + ":" + realm + ":" + pass), ha2 = md5(method + ":" + uri);
        StringBuilder h = new StringBuilder("Digest username=\"").append(user).append("\", realm=\"").append(realm)
                .append("\", nonce=\"").append(nonce).append("\", uri=\"").append(uri).append("\", algorithm=MD5");
        if (qop != null && qop.contains("auth")) {
            byte[] r = new byte[8];
            RANDOM.nextBytes(r);
            String cnonce = hex(r), nc = "00000001";
            h.append(", qop=auth, nc=").append(nc).append(", cnonce=\"").append(cnonce).append("\", response=\"")
                    .append(md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2)).append('"');
        } else {
            h.append(", response=\"").append(md5(ha1 + ":" + nonce + ":" + ha2)).append('"');
        }
        if (opaque != null) h.append(", opaque=\"").append(opaque).append('"');
        return h.toString();
    }

    private static void drain(HttpURLConnection c) {
        try {
            InputStream in = c.getErrorStream();
            if (in == null) in = c.getInputStream();
            try (InputStream s = in) {
                byte[] buf = new byte[4096];
                while (s.read(buf) > 0) {
                    // read it all, so the connection can be reused for the second request
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 1024);
        byte[] buf = new byte[32 * 1024];
        for (int n; (n = in.read(buf)) > 0; ) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) throw new Failure(-2);
        }
        return out.toByteArray();
    }

    private static String md5(String s) {
        try {
            return hex(MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format(Locale.ROOT, "%02x", x));
        return sb.toString();
    }
}
