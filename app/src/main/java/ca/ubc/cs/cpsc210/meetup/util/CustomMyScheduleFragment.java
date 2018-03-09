package ca.ubc.cs.cpsc210.meetup.util;

import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceFragment;

import ca.ubc.cs.cpsc210.meetup.R;

/**
 * Created by by on 2015/4/1.
 */
public class CustomMyScheduleFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.custommyschedulesettings);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getView().setBackgroundColor(Color.WHITE);
    }

}
