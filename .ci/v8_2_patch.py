# -*- coding: utf-8 -*-
import json, sys
from pathlib import Path

P = Path(sys.argv[1]).resolve()
MAIN = P / "app/src/main/java/com/kazanim/app/MainActivity.java"
MANIFEST = P / "app/src/main/AndroidManifest.xml"
GRADLE = P / "app/build.gradle.kts"
DATA = P / "app/src/main/assets/curriculum_2026_2027.json"

s = MAIN.read_text(encoding="utf-8")

if "import android.text.TextUtils;" not in s:
    s = s.replace("import android.text.TextWatcher;\n", "import android.text.TextWatcher;\nimport android.text.TextUtils;\n")
if "import android.widget.NumberPicker;" not in s:
    s = s.replace("import android.widget.ListView;\n", "import android.widget.ListView;\nimport android.widget.NumberPicker;\n")

old_info = """    private View infoTile(String title, String body) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(12), dp(11), dp(12), dp(12));
        tile.setBackground(roundRect(PRIMARY_FAINT, 15, DIVIDER, 1));
        tile.addView(label(title, 14, PRIMARY_DARK, true));
        addSpace(tile, 7);
        TextView text = label(emptyDash(body), 15, TEXT, false);
        text.setLineSpacing(dp(1), 1.06f);
        tile.addView(text);
        return tile;
    }
"""
new_info = """    private View infoTile(String title, String body) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(12), dp(11), dp(12), dp(12));
        tile.setBackground(roundRect(PRIMARY_FAINT, 15, DIVIDER, 1));
        boolean englishExpandable = course != null && "english".equals(course.subjectId) && "Konu".equals(title);
        TextView titleView = label(englishExpandable ? title + "  ⌄" : title, 14, PRIMARY_DARK, true);
        tile.addView(titleView);
        addSpace(tile, 7);
        TextView text = label(emptyDash(body), 15, TEXT, false);
        text.setLineSpacing(dp(1), 1.06f);
        if (englishExpandable) {
            text.setMaxLines(2);
            text.setEllipsize(TextUtils.TruncateAt.END);
            final boolean[] open = {false};
            View.OnClickListener toggle = v -> {
                open[0] = !open[0];
                text.setMaxLines(open[0] ? Integer.MAX_VALUE : 2);
                text.setEllipsize(open[0] ? null : TextUtils.TruncateAt.END);
                titleView.setText(title + (open[0] ? "  ⌃" : "  ⌄"));
                tile.requestLayout();
            };
            tile.setOnClickListener(toggle);
            text.setOnClickListener(toggle);
            tile.setClickable(true);
            tile.setFocusable(true);
        }
        tile.addView(text);
        return tile;
    }
"""
if old_info not in s:
    raise SystemExit("infoTile marker not found")
s = s.replace(old_info, new_info)

old_outcome = """            outcome.setBackground(roundRect(PRIMARY_SOFT, 16, DIVIDER, 1));
            outcome.addView(label("Öğrenme Çıktısı", 15, PRIMARY_DARK, true));
            addSpace(outcome, 7);
            TextView body = label(emptyDash(page.outcome), 16, TEXT, false);
            body.setLineSpacing(dp(2), 1.08f);
            outcome.addView(body);
            card.addView(outcome, op);
"""
new_outcome = """            outcome.setBackground(roundRect(PRIMARY_SOFT, 16, DIVIDER, 1));
            boolean englishOutcome = course != null && "english".equals(course.subjectId);
            TextView outcomeTitle = label(englishOutcome ? "Öğrenme Çıktısı  ⌄" : "Öğrenme Çıktısı", 15, PRIMARY_DARK, true);
            outcome.addView(outcomeTitle);
            addSpace(outcome, 7);
            TextView body = label(emptyDash(page.outcome), 16, TEXT, false);
            body.setLineSpacing(dp(2), 1.08f);
            if (englishOutcome) {
                body.setMaxLines(2);
                body.setEllipsize(TextUtils.TruncateAt.END);
                final boolean[] open = {false};
                View.OnClickListener toggle = v -> {
                    open[0] = !open[0];
                    body.setMaxLines(open[0] ? Integer.MAX_VALUE : 2);
                    body.setEllipsize(open[0] ? null : TextUtils.TruncateAt.END);
                    outcomeTitle.setText(open[0] ? "Öğrenme Çıktısı  ⌃" : "Öğrenme Çıktısı  ⌄");
                    outcome.requestLayout();
                };
                outcome.setOnClickListener(toggle);
                body.setOnClickListener(toggle);
                outcome.setClickable(true);
                outcome.setFocusable(true);
            }
            outcome.addView(body);
            card.addView(outcome, op);
"""
if old_outcome not in s:
    raise SystemExit("outcome marker not found")
s = s.replace(old_outcome, new_outcome)

old_arrows = """        TextView hint = label("‹  Önceki hafta                         Sonraki hafta  ›", 12, TEXT_SECONDARY, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(13), 0, dp(7));
        outer.addView(hint);
"""
new_arrows = """        LinearLayout weekArrows = new LinearLayout(this);
        weekArrows.setOrientation(LinearLayout.HORIZONTAL);
        weekArrows.setGravity(Gravity.CENTER_VERTICAL);
        weekArrows.setPadding(dp(8), dp(8), dp(8), dp(4));
        TextView previousArrow = label("‹", 30, TEXT_SECONDARY, false);
        previousArrow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        previousArrow.setOnClickListener(v -> previousPage());
        weekArrows.addView(previousArrow, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView nextArrow = label("›", 30, TEXT_SECONDARY, false);
        nextArrow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        nextArrow.setOnClickListener(v -> nextPage());
        weekArrows.addView(nextArrow, new LinearLayout.LayoutParams(0, dp(42), 1f));
        outer.addView(weekArrows, matchWrap());
"""
if old_arrows not in s:
    raise SystemExit("week arrow marker not found")
s = s.replace(old_arrows, new_arrows)

old_persistent = """        card.setPadding(dp(13), dp(9), dp(13), dp(9));
        card.setBackground(roundRect(Color.parseColor("#F4F7F5"), 16, DIVIDER, 1));
        card.addView(label("Kalıcı Not", 14, PRIMARY_DARK, true));
        TextView sub = label("Hafta değişse de burada kalır", 10.5f, TEXT_SECONDARY, false);
        card.addView(sub);
        addSpace(card, 4);

        EditText edit = new EditText(this);
        edit.setHint("Bu ders için kalıcı not…");
        edit.setText(NoteStore.getPersistent(this, course.id));
        edit.setTextSize(13.5f);
"""
new_persistent = """        card.setPadding(dp(13), dp(9), dp(13), dp(9));
        card.setBackground(roundRect(Color.parseColor("#F4F7F5"), 16, DIVIDER, 1));
        card.addView(label("Kalıcı Not", 19, PRIMARY_DARK, true));
        addSpace(card, 5);

        EditText edit = new EditText(this);
        edit.setHint("Bu ders için kalıcı not…");
        edit.setText(NoteStore.getPersistent(this, course.id));
        edit.setTextSize(16.5f);
"""
if old_persistent not in s:
    raise SystemExit("persistent marker not found")
s = s.replace(old_persistent, new_persistent)

old_listener_end = """        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                NoteStore.setPersistent(MainActivity.this, course.id, s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        card.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
"""
new_listener_end = """        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                NoteStore.setPersistent(MainActivity.this, course.id, s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        edit.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                edit.postDelayed(() -> {
                    edit.requestRectangleOnScreen(new android.graphics.Rect(0, 0, edit.getWidth(), edit.getHeight()), true);
                    edit.setSelection(edit.getText().length());
                }, 250);
            }
        });
        card.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
"""
if old_listener_end not in s:
    raise SystemExit("persistent listener marker not found")
s = s.replace(old_listener_end, new_listener_end)

old_time = """    private TextView scheduleTimeField(LinearLayout parent, String title, int minutes) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(10), dp(14), dp(10));
        box.setBackground(roundRect(Color.parseColor("#F0F4F2"), 16, Color.TRANSPARENT, 0));
        box.addView(label(title, 13, TEXT_SECONDARY, false));
        TextView time = label(ScheduleStore.formatMinutes(minutes), 19, TEXT, false);
        time.setTag(minutes);
        time.setPadding(0, dp(5), 0, dp(2));
        time.setOnClickListener(v -> {
            int current = (Integer) time.getTag();
            new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                int m = hourOfDay * 60 + minute;
                time.setTag(m);
                time.setText(ScheduleStore.formatMinutes(m));
            }, current / 60, current % 60, true).show();
        });
        box.addView(time, matchWrap());
        parent.addView(box, matchWrap());
        addSpace(parent, 10);
        return time;
    }
"""
new_time = """    private TextView scheduleTimeField(LinearLayout parent, String title, int minutes) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(10), dp(14), dp(10));
        box.setBackground(roundRect(Color.parseColor("#F0F4F2"), 16, Color.TRANSPARENT, 0));
        box.addView(label(title, 13, TEXT_SECONDARY, false));
        TextView time = label(ScheduleStore.formatMinutes(minutes), 19, TEXT, false);
        time.setTag(minutes);
        time.setPadding(0, dp(5), 0, dp(2));
        time.setOnClickListener(v -> showWheelTimePicker(time));
        box.addView(time, matchWrap());
        parent.addView(box, matchWrap());
        addSpace(parent, 10);
        return time;
    }

    private void showWheelTimePicker(TextView target) {
        int current = (Integer) target.getTag();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(24), dp(10), dp(24), dp(6));

        NumberPicker hour = new NumberPicker(this);
        hour.setMinValue(0);
        hour.setMaxValue(23);
        hour.setValue(current / 60);
        hour.setWrapSelectorWheel(true);
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format(Locale.ROOT, "%02d", i);
        hour.setDisplayedValues(hours);

        NumberPicker minute = new NumberPicker(this);
        minute.setMinValue(0);
        minute.setMaxValue(59);
        minute.setValue(current % 60);
        minute.setWrapSelectorWheel(true);
        String[] minutes = new String[60];
        for (int i = 0; i < 60; i++) minutes[i] = String.format(Locale.ROOT, "%02d", i);
        minute.setDisplayedValues(minutes);

        row.addView(hour, new LinearLayout.LayoutParams(0, dp(180), 1f));
        TextView colon = label(":", 26, TEXT, true);
        colon.setGravity(Gravity.CENTER);
        row.addView(colon, new LinearLayout.LayoutParams(dp(30), dp(180)));
        row.addView(minute, new LinearLayout.LayoutParams(0, dp(180), 1f));

        new AlertDialog.Builder(this)
                .setTitle("Saat seç")
                .setView(row)
                .setNegativeButton("İptal", null)
                .setPositiveButton("Tamam", (dialog, which) -> {
                    int m = hour.getValue() * 60 + minute.getValue();
                    target.setTag(m);
                    target.setText(ScheduleStore.formatMinutes(m));
                })
                .show();
    }
"""
if old_time not in s:
    raise SystemExit("scheduleTimeField marker not found")
s = s.replace(old_time, new_time)

old_schedule_select = """        List<String> names = new ArrayList<>();
        for (Course c : allCourses) names.add(c.grade + ". Sınıf • " + c.name);
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        spinner.setAdapter(adapter);
        if (existing != null) {
            for (int i = 0; i < allCourses.size(); i++) {
                if (allCourses.get(i).id.equals(existing.courseId)) { spinner.setSelection(i); break; }
            }
        }
"""
new_schedule_select = """        List<Course> scheduleCourses = new ArrayList<>();
        for (Course c : allCourses) if (isFavorite(c)) scheduleCourses.add(c);
        for (Course c : allCourses) if (!isFavorite(c)) scheduleCourses.add(c);
        List<String> names = new ArrayList<>();
        for (Course c : scheduleCourses) {
            names.add((isFavorite(c) ? "★  " : "") + c.grade + ". Sınıf • " + c.name);
        }
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        spinner.setAdapter(adapter);
        if (existing != null) {
            for (int i = 0; i < scheduleCourses.size(); i++) {
                if (scheduleCourses.get(i).id.equals(existing.courseId)) { spinner.setSelection(i); break; }
            }
        }
"""
if old_schedule_select not in s:
    raise SystemExit("schedule select marker not found")
s = s.replace(old_schedule_select, new_schedule_select)

s = s.replace("""                    int pos = spinner.getSelectedItemPosition();
                    if (pos < 0 || pos >= allCourses.size()) return;
                    Course c = allCourses.get(pos);
""", """                    int pos = spinner.getSelectedItemPosition();
                    if (pos < 0 || pos >= scheduleCourses.size()) return;
                    Course c = scheduleCourses.get(pos);
""")

s = s.replace('label("v8.1", 12, Color.WHITE, true)', 'label("v8.2", 12, Color.WHITE, true)')
s = s.replace("Sürüm 8.1", "Sürüm 8.2")
MAIN.write_text(s, encoding="utf-8")

m = MANIFEST.read_text(encoding="utf-8")
if 'android:windowSoftInputMode="adjustResize"' not in m:
    m = m.replace('android:screenOrientation="portrait">', 'android:screenOrientation="portrait"\n            android:windowSoftInputMode="adjustResize">')
MANIFEST.write_text(m, encoding="utf-8")

g = GRADLE.read_text(encoding="utf-8")
g = g.replace("versionCode = 9", "versionCode = 10")
g = g.replace('versionName = "8.1"', 'versionName = "8.2"')
GRADLE.write_text(g, encoding="utf-8")

d = json.loads(DATA.read_text(encoding="utf-8"))
for grade in (9, 10):
    gr = next((x for x in d["grades"] if x["grade"] == grade), None)
    if gr is None:
        raise RuntimeError(f"{grade}. sınıf yok")
    eng = next((c for c in gr["courses"] if c.get("subjectId") == "english"), None)
    if eng is None:
        raise RuntimeError(f"İngilizce {grade} kaydı yok")
    weeks = [p for p in eng.get("pages", []) if p.get("type") == "week"]
    if len(weeks) != 37:
        raise RuntimeError(f"İngilizce {grade}: 37 hafta bekleniyordu, {len(weeks)} bulundu")

print("Kazanım v8.2 patch OK")
