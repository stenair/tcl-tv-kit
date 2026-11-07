package local.tvbuttons;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * A minimal ADB client for the TV's own debugging port: connect, authenticate with this app's RSA key, and stream
 * one shell command's output line by line. The wire format is ADB's: a 24-byte little-endian header (command, two
 * arguments, payload length, payload byte sum, command ^ 0xffffffff) and the payload.
 *
 * The first connection offers the public key, and the TV shows its "Allow USB debugging?" prompt; with "Always allow"
 * ticked, later connections are signed silently. Only one stream is ever open on a connection.
 */
final class AdbClient implements Closeable {
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257;
    /** 0x01000000: the version that still sends payload checksums, which every adbd accepts. */
    private static final int VERSION = 0x01000000, MAX_PAYLOAD = 256 * 1024;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    /** ASN.1 DigestInfo prefix for SHA-1: adbd's token is treated as an already-computed SHA-1 digest. */
    private static final byte[] SHA1_DIGEST_INFO = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02,
            0x1a, 0x05, 0x00, 0x04, 0x14};

    interface LineSink {
        void line(String line);
    }

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;

    private AdbClient(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = socket.getOutputStream();
    }

    /**
     * Connects and authenticates. approvalWaitMs is how long to wait for the person to accept the on-screen prompt
     * when the key is new; a refused or unanswered prompt ends in an IOException.
     */
    static AdbClient connect(String host, int port, KeyPair keys, String keyName, int approvalWaitMs)
            throws IOException, GeneralSecurityException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 5000);
        AdbClient c = new AdbClient(s);
        try {
            c.handshake(keys, keyName, approvalWaitMs);
        } catch (IOException | GeneralSecurityException e) {
            c.close();
            throw e;
        }
        return c;
    }

    private void handshake(KeyPair keys, String keyName, int approvalWaitMs)
            throws IOException, GeneralSecurityException {
        socket.setSoTimeout(10_000);
        send(CNXN, VERSION, MAX_PAYLOAD, "host::\0".getBytes(StandardCharsets.UTF_8));
        boolean signed = false, offeredKey = false;
        while (true) {
            int[] h = new int[3];
            byte[] data = read(h);
            if (h[0] == CNXN) break;
            if (h[0] != AUTH || h[1] != AUTH_TOKEN) throw new IOException("unexpected reply during handshake");
            if (!signed) {
                send(AUTH, AUTH_SIGNATURE, 0, sign(keys, data));
                signed = true;
            } else if (!offeredKey) {
                // The key is not trusted yet: offer it, and wait while the TV asks the person.
                send(AUTH, AUTH_RSAPUBLICKEY, 0, publicKey((RSAPublicKey) keys.getPublic(), keyName));
                offeredKey = true;
                socket.setSoTimeout(approvalWaitMs);
            } else {
                throw new IOException("the TV did not accept this app's key");
            }
        }
        socket.setSoTimeout(0);
    }

    /** Runs a shell command and hands each output line to the sink until the command ends or the TV closes it. */
    void shellLines(String command, LineSink sink) throws IOException {
        final int local = 1;
        send(OPEN, local, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream pending = new ByteArrayOutputStream();
        while (true) {
            int[] h = new int[3];
            byte[] data = read(h);
            if (h[2] != local) continue;   // not our stream
            if (h[0] == WRTE) {
                send(OKAY, local, h[1], null);
                for (byte b : data) {
                    if (b == '\n') {
                        String line = pending.toString(StandardCharsets.UTF_8.name());
                        pending.reset();
                        sink.line(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
                    } else {
                        pending.write(b);
                    }
                }
            } else if (h[0] == CLSE) {
                send(CLSE, local, h[1], null);
                return;
            }
            // OKAY: the stream opened, or acknowledged nothing we sent; nothing to do.
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private void send(int command, int arg0, int arg1, byte[] data) throws IOException {
        int length = data == null ? 0 : data.length;
        int sum = 0;
        for (int i = 0; i < length; i++) sum += data[i] & 0xff;
        ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(command).putInt(arg0).putInt(arg1).putInt(length).putInt(sum).putInt(~command);
        synchronized (out) {
            out.write(header.array());
            if (length > 0) out.write(data);
            out.flush();
        }
    }

    /** Reads one message; h gets {command, arg0, arg1}; returns the payload. */
    private byte[] read(int[] h) throws IOException {
        byte[] raw = new byte[24];
        in.readFully(raw);
        ByteBuffer header = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        h[0] = header.getInt();
        h[1] = header.getInt();
        h[2] = header.getInt();
        int length = header.getInt();
        header.getInt();   // checksum: TCP already guards the bytes
        if (header.getInt() != ~h[0]) throw new IOException("bad ADB header");
        if (length < 0 || length > 1024 * 1024) throw new IOException("bad ADB payload length");
        byte[] data = new byte[length];
        in.readFully(data);
        return data;
    }

    /** PKCS#1 v1.5 signature of adbd's 20-byte token, padded by hand so it does not depend on the provider. */
    private static byte[] sign(KeyPair keys, byte[] token) throws GeneralSecurityException {
        int k = ((RSAPublicKey) keys.getPublic()).getModulus().bitLength() / 8;
        int payload = SHA1_DIGEST_INFO.length + token.length;
        byte[] block = new byte[k];
        block[0] = 0x00;
        block[1] = 0x01;
        for (int i = 2; i < k - payload - 1; i++) block[i] = (byte) 0xff;
        block[k - payload - 1] = 0x00;
        System.arraycopy(SHA1_DIGEST_INFO, 0, block, k - payload, SHA1_DIGEST_INFO.length);
        System.arraycopy(token, 0, block, k - token.length, token.length);
        Cipher rsa = Cipher.getInstance("RSA/ECB/NoPadding");
        rsa.init(Cipher.ENCRYPT_MODE, keys.getPrivate());
        return rsa.doFinal(block);
    }

    /** The public key in adbd's own format (its RSAPublicKey struct, base64) followed by " name" and a NUL. */
    private static byte[] publicKey(RSAPublicKey key, String name) {
        BigInteger n = key.getModulus();
        int bytes = n.bitLength() / 8;
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = r32.subtract(n.mod(r32).modInverse(r32));
        BigInteger rr = BigInteger.ONE.shiftLeft(2 * n.bitLength()).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + bytes + bytes + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(bytes / 4).putInt(n0inv.intValue());
        b.put(littleEndian(n, bytes)).put(littleEndian(rr, bytes));
        b.putInt(key.getPublicExponent().intValue());
        String s = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " " + name + "\0";
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] littleEndian(BigInteger v, int length) {
        byte[] be = v.toByteArray();
        byte[] le = new byte[length];
        for (int i = 0; i < length && i < be.length; i++) le[i] = be[be.length - 1 - i];
        return le;
    }

    /** This app's key pair, made once and kept in its private files. */
    static KeyPair keys(Context ctx) throws GeneralSecurityException, IOException {
        File priv = new File(ctx.getFilesDir(), "adbkey"), pub = new File(ctx.getFilesDir(), "adbkey.pub");
        KeyFactory rsa = KeyFactory.getInstance("RSA");
        if (priv.isFile() && pub.isFile()) {
            return new KeyPair(rsa.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(pub.toPath()))),
                    rsa.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(priv.toPath()))));
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        try (FileOutputStream o = new FileOutputStream(pub)) {
            o.write(pair.getPublic().getEncoded());
        }
        try (FileOutputStream o = new FileOutputStream(priv)) {
            o.write(pair.getPrivate().getEncoded());
        }
        return pair;
    }
}
