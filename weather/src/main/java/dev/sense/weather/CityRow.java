package dev.sense.weather;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.*;

/** Checkable native popup row with a separate, accessible remove action. */
public final class CityRow extends LinearLayout implements Checkable {
    private boolean checked;
    public CityRow(Context context,AttributeSet attributes){super(context,attributes);}
    @Override public void setChecked(boolean value){checked=value;refreshDrawableState();((CheckedTextView)findViewById(R.id.city_label)).setChecked(value);}
    @Override public boolean isChecked(){return checked;}
    @Override public void toggle(){setChecked(!checked);}
    @Override protected int[] onCreateDrawableState(int extra){int[] state=super.onCreateDrawableState(extra+1);if(checked)mergeDrawableStates(state,new int[]{android.R.attr.state_checked});return state;}
}
