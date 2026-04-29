package com.example.tacticalcalendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.text.style.LineBackgroundSpan;
import android.graphics.Canvas;
import android.graphics.Paint;

import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuItem;

import androidx.drawerlayout.widget.DrawerLayout;
import androidx.core.view.GravityCompat;
import androidx.appcompat.app.ActionBarDrawerToggle;

import com.google.gson.Gson;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import android.net.Uri;

public class MainActivity extends AppCompatActivity {
    private MaterialCalendarView calendarView;
    private DrawerLayout drawerLayout;
    private static final int PERMISSION_REQUEST_CODE = 123;

    private RecyclerView recyclerView;
    private CalendarAdapter adapter;
    private CalendarDao calendarDao;
    private long selectedDate;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private List<CalendarEntry> currentDayEntries = new ArrayList<>();
    private Set<String> activeTags = new HashSet<>();
    private Set<String> allAvailableTags = new HashSet<>();
    private ChipGroup tagChipGroup;
    private long alarmTimeTemp = 0;
    private boolean isFirstTagLoad = true;

    // UI Colors
    private int colorBackground = Color.WHITE;
    private int colorFab = Color.BLUE;
    private int colorSelection = Color.parseColor("#440000FF");
    private int colorToolbar = Color.parseColor("#6200EE");
    private SharedPreferences prefs;

    private static class BackupData {
        List<CalendarEntry> entries;
        int colorBackground;
        int colorFab;
        int colorSelection;
        int colorToolbar;
    }

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) performExport(uri);
            }
    );

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) performImport(uri);
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        loadUserColors();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawerLayout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        // Move the icon to the right side
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.END));
        
        checkPermissions();
        calendarDao = AppDatabase.getDatabase(this).calendarDao();

        calendarView = findViewById(R.id.calendarView);
        recyclerView = findViewById(R.id.recyclerViewEntries);
        tagChipGroup = findViewById(R.id.tagChipGroup);
        FloatingActionButton fabAdd = findViewById(R.id.fabAddEntry);
        View fabToday = findViewById(R.id.fabToday);

        applyUiColors();

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
        
        handleIntent(getIntent());
        
        findViewById(R.id.chipShowAll).setOnClickListener(v -> {
            activeTags.clear();
            activeTags.addAll(allAvailableTags);
            updateFilters();
            applyFilters();
            observeAllEntriesForDecorators(); // Odśwież kropki
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private long normalizeDate(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void handleIntent(Intent intent) {
        if (intent != null && ("ACTION_ADD_ENTRY".equals(intent.getAction()) || intent.getBooleanExtra("ACTION_ADD_ENTRY", false))) {
            intent.removeExtra("ACTION_ADD_ENTRY");
            intent.setAction(null); // Clear action to prevent multiple triggers if activity is recreated

            // WYMUSZAMY JUTRO (Dzień + 1)
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            selectedDate = cal.getTimeInMillis();
            
            CalendarDay tomorrow = CalendarDay.from(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            calendarView.setSelectedDate(tomorrow);
            calendarView.setCurrentDate(tomorrow);
            
            loadEntriesForSelectedDate();

            calendarView.postDelayed(() -> {
                CalendarEntry entry = new CalendarEntry();
                entry.date = selectedDate; // Set the correct date (tomorrow)
                entry.tags = "niepilne";
                showEntryDialog(entry);
            }, 200);
        }
    }

    private void loadUserColors() {
        colorBackground = prefs.getInt("color_bg", Color.WHITE);
        colorFab = prefs.getInt("color_fab", Color.parseColor("#6200EE"));
        colorSelection = prefs.getInt("color_selection", Color.parseColor("#446200EE"));
        colorToolbar = prefs.getInt("color_toolbar", Color.parseColor("#6200EE"));
    }

    private void applyUiColors() {
        findViewById(R.id.main).setBackgroundColor(colorBackground);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setBackgroundColor(colorToolbar);
        }

        // Status Bar Color (matched with FAB as requested)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(colorFab);
            
            // Adjust status bar icons brightness based on background
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decor = getWindow().getDecorView();
                if (isColorLight(colorFab)) {
                    decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                } else {
                    decor.setSystemUiVisibility(0);
                }
            }
        }
        
        FloatingActionButton fabAdd = findViewById(R.id.fabAddEntry);
        View fabToday = findViewById(R.id.fabToday);
        
        fabAdd.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorFab));
        if (fabToday instanceof com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton) {
            ((com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton)fabToday).setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorFab));
        }

        calendarView.setSelectionColor(colorSelection);
        updateCalendarDecorators();
    }

    private boolean isColorLight(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness < 0.5;
    }

    private void updateCalendarDecorators() {
        calendarView.removeDecorators();
        observeAllEntriesForDecorators(); // Refresh dots too
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            showSettingsDialog();
            return true;
        } else if (item.getItemId() == R.id.action_tags) {
            drawerLayout.openDrawer(GravityCompat.END);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        builder.setView(view);

        View vBg = view.findViewById(R.id.viewBgPreview);
        View vFab = view.findViewById(R.id.viewFabPreview);
        View vSel = view.findViewById(R.id.viewSelectionPreview);
        View vTool = view.findViewById(R.id.viewToolbarPreview);

        vBg.setBackgroundColor(colorBackground);
        vFab.setBackgroundColor(colorFab);
        vSel.setBackgroundColor(colorSelection);
        vTool.setBackgroundColor(colorToolbar);

        view.findViewById(R.id.btnPickBgColor).setOnClickListener(v -> pickColor(c -> {
            colorBackground = c;
            vBg.setBackgroundColor(c);
        }, colorBackground));

        view.findViewById(R.id.btnPickFabColor).setOnClickListener(v -> pickColor(c -> {
            colorFab = c;
            vFab.setBackgroundColor(c);
        }, colorFab));

        view.findViewById(R.id.btnPickSelectionColor).setOnClickListener(v -> pickColor(c -> {
            colorSelection = c;
            vSel.setBackgroundColor(colorSelection);
        }, colorSelection));

        view.findViewById(R.id.btnPickToolbarColor).setOnClickListener(v -> pickColor(c -> {
            colorToolbar = c;
            vTool.setBackgroundColor(c);
        }, colorToolbar));

        view.findViewById(R.id.btnExport).setOnClickListener(v -> {
            exportLauncher.launch("tactical_calendar_backup.json");
        });

        view.findViewById(R.id.btnImport).setOnClickListener(v -> {
            importLauncher.launch(new String[]{"application/json", "application/octet-stream"});
        });

        builder.setPositiveButton("Save", (d, w) -> {
            prefs.edit()
                .putInt("color_bg", colorBackground)
                .putInt("color_fab", colorFab)
                .putInt("color_selection", colorSelection)
                .putInt("color_toolbar", colorToolbar)
                .apply();
            applyUiColors();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void pickColor(OnColorPickedListener listener, int initialColor) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_color_picker, null);
        builder.setView(view);

        View vPreview = view.findViewById(R.id.viewColorPreview);
        SeekBar sbR = view.findViewById(R.id.sbRed);
        SeekBar sbG = view.findViewById(R.id.sbGreen);
        SeekBar sbB = view.findViewById(R.id.sbBlue);
        SeekBar sbA = view.findViewById(R.id.sbAlpha);
        SeekBar sbS = view.findViewById(R.id.sbSaturation);

        // Ustawienie początkowych wartości
        sbR.setProgress(Color.red(initialColor));
        sbG.setProgress(Color.green(initialColor));
        sbB.setProgress(Color.blue(initialColor));
        sbA.setProgress(Color.alpha(initialColor));
        float[] initialHsv = new float[3];
        Color.colorToHSV(initialColor, initialHsv);
        sbS.setProgress((int)(initialHsv[1] * 100));
        vPreview.setBackgroundColor(initialColor);

        SeekBar.OnSeekBarChangeListener changeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int baseColor = Color.rgb(sbR.getProgress(), sbG.getProgress(), sbB.getProgress());
                float[] hsv = new float[3];
                Color.colorToHSV(baseColor, hsv);
                hsv[1] = sbS.getProgress() / 100f;
                int color = Color.HSVToColor(sbA.getProgress(), hsv);
                vPreview.setBackgroundColor(color);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        sbR.setOnSeekBarChangeListener(changeListener);
        sbG.setOnSeekBarChangeListener(changeListener);
        sbB.setOnSeekBarChangeListener(changeListener);
        sbA.setOnSeekBarChangeListener(changeListener);
        sbS.setOnSeekBarChangeListener(changeListener);

        builder.setPositiveButton("Wybierz", (d, w) -> {
            int baseColor = Color.rgb(sbR.getProgress(), sbG.getProgress(), sbB.getProgress());
            float[] hsv = new float[3];
            Color.colorToHSV(baseColor, hsv);
            hsv[1] = sbS.getProgress() / 100f;
            int finalColor = Color.HSVToColor(sbA.getProgress(), hsv);
            listener.onColorPicked(finalColor);
        });
        builder.setNegativeButton("Anuluj", null);
        builder.show();
    }

    interface OnColorPickedListener {
        void onColorPicked(int color);
    }

    private void observeAllEntriesForDecorators() {
        calendarDao.getAllEntries().observe(this, entries -> {
            calendarView.removeDecorators();
            
            java.util.Map<CalendarDay, java.util.List<Integer>> dateColors = new java.util.HashMap<>();
            for (CalendarEntry entry : entries) {
                // Filtrowanie wpisów dla dekoratorów (kropek)
                boolean matchesFilter = activeTags.isEmpty();
                if (!matchesFilter) {
                    if (entry.tags == null || entry.tags.isEmpty()) {
                        matchesFilter = activeTags.size() == allAvailableTags.size(); 
                    } else {
                        for (String t : entry.tags.split(",")) {
                            if (activeTags.contains(t.trim())) {
                                matchesFilter = true;
                                break;
                            }
                        }
                    }
                }

                if (!matchesFilter) continue;

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
            if (!colors.isEmpty()) {
                view.addSpan(new MultiDotSpan(colors));
            }
        }
    }

    private static class MultiDotSpan implements LineBackgroundSpan {
        private final List<Integer> colors;
        private static final float DOT_RADIUS = 6f; // Smaller dots
        private static final float SPACING = 4f;

        public MultiDotSpan(List<Integer> colors) {
            this.colors = colors;
        }

        @Override
        public void drawBackground(Canvas canvas, Paint paint, int left, int right, int top, int edit, int bottom, CharSequence text, int start, int end, int lnum) {
            int total = Math.min(colors.size(), 5); // Max 5 dots to keep it clean
            int oldColor = paint.getColor();
            
            float centerX = (left + right) / 2f;
            float startX = centerX - ((total - 1) * (DOT_RADIUS + SPACING) / 2f);

            for (int i = 0; i < total; i++) {
                paint.setColor(colors.get(i));
                canvas.drawCircle(startX + i * (DOT_RADIUS * 2 + SPACING), bottom + DOT_RADIUS + 2, DOT_RADIUS, paint);
            }
            
            paint.setColor(oldColor);
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
            }
        }
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
            allTags.add("niepilne"); // Zawsze dodawaj tag "niepilne" do listy dostępnych
            for (CalendarEntry entry : entries) {
                if (entry.tags != null && !entry.tags.isEmpty()) {
                    for (String t : entry.tags.split(",")) {
                        allTags.add(t.trim());
                    }
                }
            }
            
            if (isFirstTagLoad) {
                allAvailableTags = new HashSet<>(allTags);
                activeTags = new HashSet<>(allTags);
                
                // Domyślnie zawsze ukrywamy "niepilne"
                activeTags.remove("niepilne");
                
                isFirstTagLoad = false;
                applyFilters();
                observeAllEntriesForDecorators();
            } else {
                allAvailableTags.addAll(allTags);
            }
            updateTagChips(allTags);
        });
    }

    private void updateTagChips(Set<String> tags) {
        tagChipGroup.removeAllViews();

        for (String tag : tags) {
            Chip chip = new Chip(this);
            chip.setText(tag);
            chip.setCheckable(true);
            boolean isChecked = activeTags.contains(tag);
            chip.setChecked(isChecked);
            
            // Stylizacja dla lepszej widoczności zaznaczenia
            if (isChecked) {
                chip.setChipBackgroundColorResource(R.color.purple_200); // Or use colorFab
                chip.setTextColor(Color.WHITE);
            } else {
                chip.setChipBackgroundColor(null); // Default
                chip.setTextColor(Color.BLACK);
            }

            chip.setOnCheckedChangeListener((buttonView, checked) -> {
                if (checked) activeTags.add(tag);
                else activeTags.remove(tag);
                applyFilters();
                observeAllEntriesForDecorators();
                updateTagChips(tags); // Refresh to apply styles
            });
            tagChipGroup.addView(chip);
        }
    }

    private void applyFilters() {
        if (currentDayEntries == null) return;
        
        List<CalendarEntry> filtered = new ArrayList<>();
        for (CalendarEntry entry : currentDayEntries) {
            if (entry.tags == null || entry.tags.trim().isEmpty()) {
                filtered.add(entry);
            } else {
                String[] entryTags = entry.tags.split(",");
                for (String t : entryTags) {
                    if (activeTags.contains(t.trim())) {
                        filtered.add(entry);
                        break;
                    }
                }
            }
        }
        adapter.setEntries(filtered);
    }

    private void updateFilters() {
        for (int i = 0; i < tagChipGroup.getChildCount(); i++) {
            View v = tagChipGroup.getChildAt(i);
            if (v instanceof Chip) {
                ((Chip)v).setChecked(activeTags.contains(((Chip)v).getText().toString()));
            }
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
    private float dialogSaturation = 1.0f;
    private long dialogEntryDate = 0;

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
        SeekBar sbSaturation = view.findViewById(R.id.sbSaturation);
        TextView tvSaturationLabel = view.findViewById(R.id.tvSaturationLabel);
        
        Button btnPickDate = view.findViewById(R.id.btnPickDate);
        TextView tvCurrentDate = view.findViewById(R.id.tvCurrentDate);

        if (entryToEdit != null && entryToEdit.id != 0) {
            etTitle.setText(entryToEdit.title);
            etDescription.setText(entryToEdit.description);
            etTags.setText(entryToEdit.tags);
            alarmTimeTemp = entryToEdit.alarmTime;
            dialogSelectedColor = entryToEdit.color != 0 ? entryToEdit.color : Color.WHITE;
            dialogOpacity = Color.alpha(dialogSelectedColor);
            float[] hsv = new float[3];
            Color.colorToHSV(dialogSelectedColor, hsv);
            dialogSaturation = hsv[1];
            dialogEntryDate = entryToEdit.date;
        } else {
            if (entryToEdit != null) {
                etTags.setText(entryToEdit.tags);
                dialogEntryDate = entryToEdit.date != 0 ? entryToEdit.date : selectedDate;
            } else {
                dialogEntryDate = selectedDate;
            }
            alarmTimeTemp = 0;
            dialogSelectedColor = Color.WHITE;
            dialogOpacity = 255;
            dialogSaturation = 1.0f;
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        tvCurrentDate.setText(sdf.format(new Date(dialogEntryDate)));

        btnPickDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(dialogEntryDate);
            new DatePickerDialog(this, (view1, year, month, dayOfMonth) -> {
                Calendar newCal = Calendar.getInstance();
                newCal.set(year, month, dayOfMonth, 0, 0, 0);
                newCal.set(Calendar.MILLISECOND, 0);
                dialogEntryDate = newCal.getTimeInMillis();
                tvCurrentDate.setText(sdf.format(new Date(dialogEntryDate)));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        updateColorPreview(viewSelectedColor, tvOpacityLabel, sbOpacity, tvSaturationLabel, sbSaturation);

        rgColors.setOnCheckedChangeListener((group, checkedId) -> {
            int baseColor;
            if (checkedId == R.id.rbRed) baseColor = Color.parseColor("#FFCDD2");
            else if (checkedId == R.id.rbBlue) baseColor = Color.parseColor("#BBDEFB");
            else if (checkedId == R.id.rbGreen) baseColor = Color.parseColor("#C8E6C9");
            else if (checkedId == R.id.rbYellow) baseColor = Color.parseColor("#FFF9C4");
            else baseColor = Color.WHITE;
            
            float[] hsv = new float[3];
            Color.colorToHSV(baseColor, hsv);
            hsv[1] = dialogSaturation; // Keep current saturation
            dialogSelectedColor = Color.HSVToColor(dialogOpacity, hsv);
            updateColorPreview(viewSelectedColor, tvOpacityLabel, sbOpacity, tvSaturationLabel, sbSaturation);
        });

        sbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                dialogOpacity = progress;
                float[] hsv = new float[3];
                Color.colorToHSV(dialogSelectedColor, hsv);
                dialogSelectedColor = Color.HSVToColor(dialogOpacity, hsv);
                updateColorPreview(viewSelectedColor, tvOpacityLabel, sbOpacity, tvSaturationLabel, sbSaturation);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbSaturation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                dialogSaturation = progress / 100f;
                float[] hsv = new float[3];
                Color.colorToHSV(dialogSelectedColor, hsv);
                hsv[1] = dialogSaturation;
                dialogSelectedColor = Color.HSVToColor(dialogOpacity, hsv);
                updateColorPreview(viewSelectedColor, tvOpacityLabel, sbOpacity, tvSaturationLabel, sbSaturation);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        view.findViewById(R.id.btnCustomColor).setOnClickListener(v -> {
            pickColor(color -> {
                dialogSelectedColor = color;
                dialogOpacity = Color.alpha(color);
                updateColorPreview(viewSelectedColor, tvOpacityLabel, sbOpacity, tvSaturationLabel, sbSaturation);
                rgColors.clearCheck();
            }, dialogSelectedColor);
        });

        view.findViewById(R.id.btnSetAlarm).setOnClickListener(v -> {
            Calendar currentTime = Calendar.getInstance();
            int hour = currentTime.get(Calendar.HOUR_OF_DAY);
            int minute = currentTime.get(Calendar.MINUTE);

            // Ustawiamy domyślnie aktualny czas na wybranym dniu
            Calendar defaultAlarm = Calendar.getInstance();
            defaultAlarm.setTimeInMillis(dialogEntryDate);
            defaultAlarm.set(Calendar.HOUR_OF_DAY, hour);
            defaultAlarm.set(Calendar.MINUTE, minute);
            alarmTimeTemp = defaultAlarm.getTimeInMillis();
            
            Toast.makeText(this, "Default alarm set to current time", Toast.LENGTH_SHORT).show();

            new TimePickerDialog(this, (view1, hourOfDay, minute1) -> {
                Calendar alarmCal = Calendar.getInstance();
                alarmCal.setTimeInMillis(dialogEntryDate);
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

        view.findViewById(R.id.btnMoveOneDay).setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(dialogEntryDate);
            cal.add(Calendar.DAY_OF_YEAR, 1);
            dialogEntryDate = cal.getTimeInMillis();
            tvCurrentDate.setText(sdf.format(new Date(dialogEntryDate)));
            Toast.makeText(this, "Przeniesiono o 1 dzień", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btnMoveSevenDays).setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(dialogEntryDate);
            cal.add(Calendar.DAY_OF_YEAR, 7);
            dialogEntryDate = cal.getTimeInMillis();
            tvCurrentDate.setText(sdf.format(new Date(dialogEntryDate)));
            Toast.makeText(this, "Przeniesiono o 7 dni", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btnMoveOneMonth).setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(dialogEntryDate);
            cal.add(Calendar.MONTH, 1);
            dialogEntryDate = cal.getTimeInMillis();
            tvCurrentDate.setText(sdf.format(new Date(dialogEntryDate)));
            Toast.makeText(this, "Przeniesiono o 1 miesiąc", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btnMoveToSunday).setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(dialogEntryDate);
            
            // Zawsze przechodzimy do kolejnej niedzieli (minimum +1 dzień)
            do {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            } while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY);

            // Wyzeruj czas dla spójności
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            dialogEntryDate = cal.getTimeInMillis();
            tvCurrentDate.setText(sdf.format(new Date(dialogEntryDate)));
            Toast.makeText(this, "Przeniesiono do najbliższej niedzieli", Toast.LENGTH_SHORT).show();
        });

        builder.setPositiveButton("Save", (dialog, which) -> {
            // Hide keyboard
            View currentFocus = ((AlertDialog)dialog).getCurrentFocus();
            if (currentFocus != null) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }

            String title = etTitle.getText().toString();
            String desc = etDescription.getText().toString();
            String tags = etTags.getText().toString();

            CalendarEntry entry = entryToEdit != null ? entryToEdit : new CalendarEntry();
            entry.title = title;
            entry.description = desc;
            entry.tags = tags;
            
            // Use the date selected in the dialog
            entry.date = dialogEntryDate;

            entry.color = dialogSelectedColor;
            entry.alarmTime = alarmTimeTemp;
            entry.hasAlarm = alarmTimeTemp > 0;

            executorService.execute(() -> {
                long id;
                if (entryToEdit != null && entryToEdit.id != 0) {
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
                
                runOnUiThread(() -> {
                    loadEntriesForSelectedDate();
                    Toast.makeText(this, "Entry saved", Toast.LENGTH_SHORT).show();
                });
            });
        });

        builder.setNegativeButton("Cancel", null);
        if (entryToEdit != null) {
            builder.setNeutralButton("Delete", (dialog, which) -> {
                executorService.execute(() -> calendarDao.delete(entryToEdit));
            });
        }

        AlertDialog dialog = builder.create();
        
        // Zamykanie klawiatury po kliknięciu "Done" w ostatnim polu (Tags)
        etTags.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        // To sprawi, że dialog "podskoczy" nad klawiaturę
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        
        dialog.show();
    }

    private void updateColorPreview(View preview, TextView opLabel, SeekBar opSeekBar, TextView satLabel, SeekBar satSeekBar) {
        preview.setBackgroundColor(dialogSelectedColor);
        int opPercent = (int) ((dialogOpacity / 255.0) * 100);
        opLabel.setText("Opacity: " + opPercent + "%");
        opSeekBar.setProgress(dialogOpacity);
        
        int satPercent = (int) (dialogSaturation * 100);
        satLabel.setText("Saturation: " + satPercent + "%");
        satSeekBar.setProgress(satPercent);
    }

    private void performExport(Uri uri) {
        executorService.execute(() -> {
            try {
                BackupData backup = new BackupData();
                backup.entries = calendarDao.getAllEntriesSync();
                backup.colorBackground = colorBackground;
                backup.colorFab = colorFab;
                backup.colorSelection = colorSelection;
                backup.colorToolbar = colorToolbar;

                String json = new Gson().toJson(backup);
                try (OutputStream os = getContentResolver().openOutputStream(uri);
                     OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                    writer.write(json);
                }
                runOnUiThread(() -> Toast.makeText(this, "Data exported successfully", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void performImport(Uri uri) {
        executorService.execute(() -> {
            try {
                BackupData backup;
                try (InputStream is = getContentResolver().openInputStream(uri);
                     InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    backup = new Gson().fromJson(reader, BackupData.class);
                }

                if (backup != null) {
                    if (backup.entries != null) {
                        calendarDao.deleteAll(); // Wyczyść stare dane przed importem
                        for (CalendarEntry entry : backup.entries) {
                            entry.id = 0; // Ensure new insertion
                            calendarDao.insert(entry);
                        }
                    }
                    
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("color_bg", backup.colorBackground);
                    editor.putInt("color_fab", backup.colorFab);
                    editor.putInt("color_selection", backup.colorSelection);
                    editor.putInt("color_toolbar", backup.colorToolbar);
                    editor.apply();

                    runOnUiThread(() -> {
                        loadUserColors();
                        applyUiColors();
                        Toast.makeText(this, "Data imported successfully", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
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
