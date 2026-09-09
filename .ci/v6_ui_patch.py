# -*- coding: utf-8 -*-
import sys
from pathlib import Path

P=Path(sys.argv[1])
M=P/"app/src/main/java/com/kazanim/app/model/PlanPage.java"
R=P/"app/src/main/java/com/kazanim/app/data/PlanRepository.java"
A=P/"app/src/main/java/com/kazanim/app/MainActivity.java"
G=P/"app/build.gradle.kts"

s=M.read_text(encoding="utf-8")
s=s.replace("    public String special;","    public String special;\n    public String hintsJson;")
s=s.replace("    public boolean isHoliday() {","    public boolean hasHints() { return hintsJson != null && !hintsJson.trim().isEmpty(); }\n\n    public boolean isHoliday() {")
M.write_text(s,encoding="utf-8")

s=R.read_text(encoding="utf-8")
needle='''                    if (p.has("special") && !p.isNull("special")) page.special = p.getString("special");
                    course.pages.add(page);'''
repl='''                    if (p.has("special") && !p.isNull("special")) page.special = p.getString("special");
                    if (p.has("hints") && !p.isNull("hints")) page.hintsJson = p.getJSONObject("hints").toString();
                    course.pages.add(page);'''
if needle not in s: raise SystemExit("repo marker yok")
R.write_text(s.replace(needle,repl),encoding="utf-8")

s=A.read_text(encoding="utf-8")
if "import org.json.JSONArray;" not in s:
    s=s.replace("import com.kazanim.app.model.PlanPage;","import com.kazanim.app.model.PlanPage;\n\nimport org.json.JSONArray;\nimport org.json.JSONObject;")
old='''            card.addView(outcome, op);

            // Bilinçli olarak boş bırakılır; ileride not alanı için kullanılacak.
            Space noteArea = new Space(this);
            card.addView(noteArea, new LinearLayout.LayoutParams(1, dp(145)));'''
new='''            card.addView(outcome, op);

            if (page.hasHints()) {
                card.addView(createHintPanel(page));
                Space noteArea = new Space(this);
                card.addView(noteArea, new LinearLayout.LayoutParams(1, dp(58)));
            } else {
                Space noteArea = new Space(this);
                card.addView(noteArea, new LinearLayout.LayoutParams(1, dp(145)));
            }'''
if old not in s: raise SystemExit("outcome marker yok")
s=s.replace(old,new)

marker="    private View infoTile(String title, String body) {"
methods=r'''    private View createHintPanel(PlanPage page) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.setMargins(dp(14), dp(12), dp(14), 0);
        panel.setLayoutParams(pp);
        panel.setBackground(roundRect(Color.parseColor("#FFF9E8"), 18, Color.parseColor("#E9D79A"), 1));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(12), dp(12), dp(12));
        TextView icon = label("💡", 20, TEXT, false);
        head.addView(icon, new LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.addView(label("İpucu", 16, Color.parseColor("#745A12"), true));
        titleCol.addView(label("2 alternatif • 30 dk • özgün ders fikri", 12, TEXT_SECONDARY, false));
        head.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = label("⌄", 24, Color.parseColor("#745A12"), true);
        arrow.setGravity(Gravity.CENTER);
        head.addView(arrow, new LinearLayout.LayoutParams(dp(38), dp(38)));
        panel.addView(head);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), 0, dp(12), dp(13));
        body.setVisibility(View.GONE);
        panel.addView(body);

        try {
            JSONObject hints = new JSONObject(page.hintsJson);
            JSONArray plans = hints.getJSONArray("plans");
            LinearLayout tabs = new LinearLayout(this);
            tabs.setOrientation(LinearLayout.HORIZONTAL);
            tabs.setPadding(0, dp(5), 0, dp(10));
            body.addView(tabs, matchWrap());

            FrameLayout host = new FrameLayout(this);
            body.addView(host, matchWrap());

            final int[] selected = {0};
            final TextView[] chips = new TextView[plans.length()];

            for (int i = 0; i < plans.length(); i++) {
                final int idx = i;
                TextView chip = label("Plan " + (char)('A' + i), 14,
                        i == 0 ? Color.WHITE : Color.parseColor("#745A12"), true);
                chip.setGravity(Gravity.CENTER);
                chip.setBackground(roundRect(i == 0 ? Color.parseColor("#B88918") : Color.WHITE,
                        14, Color.parseColor("#E5D39B"), 1));
                chip.setOnClickListener(v -> {
                    try {
                        selected[0] = idx;
                        host.removeAllViews();
                        host.addView(buildHintPlanView(plans.getJSONObject(idx)), matchWrap());
                        for (int k = 0; k < chips.length; k++) {
                            boolean active = k == selected[0];
                            chips[k].setTextColor(active ? Color.WHITE : Color.parseColor("#745A12"));
                            chips[k].setBackground(roundRect(active ? Color.parseColor("#B88918") : Color.WHITE,
                                    14, Color.parseColor("#E5D39B"), 1));
                        }
                    } catch (Exception ignored) { }
                });
                chips[i] = chip;
                tabs.addView(chip, new LinearLayout.LayoutParams(0, dp(42), 1f));
                if (i < plans.length() - 1) addHorizontalSpace(tabs, 8);
            }
            host.addView(buildHintPlanView(plans.getJSONObject(0)), matchWrap());
        } catch (Exception e) {
            body.addView(label("İpucu içeriği açılamadı.", 13, TEXT_SECONDARY, false));
        }

        head.setOnClickListener(v -> {
            boolean open = body.getVisibility() == View.VISIBLE;
            body.setVisibility(open ? View.GONE : View.VISIBLE);
            arrow.setText(open ? "⌄" : "⌃");
            if (!open) {
                body.setAlpha(0f);
                body.animate().alpha(1f).setDuration(120).start();
            }
        });
        return panel;
    }

    private View buildHintPlanView(JSONObject plan) throws Exception {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView ptitle = label(plan.optString("title", "Ders planı"), 16, TEXT, true);
        ptitle.setPadding(dp(2), 0, dp(2), dp(8));
        root.addView(ptitle);

        JSONArray blocks = plan.getJSONArray("blocks");
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject block = blocks.getJSONObject(i);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(12), dp(11), dp(12), dp(12));
            box.setBackground(roundRect(Color.WHITE, 15, Color.parseColor("#E9DDB8"), 1));

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView num = label(String.format(Locale.ROOT, "%02d", i + 1), 13, Color.WHITE, true);
            num.setGravity(Gravity.CENTER);
            num.setBackground(roundRect(Color.parseColor("#B88918"), 12, Color.TRANSPARENT, 0));
            top.addView(num, new LinearLayout.LayoutParams(dp(36), dp(30)));
            addHorizontalSpace(top, 9);

            TextView bt = label(block.optString("title", "Bölüm"), 15, TEXT, true);
            bt.setMaxLines(2);
            top.addView(bt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView mins = label(block.optInt("minutes", 10) + " dk", 12, Color.parseColor("#745A12"), true);
            mins.setGravity(Gravity.CENTER);
            mins.setPadding(dp(8), dp(4), dp(8), dp(4));
            mins.setBackground(roundRect(Color.parseColor("#FFF3C8"), 12, Color.TRANSPARENT, 0));
            top.addView(mins);
            box.addView(top);

            addSpace(box, 8);
            TextView desc = label(block.optString("text", ""), 14, TEXT, false);
            desc.setLineSpacing(dp(1), 1.08f);
            box.addView(desc);

            JSONArray qs = block.optJSONArray("questions");
            if (qs != null && qs.length() > 0) {
                addSpace(box, 7);
                for (int q = 0; q < qs.length(); q++) {
                    String txt = qs.optString(q, "");
                    if (txt.trim().isEmpty()) continue;
                    TextView qt = label("• " + txt, 13, TEXT_SECONDARY, false);
                    qt.setPadding(dp(2), dp(2), dp(2), dp(2));
                    box.addView(qt);
                }
            }
            root.addView(box, matchWrap());
            if (i < blocks.length() - 1) addSpace(root, 9);
        }
        return root;
    }

'''
if marker not in s: raise SystemExit("ui marker yok")
s=s.replace(marker,methods+marker)
s=s.replace('TextView badge = label("v5", 13, Color.WHITE, true);','TextView badge = label("v6", 13, Color.WHITE, true);')
s=s.replace("Sürüm 5","Sürüm 6")
s=s.replace("Lise derslerinde haftalık ünite/tema, konu ve öğrenme çıktıları. Temel Dini Bilgiler (İslam 1-2) ve 9. sınıf Sağlık Bilgisi ve Trafik Kültürü de dahildir.",
            "Lise derslerinde haftalık ünite/tema, konu ve öğrenme çıktıları. DKAB 9–11 için her öğretim haftasında iki alternatif 30 dakikalık İpucu ders planı vardır.")
A.write_text(s,encoding="utf-8")

s=G.read_text(encoding="utf-8")
s=s.replace("versionCode = 5","versionCode = 6").replace('versionName = "5"','versionName = "6"')
G.write_text(s,encoding="utf-8")
print("v6 UI patched")
