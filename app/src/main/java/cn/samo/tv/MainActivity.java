package cn.samo.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** A small offline TV home screen, controlled with a standard directional remote. */
public final class MainActivity extends Activity {
    private static final int WHITE = Color.rgb(243,246,252);
    private static final int MUTED = Color.rgb(163,175,191);
    private static final int HOME = 0, APPS = 1, SETTINGS = 2, APP_COLUMNS = 5;
    private final List<AppEntry> apps = new ArrayList<>();
    private final List<String> pinned = new ArrayList<>();
    private final Handler clockHandler = new Handler();
    private final TextView[] tabs = new TextView[3];
    private FrameLayout page;
    private TextView clock, description, adapterStatus, heroTitle, heroSubtitle, heroCategory;
    private ImageView heroArtwork;
    private final Drawable[] navIcons = new Drawable[3];
    private TvDialog activeSheet;
    private View adapterSettingRow;
    private View homeBrowse, lastShelfFocus;
    private AmbientView ambient;
    private final List<View> pageCards = new ArrayList<>();
    private SharedPreferences prefs;
    private int selectedTab = HOME;
    private float scale = 1f;
    private boolean resumed;
    private boolean built;
    private View preferredFocus;
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            if (clock != null) clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            clockHandler.postDelayed(this, 1000L * (60 - (System.currentTimeMillis() / 1000L) % 60));
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        float widthDp = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        float heightDp = getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density;
        scale = Math.min(widthDp / 960f, heightDp / 540f);
        prefs = getSharedPreferences("samo_home", MODE_PRIVATE);
        selectedTab = state == null ? HOME : state.getInt("tab", HOME);
        refreshApps();
        createChrome();
        built = true;
        showPage(selectedTab, false);
    }

    @Override protected void onResume() {
        super.onResume();
        HomeAdapter.acknowledgeResume(this);
        resumed = true;
        clockHandler.removeCallbacks(clockTick);
        clockTick.run();
        if (built) {
            if (refreshApps()) showPage(selectedTab, false);
            else if (selectedTab==SETTINGS && adapterStatus!=null) adapterStatus.setText(homeAdapterStatus());
        }
    }

    @Override protected void onPause() {
        resumed = false;
        dismissSheet();
        clockHandler.removeCallbacks(clockTick);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("tab", selectedTab);
        super.onSaveInstanceState(out);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_HOME)) { dismissSheet(); showPage(HOME, false); }
    }

    @Override protected void onDestroy() {
        dismissSheet();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (activeSheet!=null) { dismissSheet(); return; }
        if (selectedTab != HOME) showPage(HOME, false);
        else if (preferredFocus != null) preferredFocus.requestFocus();
        else tabs[APPS].requestFocus();
    }

    private int d(float value) { return Math.round(value * scale * getResources().getDisplayMetrics().density); }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size * scale); view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color); result.setCornerRadius(d(radius)); return result;
    }

    private void createChrome() {
        FrameLayout root=new FrameLayout(this);
        ambient=new AmbientView(this);root.addView(ambient,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(d(44),d(20),d(44),d(20));content.setClipChildren(false);content.setClipToPadding(false);
        root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout top=new FrameLayout(this);top.setClipChildren(false);content.addView(top,new LinearLayout.LayoutParams(-1,d(48)));
        LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.ic_brand_mark);brand.addView(mark,new LinearLayout.LayoutParams(d(26),d(26)));
        TextView wordmark=text("Samo",16,0xE6F5F6F7);wordmark.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        LinearLayout.LayoutParams wordLp=new LinearLayout.LayoutParams(-2,-2);wordLp.leftMargin=d(8);brand.addView(wordmark,wordLp);
        top.addView(brand,new FrameLayout.LayoutParams(-2,-1,Gravity.LEFT|Gravity.CENTER_VERTICAL));
        LinearLayout navigation=new LinearLayout(this);navigation.setGravity(Gravity.CENTER_VERTICAL);navigation.setPadding(d(5),d(5),d(5),d(5));
        GradientDrawable glass=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x9C364153,0xA31A2330});glass.setCornerRadius(d(26));glass.setStroke(Math.max(1,d(.6f)),0x39FFFFFF);navigation.setBackground(glass);
        top.addView(navigation,new FrameLayout.LayoutParams(-2,d(46),Gravity.CENTER));
        int[] icons={R.drawable.ic_home,R.drawable.ic_grid,R.drawable.ic_settings};String[] names={"首页","应用","设置"};
        for(int i=0;i<3;i++) {
            final int index=i;TextView control=text(names[i],12,WHITE);control.setContentDescription(i==APPS?"全部应用":names[i]);control.setGravity(Gravity.CENTER);
            control.setFocusable(true);control.setClickable(true);control.setId(View.generateViewId());
            Drawable icon=getDrawable(icons[i]).mutate();icon.setBounds(0,0,d(16),d(16));navIcons[i]=icon;
            control.setCompoundDrawables(icon,null,null,null);control.setCompoundDrawablePadding(d(6));control.setPadding(d(12),0,d(12),0);tabs[i]=control;
            navigation.addView(control,new LinearLayout.LayoutParams(d(80),d(36)));
            control.setOnClickListener(v -> showPage(index,false));control.setOnFocusChangeListener((v,focused) -> styleTab(index,focused));
            control.setOnKeyListener((v,key,event) -> {
                if(key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
                if(event.getAction()==KeyEvent.ACTION_DOWN) {if(selectedTab!=index)showPage(index,false);else if(preferredFocus!=null)preferredFocus.requestFocus();}
                return true;
            });
        }
        for(int i=0;i<3;i++){tabs[i].setNextFocusLeftId(tabs[Math.max(0,i-1)].getId());tabs[i].setNextFocusRightId(tabs[Math.min(2,i+1)].getId());}
        clock=text("",15,0xE6F5F6F7);clock.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);clock.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        top.addView(clock,new FrameLayout.LayoutParams(d(76),-1,Gravity.RIGHT|Gravity.CENTER_VERTICAL));
        page=new FrameLayout(this);page.setClipChildren(false);page.setClipToPadding(false);
        LinearLayout.LayoutParams pageLp=new LinearLayout.LayoutParams(-1,0,1);pageLp.topMargin=d(12);content.addView(page,pageLp);
        setContentView(root);
    }

    private void styleTab(int index,boolean focused) {
        boolean selected=selectedTab==index;
        tabs[index].setBackground(focused?rounded(0xF5F5F6F8,18):(selected?rounded(0x28FFFFFF,18):null));
        tabs[index].setTextColor(focused?0xFF192333:(selected?WHITE:0xB9F0F3F8));
        navIcons[index].setTint(focused?0xFF192333:(selected?WHITE:0xB9F0F3F8));
    }

    private void showPage(int index,boolean keepControlFocus) {
        selectedTab=index;preferredFocus=null;page.removeAllViews();pageCards.clear();description=null;adapterStatus=null;adapterSettingRow=null;homeBrowse=null;lastShelfFocus=null;heroTitle=null;heroSubtitle=null;heroCategory=null;heroArtwork=null;
        ambient.setDimmed(index!=HOME);
        if(index!=HOME)ambient.setAccent(0xFF8799BB);
        for(int i=0;i<3;i++)styleTab(i,tabs[i].hasFocus());
        if(index==HOME)buildHome();else if(index==APPS)buildApps();else buildSettings();
        if(keepControlFocus && tabs[index].getVisibility()==View.VISIBLE)tabs[index].requestFocus();
        else if(preferredFocus!=null)preferredFocus.requestFocus();
        else tabs[APPS].requestFocus();
    }

    private LinearLayout column() {
        LinearLayout result=new LinearLayout(this);result.setOrientation(LinearLayout.VERTICAL);result.setClipChildren(false);result.setClipToPadding(false);
        page.addView(result,new FrameLayout.LayoutParams(-1,-1));return result;
    }

    private void buildHome() {
        LinearLayout layout=column();
        List<AppEntry> favorites=new ArrayList<>();
        for(String pkg:pinned)for(AppEntry app:apps)if(app.packageName.equals(pkg)){favorites.add(app);break;}
        LinearLayout hero=new LinearLayout(this);hero.setGravity(Gravity.CENTER_VERTICAL);hero.setClipChildren(false);
        layout.addView(hero,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout story=new LinearLayout(this);story.setOrientation(LinearLayout.VERTICAL);story.setGravity(Gravity.CENTER_VERTICAL);story.setPadding(0,0,d(36),0);
        hero.addView(story,new LinearLayout.LayoutParams(0,-1,1));
        heroCategory=text("常用应用",13,0xBDDFE7F1);story.addView(heroCategory,new LinearLayout.LayoutParams(-1,-2));
        heroTitle=text("从喜欢的应用开始",36,WHITE);heroTitle.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));heroTitle.setMaxLines(2);heroTitle.setEllipsize(TextUtils.TruncateAt.END);heroTitle.setLineSpacing(d(2),1);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);titleLp.topMargin=d(13);story.addView(heroTitle,titleLp);
        heroSubtitle=text("把常用应用留在首页，轻松开始观看。",14,0xC9E1E7EF);heroSubtitle.setMaxLines(2);heroSubtitle.setLineSpacing(d(5),1);
        LinearLayout.LayoutParams subtitleLp=new LinearLayout.LayoutParams(-1,-2);subtitleLp.topMargin=d(15);story.addView(heroSubtitle,subtitleLp);
        TextView hint=text("按确认键打开   ·   长按管理",12,0xA5D9E1EB);LinearLayout.LayoutParams hintLp=new LinearLayout.LayoutParams(-1,-2);hintLp.topMargin=d(22);story.addView(hint,hintLp);
        FrameLayout artworkArea=new FrameLayout(this);artworkArea.setClipChildren(false);hero.addView(artworkArea,new LinearLayout.LayoutParams(d(340),-1));
        FrameLayout display=new FrameLayout(this);display.setBackground(rounded(0xFF17202C,28));display.setClipToOutline(true);display.setElevation(d(12));
        FrameLayout.LayoutParams displayLp=new FrameLayout.LayoutParams(d(280),d(194),Gravity.RIGHT|Gravity.CENTER_VERTICAL);displayLp.rightMargin=d(8);artworkArea.addView(display,displayLp);
        heroArtwork=new ImageView(this);heroArtwork.setScaleType(ImageView.ScaleType.FIT_XY);display.addView(heroArtwork,new FrameLayout.LayoutParams(-1,-1));
        display.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);display.setFocusable(false);
        TextView section=text("常用应用",13,0xCBEBF0F6);LinearLayout.LayoutParams sectionLp=new LinearLayout.LayoutParams(-1,-2);sectionLp.bottomMargin=d(3);layout.addView(section,sectionLp);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setClipChildren(false);scroll.setClipToPadding(false);scroll.setHorizontalScrollBarEnabled(false);scroll.setFillViewport(true);
        LinearLayout shelf=new LinearLayout(this);shelf.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);shelf.setClipChildren(false);shelf.setClipToPadding(false);shelf.setPadding(d(12),d(12),d(12),d(4));
        scroll.addView(shelf,new FrameLayout.LayoutParams(-2,-1));
        LinearLayout.LayoutParams scrollLp=new LinearLayout.LayoutParams(-1,d(140));scrollLp.leftMargin=-d(12);scrollLp.rightMargin=-d(12);layout.addView(scroll,scrollLp);
        if(favorites.isEmpty()) {TextView empty=text("在全部应用中长按确认键，添加常用应用",14,MUTED);empty.setGravity(Gravity.CENTER_VERTICAL);shelf.addView(empty,new LinearLayout.LayoutParams(-1,-1));heroArtwork.setScaleType(ImageView.ScaleType.FIT_CENTER);heroArtwork.setPadding(d(96),d(53),d(96),d(53));heroArtwork.setImageResource(R.drawable.ic_brand_mark);}
        for(int i=0;i<favorites.size();i++) {
            View card=appTile(favorites.get(i),true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(d(164),d(124));if(i<favorites.size()-1)lp.rightMargin=d(18);shelf.addView(card,lp);if(preferredFocus==null)preferredFocus=card;
        }
        wireCards(true,1);
        if(!favorites.isEmpty())updateHero(favorites.get(0));
        TextView browse=text("全部应用  ﹀",12,0xBFE1E7F1);browse.setGravity(Gravity.CENTER);browse.setFocusable(true);browse.setClickable(true);browse.setId(View.generateViewId());
        browse.setContentDescription("全部应用");browse.setOnClickListener(v -> showPage(APPS,false));
        browse.setOnFocusChangeListener((v,focused) -> {browse.setBackground(focused?rounded(0xEAF2F5FC,18):null);browse.setTextColor(focused?0xFF202A3B:0xBFE1E7F1);});
        browse.setOnKeyListener((v,key,event) -> {
            if(key==KeyEvent.KEYCODE_DPAD_UP){if(event.getAction()==KeyEvent.ACTION_DOWN){if(lastShelfFocus!=null)lastShelfFocus.requestFocus();else if(!pageCards.isEmpty())pageCards.get(0).requestFocus();else tabs[APPS].requestFocus();}return true;}
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN)showPage(APPS,false);return true;}return false;
        });
        LinearLayout.LayoutParams browseLp=new LinearLayout.LayoutParams(d(126),d(32));browseLp.gravity=Gravity.CENTER_HORIZONTAL;browseLp.topMargin=d(2);layout.addView(browse,browseLp);homeBrowse=browse;if(preferredFocus==null)preferredFocus=browse;
    }

    private void updateHero(AppEntry app) {
        if(heroTitle==null)return;
        heroTitle.setText(app.label);heroTitle.setTextSize((app.label.length()>12?30:36)*scale);
        String category="常用应用";String detail="你的常用应用，随时打开。";
        if(app.packageName.equals("com.ktcp.video")){category="影音娱乐";detail="电影、剧集与综艺，在大屏上继续发现。";}
        else if(app.packageName.equals("com.ugreen.nasprotv")){category="私人媒体库";detail="打开私人媒体库，重温你的影像与收藏。";}
        else if(app.packageName.equals("com.oriontv")){category="频道与直播";detail="进入喜爱的频道，开始观看。";}
        heroCategory.setText(category);heroSubtitle.setText(detail);heroArtwork.setImageDrawable(copyDrawable(app.filledIcon));
    }

    private Drawable copyDrawable(Drawable source) {
        if(source==null)return null;
        Drawable.ConstantState state=source.getConstantState();return state==null?source:state.newDrawable(getResources()).mutate();
    }

    private void buildApps() {
        LinearLayout layout=column();
        TextView heading=text("全部应用",28,WHITE);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        LinearLayout.LayoutParams headingLp=new LinearLayout.LayoutParams(-1,-2);headingLp.bottomMargin=d(20);layout.addView(heading,headingLp);
        ScrollView scroll=new ScrollView(this);scroll.setClipChildren(true);scroll.setClipToPadding(true);scroll.setOutlineProvider(ViewOutlineProvider.BOUNDS);scroll.setClipToOutline(true);scroll.setVerticalScrollBarEnabled(false);layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);grid.setClipChildren(false);grid.setClipToPadding(false);grid.setPadding(d(12),d(14),d(12),d(20));
        scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
        if(apps.isEmpty())grid.addView(text("没有找到可打开的应用",16,MUTED));
        LinearLayout row=null;
        for(int i=0;i<apps.size();i++) {
            if(i%APP_COLUMNS==0){row=new LinearLayout(this);row.setClipChildren(false);row.setClipToPadding(false);grid.addView(row,new LinearLayout.LayoutParams(-1,d(128)));}
            View card=appTile(apps.get(i),false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,d(118),1);if(i%APP_COLUMNS<APP_COLUMNS-1)lp.rightMargin=d(20);row.addView(card,lp);if(i==0)preferredFocus=card;
        }
        if(row!=null && apps.size()%APP_COLUMNS!=0)for(int i=apps.size()%APP_COLUMNS;i<APP_COLUMNS;i++) {
            View spacer=new View(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,d(118),1);if(i<APP_COLUMNS-1)lp.rightMargin=d(20);row.addView(spacer,lp);
        }
        wireCards(false,APP_COLUMNS);
    }

    private View appTile(final AppEntry app,boolean home) {
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER_HORIZONTAL);card.setFocusable(true);card.setClickable(true);card.setLongClickable(true);
        card.setClipChildren(false);card.setClipToPadding(false);card.setPadding(d(4),d(4),d(4),d(4));card.setId(View.generateViewId());pageCards.add(card);final int cardIndex=pageCards.size()-1;
        card.setContentDescription(app.label+(pinned.contains(app.packageName)?"，常用应用":""));
        FrameLayout artwork=new FrameLayout(this);artwork.setBackground(rounded(app.filledIcon.getEdgeColor(),home?14:12));artwork.setClipToOutline(true);
        card.addView(artwork,new LinearLayout.LayoutParams(-1,d(home?86:79)));
        ImageView image=new ImageView(this);image.setImageDrawable(copyDrawable(app.filledIcon));image.setScaleType(ImageView.ScaleType.FIT_XY);
        artwork.addView(image,new FrameLayout.LayoutParams(-1,-1));
        View sheen=new View(this);GradientDrawable gloss=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x1FFFFFFF,0x00FFFFFF,0x08000000});artwork.addView(sheen,new FrameLayout.LayoutParams(-1,-1));sheen.setBackground(gloss);
        TextView label=text(app.label,home?11:11,WHITE);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);label.setGravity(Gravity.CENTER);label.setAlpha(.72f);
        LinearLayout.LayoutParams labelLp=new LinearLayout.LayoutParams(-1,d(home?20:24));labelLp.topMargin=d(home?6:7);card.addView(label,labelLp);
        card.setOnFocusChangeListener((v,focused) -> {
            artwork.setScaleX(focused?1.08f:1f);artwork.setScaleY(focused?1.08f:1f);artwork.setTranslationY(focused?-d(3):0);artwork.setElevation(focused?d(22):d(2));
            label.setAlpha(focused?1f:.72f);sheen.setAlpha(focused?1f:.25f);if(focused){ambient.setAccent(app.accent);if(home){lastShelfFocus=card;updateHero(app);}}
        });
        card.setOnClickListener(v -> launchApp(app));card.setOnLongClickListener(v -> {showAppMenu(app);return true;});
        card.setOnKeyListener((v,key,event) -> {
            if(key==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP)showAppMenu(app);return true;}
            if(home && key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN){if(homeBrowse!=null)homeBrowse.requestFocus();else showPage(APPS,false);}return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP && (home || cardIndex<APP_COLUMNS)){if(event.getAction()==KeyEvent.ACTION_DOWN)tabs[APPS].requestFocus();return true;}
            return false;
        });
        return card;
    }

    private void wireCards(boolean home,int columns) {
        for(int i=0;i<pageCards.size();i++) {
            View card=pageCards.get(i);
            card.setNextFocusLeftId(pageCards.get(i==0 || (!home && i%columns==0)?i:i-1).getId());
            card.setNextFocusRightId(pageCards.get(i==pageCards.size()-1 || (!home && i%columns==columns-1)?i:i+1).getId());
            card.setNextFocusUpId(home || i<columns?tabs[APPS].getId():pageCards.get(i-columns).getId());
            if(!home)card.setNextFocusDownId(pageCards.get(i/columns==(pageCards.size()-1)/columns?i:Math.min(i+columns,pageCards.size()-1)).getId());
        }
    }

    private void dismissSheet() {
        TvDialog sheet=activeSheet;activeSheet=null;setSheetBackdropBlur(false);if(sheet!=null)sheet.dismiss();
    }

    private void setSheetBackdropBlur(boolean enabled) {
        if(Build.VERSION.SDK_INT<31)return;
        try { getWindow().getDecorView().setRenderEffect(enabled?RenderEffect.createBlurEffect(d(10),d(10),Shader.TileMode.CLAMP):null); }
        catch(RuntimeException unsupportedBlur) { /* The opaque sheet remains readable on older renderers. */ }
    }

    private void showSheet(String heading,String message,TvDialog.Action... actions) {
        dismissSheet();final View anchor=getCurrentFocus();final TvDialog sheet=new TvDialog(this,scale,heading,message,actions);activeSheet=sheet;
        sheet.setOnDismissListener(dialog -> {
            if(activeSheet==sheet)activeSheet=null;
            if(activeSheet==null)setSheetBackdropBlur(false);
            if(!isFinishing() && anchor!=null && anchor.isAttachedToWindow() && anchor.hasWindowFocus())anchor.requestFocus();
        });
        sheet.show();setSheetBackdropBlur(true);
    }

    private void showAppMenu(final AppEntry app) {
        final boolean isPinned=pinned.contains(app.packageName);
        showSheet(app.label,null,
            new TvDialog.Action(isPinned?"移出常用应用":"添加到常用应用",() -> {
                if(pinned.contains(app.packageName))pinned.remove(app.packageName);else pinned.add(app.packageName);
                savePins();showPage(selectedTab,false);
            },isPinned),
            new TvDialog.Action("应用信息",() -> openSetting(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.packageName)),"应用信息")),
            new TvDialog.Action("取消",null));
    }

    private void buildSettings() {
        LinearLayout layout=new LinearLayout(this);layout.setGravity(Gravity.CENTER_VERTICAL);page.addView(layout,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout intro=new LinearLayout(this);intro.setOrientation(LinearLayout.VERTICAL);intro.setGravity(Gravity.CENTER_HORIZONTAL);intro.setPadding(0,0,d(40),0);layout.addView(intro,new LinearLayout.LayoutParams(d(290),-2));
        ImageView emblem=new ImageView(this);emblem.setImageResource(R.drawable.ic_brand_mark);intro.addView(emblem,new LinearLayout.LayoutParams(d(58),d(58)));
        TextView heading=text("设置",36,WHITE);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));LinearLayout.LayoutParams headingLp=new LinearLayout.LayoutParams(-1,-2);headingLp.topMargin=d(18);heading.setGravity(Gravity.CENTER);intro.addView(heading,headingLp);
        description=text("调整画面、声音和电视的其他设置",14,MUTED);description.setGravity(Gravity.CENTER);description.setLineSpacing(d(5),1);LinearLayout.LayoutParams descriptionLp=new LinearLayout.LayoutParams(-1,d(66));descriptionLp.topMargin=d(16);intro.addView(description,descriptionLp);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(d(6),d(6),d(6),d(6));list.setBackground(null);layout.addView(list,new LinearLayout.LayoutParams(0,-2,1));
        List<View> rows=new ArrayList<>();
        rows.add(settingRow("画面、声音与设备","调整显示、音效、蓝牙及其他电视选项",null,() -> openTvSettings()));
        rows.add(settingRow("网络","连接 Wi-Fi，或检查电视的网络连接",null,() -> openSetting(new Intent(Settings.ACTION_WIFI_SETTINGS),"网络")));
        rows.add(settingRow("应用管理","查看已安装的应用，管理存储与权限",null,() -> openSetting(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),"应用管理")));
        rows.add(settingRow("默认启动器","打开系统提供的桌面选择入口",null,() -> openSetting(new Intent(Settings.ACTION_HOME_SETTINGS),"默认启动器")));
        LinearLayout adapterRow=settingRow("主页键接管","开启后，按主页键回到 Samo。暂停后使用电视原桌面。",homeAdapterStatus(),() -> showHomeAdapterSettings());adapterSettingRow=adapterRow;adapterStatus=(TextView)adapterRow.getChildAt(1);rows.add(adapterRow);
        for(int i=0;i<rows.size();i++) {
            if(i>0){View line=new View(this);line.setBackgroundColor(0x1CFFFFFF);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Math.max(1,d(.5f)));lp.setMargins(d(18),0,d(18),0);list.addView(line,lp);}
            View row=rows.get(i);row.setId(View.generateViewId());list.addView(row,new LinearLayout.LayoutParams(-1,d(52)));
        }
        for(int i=0;i<rows.size();i++) {
            final int position=i;View row=rows.get(i);row.setNextFocusLeftId(row.getId());row.setNextFocusRightId(row.getId());
            row.setNextFocusUpId(i==0?tabs[SETTINGS].getId():rows.get(i-1).getId());row.setNextFocusDownId(rows.get(Math.min(i+1,rows.size()-1)).getId());
            row.setOnKeyListener((v,key,event) -> {
                if(key==KeyEvent.KEYCODE_DPAD_UP && position==0){if(event.getAction()==KeyEvent.ACTION_DOWN)tabs[SETTINGS].requestFocus();return true;}
                return false;
            });
        }
        preferredFocus=rows.get(0);
    }

    private String homeAdapterStatus() {
        if(!HomeAdapter.isRequested(this))return "已暂停  ›";
        return HomeAdapter.isServiceEnabled(this)?"已开启  ›":"待授权  ›";
    }

    private void showHomeAdapterSettings() {
        final boolean enabled=HomeAdapter.isRequested(this);
        showSheet("主页键接管",(enabled?"当前已开启。":"当前已暂停。")+"仅识别电视原桌面的窗口名称，不读取屏幕内容、不接收按键、不执行手势。",
            new TvDialog.Action(enabled?"暂停接管":"开启接管",() -> {
                HomeAdapter.setRequested(this,!HomeAdapter.isRequested(this));showPage(SETTINGS,false);if(adapterSettingRow!=null)adapterSettingRow.requestFocus();
            },enabled),
            new TvDialog.Action("系统无障碍设置",() -> openSetting(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),"无障碍设置")),
            new TvDialog.Action("取消",null));
    }

    private LinearLayout settingRow(String label,String detail,String value,Runnable action) {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(d(18),0,d(18),0);row.setFocusable(true);row.setClickable(true);
        TextView name=text(label,17,WHITE);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=text(value==null?"›":value,value==null?22:14,0xBFCED7E5);status.setGravity(Gravity.RIGHT);row.addView(status,new LinearLayout.LayoutParams(-2,-2));
        row.setOnClickListener(v -> action.run());row.setOnFocusChangeListener((v,focused) -> {
            row.setBackground(focused?rounded(0xF2F2F5FA,11):null);row.setElevation(focused?d(3):0);
            name.setTextColor(focused?0xFF202A3B:WHITE);status.setTextColor(focused?0xFF596478:0xBFCED7E5);
            if(focused && description!=null)description.setText(detail);
        });return row;
    }

    private void openTvSettings() {
        try {
            Intent nativeSettings=getPackageManager().getLaunchIntentForPackage("com.xiaomi.mitv.settings");
            if(nativeSettings!=null) {
                nativeSettings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(nativeSettings);
                return;
            }
        } catch(RuntimeException unavailable) {
            // The standard Android settings entry remains the fallback.
        }
        openSetting(new Intent(Settings.ACTION_SETTINGS),"电视设置");
    }

    private void openSetting(Intent intent,String label) {
        try { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent); }
        catch(ActivityNotFoundException|SecurityException ex) {
            if(!Settings.ACTION_SETTINGS.equals(intent.getAction())) {
                Toast.makeText(this,"请在电视设置中打开「"+label+"」",Toast.LENGTH_LONG).show();
                openSetting(new Intent(Settings.ACTION_SETTINGS),"电视设置");
            } else showSheet("无法打开电视设置","请使用电视遥控器的设置键打开。",new TvDialog.Action("好",null));
        }
    }

    private void launchApp(AppEntry app) {
        try {
            Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(app.leanback?Intent.CATEGORY_LEANBACK_LAUNCHER:Intent.CATEGORY_LAUNCHER);
            launch.setComponent(app.component); launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
        } catch(ActivityNotFoundException|SecurityException ex) {
            showSheet("无法打开 "+app.label,"应用可能已停用或被系统限制。可进入设置中的应用管理检查。",new TvDialog.Action("好",null));
            if(refreshApps()) showPage(selectedTab,false);
        }
    }

    private boolean refreshApps() {
        String old=appSignature();
        LinkedHashMap<String,AppEntry> found=new LinkedHashMap<>();
        collectApps(found,Intent.CATEGORY_LEANBACK_LAUNCHER,true);
        collectApps(found,Intent.CATEGORY_LAUNCHER,false);
        apps.clear(); apps.addAll(found.values());
        final Collator collator=Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        Collections.sort(apps,(a,b) -> collator.compare(a.label,b.label));
        pinned.clear();
        String saved=prefs.getString("favorites",null);
        if(saved==null) {
            List<AppEntry> defaults=new ArrayList<>(apps);
            Collections.sort(defaults,(a,b) -> Integer.compare(favoriteRank(a),favoriteRank(b)));
            for(AppEntry app:defaults) {
                if(favoriteRank(app)<200 && pinned.size()<6) pinned.add(app.packageName);
            }
            savePins();
        } else {
            for(String pkg:saved.split(",")) if(found.containsKey(pkg) && !pinned.contains(pkg))pinned.add(pkg);
            if(!saved.equals(TextUtils.join(",",pinned)))savePins();
        }
        return !old.equals(appSignature());
    }

    private void collectApps(LinkedHashMap<String,AppEntry> found,String category,boolean leanback) {
        PackageManager pm=getPackageManager();
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> resolved=pm.queryIntentActivities(query,0);
        for(ResolveInfo info:resolved) {
            if(info.activityInfo==null || !info.activityInfo.enabled || !info.activityInfo.exported || !info.activityInfo.applicationInfo.enabled)continue;
            String pkg=info.activityInfo.packageName;
            // System settings live behind Samo's single Settings tab, not duplicate app cards.
            if(getPackageName().equals(pkg) || isSystemSettingsPackage(pkg) || found.containsKey(pkg))continue;
            AppEntry app=new AppEntry(); app.packageName=pkg; app.component=new ComponentName(pkg,info.activityInfo.name); app.leanback=leanback;
            app.systemApp=(info.activityInfo.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
            try { CharSequence label=info.loadLabel(pm); app.label=label==null?pkg:label.toString(); app.icon=info.loadIcon(pm); }
            catch(RuntimeException ignored) { app.label=pkg; app.icon=pm.getDefaultActivityIcon(); }
            app.iconSource=info.getIconResource()+"|"+info.activityInfo.applicationInfo.sourceDir;
            for(AppEntry previous:apps)if(pkg.equals(previous.packageName) && app.iconSource.equals(previous.iconSource)){app.filledIcon=previous.filledIcon;break;}
            if(app.filledIcon==null) {
                try {app.filledIcon=AppIconDrawable.create(app.icon);}
                catch(RuntimeException invalidArtwork){app.filledIcon=AppIconDrawable.create(pm.getDefaultActivityIcon());}
            }
            app.accent=sampleAccent(app.icon);
            found.put(pkg,app);
        }
    }

    private static boolean isSystemSettingsPackage(String packageName) {
        return "com.xiaomi.mitv.settings".equals(packageName)
            || "com.android.tv.settings".equals(packageName)
            || "com.android.settings".equals(packageName);
    }

    /** Samples local app artwork once on discovery; never downloads or reads app data. */
    private int sampleAccent(Drawable artwork) {
        if(artwork==null)return 0xFF58809F;
        Bitmap sample=Bitmap.createBitmap(12,12,Bitmap.Config.ARGB_8888);Rect bounds=new Rect(artwork.getBounds());
        try {
            artwork.setBounds(0,0,12,12);artwork.draw(new Canvas(sample));
            float[] red=new float[12],green=new float[12],blue=new float[12],weight=new float[12],hsv=new float[3];
            for(int y=0;y<12;y++)for(int x=0;x<12;x++) {
                int color=sample.getPixel(x,y);Color.colorToHSV(color,hsv);
                float w=Color.alpha(color)/255f*hsv[1]*hsv[1]*hsv[2];
                int bucket=Math.min(11,(int)(hsv[0]/30));
                red[bucket]+=Color.red(color)*w;green[bucket]+=Color.green(color)*w;blue[bucket]+=Color.blue(color)*w;weight[bucket]+=w;
            }
            int dominant=0;for(int i=1;i<12;i++)if(weight[i]>weight[dominant])dominant=i;
            float total=weight[dominant];if(total<.15f)return 0xFF58809F;
            Color.colorToHSV(Color.rgb(Math.round(red[dominant]/total),Math.round(green[dominant]/total),Math.round(blue[dominant]/total)),hsv);
            hsv[1]=Math.min(.72f,Math.max(.35f,hsv[1]));hsv[2]=Math.min(.85f,Math.max(.5f,hsv[2]));return Color.HSVToColor(hsv);
        } catch(RuntimeException unsupportedArtwork) { return 0xFF58809F; }
        finally { artwork.setBounds(bounds);sample.recycle(); }
    }

    private String appSignature() {
        StringBuilder value=new StringBuilder();
        for(AppEntry app:apps)value.append(app.packageName).append('/').append(app.component).append('/').append(app.label).append('/').append(app.iconSource).append(';');
        value.append('|').append(TextUtils.join(",",pinned)); return value.toString();
    }

    private void savePins() { prefs.edit().putString("favorites",TextUtils.join(",",pinned)).apply(); }

    /** Only used when no saved favorites exist. Never changes the user's chosen list. */
    private int favoriteRank(AppEntry app) {
        String key=app.packageName.toLowerCase(Locale.ROOT);
        String[] excluded={"videodaily","videocall","launcher","tvhome","settings","tvmanager","appstore","market","installer","service","daemon","updater","security","adcenter","advert","recommend","promotion"};
        for(String value:excluded)if(key.contains(value))return 200;
        String[] preferred={"com.oriontv","com.ugreen.nasprotv","com.ktcp.video"};
        for(int i=0;i<preferred.length;i++)if(key.equals(preferred[i]))return i;
        String[] media={"netflix","youtube","bilibili","danmaku","xiaodianshi","qiyi","iqiyi","youku","tencent.qqlive","mgtv","hunantv","cctv","yangshipin","plex","emby","jellyfin","kodi","xbmc","vlc","douyu","huya","spotify","disney"};
        for(int i=0;i<media.length;i++)if(key.contains(media[i]))return 10+i;
        // A generic "video" match also catches news feeds and video calling.
        // Prefer explicit media identities, then other user-installed apps.
        if(!app.systemApp && key.contains("player"))return 70;
        return app.systemApp?200:100;
    }

    private static final class AppEntry {
        String packageName,label,iconSource; ComponentName component; Drawable icon; AppIconDrawable filledIcon; int accent; boolean leanback,systemApp;
    }
}
