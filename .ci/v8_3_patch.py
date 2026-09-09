# -*- coding: utf-8 -*-
import sys
from pathlib import Path

P=Path(sys.argv[1]).resolve()
MAIN=P/"app/src/main/java/com/kazanim/app/MainActivity.java"
WIDGET=P/"app/src/main/java/com/kazanim/app/widget/LessonWidgetProvider.java"
WIDGET_XML=P/"app/src/main/res/layout/widget_lesson.xml"
GRADLE=P/"app/build.gradle.kts"

s=MAIN.read_text(encoding="utf-8")

# 1) Kalıcı notu ana ekranda önizleme + klavyenin üstünde açılan popup editör yap.
start=s.index("    private void renderPersistentNotePanel() {")
end=s.index("    private View createHintPanel", start)
new_method=r'''    private void renderPersistentNotePanel() {
        if (persistentNoteHost == null || course == null) return;
        persistentNoteHost.removeAllViews();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(9), dp(13), dp(9));
        card.setBackground(roundRect(Color.parseColor("#F4F7F5"), 16, DIVIDER, 1));
        card.addView(label("Kalıcı Not", 19, PRIMARY_DARK, true));
        addSpace(card, 5);

        TextView preview = label(NoteStore.getPersistent(this, course.id), 17.5f, TEXT, false);
        preview.setHint("Bu ders için kalıcı not…");
        preview.setHintTextColor(Color.parseColor("#96A29C"));
        preview.setGravity(Gravity.TOP | Gravity.START);
        preview.setMinLines(3);
        preview.setMaxLines(5);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        preview.setPadding(dp(10), dp(8), dp(10), dp(8));
        preview.setBackground(roundRect(Color.WHITE, 11, DIVIDER, 1));
        preview.setOnClickListener(v -> showPersistentNoteEditor(preview));
        preview.setClickable(true);
        preview.setFocusable(true);

        card.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        persistentNoteHost.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showPersistentNoteEditor(TextView preview) {
        if (course == null) return;

        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setPadding(dp(18), dp(6), dp(18), dp(8));

        EditText editor = new EditText(this);
        editor.setHint("Bu ders için kalıcı not…");
        editor.setText(NoteStore.getPersistent(this, course.id));
        editor.setTextSize(19f);
        editor.setTextColor(TEXT);
        editor.setHintTextColor(Color.parseColor("#96A29C"));
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setSingleLine(false);
        editor.setMinLines(6);
        editor.setMaxLines(10);
        editor.setVerticalScrollBarEnabled(true);
        editor.setPadding(dp(12), dp(10), dp(12), dp(10));
        editor.setBackground(roundRect(Color.WHITE, 12, DIVIDER, 1));
        editor.setSelection(editor.getText().length());

        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                String value = text == null ? "" : text.toString();
                NoteStore.setPersistent(MainActivity.this, course.id, value);
                preview.setText(value);
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        holder.addView(editor, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Kalıcı Not")
                .setView(holder)
                .setPositiveButton("Kapat", null)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                                | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            editor.requestFocus();
            editor.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
            }, 120);
        });
        dialog.show();
    }

'''
s=s[:start]+new_method+s[end:]

# 2) Alt kısımdaki 1/37 ve tarih durumunu görünmez yap; Bugüne Git kalsın.
old_footer=r'''        counterText = label("", 18, PRIMARY_DARK, true);
        counterText.setGravity(Gravity.CENTER);
        footer.addView(counterText);

        statusText = label("", 13, TEXT_SECONDARY, false);
        statusText.setGravity(Gravity.CENTER);
        footer.addView(statusText);
        addSpace(footer, 7);
'''
new_footer=r'''        counterText = label("", 18, PRIMARY_DARK, true);
        statusText = label("", 13, TEXT_SECONDARY, false);
        counterText.setVisibility(View.GONE);
        statusText.setVisibility(View.GONE);
'''
if old_footer not in s:
    raise SystemExit("footer marker not found")
s=s.replace(old_footer,new_footer,1)

# 3) Hafta değişim animasyonunu ve okları daha belirgin yap.
s=s.replace("page.setAlpha(0.86f);","page.setAlpha(0.68f);",1)
s=s.replace("page.setTranslationX(forward ? dp(26) : -dp(26));","page.setTranslationX(forward ? dp(58) : -dp(58));",1)
s=s.replace("page.animate().alpha(1f).translationX(0f).setDuration(90).start();",
            "page.animate().alpha(1f).translationX(0f).setDuration(150).start();",1)
s=s.replace("if (Math.abs(dx) > dp(40) && Math.abs(dx) > Math.abs(dy) * 1.12f && Math.abs(velocityX) > 110) {",
            "if (Math.abs(dx) > dp(32) && Math.abs(dx) > Math.abs(dy) * 1.08f && Math.abs(velocityX) > 85) {",1)

old_arrows=r'''        LinearLayout weekArrows = new LinearLayout(this);
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
'''
new_arrows=r'''        LinearLayout weekArrows = new LinearLayout(this);
        weekArrows.setOrientation(LinearLayout.HORIZONTAL);
        weekArrows.setGravity(Gravity.CENTER_VERTICAL);
        weekArrows.setPadding(dp(8), dp(10), dp(8), dp(6));

        TextView previousArrow = label("‹", 38, PRIMARY_DARK, true);
        previousArrow.setGravity(Gravity.CENTER);
        previousArrow.setContentDescription("Önceki hafta");
        previousArrow.setBackground(roundRect(PRIMARY_SOFT, 18, PRIMARY, 1));
        previousArrow.setOnClickListener(v -> previousPage());
        weekArrows.addView(previousArrow, new LinearLayout.LayoutParams(dp(58), dp(48)));

        Space arrowSpace = new Space(this);
        weekArrows.addView(arrowSpace, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView nextArrow = label("›", 38, PRIMARY_DARK, true);
        nextArrow.setGravity(Gravity.CENTER);
        nextArrow.setContentDescription("Sonraki hafta");
        nextArrow.setBackground(roundRect(PRIMARY_SOFT, 18, PRIMARY, 1));
        nextArrow.setOnClickListener(v -> nextPage());
        weekArrows.addView(nextArrow, new LinearLayout.LayoutParams(dp(58), dp(48)));

        outer.addView(weekArrows, matchWrap());
'''
if old_arrows not in s:
    raise SystemExit("arrow marker not found")
s=s.replace(old_arrows,new_arrows,1)

s=s.replace('label("v8.2", 12, Color.WHITE, true)','label("v8.3", 12, Color.WHITE, true)')
s=s.replace("Sürüm 8.2","Sürüm 8.3")
MAIN.write_text(s,encoding="utf-8")

# 4) Widget metinlerini büyüt.
x=WIDGET_XML.read_text(encoding="utf-8")
x=x.replace('android:textSize="14sp"','android:textSize="16sp"',1)
x=x.replace('android:textSize="12sp"','android:textSize="14sp"',1)
WIDGET_XML.write_text(x,encoding="utf-8")

# 5) Ders bitişinde widgetı olabildiğince kesin yenile: exact denenir, izin yoksa mevcut güvenli yönteme düşer.
w=WIDGET.read_text(encoding="utf-8")
old_sched=r'''            alarm.cancel(pi);
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ScheduleStore.nextBoundaryMillis(context), pi);
        } catch (Exception ignored) { }
'''
new_sched=r'''            alarm.cancel(pi);
            long when = ScheduleStore.nextBoundaryMillis(context);
            try {
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
                } else {
                    alarm.setExact(AlarmManager.RTC_WAKEUP, when, pi);
                }
            } catch (SecurityException exactNotAllowed) {
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
                } else {
                    alarm.set(AlarmManager.RTC_WAKEUP, when, pi);
                }
            }
        } catch (Exception ignored) { }
'''
if old_sched not in w:
    raise SystemExit("widget schedule marker not found")
w=w.replace(old_sched,new_sched,1)
WIDGET.write_text(w,encoding="utf-8")

g=GRADLE.read_text(encoding="utf-8")
g=g.replace("versionCode = 10","versionCode = 11")
g=g.replace('versionName = "8.2"','versionName = "8.3"')
GRADLE.write_text(g,encoding="utf-8")

print("Kazanım v8.3 patch OK")
