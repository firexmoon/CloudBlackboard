package com.beautyli.app.cloudblackboard;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity{

    private EditText mUserView;
    private EditText mPasswordView;
    private EditText mServerIPView;

    static public SharedPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mPreferences = getPreferences(Activity.MODE_PRIVATE);
        if(mPreferences.getBoolean("auto_logon", false)) {
            gotoMainActivity();
            return;
        }

        mUserView = findViewById(R.id.user);
        mPasswordView = findViewById(R.id.password);
        mServerIPView = findViewById(R.id.server_ip);

        mUserView.setText(mPreferences.getString("user", ""));
        mPasswordView.setText(mPreferences.getString("pin", ""));

        String ip_port = mPreferences.getString("server_ip", "") + ":" + mPreferences.getString("server_port", "");
        if(ip_port.length() > 3) {
            mServerIPView.setText(ip_port);
        }

        Button mSignInButton = findViewById(R.id.sign_in_button);
        mSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!getLogonInfo()) {
                    return;
                }
                gotoMainActivity();
            }
        });
        mSignInButton.setFocusable(true);
        mSignInButton.requestFocus();
        mSignInButton.setFocusableInTouchMode(true);
        mSignInButton.requestFocusFromTouch();

        Button mRegisterButton = findViewById(R.id.register_button);
        mRegisterButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!getLogonInfo()) {
                    return;
                }

                MyClient.mServer_IP = mPreferences.getString("server_ip", "");
                MyClient.mServer_Port = Integer.parseInt(mPreferences.getString("server_port", ""));

                new registerThread().start();
            }
        });
    }

    private void gotoMainActivity() {
        MyClient.mServer_IP = mPreferences.getString("server_ip", "");
        MyClient.mServer_Port = Integer.parseInt(mPreferences.getString("server_port", ""));

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra("user", mPreferences.getString("user", ""));
        intent.putExtra("pin", mPreferences.getString("pin", ""));
        startActivity(intent);
        this.finish();
    }

    protected void showMsg(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    protected class registerThread extends Thread {
        public void run() {
            MyClient mClient = new MyClient();

            if (!mClient.connect()) {
                showMsg("无法连接到服务器");
                return;
            }

            if (!mClient.register(mPreferences.getString("user", ""),
                    mPreferences.getString("pin", ""))) {
                showMsg("注册失败：" + mClient.mLastReason);
                return;
            }
            mClient.close();

            showMsg("注册成功");
        }
    }

    protected boolean getLogonInfo()
    {
        String sUser = mUserView.getText().toString();
        String sPIN = mPasswordView.getText().toString();
        String sServerIP = mServerIPView.getText().toString();

        if(sServerIP.length() < 6 || !sServerIP.contains(":") || sUser.length() < 3 || sPIN.length() < 6)
        {
            showMsg("请输入正确的登录信息");
            return false;
        }

        String[] ip_port = sServerIP.split(":");
        if(ip_port.length != 2) {
            showMsg("无效的服务器地址");
            return false;
        }

        String sIP = ip_port[0];
        String sPort = ip_port[1];
        if(sIP.length() < 6 || sPort.length() < 2) {
            showMsg("无效的服务器地址");
            return false;
        }

        String base64_pin;
        if(sPIN.length() > 32) {
            base64_pin = sPIN;
        } else {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(sPIN.getBytes("UTF-8"));
                base64_pin = Base64.encodeToString(hash, Base64.DEFAULT).trim();
            } catch (NoSuchAlgorithmException e) {
                showMsg("不支持的Hash算法");
                return false;
            } catch (UnsupportedEncodingException e) {
                showMsg("密码包含非法字符");
                return false;
            }
        }

        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString("user", sUser);
        editor.putString("pin", base64_pin);
        editor.putString("server_ip", sIP);
        editor.putString("server_port", sPort);
        editor.commit();
        return true;
    }
}

