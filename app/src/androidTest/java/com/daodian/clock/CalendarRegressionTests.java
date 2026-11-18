package com.daodian.clock;

import android.test.InstrumentationTestCase;
import android.graphics.*;
import com.daodian.clock.core.CalendarGrid;
import java.time.LocalDate;
import org.json.*;

public class CalendarRegressionTests extends InstrumentationTestCase {
    private Bitmap asset(String name)throws Exception {try(java.io.InputStream in=getInstrumentation().getContext().getAssets().open(name)){return BitmapFactory.decodeStream(in);}}
    private String read(Bitmap image,CalendarGrid.Layout layout){String[] out=new String[1];CalendarReader reader=new CalendarReader();reader.readGrid(image,layout,(date,reason)->out[0]=date);reader.close();return out[0];}
    public void testAllRealFullMonthFixtures()throws Exception {
        JSONArray fixtures;
        try(java.io.InputStream in=getInstrumentation().getContext().getAssets().open("calendar-grid/fixtures.json")){fixtures=new JSONArray(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        int checked=0;
        for(int i=0;i<fixtures.length();i++){
            JSONObject f=fixtures.getJSONObject(i);if("collapsed".equals(f.optString("layout")))continue;
            Bitmap image=asset(f.getString("asset"));
            assertEquals(f.getString("source"),f.getString("expected"),read(image,CalendarGridFixtures.layout(f.getInt("year"),f.getInt("month"))));
            image.recycle();checked++;
        }
        assertEquals(61,checked);
    }
    public void testCollapsedWeekCannotMasqueradeAsFullMonth()throws Exception {
        for(String name:new String[]{"calendar.png","calendar-grid/2026-11-29-collapsed.png","calendar-grid/2026-11-30-collapsed.png"}){
            Bitmap image=asset(name);assertNull(read(image,null));image.recycle();
        }
    }
    public void testMissingDigitDoesNotAffectPositionProof()throws Exception {
        Bitmap image=asset("calendar-grid/2026-11-18.png").copy(Bitmap.Config.ARGB_8888,true);
        CalendarGrid.Layout layout=CalendarGridFixtures.layout(2026,11);CalendarGrid.Box b=layout.cellFor(LocalDate.of(2026,11,18)).bounds;
        Paint p=new Paint();p.setColor(Color.rgb(44,126,249));new Canvas(image).drawRect(b.centerX()-35,b.centerY()-40,b.centerX()+35,b.centerY()+30,p);
        assertEquals("2026-11-18",read(image,layout));image.recycle();
    }
    public void testMultipleSelectionShapesAreRejected()throws Exception {
        Bitmap image=asset("calendar-grid/2026-11-18.png").copy(Bitmap.Config.ARGB_8888,true);
        CalendarGrid.Layout layout=CalendarGridFixtures.layout(2026,11);CalendarGrid.Box b=layout.cellFor(LocalDate.of(2026,11,19)).bounds;
        Paint p=new Paint();p.setColor(Color.rgb(44,126,249));new Canvas(image).drawCircle(b.centerX(),b.centerY()-5,53,p);
        assertNull(read(image,layout));image.recycle();
    }
    public void testSixthRowIsNotLimitedToScreenMidpoint()throws Exception {
        Bitmap original=asset("calendar-grid/2026-10-31.png");CalendarGrid.Layout old=CalendarGridFixtures.layout(2026,10);
        CalendarGrid.Box source=old.cellFor(LocalDate.of(2026,10,31)).bounds;
        Bitmap image=Bitmap.createBitmap(1080,2340,Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);
        CalendarGrid.Layout six=CalendarGridFixtures.layout(2026,8);CalendarGrid.Box destination=six.cellFor(LocalDate.of(2026,8,31)).bounds;
        new Canvas(image).drawBitmap(original,new Rect(source.left,source.top,source.right,source.bottom),new Rect(destination.left,destination.top,destination.right,destination.bottom),null);
        assertTrue(destination.centerY()>image.getHeight()/2);
        assertEquals("2026-08-31",read(image,six));image.recycle();original.recycle();
    }
    public void testSameMonthSelectionCanCrossWeeks()throws Exception {
        Bitmap image=asset("calendar-grid/2026-11-01.png");CalendarReader reader=new CalendarReader();CalendarGrid.Layout layout=CalendarGridFixtures.layout(2026,11);
        reader.readGrid(image,layout,(date,reason)->assertEquals("2026-11-01",date));
        Point target=reader.targetInMonth("2026-11-01","2026-11-18");assertNotNull(target);
        CalendarGrid.Box expected=layout.cellFor(LocalDate.of(2026,11,18)).bounds;assertEquals(expected.centerX(),target.x);assertEquals(expected.centerY(),target.y);
        assertNull(reader.targetInMonth("2026-11-01","2026-12-01"));image.recycle();reader.close();
    }
    public void testFailedReadClearsStaleTarget()throws Exception {
        Bitmap image=asset("calendar-grid/2026-11-18.png");CalendarReader reader=new CalendarReader();CalendarGrid.Layout layout=CalendarGridFixtures.layout(2026,11);
        reader.readGrid(image,layout,(date,reason)->assertEquals("2026-11-18",date));
        Bitmap blank=Bitmap.createBitmap(1080,2340,Bitmap.Config.ARGB_8888);blank.eraseColor(Color.WHITE);
        reader.readGrid(blank,layout,(date,reason)->assertNull(date));assertNull(reader.targetInMonth("2026-11-18","2026-11-17"));
        blank.recycle();image.recycle();reader.close();
    }
}
