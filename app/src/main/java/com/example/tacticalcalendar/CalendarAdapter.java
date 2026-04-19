package com.example.tacticalcalendar;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tacticalcalendar.data.CalendarEntry;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {

    private List<CalendarEntry> entries = new ArrayList<>();
    private OnEntryClickListener listener;

    public interface OnEntryClickListener {
        void onEntryClick(CalendarEntry entry);
    }

    public CalendarAdapter(OnEntryClickListener listener) {
        this.listener = listener;
    }

    public void setEntries(List<CalendarEntry> entries) {
        this.entries = entries;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CalendarEntry entry = entries.get(position);
        holder.tvTitle.setText(entry.title);
        holder.tvDescription.setText(entry.description);
        holder.tvTags.setText(entry.tags);
        holder.cardView.setCardBackgroundColor(entry.color != 0 ? entry.color : Color.WHITE);
        
        holder.itemView.setOnClickListener(v -> listener.onEntryClick(entry));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDescription, tvTags;
        MaterialCardView cardView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvTags = itemView.findViewById(R.id.tvTags);
            cardView = itemView.findViewById(R.id.cardView);
        }
    }
}
