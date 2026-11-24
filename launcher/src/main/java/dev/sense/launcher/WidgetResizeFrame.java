package dev.sense.launcher;

import android.appwidget.*;
import android.graphics.*;
import android.view.*;
import org.json.JSONObject;

/** Non-blocking overlay: only resize handles and Done take touches; widget content remains interactive. */
final class WidgetResizeFrame extends View {
    final JSONObject item;
    private final Workspace workspace;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame=new RectF(),done=new RectF();
    private int x,y,w,h,startX,startY,startW,startH,edges;
    private int minW=1,minH=1,maxW,maxH;
    private boolean horizontal=true,vertical=true,valid=true,donePressed;
    private float downX,downY;
    WidgetResizeFrame(Workspace workspace,JSONObject item) {
        super(workspace.activity);this.workspace=workspace;this.item=item;
        x=item.optInt("x");y=item.optInt("y");w=item.optInt("w",1);h=item.optInt("h",1);
        maxW=workspace.columns;maxH=workspace.rows;
        if("search".equals(item.optString("type"))){minW=Math.min(maxW,2);minH=maxH=1;vertical=false;}
        if("music".equals(item.optString("type"))){minW=Math.min(maxW,3);minH=Math.min(maxH,item.optInt("musicRows",2));}
        if("widget".equals(item.optString("type"))) {
            AppWidgetProviderInfo info=AppWidgetManager.getInstance(getContext()).getAppWidgetInfo(item.optInt("id"));
            if(info!=null) {
                horizontal=(info.resizeMode&AppWidgetProviderInfo.RESIZE_HORIZONTAL)!=0;
                vertical=(info.resizeMode&AppWidgetProviderInfo.RESIZE_VERTICAL)!=0;
                float cw=workspace.getWidth()/(float)workspace.columns,ch=workspace.getHeight()/(float)workspace.rows;
                Rect padding=AppWidgetHostView.getDefaultPaddingForWidget(getContext(),info.provider,null);
                minW=Math.min(maxW,Math.max(1,(int)Math.ceil(((info.minResizeWidth>0?info.minResizeWidth:info.minWidth)+padding.left+padding.right)/Math.max(1,cw))));
                minH=Math.min(maxH,Math.max(1,(int)Math.ceil(((info.minResizeHeight>0?info.minResizeHeight:info.minHeight)+padding.top+padding.bottom)/Math.max(1,ch))));
                if(info.maxResizeWidth>0)maxW=Math.max(minW,Math.min(maxW,(int)((info.maxResizeWidth+padding.left+padding.right)/Math.max(1,cw))));
                if(info.maxResizeHeight>0)maxH=Math.max(minH,Math.min(maxH,(int)((info.maxResizeHeight+padding.top+padding.bottom)/Math.max(1,ch))));
                if(!horizontal){minW=w;maxW=w;}
                if(!vertical){minH=h;maxH=h;}
            }
        }
        setContentDescription("Widget resize handles. Drag an edge to change size. Tap Done to finish.");
    }
    boolean supported(){return horizontal||vertical;}
    private int dp(int value){return workspace.dp(value);}
    private void updateFrame() {
        float cw=getWidth()/(float)workspace.columns,ch=getHeight()/(float)workspace.rows;
        frame.set(x*cw+dp(3),y*ch+dp(3),(x+w)*cw-dp(3),(y+h)*ch-dp(3));
        float top=frame.top>dp(38)?frame.top-dp(34):frame.top+dp(6);
        done.set(Math.max(dp(3),frame.right-dp(66)),top,frame.right,top+dp(28));
    }
    boolean contains(float x,float y){updateFrame();return done.contains(x,y)||(x>=frame.left-dp(20)&&x<=frame.right+dp(20)&&y>=frame.top-dp(20)&&y<=frame.bottom+dp(20));}
    @Override protected void onDraw(Canvas canvas) {
        updateFrame();
        if(edges!=0) {
            paint.setColor(0x45ffffff);paint.setStrokeWidth(dp(1));paint.setStyle(Paint.Style.STROKE);
            float cw=getWidth()/(float)workspace.columns,ch=getHeight()/(float)workspace.rows;
            for(int c=0;c<=workspace.columns;c++)canvas.drawLine(c*cw,0,c*cw,getHeight(),paint);
            for(int r=0;r<=workspace.rows;r++)canvas.drawLine(0,r*ch,getWidth(),r*ch,paint);
        }
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(valid?0xffb4e477:0xffff9188);
        canvas.drawRoundRect(frame,dp(8),dp(8),paint);paint.setStyle(Paint.Style.FILL);
        if(horizontal){handle(canvas,frame.left,frame.centerY());handle(canvas,frame.right,frame.centerY());}
        if(vertical){handle(canvas,frame.centerX(),frame.top);handle(canvas,frame.centerX(),frame.bottom);}
        paint.setColor(0xee172320);canvas.drawRoundRect(done,dp(6),dp(6),paint);
        paint.setTextSize(dp(13));paint.setTextAlign(Paint.Align.CENTER);paint.setColor(Color.WHITE);
        canvas.drawText("Done",done.centerX(),done.centerY()-(paint.ascent()+paint.descent())/2,paint);
        String size=w+" × "+h+(valid?"":" · No space");
        float labelWidth=paint.measureText(size)+dp(16);
        float left=Math.max(dp(3),Math.min(frame.left,getWidth()-labelWidth-dp(3)));
        float top=done.top;
        if(left+labelWidth>done.left-dp(4))top=frame.bottom-dp(30);
        paint.setColor(0xee172320);canvas.drawRoundRect(left,top,left+labelWidth,top+dp(28),dp(6),dp(6),paint);
        paint.setColor(Color.WHITE);canvas.drawText(size,left+labelWidth/2,top+dp(19),paint);
    }
    private void handle(Canvas canvas,float x,float y){canvas.drawCircle(x,y,dp(6),paint);}
    @Override public boolean onTouchEvent(MotionEvent event) {
        updateFrame();
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                donePressed=done.contains(event.getX(),event.getY());edges=0;
                if(!donePressed) {
                    float tolerance=dp(20),ex=event.getX(),ey=event.getY();
                    if(horizontal && Math.abs(ey-frame.centerY())<=tolerance) {
                        if(Math.abs(ex-frame.left)<=tolerance)edges|=1;
                        else if(Math.abs(ex-frame.right)<=tolerance)edges|=2;
                    }
                    if(vertical && Math.abs(ex-frame.centerX())<=tolerance) {
                        if(Math.abs(ey-frame.top)<=tolerance)edges|=4;
                        else if(Math.abs(ey-frame.bottom)<=tolerance)edges|=8;
                    }
                    if(edges==0)return false;
                }
                downX=event.getX();downY=event.getY();startX=x;startY=y;startW=w;startH=h;
                getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                if(!donePressed)preview(event.getX(),event.getY());
                return true;
            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                if(donePressed){donePressed=false;performClick();return true;}
                preview(event.getX(),event.getY());edges=0;
                if(valid) {
                    Workspace.put(item,"x",x);Workspace.put(item,"y",y);Workspace.put(item,"w",w);Workspace.put(item,"h",h);
                    workspace.save();workspace.render();workspace.beginResize(item);
                } else {x=startX;y=startY;w=startW;h=startH;valid=true;workspace.toast("Not enough empty space");invalidate();}
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                x=startX;y=startY;w=startW;h=startH;edges=0;donePressed=false;valid=true;
                getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
            default:return true;
        }
    }
    private void preview(float px,float py) {
        int dx=Math.round((px-downX)*workspace.columns/Math.max(1,getWidth()));
        int dy=Math.round((py-downY)*workspace.rows/Math.max(1,getHeight()));
        int left=startX,right=startX+startW,top=startY,bottom=startY+startH;
        if((edges&1)!=0)left=clamp(startX+dx,Math.max(0,right-maxW),right-minW);
        if((edges&2)!=0)right=clamp(startX+startW+dx,left+minW,Math.min(workspace.columns,left+maxW));
        if((edges&4)!=0)top=clamp(startY+dy,Math.max(0,bottom-maxH),bottom-minH);
        if((edges&8)!=0)bottom=clamp(startY+startH+dy,top+minH,Math.min(workspace.rows,top+maxH));
        x=left;y=top;w=right-left;h=bottom-top;
        valid=w>=minW&&h>=minH&&workspace.free(workspace.page,x,y,w,h,item);
        setContentDescription("Widget size "+w+" by "+h+(valid?"":". Space occupied"));invalidate();
    }
    private int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    @Override public boolean performClick(){super.performClick();workspace.finishResize();return true;}
}
