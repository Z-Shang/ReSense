package dev.sense.framework;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.StateListDrawable;
import android.text.*;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

/** Native recreation of HTC SearchBoxView: inset field and conditional stock clear control. */
public final class SenseSearchBox extends FrameLayout {
    private final EditText field;
    public SenseSearchBox(Context context,String hint,String clearDescription){
        super(context);setBackgroundResource(R.drawable.sense_common_panel_input_base);
        int horizontal=SenseStyle.dp(context,8),vertical=SenseStyle.dp(context,4);setPadding(horizontal,vertical,horizontal,vertical);
        field=new EditText(context);field.setSingleLine(true);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_FILTER);field.setImeOptions(EditorInfo.IME_ACTION_DONE);field.setTextSize(17.3f);field.setTextColor(0xff222222);field.setHintTextColor(0xff999999);field.setHint(hint);field.setContentDescription(hint);
        StateListDrawable background=new StateListDrawable();background.addState(new int[]{android.R.attr.state_focused},context.getDrawable(R.drawable.sense_common_inputfield_selected));background.addState(new int[]{},context.getDrawable(R.drawable.sense_common_inputfield_rest));field.setBackground(background);field.setPadding(horizontal,vertical,SenseStyle.dp(context,48),vertical);addView(field,new LayoutParams(-1,-1));
        ImageButton clear=new ImageButton(context);StateListDrawable image=new StateListDrawable();image.addState(new int[]{android.R.attr.state_pressed},context.getDrawable(R.drawable.sense_common_icon_clear_input_press));image.addState(new int[]{},context.getDrawable(R.drawable.sense_common_icon_clear_input_rest));clear.setImageDrawable(image);clear.setScaleType(ImageView.ScaleType.CENTER_INSIDE);clear.setBackgroundColor(Color.TRANSPARENT);clear.setContentDescription(clearDescription);clear.setVisibility(INVISIBLE);clear.setOnClickListener(v->field.setText(""));addView(clear,new LayoutParams(SenseStyle.dp(context,48),-1,Gravity.RIGHT));
        field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int start,int before,int count){clear.setVisibility(s.length()==0?INVISIBLE:VISIBLE);}});
    }
    public EditText getTextField(){return field;}
}
