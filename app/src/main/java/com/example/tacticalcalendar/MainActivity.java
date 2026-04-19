package com.example.tacticalcalendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tacticalcalendar.data.AppDatabase;
import com.example.tacticalcalendar.data.CalendarDao;
import com.example.tacticalcalendar.data.CalendarEntry;
import com.example.tacticalcalendar.receiver.AlarmReceiver;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener;
import com.prolificinteractive.materialcalendarview.spans.DotSpan;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private MaterialCalendarView calendarView;
    private static final int PERMISSION_REQUEST_CODE = 123;

    private RecyclerView recyclerView;
    private CalendarAdapter adapter;
    private CalendarDao calendarDao;
    private long selectedDate;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private List<CalendarEntry> currentDayEntries = new ArrayList<>();
    private Set<String> activeTags = new HashSet<>();
    private ChipGroup tagChipGroup;
    private long alarmTimeTemp = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        checkPermissions();
        calendarDao = AppDatabase.getDatabase(this).calendarDao();

        calendarView = findViewById(R.id.calendarView);
        recyclerView = findViewById(R.id.recyclerViewEntries);
        tagChipGroup = findViewById(R.id.tagChipGroup);
        FloatingActionButton fabAdd = findViewById(R.id.fabAddEntry);
        View fabToday = findViewById(R.id.fabToday);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CalendarAdapter(this::showEditDialog);
        recyclerView.setAdapter(adapter);

        // Default to today
        selectedDate = normalizeDate(System.currentTimeMillis());
        calendarView.setSelectedDate(CalendarDay.today());

        calendarView.setOnDateChangedListener((widget, date, selected) -> {
            Calendar cal = Calendar.getInstance();
            cal.set(date.getYear(), date.getMonth() - 1, date.getDay(), 0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
            selectedDate = cal.getTimeInMillis();
            loadEntriesForSelectedDate();
        });

        fabAdd.setOnClickListener(v -> showAddDialog());
        fabToday.setOnClickListener(v -> {
            CalendarDay today = CalendarDay.today();
            calendarView.setSelectedDate(today);
            calendarView.setCurrentDate(today);
            selectedDate = normalizeDate(System.currentTimeMillis());
            loadEntriesForSelectedDate();
        });

        loadEntriesForSelectedDate();
        observeAllTags();
        observeAllEntriesForDecorators();
        
        findViewById(R.id.chipShowAll).setOnClickListener(v -> {
            activeTags.clear();
            updateFilters();
        });
    }

    private void observeAllEntriesForDecorators() {
        calendarDao.getAllEntries().observe(this, entries -> {
            calendarView.removeDecorators();
            
            // Map dates to list of colors
            java.util.Map<CalendarDay, java.util.List<Integer>> dateColors = new java.util.HashMap<>();
            for (CalendarEntry entry : entries) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(entry.date);
                CalendarDay day = CalendarDay.from(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
                
                if (!dateColors.containsKey(day)) {
                    dateColors.put(day, new ArrayList<>());
                }
                dateColors.get(day).add(entry.color != 0 ? entry.color : Color.GRAY);
            }

            for (java.util.Map.Entry<CalendarDay, java.util.List<Integer>> entry : dateColors.entrySet()) {
                calendarView.addDecorator(new EventDecorator(entry.getValue(), entry.getKey()));
            }
            
            // Force refresh decorators
            calendarView.invalidateDecorators();
        });
    }

    private static class EventDecorator implements DayViewDecorator {
        private final List<Integer> colors;
        private final CalendarDay day;

        public EventDecorator(List<Integer> colors, CalendarDay day) {
            this.colors = colors;
            this.day = day;
        }

        @Override
        public boolean shouldDecorate(CalendarDay day) {
            return this.day.equals(day);
        }

        @Override
        public void decorate(DayViewFacade view) {
            // Increased dot radius from 8 to 12 for better visibility
            if (!colors.isEmpty()) {
                view.addSpan(new DotSpan(12, colors.get(0)));
            }
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
            }
        }
    }

    private long normalizeDate(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void loadEntriesForSelectedDate() {
        calendarDao.getEntriesForDate(selectedDate).observe(this, entries -> {
            currentDayEntries = entries;
            applyFilters();
        });
    }

    private void observeAllTags() {
        calendarDao.getAllEntries().observe(this, entries -> {
            Set<String> allTags = new HashSet<>();
            for (CalendarEntry entry : entries) {
                if (entry.tags != null && !entry.tags.isEmpty()) {
                    for (String t : entry.tags.split(",")) {
                        allTags.add(t.trim());
                    }
                }
            }
            updateTagChips(allTags);
        });
    }

    private void updateTagChips(Set<String> tags) {
        // Keep the "Show All" chip
        View showAll = findViewById(R.id.chipShowAll);
        tagChipGroup.removeAllViews();
        tagChipGroup.addView(showAll);

        for (String tag : tags) {
            Chip chip = new Chip(this);
            chip.setText(tag);
            chip.setCheckable(true);
            chip.setChecked(activeTags.contains(tag));
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) activeTags.add(tag);
                else activeTags.remove(tag);
                applyFilters();
            });
            tagChipGroup.addView(chip);
        }
    }

    private void applyFilters() {
        if (activeTags.isEmpty()) {
            adapter.setEntries(currentDayEntries);
        } else {
            List<CalendarEntry> filtered = new ArrayList<>();
            for (CalendarEntry entry : currentDayEntries) {
                boolean match = false;
                if (entry.tags != null) {
                    for (String t : entry.tags.split(",")) {
                        if (activeTags.contains(t.trim())) {
                            match = true;
                            break;
                        }
                    }
                }
                if (match) filtered.add(entry);
            }
            adapter.setEntries(filtered);
        }
    }
    
    private void updateFilters() {
        for (int i = 1; i < tagChipGroup.getChildCount(); i++) {
            ((Chip)tagChipGroup.getChildAt(i)).setChecked(false);
        }
        applyFilters();
    }

    private void showAddDialog() {
        showEntryDialog(null);
    }

    private void showEditDialog(CalendarEntry entry) {
        showEntryDialog(entry);
    }

    private int dialogSelectedColor = Color.WHITE;
    private int dialogOpacity = 255;

    private void showEntryDialog(CalendarEntry entryToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_entry, null);
        builder.setView(view);

        EditText etTitle = view.findViewById(R.id.etTitle);
        EditText etDescription = view.findViewById(R.id.etDescription);
        EditText etTags = view.findViewById(R.id.etTags);
        RadioGroup rgColors = view.findViewById(R.id.rgColors);
        View viewSelectedColor = view.findViewById(R.id.viewSelectedColor);
        SeekBar sbOpacity = view.findViewById(R.id.sbOpacity);
        TextView tvOpacityLabel = view.findViewById(R.id.tvOpacityLabel);

        if (entryToEdit != null) {
            etTitle.setText(entryToEdit.title);
            etDescription.setText(entryToEdit.description);
            etTags.setText(entryToEdit.tags);
            alarmTimeTemp = entryToEdit.alarmTime;
            dialogSelectedColor = entryToEdit.color;
            dialogOpacity = Color.alpha(dialogSelectedColor);
        } else {
            alarmTimeTemp = 0;
            dialogSelectedColor = Color.WHITE;
            dialogOpacity = 255;
        }
        
        updateColorPreview(viewSelectedColor, tvOpacityLabel, sbOpacity);

        rgColors.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbRed) dialogSelectedColor = Color.parseColor("#FFCDD2");
            else if (checkedId == R.id.rbBlue) dialogSelectedColor = Color.parseColor("#BBDEFB");
            else if (checkedId == R.id.rbGreen) dialogSelectedColor = Color.parseColor("#C8E6C9");
            else if (checkedId == R.id.rbYellow) dialogSelectedColor = Color.parseColor("#FFF9C4");
            
            // Preserve current opacity when picking preset
            dialogSelectedColor = Color.argb(dialogOpacity, Color.red(dialogSelectedColor), Color.green(dialogSelectedColor), Color.blue(dialogSelectedColor));
            updateColorPreview(viewSelectedColor, tvOpacityLabel, sbOpacity);
        });

        sbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                dialogOpacity = progress;
                dialogSelectedColor = Color.argb(dialogOpacity, Color.red(dialogSelectedColor), Color.green(dialogSelectedColor), Color.blue(dialogSelectedColor));
                updateColorPreview(viewSelectedColor, tvOpacityLabel, sbOpacity);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        view.findViewById(R.id.btnCustomColor).setOnClickListener(v -> {
            final EditText input = new EditText(this);
            input.setHint("#RRGGBB or #AARRGGBB");
            new AlertDialog.Builder(this)
                .setTitle("Enter Hex Color")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    try {
                        int color = Color.parseColor(input.getText().toString());
                        dialogSelectedColor = color;
                        dialogOpacity = Color.alpha(color);
                        updateColorPreview(viewSelectedColor, tvOpacityLabel, sbOpacity);
                        rgColors.clearCheck();
                    } catch (Exception e) {
                        Toast.makeText(this, "Invalid color format", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        view.findViewById(R.id.btnSetAlarm).setOnClickListener(v -> {
            Calendar currentTime = Calendar.getInstance();
            int hour = currentTime.get(Calendar.HOUR_OF_DAY);
            int minute = currentTime.get(Calendar.MINUTE);

            // Ustawiamy domyślnie aktualny czas zaraz po kliknięciu
            Calendar defaultAlarm = Calendar.getInstance();
            defaultAlarm.setTimeInMillis(selectedDate);
            defaultAlarm.set(Calendar.HOUR_OF_DAY, hour);
            defaultAlarm.set(Calendar.MINUTE, minute);
            alarmTimeTemp = defaultAlarm.getTimeInMillis();
            
            Toast.makeText(this, "Default alarm set to current time", Toast.LENGTH_SHORT).show();

            new TimePickerDialog(this, (view1, hourOfDay, minute1) -> {
                Calendar alarmCal = Calendar.getInstance();
                alarmCal.setTimeInMillis(selectedDate);
                alarmCal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                alarmCal.set(Calendar.MINUTE, minute1);
                alarmTimeTemp = alarmCal.getTimeInMillis();
                Toast.makeText(this, "Alarm time updated", Toast.LENGTH_SHORT).show();
            }, hour, minute, true).show();
        });

        view.findViewById(R.id.btnRemoveAlarm).setOnClickListener(v -> {
            alarmTimeTemp = 0;
            Toast.makeText(this, "Alarm removed", Toast.LENGTH_SHORT).show();
        });

        builder.setPositiveButton("Save", (dialog, which) -> {
            String title = etTitle.getText().toString();
            String desc = etDescription.getText().toString();
            String tags = etTags.getText().toString();

            CalendarEntry entry = entryToEdit != null ? entryToEdit : new CalendarEntry();
            entry.title = title;
            entry.description = desc;
            entry.tags = tags;
            entry.date = selectedDate;
            entry.color = dialogSelectedColor;
            entry.alarmTime = alarmTimeTemp;
            entry.hasAlarm = alarmTimeTemp > 0;

            executorService.execute(() -> {
                long id;
                if (entryToEdit != null) {
                    calendarDao.update(entry);
                    id = entry.id;
                    // Cancel existing alarm if removed
                    if (!entry.hasAlarm) {
                        cancelAlarm((int)id);
                    }
                } else {
                    id = calendarDao.insert(entry);
                }

                if (entry.hasAlarm) {
                    scheduleAlarm((int)id, entry.title, entry.alarmTime);
                }
            });
        });

        builder.setNegativeButton("Cancel", null);
        if (entryToEdit != null) {
            builder.setNeutralButton("Delete", (dialog, which) -> {
                executorService.execute(() -> calendarDao.delete(entryToEdit));
            });
        }

        builder.show();
    }

    private void updateColorPreview(View preview, TextView label, SeekBar seekBar) {
        preview.setBackgroundColor(dialogSelectedColor);
        int percent = (int) ((dialogOpacity / 255.0) * 100);
        label.setText("Opacity: " + percent + "%");
        seekBar.setProgress(dialogOpacity);
    }

    private void cancelAlarm(int entryId) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, entryId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    private void scheduleAlarm(int entryId, String title, long timeInMillis) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra(AlarmReceiver.EXTRA_ENTRY_ID, entryId);
        intent.putExtra("title", title);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, entryId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        if (timeInMillis > System.currentTimeMillis()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);
        }
    }
}
