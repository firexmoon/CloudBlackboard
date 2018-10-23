package com.beautyli.app.cloudblackboard;

import android.content.DialogInterface;
import android.content.Intent;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.JsonObject;


public class EditNoteActivity extends AppCompatActivity {

    private String note_id;
    private String note_text;
    private String user;
    private String pin;

    private EditText editText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_note);

        Intent intent = getIntent();
        note_id = intent.getStringExtra("id");
        note_text = intent.getStringExtra("text");
        user = intent.getStringExtra("user");
        pin = intent.getStringExtra("pin");
        boolean show_delete = intent.getBooleanExtra("show_delete", false);

        editText = findViewById(R.id.editText_noteText);
        editText.setText(note_text);

        Button btn_upload = findViewById(R.id.button_uploadNote);
        btn_upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String tmpText = editText.getText().toString();
                if(tmpText.compareTo(note_text) == 0) {
                    showMsg("数据未更改");
                    return;
                }
                note_text = editText.getText().toString();
                new uploadNotesThread().start();
            }
        });

        Button btn_delete = findViewById(R.id.button_deleteNote);
        btn_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(EditNoteActivity.this);
                builder.setTitle("确认删除");
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage("确定要删除条目吗？");
                builder.setCancelable(true);
                builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new deleteNotesThread().start();
                    }
                });
                builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                builder.create().show();
            }
        });
        btn_delete.setVisibility(show_delete ? View.VISIBLE : View.INVISIBLE);
    }

    protected void showMsg(final String msg) {
        showMsg(msg, false);
    }

    protected void showMsg(final String msg, final boolean bfinish) {
        runOnUiThread(new Runnable(){
            @Override
            public void run() {
                Toast.makeText(EditNoteActivity.this, msg, Toast.LENGTH_SHORT).show();
                if(bfinish) {
                    EditNoteActivity.this.finish();
                }
            }
        });
    }

    protected class deleteNotesThread extends Thread {
        public void run() {
            MyClient mClient = new MyClient();

            if (!mClient.connect()) {
                showMsg("无法连接到服务器");
                return;
            }

            if (!mClient.logon(user, pin)) {
                showMsg("登录失败：" + mClient.mLastReason);
                return;
            }

            JsonObject respond = mClient.deleteNotes(note_id);
            if (respond == null) {
                showMsg("删除失败：" + mClient.mLastReason);
                return;
            }
            mClient.close();

            showMsg("删除成功", true);
        }
    }

    protected class uploadNotesThread extends Thread {
        public void run() {
            MyClient mClient = new MyClient();

            if (!mClient.connect()) {
                showMsg("无法连接到服务器");
                return;
            }

            if (!mClient.logon(user, pin)) {
                showMsg("登录失败：" + mClient.mLastReason);
                return;
            }

            String tmp_text = note_text.replace("\r", "");
            tmp_text = tmp_text.replace("\n", "\r\n");

            JsonObject respond = mClient.uploadNotes(note_id, tmp_text);
            if (respond == null) {
                showMsg("上传失败：" + mClient.mLastReason);
                return;
            }
            mClient.close();

            showMsg("上传成功", true);
        }
    }

    @Override
    public void onBackPressed() {
        String tmpText = editText.getText().toString();
        if(tmpText.compareTo(note_text) == 0) {
            super.onBackPressed();
            return;
        }

        new AlertDialog.Builder(this).setTitle("确认退出吗，将丢失所有更改")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        EditNoteActivity.this.finish();
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).show();
    }
}
