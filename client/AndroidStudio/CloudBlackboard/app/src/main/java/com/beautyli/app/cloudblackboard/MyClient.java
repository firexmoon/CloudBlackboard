package com.beautyli.app.cloudblackboard;

import android.util.Base64;

import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class MyClient {

    public static String mServer_IP;
    public static int mServer_Port;
    public static long mCurDBVersion = 0;

    public String mLastReason;

    private JsonParser mJsonParser = new JsonParser();
    private Socket mSocket = new Socket();
    private DataInputStream mInput;
    private DataOutputStream mOutput;
    private RC4 mRC4 = new RC4();

    private final String mRC4_BaseKey = "CloudBlackboard_rc4_base_key";

    public synchronized boolean connect() {
        try {
            mSocket = new Socket(mServer_IP, mServer_Port);
            mInput = new DataInputStream(new BufferedInputStream(mSocket.getInputStream()));
            mOutput = new DataOutputStream(mSocket.getOutputStream());
        } catch (IOException e) {
            return false;
        }

        return mSocket.isConnected();
    }

    public synchronized void close() {
        try {
            mSocket.close();
        } catch (IOException ignored) {
        }
    }

    public synchronized boolean logon(String user, String pin) {
        mLastReason = "null";
        try {
            if (!mSocket.isConnected()) {
                return false;
            }

            byte[] rand_buffer = new byte[8];
            if (mInput.read(rand_buffer) < rand_buffer.length) {
                return false;
            }

            //拼接密钥
            byte[] rc4_key = (mRC4_BaseKey + pin).getBytes("UTF-8");

            //加密随机数
            mRC4.setKey(rc4_key);
            byte[] enc_rand = mRC4.doEnc(rand_buffer);
            String challenge = Base64.encodeToString(enc_rand, Base64.DEFAULT).trim();

            JsonObject request = new JsonObject();
            request.addProperty("transaction", "logon");
            request.addProperty("user", user);
            request.addProperty("challenge", challenge);

            String request_string = request.toString();
            byte[] request_data = request_string.getBytes("UTF-8");

            if (!sendPack(request_data)) {
                return false;
            }

            JsonObject respond = doRequest(null);
            if (respond == null) {
                return false;
            }

            if (respond.has("reason")) {
                mLastReason = respond.get("reason").getAsString();
            }
            String result = respond.get("result").getAsString();
            if (0 != result.compareTo("success"))
                return false;

            byte[] session_key = Base64.decode(respond.get("session_key").getAsString(), Base64.DEFAULT);
            mRC4.setKey(session_key);
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    public synchronized boolean register(String user, String pin) {
        mLastReason = "null";
        try {
            if (!mSocket.isConnected()) {
                return false;
            }

            byte[] rand_buffer = new byte[8];
            if (mInput.read(rand_buffer) < rand_buffer.length) {
                return false;
            }

            //密钥
            byte[] rc4_key = mRC4_BaseKey.getBytes("UTF-8");

            //加密随机数
            mRC4.setKey(rc4_key);
            byte[] enc_rand = mRC4.doEnc(rand_buffer);
            String challenge = Base64.encodeToString(enc_rand, Base64.DEFAULT);

            //加密PIN
            String enc_pin = Base64.encodeToString(mRC4.doEnc(pin.getBytes("UTF-8")), Base64.DEFAULT);

            JsonObject request = new JsonObject();
            request.addProperty("transaction", "register");
            request.addProperty("user", user);
            request.addProperty("challenge", challenge);
            request.addProperty("pin", enc_pin);

            String request_string = request.toString();
            byte[] request_data = request_string.getBytes("UTF-8");
            if (!sendPack(request_data)) {
                return false;
            }

            JsonObject respond = doRequest(null);
            if (respond == null) {
                return false;
            }

            if (respond.has("reason")) {
                mLastReason = respond.get("reason").getAsString();
            }
            String result = respond.get("result").getAsString();
            return result.compareTo("success") == 0;
        }
        catch (IOException e) {
            return false;
        }
    }

    public synchronized JsonObject getAllNotes() {
        mLastReason = "null";
        if (!mSocket.isConnected()) {
            return null;
        }

        JsonObject request = new JsonObject();
        request.addProperty("transaction", "get_new_notes");
        request.addProperty("sheet", "default");
        request.addProperty("client_db_version", mCurDBVersion);

        JsonObject respond = doRequest(request);
        if (null == respond) {
            return null;
        }

        if (respond.has("reason")) {
            mLastReason = respond.get("reason").getAsString();
        }
        String result = respond.get("result").getAsString();
        if (0 == result.compareTo("success")) {
            mCurDBVersion = respond.get("ver").getAsLong();
            return respond;
        } else {
            return null;
        }
    }

    public synchronized JsonObject uploadNotes(String noteID, String noteText) {
        mLastReason = "null";
        if (!mSocket.isConnected()) {
            return null;
        }

        JsonObject request = new JsonObject();
        request.addProperty("transaction", "put_note");
        request.addProperty("sheet", "default");
        request.addProperty("note_id", noteID);
        request.addProperty("time", System.currentTimeMillis() / 1000);
        request.addProperty("text", noteText);

        JsonObject respond = doRequest(request);
        if (null == respond) {
            return null;
        }

        if (respond.has("reason")) {
            mLastReason = respond.get("reason").getAsString();
        }
        String result = respond.get("result").getAsString();
        if (0 == result.compareTo("success")) {
            return respond;
        } else {
            return null;
        }
    }

    public synchronized JsonObject deleteNotes(String noteID) {
        mLastReason = "null";
        if (!mSocket.isConnected()) {
            return null;
        }

        JsonObject request = new JsonObject();
        request.addProperty("transaction", "delete_note");
        request.addProperty("sheet", "default");
        request.addProperty("note_id", noteID);

        JsonObject respond = doRequest(request);
        if (null == respond) {
            return null;
        }

        if (respond.has("reason")) {
            mLastReason = respond.get("reason").getAsString();
        }
        String result = respond.get("result").getAsString();
        if (0 == result.compareTo("success")) {
            return respond;
        } else {
            return null;
        }
    }

    private synchronized JsonObject doRequest(JsonObject request) {
        try {
            if (!mSocket.isConnected()) {
                return null;
            }

            if (request != null) {
                String request_string = request.toString();
                byte[] request_data = request_string.getBytes("UTF-8");
                byte[] send_data = packSendData(request_data);
                sendPack(send_data);
            }

            byte[] receive_data = receivePack();
            if (null == receive_data) {
                return null;
            }

            byte[] respond_data = unpackReceiveData(receive_data);
            if(respond_data == null) {
                return null;
            }
            String respond_string = new String(respond_data, "UTF-8");
            return mJsonParser.parse(respond_string).getAsJsonObject();

        } catch (UnsupportedEncodingException | JsonIOException | JsonSyntaxException e) {
            return null;
        }
    }

    private byte[] receivePack() {
        try {
            if (!mSocket.isConnected()) {
                return null;
            }

            byte[] pack_size_buffer = new byte[4];
            if (mInput.read(pack_size_buffer) < pack_size_buffer.length) {
                return null;
            }

            int pack_size = (pack_size_buffer[0] & 0xff) |
                    (pack_size_buffer[1] & 0xff) << 8 |
                    (pack_size_buffer[2] & 0xff) << 16 |
                    (pack_size_buffer[3] & 0xff) << 24;
            if (pack_size < 16 || pack_size > 1024 * 1024 * 32) {
                return null;
            }

            byte[] receive_data = new byte[pack_size];
            int receive_len = 0;
            while (receive_len < pack_size) {
                byte[] receive_buffer = new byte[pack_size - receive_len];
                int temp_len = mInput.read(receive_buffer);
                if (temp_len <= 0) {
                    return null;
                }
                System.arraycopy(receive_buffer, 0, receive_data, receive_len, temp_len);
                receive_len += temp_len;
            }
            return receive_data;

        } catch (IOException e) {
            return null;
        }
    }

    private boolean sendPack(byte[] data) {
        try {
            if (!mSocket.isConnected()) {
                return false;
            }

            int data_len = data.length;
            byte[] b_data_len = new byte[4];
            b_data_len[0] = (byte) (data_len & 0xff);
            b_data_len[1] = (byte) ((data_len >> 8) & 0xff);
            b_data_len[2] = (byte) ((data_len >> 16) & 0xff);
            b_data_len[3] = (byte) ((data_len >> 24) & 0xff);

            mOutput.write(b_data_len);
            mOutput.write(data);
            return true;

        } catch (IOException e) {
            return false;
        }
    }

    private byte[] unpackReceiveData(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int hash_size = digest.getDigestLength();
            //解密数据
            byte[] dec_data = mRC4.doEnc(data);
            if (dec_data.length > hash_size) {
                byte[] receive_hash = new byte[hash_size];
                byte[] receive_data = new byte[dec_data.length - hash_size];
                System.arraycopy(dec_data, 0, receive_hash, 0, hash_size);
                System.arraycopy(dec_data, hash_size, receive_data, 0, receive_data.length);
                //校验hash
                byte[] hash = digest.digest(receive_data);
                if (Arrays.equals(hash, receive_hash)) {
                    // 解压缩
                    return decompress(receive_data);
                }
            }
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        return null;
    }

    private byte[] packSendData(byte[] data) {
        try {
            // 压缩
            byte[] zip_data = compress(data);
            if(zip_data == null) {
                return null;
            }
            // Hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(zip_data);

            byte[] send_data = new byte[hash.length + zip_data.length];
            System.arraycopy(hash, 0, send_data, 0, hash.length);
            System.arraycopy(zip_data, 0, send_data, hash.length, zip_data.length);

            return mRC4.doEnc(send_data);

        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private byte[] compress(byte[] data) {
        byte[] output;
        Deflater compresser = new Deflater();
        compresser.reset();
        compresser.setInput(data);
        compresser.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        try {
            byte[] buf = new byte[2048];
            while (!compresser.finished()) {
                int i = compresser.deflate(buf);
                bos.write(buf, 0, i);
            }
            output = bos.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            try {
                bos.close();
            } catch (IOException ignored) {
            }
        }
        compresser.end();
        return output;
    }

    private byte[] decompress(byte[] data) {
        byte[] output;
        Inflater decompresser = new Inflater();
        decompresser.reset();
        decompresser.setInput(data);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        try {
            byte[] buf = new byte[2048];
            while (!decompresser.finished()) {
                int i = decompresser.inflate(buf);
                bos.write(buf, 0, i);
            }
            output = bos.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            try {
                bos.close();
            } catch (IOException ignored) {
            }
        }
        decompresser.end();
        return output;
    }
}
