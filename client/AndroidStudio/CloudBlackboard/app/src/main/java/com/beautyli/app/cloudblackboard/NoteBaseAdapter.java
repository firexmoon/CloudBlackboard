package com.beautyli.app.cloudblackboard;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.gson.JsonObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class NoteBaseAdapter extends RecyclerView.Adapter<NoteBaseAdapter.ViewHolder> {

    private List<JsonObject> mNoteList;
    private OnItemClickListener mOnItemClickListener;

    static class ViewHolder extends RecyclerView.ViewHolder{
        TextView noteText;
        TextView noteDate;

        ViewHolder(View view) {
            super(view);
            noteText = view.findViewById(R.id.note_text);
            noteDate = view.findViewById(R.id.note_date);
        }
    }

    public void update(List<JsonObject> noteList) {
        this.mNoteList = noteList;
        notifyDataSetChanged();
    }

    NoteBaseAdapter(List<JsonObject> noteList) {
        this.mNoteList = noteList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.note_recyclerview, parent,false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, final int position) {
        JsonObject note = mNoteList.get(position);

        holder.noteText.setText(note.get("text").getAsString());

        Double note_time = note.get("time").getAsDouble();
        Date note_date = new Date(Math.round(note_time * 1000));
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
        String note_time_str = df.format(note_date);
        holder.noteDate.setText(note_time_str);

        if (mOnItemClickListener != null) {
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mOnItemClickListener.onClick(position);
                }
            });

            holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    mOnItemClickListener.onLongClick(position);
                    return false;
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return mNoteList.size();
    }

    public interface OnItemClickListener{
        void onClick( int position);
        void onLongClick( int position);
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener ){
        this.mOnItemClickListener = onItemClickListener;
    }

}
