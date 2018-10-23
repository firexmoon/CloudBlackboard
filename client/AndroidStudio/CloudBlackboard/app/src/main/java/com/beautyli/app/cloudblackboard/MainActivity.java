package com.beautyli.app.cloudblackboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map.*;
import java.util.Random;


public class MainActivity extends AppCompatActivity {

    private Random mRandom = new Random();
    private List<JsonObject> mNoteList = new ArrayList<>();
    private NoteBaseAdapter mAdapter;

    private MyClient mClient = new MyClient();

    private String mUser;
    private String mPIN;

    @Override
    protected void onResume() {
        super.onResume();

        new getAllNotesThread().start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        mUser = intent.getStringExtra("user");
        mPIN = intent.getStringExtra("pin");

        String sDBCache = LoginActivity.mPreferences.getString("db_cache", "[]");
        JsonArray ja = (new JsonParser()).parse(sDBCache).getAsJsonArray();
        for (JsonElement e : ja) {
            JsonObject obj = e.getAsJsonObject();
            mNoteList.add(obj);
        }
        MyClient.mCurDBVersion = LoginActivity.mPreferences.getLong("db_version", 0);

        FloatingActionButton fab_add = findViewById(R.id.fab_add);
        fab_add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                byte[] rnd = new byte[8];
                mRandom.nextBytes(rnd);
                byte[] t_rnd = (new Date()).toString().getBytes();

                String note_id;
                try {
                    MessageDigest digest = MessageDigest.getInstance("MD5");
                    digest.update(rnd);
                    digest.update(t_rnd);
                    note_id = Base64.encodeToString(digest.digest(), Base64.DEFAULT).trim();
                    note_id = note_id.substring(0, 8);
                } catch (NoSuchAlgorithmException e) {
                    note_id = Base64.encodeToString(rnd, Base64.DEFAULT).trim();
                    note_id = note_id.substring(0, 8);
                }

                Intent intent = new Intent(MainActivity.this, EditNoteActivity.class);
                intent.putExtra("id", note_id);
                intent.putExtra("text", "");
                intent.putExtra("user", mUser);
                intent.putExtra("pin", mPIN);
                intent.putExtra("show_delete", false);
                startActivity(intent);
            }
        });

        //初始化RecyclerView
        RecyclerView recyclerview = findViewById(R.id.recycler_view);
        //设置RecyclerView布局
        recyclerview.setLayoutManager(new LinearLayoutManager(this));
        //设置Adapter
        mAdapter = new NoteBaseAdapter(mNoteList);
        mAdapter.setOnItemClickListener(new NoteBaseAdapter.OnItemClickListener() {
            @Override
            public void onLongClick(int position) {

            }
            @Override
            public void onClick(int position) {
                JsonObject note = mNoteList.get(position);

                Intent intent = new Intent(MainActivity.this, EditNoteActivity.class);
                intent.putExtra("id", note.get("id").getAsString());
                intent.putExtra("text", note.get("text").getAsString());
                intent.putExtra("user", mUser);
                intent.putExtra("pin", mPIN);
                intent.putExtra("show_delete", true);
                startActivity(intent);
            }
        });
        recyclerview.setAdapter(mAdapter);
    }

    protected void showMsg(final String msg) {
        runOnUiThread(new Runnable(){
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    protected class getAllNotesThread extends Thread{
        public void run(){

            if(!mClient.connect()) {
                showMsg("无法连接到服务器");
                return;
            }

            if(!mClient.logon(mUser, mPIN)) {
                showMsg("登录失败：" + mClient.mLastReason);
                return;
            }

            JsonObject respond = mClient.getAllNotes();
            if(respond == null) {
                showMsg("下载失败：" + mClient.mLastReason);
                return;
            }
            mClient.close();

            //先处理要删除的note
            JsonArray deleted_notes = respond.get("deleted").getAsJsonArray();
            for (JsonElement e : deleted_notes) {
                String note_id = e.getAsString();
                for(JsonObject o : mNoteList) {
                    if(o.get("id").getAsString().compareTo(note_id) == 0) {
                        mNoteList.remove(o);
                        break;
                    }
                }
            }

            //再处理新增加/修改的note
            JsonObject notes = respond.get("new_notes").getAsJsonObject();
            for (Entry o : notes.entrySet()) {
                JsonObject note = ((JsonObject) o.getValue());
                note.addProperty("id", o.getKey().toString());
                //更新已有的note
                boolean bRepleace = false;
                for(int i=0; i<mNoteList.size(); i++) {
                    JsonObject obj = mNoteList.get(i);
                    if(obj.get("id").getAsString().compareTo(o.getKey().toString()) == 0) {
                        mNoteList.set(i, note);
                        bRepleace = true;
                        break;
                    }
                }
                //新增note
                if(!bRepleace) {
                    mNoteList.add(note);
                }
            }

            //准备本地cache
            JsonArray ja = new JsonArray();
            for(JsonObject o : mNoteList) {
                ja.add(o);
            }
            SharedPreferences.Editor editor = LoginActivity.mPreferences.edit();
            if(!LoginActivity.mPreferences.getBoolean("auto_logon", false)) {
                editor.putBoolean("auto_logon", true);
            }
            editor.putString("db_cache", ja.toString());
            editor.putLong("db_version", MyClient.mCurDBVersion);
            editor.apply();

            runOnUiThread(new Runnable(){
                @Override
                public void run() {
                    //更新UI
                    mAdapter.update(mNoteList);
                    showMsg("数据已更新");
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_disable_auto_logon) {
            SharedPreferences.Editor editor = LoginActivity.mPreferences.edit();
            editor.putBoolean("auto_logon", false);
            editor.commit();
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
