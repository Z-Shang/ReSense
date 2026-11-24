package dev.sense.launcher;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import dev.sense.launcher.ui.SenseAppIcon;
import java.util.*;
import java.util.function.BiConsumer;

/** A scrollable app grid with a bounded pool of native, accessible icon views. */
final class DrawerAppViewport extends ScrollView {
    private final List<AppCatalog.Entry> apps;
    private final int columns,baseCellHeight,windowRows;
    private int cellHeight,contentHeight,displayCount;
    private final boolean singlePage;
    private final FrameLayout cells;
    private final BiConsumer<SenseAppIcon,AppCatalog.Entry> bind;
    private final java.util.function.Supplier<SenseAppIcon> create;
    private final Map<Integer,SenseAppIcon> visible=new HashMap<>();
    private final ArrayDeque<SenseAppIcon> pool=new ArrayDeque<>();
    private int firstRow=-1;

    DrawerAppViewport(Context context,List<AppCatalog.Entry> apps,boolean listMode,
            java.util.function.Supplier<SenseAppIcon> create,BiConsumer<SenseAppIcon,AppCatalog.Entry> bind){
        this(context,apps,listMode,false,create,bind);
    }
    DrawerAppViewport(Context context,List<AppCatalog.Entry> apps,boolean listMode,boolean singlePage,
            java.util.function.Supplier<SenseAppIcon> create,BiConsumer<SenseAppIcon,AppCatalog.Entry> bind){
        super(context);this.apps=List.copyOf(apps);this.create=create;this.bind=bind;this.singlePage=singlePage;
        displayCount=apps.size();
        columns=listMode?1:4;
        cellHeight=Math.round((listMode?72:104)*getResources().getDisplayMetrics().density);
        baseCellHeight=cellHeight;
        contentHeight=Math.max(cellHeight,((apps.size()+columns-1)/columns)*cellHeight);
        // Include spare rows so attaching the warmed viewport does not create icons.
        windowRows=(int)Math.ceil(getResources().getDisplayMetrics().heightPixels/(double)cellHeight)+3;
        setFillViewport(true);setClipToPadding(false);
        cells=new FrameLayout(context){
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                // Give cells their real widths before FrameLayout measures them.
                // Updating in onSizeChanged is too late for a short first page:
                // it leaves the initially zero-width icons blank until relayout.
                positionCells(MeasureSpec.getSize(widthSpec));
                // ScrollView measures its child with an unconstrained height.
                // Recycled children describe only a moving window of rows, not
                // the full extent; deriving height from them changes the thumb
                // and clamps the scroll position on every recycling/layout pass.
                int viewportMinimum=MeasureSpec.getMode(heightSpec)==MeasureSpec.EXACTLY?MeasureSpec.getSize(heightSpec):0;
                int stableHeight=Math.max(contentHeight,Math.max(viewportMinimum,getSuggestedMinimumHeight()));
                super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(stableHeight,MeasureSpec.EXACTLY));
            }
        };
        addView(cells,new ScrollView.LayoutParams(-1,contentHeight));
        updateWindow(0);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        if(singlePage && MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED){
            int height=Math.max(1,MeasureSpec.getSize(heightSpec)-getPaddingTop()-getPaddingBottom());
            int fittedCellHeight=Math.min(baseCellHeight,height);
            int count=Math.min(apps.size(),columns*Math.max(1,height/fittedCellHeight));
            if(count!=displayCount || fittedCellHeight!=cellHeight){
                displayCount=count;cellHeight=fittedCellHeight;
                contentHeight=Math.max(cellHeight,((displayCount+columns-1)/columns)*cellHeight);
                cells.getLayoutParams().height=contentHeight;
                firstRow=-1;updateWindow(0);scrollTo(0,0);
            }
        }
        super.onMeasure(widthSpec,heightSpec);
    }
    int appCount(){return displayCount;}
    int boundCount(){return visible.size();}
    int positionOf(View view){for(var entry:visible.entrySet())if(entry.getValue()==view)return entry.getKey();return -1;}
    void jumpTo(int position){scrollTo(0,(position/columns)*cellHeight);}
    @Override protected void onScrollChanged(int x,int y,int oldx,int oldy){super.onScrollChanged(x,y,oldx,oldy);updateWindow(Math.max(0,y/cellHeight-1));}
    private void updateWindow(int row){
        if(row==firstRow)return;firstRow=row;
        int start=row*columns,end=Math.min(displayCount,(row+windowRows)*columns);
        var iterator=visible.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();
            if(entry.getKey()<start || entry.getKey()>=end){
                // Do not recycle the focused item underneath keyboard/accessibility users.
                if(entry.getValue().hasFocus() || entry.getValue().isAccessibilityFocused())continue;
                cells.removeView(entry.getValue());pool.add(entry.getValue());iterator.remove();
            }
        }
        for(int position=start;position<end;position++)if(!visible.containsKey(position)){
            SenseAppIcon icon=pool.poll();if(icon==null)icon=create.get();
            bind.accept(icon,apps.get(position));visible.put(position,icon);
            FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(0,cellHeight);
            cells.addView(icon,params);
        }
        positionCells(cells.getWidth());
    }
    private void positionCells(int width){
        for(var entry:visible.entrySet()){
            int position=entry.getKey(),column=position%columns;
            FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)entry.getValue().getLayoutParams();
            int left=width*column/columns,right=width*(column+1)/columns;
            if(getLayoutDirection()==View.LAYOUT_DIRECTION_RTL){left=width-right;right=width-width*column/columns;}
            int top=position/columns*cellHeight;
            if(params.width!=right-left || params.height!=cellHeight || params.leftMargin!=left || params.topMargin!=top){
                params.width=right-left;params.height=cellHeight;params.leftMargin=left;params.topMargin=top;entry.getValue().setLayoutParams(params);
            }
        }
    }
}
