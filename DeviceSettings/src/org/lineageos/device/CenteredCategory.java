package org.lineageos.device;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

public class CenteredCategory extends PreferenceCategory {

    public CenteredCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View itemView = holder.itemView;
        if (itemView != null) {
            int paddingPx = Math.round(4 * itemView.getResources().getDisplayMetrics().density);
            itemView.setPadding(itemView.getPaddingLeft(), paddingPx, itemView.getPaddingRight(), paddingPx);
        }

        View rawTitleView = holder.findViewById(android.R.id.title);
        if (rawTitleView instanceof TextView) {
            TextView titleView = (TextView) rawTitleView;
            ViewGroup.LayoutParams lp = titleView.getLayoutParams();
            if (lp != null) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                titleView.setLayoutParams(lp);
            }
            titleView.setGravity(android.view.Gravity.CENTER);
            titleView.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        }
    }
}
