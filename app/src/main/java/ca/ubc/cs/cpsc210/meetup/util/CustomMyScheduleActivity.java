package ca.ubc.cs.cpsc210.meetup.util;


import android.app.Activity;
import android.os.Bundle;

/**
 * Created by by on 2015/4/1.
 */
public class CustomMyScheduleActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new CustomMyScheduleFragment()).commit();
    }
}
