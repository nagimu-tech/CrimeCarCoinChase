package com.nagimutech.crimecarcoinchase;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

import javax.net.ssl.SSLSocketFactory;

final class SimpleWebSocketClient {
    interface Listener {
        void onOpen();
        void onText(String text);
        void onClosed(String reason);
        void onError(String message);
    }

    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final URI uri;
    private final Listener listener;
    private final SecureRandom random = new SecureRandom();
    private Socket socket;
    private InputStream input;
    private OutputStream output;
    private Thread worker;
    private volatile boolean running;

    SimpleWebSocketClient(String url, Listener listener) {
        this.uri = URI.create(url);
        this.listener = listener;
    }

    void connect() {
        running = true;
        worker = new Thread(this::runLoop, "crime-car-ws");
        worker.start();
    }

    synchronized void sendText(String text) {
        if (!running || output == null) {
            return;
        }
        try {
            byte[] payload = text.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x81);
            writeLength(frame, payload.length, true);
            byte[] mask = new byte[4];
            random.nextBytes(mask);
            frame.write(mask, 0, mask.length);
            for (int i = 0; i < payload.length; i++) {
                frame.write(payload[i] ^ mask[i % 4]);
            }
            output.write(frame.toByteArray());
            output.flush();
        } catch (IOException e) {
            listener.onError("Не удалось отправить сообщение: " + e.getMessage());
        }
    }

    void close() {
        running = false;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void runLoop() {
        try {
            openSocket();
            handshake();
            listener.onOpen();
            while (running) {
                String text = readFrame();
                if (text == null) {
                    break;
                }
                listener.onText(text);
            }
            listener.onClosed("Соединение закрыто");
        } catch (Exception e) {
            if (running) {
                listener.onError(e.getMessage() == null ? "Ошибка соединения" : e.getMessage());
            }
        } finally {
            close();
        }
    }

    private void openSocket() throws IOException {
        String scheme = uri.getScheme() == null ? "ws" : uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if ("wss".equals(scheme)) {
            socket = SSLSocketFactory.getDefault().createSocket(uri.getHost(), port > 0 ? port : 443);
        } else if ("ws".equals(scheme)) {
            socket = new Socket(uri.getHost(), port > 0 ? port : 80);
        } else {
            throw new IOException("Поддерживаются только ws:// и wss://");
        }
        input = socket.getInputStream();
        output = socket.getOutputStream();
    }

    private void handshake() throws Exception {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String key = Base64.encodeToString(nonce, Base64.NO_WRAP);
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (uri.getRawQuery() != null) {
            path += "?" + uri.getRawQuery();
        }
        String host = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        String request = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + host + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n\r\n";
        output.write(request.getBytes(StandardCharsets.US_ASCII));
        output.flush();

        String response = readHttpHeader();
        if (!response.startsWith("HTTP/1.1 101") && !response.startsWith("HTTP/1.0 101")) {
            throw new IOException("Сервер не принял WebSocket-подключение");
        }
        String accept = Base64.encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key + MAGIC).getBytes(StandardCharsets.US_ASCII)), Base64.NO_WRAP);
        if (!response.toLowerCase(Locale.ROOT).contains(("sec-websocket-accept: " + accept).toLowerCase(Locale.ROOT))) {
            throw new IOException("Некорректный WebSocket-ответ сервера");
        }
    }

    private String readHttpHeader() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int previous3 = -1;
        int previous2 = -1;
        int previous1 = -1;
        int current;
        while ((current = input.read()) != -1) {
            bytes.write(current);
            if (previous3 == '\r' && previous2 == '\n' && previous1 == '\r' && current == '\n') {
                break;
            }
            previous3 = previous2;
            previous2 = previous1;
            previous1 = current;
        }
        return bytes.toString("US-ASCII");
    }

    private String readFrame() throws IOException {
        int first = input.read();
        if (first == -1) {
            return null;
        }
        int second = input.read();
        if (second == -1) {
            return null;
        }
        int opcode = first & 0x0F;
        boolean masked = (second & 0x80) != 0;
        long length = second & 0x7F;
        if (length == 126) {
            length = ((long) readByte() << 8) | readByte();
        } else if (length == 127) {
            length = 0L;
            for (int i = 0; i < 8; i++) {
                length = (length << 8) | readByte();
            }
        }
        byte[] mask = null;
        if (masked) {
            mask = new byte[]{(byte) readByte(), (byte) readByte(), (byte) readByte(), (byte) readByte()};
        }
        if (length > 1024 * 1024) {
            throw new IOException("Слишком большое сообщение сервера");
        }
        byte[] payload = new byte[(int) length];
        int offset = 0;
        while (offset < payload.length) {
            int count = input.read(payload, offset, payload.length - offset);
            if (count == -1) {
                return null;
            }
            offset += count;
        }
        if (masked && mask != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        }
        if (opcode == 0x8) {
            running = false;
            return null;
        }
        if (opcode == 0x9) {
            sendPong(payload);
            return "";
        }
        if (opcode != 0x1) {
            return "";
        }
        return new String(payload, StandardCharsets.UTF_8);
    }

    private int readByte() throws IOException {
        int value = input.read();
        if (value == -1) {
            throw new IOException("Соединение оборвалось");
        }
        return value & 0xFF;
    }

    private synchronized void sendPong(byte[] payload) throws IOException {
        if (output == null) {
            return;
        }
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x8A);
        writeLength(frame, payload.length, true);
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        frame.write(mask, 0, mask.length);
        for (int i = 0; i < payload.length; i++) {
            frame.write(payload[i] ^ mask[i % 4]);
        }
        output.write(frame.toByteArray());
        output.flush();
    }

    private void writeLength(ByteArrayOutputStream frame, int length, boolean masked) {
        int maskBit = masked ? 0x80 : 0;
        if (length <= 125) {
            frame.write(maskBit | length);
        } else if (length <= 65535) {
            frame.write(maskBit | 126);
            frame.write((length >> 8) & 0xFF);
            frame.write(length & 0xFF);
        } else {
            frame.write(maskBit | 127);
            for (int i = 7; i >= 0; i--) {
                frame.write((length >> (8 * i)) & 0xFF);
            }
        }
    }
}
