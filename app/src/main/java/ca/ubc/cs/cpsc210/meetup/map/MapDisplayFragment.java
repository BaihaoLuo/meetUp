package ca.ubc.cs.cpsc210.meetup.map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.OverlayManager;
import org.osmdroid.views.overlay.PathOverlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import ca.ubc.cs.cpsc210.meetup.R;
import ca.ubc.cs.cpsc210.meetup.model.Building;
import ca.ubc.cs.cpsc210.meetup.model.Course;
import ca.ubc.cs.cpsc210.meetup.model.CourseFactory;
import ca.ubc.cs.cpsc210.meetup.model.EatingPlace;
import ca.ubc.cs.cpsc210.meetup.model.Place;
import ca.ubc.cs.cpsc210.meetup.model.PlaceFactory;
import ca.ubc.cs.cpsc210.meetup.model.Schedule;
import ca.ubc.cs.cpsc210.meetup.model.Section;
import ca.ubc.cs.cpsc210.meetup.model.Student;
import ca.ubc.cs.cpsc210.meetup.model.StudentManager;
import ca.ubc.cs.cpsc210.meetup.util.GPSTracker;
import ca.ubc.cs.cpsc210.meetup.util.LatLon;
import ca.ubc.cs.cpsc210.meetup.util.SchedulePlot;

/**
 * Fragment holding the map in the UI.
 */
public class MapDisplayFragment extends Fragment {

    /**
     * Log tag for LogCat messages
     */
    private final static String LOG_TAG = "MapDisplayFragment";

    /**
     * Preference manager to access user preferences
     */
    private SharedPreferences sharedPreferences;

    /**
     * String to know whether we are dealing with MWF or TR schedule.
     * You will need to update this string based on the settings dialog at appropriate
     * points in time. See the project page for details on how to access
     * the value of a setting.
     */
    private String activeDay = "MWF";

    /**
     * A central location in campus that might be handy.
     */
    private final static GeoPoint UBC_MARTHA_PIPER_FOUNTAIN = new GeoPoint(49.264865,
            -123.252782);

    /**
     * Meetup Service URL
     * CPSC 210 Students: Complete the string.
     */
    private final String getStudentURL = "http://kramer.nss.cs.ubc.ca:8082/getStudent";

    /**
     * FourSquare URLs. You must complete the client_id and client_secret with values
     * you sign up for.
     */
    private static String FOUR_SQUARE_URL = "https://api.foursquare.com/v2/venues/explore";
    private static String FOUR_SQUARE_CLIENT_ID = "";
    private static String FOUR_SQUARE_CLIENT_SECRET = "";


    /**
     * Overlays for displaying my schedules, buildings, etc.
     */
    private List<PathOverlay> scheduleOverlay;
    private ItemizedIconOverlay<OverlayItem> buildingOverlay;
    private OverlayItem selectedBuildingOnMap;

    /*
     * Custom Fields
     */
    private SchedulePlot schedulePlotLastRandomStudent;
    private PathOverlay pathOverlayLastRandomStudent;

    private String useCustomSchedule;
    private ArrayList<String> routeColors = new ArrayList<String>();
    private ArrayList<Integer> icons = new ArrayList<Integer>();



    /**
     * View that shows the map
     */
    private MapView mapView;

    /**
     * Access to domain model objects. Only store "me" in the studentManager for
     * the base project (i.e., unless you are doing bonus work).
     */
    private StudentManager studentManager;
    private ArrayList<Student> randomStudents = new ArrayList<Student>();
    private Student me = null;
    private static int ME_ID = 999999;


    /**
     * Map controller for zooming in/out, centering
     */
    private IMapController mapController;

    // ******************** Android methods for starting, resuming, ...

    // You should not need to touch this method
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        scheduleOverlay = new ArrayList<PathOverlay>();

        // You need to setup the courses for the app to know about. Ideally
        // we would access a web service like the UBC student information system
        // but that is not currently possible
        initializeCourses();

        // Initialize the data for the "me" schedule. Note that this will be
        // hard-coded for now
        initializeMySchedule();

        // You are going to need an overlay to draw buildings and locations on the map
        buildingOverlay = createBuildingOverlay();
    }

    // You should not need to touch this method
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK)
            return;
    }

    // You should not need to touch this method
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        if (mapView == null) {
            mapView = new MapView(getActivity(), null);

            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setClickable(true);
            mapView.setBuiltInZoomControls(true);
            mapView.setMultiTouchControls(true);

            mapController = mapView.getController();
            mapController.setZoom(mapView.getMaxZoomLevel() - 2);
            mapController.setCenter(UBC_MARTHA_PIPER_FOUNTAIN);
        }

        return mapView;
    }

    // You should not need to touch this method
    @Override
    public void onDestroyView() {
        Log.d(LOG_TAG, "onDestroyView");
        ((ViewGroup) mapView.getParent()).removeView(mapView);
        super.onDestroyView();
    }

    // You should not need to touch this method
    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
    }

    // You should not need to touch this method
    @Override
    public void onResume() {
        Log.d(LOG_TAG, "onResume");
        super.onResume();
    }

    // You should not need to touch this method
    @Override
    public void onPause() {
        Log.d(LOG_TAG, "onPause");
        super.onPause();
    }

    /**
     * Save map's zoom level and centre. You should not need to
     * touch this method
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(LOG_TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);

        if (mapView != null) {
            outState.putInt("zoomLevel", mapView.getZoomLevel());
            IGeoPoint cntr = mapView.getMapCenter();
            outState.putInt("latE6", cntr.getLatitudeE6());
            outState.putInt("lonE6", cntr.getLongitudeE6());
            Log.i("MapSave", "Zoom: " + mapView.getZoomLevel());
        }
    }

    // ****************** App Functionality

    /**
     * Show my schedule on the map. Every time "me"'s schedule shows, the map
     * should be cleared of all existing schedules, buildings, meetup locations, etc.
     */
    public void showMySchedule() {
        clearSchedules();
        String dayOfWeek = sharedPreferences.getString("dayOfWeek","MWF");
        initializeMySchedule();
        SortedSet<Section> sections = me.getSchedule().getSections(dayOfWeek);
        int icon = R.drawable.ic_action_place_blue;
        String myName = me.getLastName()+","+me.getFirstName();

        SchedulePlot mySchedulePlot = new SchedulePlot(sections, myName,"#407cc9",icon);
        useCustomSchedule = sharedPreferences.getString("turnOnCustomSchedule","No");
        if(useCustomSchedule.equals("Yes")){
            me.getSchedule().removeAll();
            for(int i = 0 ; i < 5 ; i++) {
                String classNumber = "class" + Integer.toString(i);
                String rawSectionInfo = sharedPreferences.getString(classNumber, "CPSC210201");
                if (!rawSectionInfo.equals("No Class")) {
                    String courseCode = rawSectionInfo.substring(0, 4);
                    String courseNumberString = rawSectionInfo.substring(4, 7);
                    int courseNumberInt = Integer.parseInt(courseNumberString);
                    String sectionNumber = rawSectionInfo.substring(7);

                    Section section = CourseFactory.getInstance().getCourse(courseCode, courseNumberInt).getSection(sectionNumber);
                    if (!me.getSchedule().getSections(dayOfWeek).contains(section)) {
                        studentManager.addSectionToSchedule(ME_ID, courseCode, courseNumberInt, sectionNumber);
                    }
                }
            }
        }else{
            me.getSchedule().removeAll();
            studentManager.addSectionToSchedule(ME_ID,"CPSC",210,"201");
            studentManager.addSectionToSchedule(ME_ID,"SCIE",220,"200");
            studentManager.addSectionToSchedule(ME_ID,"MATH",200,"201");
            studentManager.addSectionToSchedule(ME_ID,"JAPN",103,"002");
            studentManager.addSectionToSchedule(ME_ID,"BIOL",201,"201");
        }

        new GetRoutingForSchedule().execute(mySchedulePlot);

        // UNCOMMENT NEXT LINE ONCE YOU HAVE INSTANTIATED mySchedulePlot




        // CPSC 210 Students: You must complete the implementation of this method.
        // The very last part of the method should call the asynchronous
        // task (which you will also write the code for) to plot the route
        // for "me"'s schedule for the day of the week set in the Settings

        // Asynchronous tasks are a bit onerous to deal with. In order to provide
        // all information needed in one object to plot "me"'s route, we
        // create a SchedulePlot object and pass it to the asynchrous task.
        // See the project page for more details.


        // Get a routing between these points. This line of code creates and calls
        // an asynchronous task to do the calls to MapQuest to determine a route
        // and plots the route.
        // Assumes mySchedulePlot is a create and initialized SchedulePlot object


    }

    /**
     * Retrieve a random student's schedule from the Meetup web service and
     * plot a route for the schedule on the map. The plot should be for
     * the given day of the week as determined when "me"'s schedule
     * was plotted.
     */
    public void showRandomStudentsSchedule() {
        // To get a random student's schedule, we have to call the MeetUp web service.
        // Calling this web service requires a network access to we have to
        // do this in an asynchronous task. See below in this class for where
        // you need to implement methods for performing the network access
        // and plotting.
        if(randomStudents.isEmpty()){
            setUpColorsAndIcons();
        }

        new GetRandomSchedule().execute();
            /*
        if(randomStudents.isEmpty()){
            //OverlayManager om = mapView.getOverlayManager();
            //removeBuildingsForRandomStudent(schedulePlotLastRandomStudent);
            //scheduleOverlay.remove(pathOverlayLastRandomStudent);
            //om.remove(1);
            AlertDialog alertDialog = createSimpleDialog("Already have one randomStudent. If you want" +
                    " to get a new randomStudent, please choose show my schedule first and then choose" +
                    " this function again");
            alertDialog.show();
        }else{
          */


        //}
    }

    /**
     * Clear all schedules on the map
     */
    public void clearSchedules() {
        randomStudents.clear();
        studentManager.clear();
        OverlayManager om = mapView.getOverlayManager();
        om.clear();
        scheduleOverlay.clear();
        buildingOverlay.removeAllItems();
        om.addAll(scheduleOverlay);
        om.add(buildingOverlay);
        mapView.invalidate();

        setUpColorsAndIcons();

    }

    public void setUpColorsAndIcons(){
        routeColors.clear();
        icons.clear();

        routeColors.add("#ff0000");
        routeColors.add("#00ff00");
        routeColors.add("#66b40b");
        routeColors.add("#ffd700");
        routeColors.add("#00ffff");
        routeColors.add("#ff008d");
        routeColors.add("#9400d3");

        icons.add(R.drawable.ic_action_place_red);
        icons.add(R.drawable.ic_action_place_2);
        icons.add(R.drawable.ic_action_place_3);
        icons.add(R.drawable.ic_action_place_4);
        icons.add(R.drawable.ic_action_place_5);
        icons.add(R.drawable.ic_action_place_6);
        icons.add(R.drawable.ic_action_place_7);

    }
    /*
    this method is for fun, cannot remove object from OverlayManger

    public void clearRandomStudentSchedules(){
        randomStudent = null;
        if(schedulePlotLastRandomStudent != null){
            OverlayManager om = mapView.getOverlayManager();
            removeBuildingsForRandomStudent(schedulePlotLastRandomStudent);
            scheduleOverlay.remove(pathOverlayLastRandomStudent);
            om.remove(schedulePlotLastRandomStudent);

        }
    }
    */
     public Set<Place> placeFiitlerWithFood(Set<Place> places, String typeOfFood){
         Set<Place> returnPlaces = new HashSet<Place>();
         if(typeOfFood.equals("All")){
             return places;
         }else {
              for (Place p : places) {
                 if (p.getCategories().equals(typeOfFood)) {
                     returnPlaces.add(p);
                 }
             }
             return returnPlaces;
         }
     }
    /**
     * Find all possible locations at which "me" and random student could meet
     * up for the set day of the week and the set time to meet and the set
     * distance either "me" or random is willing to travel to meet.
     * A meetup is only possible if both "me" and random are free at the
     * time specified in the settings and each of us must have at least an hour
     * (>= 60 minutes) free. You should display dialog boxes if there are
     * conditions under which no meetup can happen (e.g., me or random is
     * in class at the specified time)
     */
    public void findMeetupPlace(LatLon latLon) {
        if (randomStudents.isEmpty()){
            AlertDialog alertDialog = createSimpleDialog("There is not randomStudent! please get randomStudent schedule!");
            alertDialog.show();
        }else {
            PlaceFactory placeFactory = PlaceFactory.getInstance();
            if(placeFactory.isEmpty()){
                AlertDialog alertDialog = createSimpleDialog("Places have not been load from FourSquare");
                alertDialog.show();
            }else {
                String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");
                String timeOfDay = sharedPreferences.getString("timeOfDay", "12");
                String typeOfFood = sharedPreferences.getString("typeOfFood","All");

                Schedule mySchedule = me.getSchedule();
                Boolean amIFreeAtMeetUpTime = mySchedule.haveTimeToMeetUp(dayOfWeek, timeOfDay);


                if (amIFreeAtMeetUpTime && areRandomStudentFree()) {

                    String selectedDistance = sharedPreferences.getString("placeDistance", "100");
                    int distanceInt = Integer.parseInt(selectedDistance);

                    String letMeWalkAroundSetting = sharedPreferences.getString("letMeWalkAround","Off");

                    LatLon myLatLon;
                    if(latLon == null || letMeWalkAroundSetting.equals("Off")){
                        myLatLon = mySchedule.whereAmI(dayOfWeek, timeOfDay).getLatLon();
                    }else {
                        myLatLon = latLon;
                    }
                    Set<Place> placeICanGo = placeFactory.findPlacesWithinDistance(myLatLon, distanceInt);

                    if (placeICanGo.isEmpty()) {
                        AlertDialog alertDialog = createSimpleDialog("I cannot find meetUp place in the given distance");
                        alertDialog.show();
                    } else {
                        placeICanGo = placeFiitlerWithFood(placeICanGo, typeOfFood);
                        if (placeICanGo.isEmpty()) {
                            AlertDialog alertDialog = createSimpleDialog("I cannot find meetUp place of given typeOfFood in the given distance");
                            alertDialog.show();
                        } else {
                            Set<Place> placeRandomStudentsCanGo = findMeetUpPlaceForRandomStudents();
                            if (placeRandomStudentsCanGo.isEmpty()) {
                                AlertDialog alertDialog = createSimpleDialog("randomStudents cannot find meetUp place in the given distance");
                                alertDialog.show();
                            } else {
                                placeRandomStudentsCanGo = placeFiitlerWithFood(placeRandomStudentsCanGo, typeOfFood);
                                if (placeRandomStudentsCanGo.isEmpty()) {
                                    AlertDialog alertDialog = createSimpleDialog("randomStudents cannot find meetUp place of given typeOfFood in the given distance");
                                    alertDialog.show();
                                } else {
                                    Set<Place> meetUpPlace = new HashSet<Place>();
                                    for (Place p1 : placeICanGo) {
                                        for (Place p2 : placeRandomStudentsCanGo) {
                                            if (p1.equals(p2)) {
                                                meetUpPlace.add(p2);
                                            }
                                        }
                                    }
                                    if (meetUpPlace.isEmpty()) {
                                        AlertDialog alertDialog = createSimpleDialog("In the given distance for meetUP, there is not meetUp place");
                                        alertDialog.show();
                                    } else {

                                        Set<Building> meetUpPlaceBuilding = new HashSet<Building>();
                                        for (Place p : meetUpPlace) {
                                            String name = p.getName();
                                            LatLon ll = p.getLatLon();
                                            String location = p.getLocation();
                                            String price = p.getPrice();
                                            String categories = p.getCategories();
                                            String contact = p.getPhoneNumber();
                                            ArrayList<String> reviews = p.getReviews();
                                            Building bd = new Building(name, ll);
                                            bd.setCategories(categories);
                                            bd.setPhoneNumber(contact);
                                            bd.setLocation(location);
                                            bd.setReviews(reviews);
                                            bd.setPrice(price);
                                            meetUpPlaceBuilding.add(bd);
                                        }

                                        for (Building b : meetUpPlaceBuilding) {
                                            String title = b.getName();
                                            ArrayList<String> reviews = b.getReviews();
                                            String location = b.getLocation();
                                            String price = b.getPrice();
                                            String reviewsString = "no reviews";
                                            if (!reviews.isEmpty()) {
                                                reviewsString = "";
                                                for (String s : reviews) {
                                                    reviewsString = reviewsString + s + "\n";
                                                }
                                            }
                                            String categories = b.getCategories();
                                            String contact = b.getPhoneNumber();

                                            String msg = "MeetUp time: " + timeOfDay + ":00" + "\n" + "Address: " + location +
                                                    "\nPhoneNumber: " + contact + "\n\n" + "\n" +
                                                    "Categories: " + categories + "\n\n" +
                                                    "Price: " + price + "\n\n" +
                                                    "Review: " + "\n" + reviewsString;
                                            int icon = R.drawable.ic_action_place;
                                            plotABuilding(b, title, msg, icon);
                                        }

                                        Place cloestPlace = null;
                                        OverlayManager om = mapView.getOverlayManager();
                                        if (letMeWalkAroundSetting.equals("On") && !meetUpPlace.isEmpty()) {

                                            ArrayList<Place> meetUpPlaceList = new ArrayList<>(meetUpPlace);
                                            if (meetUpPlace.size() == 1) {
                                                cloestPlace = meetUpPlaceList.get(0);
                                            } else {
                                                for (int i = 0; i < meetUpPlaceList.size(); i++) {
                                                    if (i == 0) {
                                                        cloestPlace = meetUpPlaceList.get(0);
                                                    } else {
                                                        LatLon latLon11 = meetUpPlaceList.get(i).getLatLon();
                                                        LatLon latLon12 = cloestPlace.getLatLon();
                                                        if (LatLon.distanceBetweenTwoLatLon(latLon11, myLatLon) <= LatLon.distanceBetweenTwoLatLon(latLon12, myLatLon)) {
                                                            cloestPlace = meetUpPlaceList.get(i);
                                                        }
                                                    }
                                                }
                                            }
                                            String head = "http://open.mapquestapi.com/directions/v2/route?key=Fmjtd%7Cluu82lu1n1%2Cbs%3Do5-948wlz&outFormat=json&routeType=pedestrian&timeType=0&enhancedNarrative=false&shapeFormat=raw&generalize=0&locale=en_US&unit=k&from=";
                                            String mid = "&to=";
                                            String myLatLonString = Double.toString(myLatLon.getLatitude()).substring(0, 12) + "," + Double.toString(myLatLon.getLongitude()).substring(0, 14);
                                            String closetPlaceLatLonString = Double.toString(cloestPlace.getLatLon().getLatitude()).substring(0, 12) + "," + Double.toString(cloestPlace.getLatLon().getLongitude()).substring(0, 14);
                                            String callString = head + myLatLonString + mid + closetPlaceLatLonString;

                                            new getMyLocation().execute(callString);

                                            Building whereIAm = new Building("where I am", myLatLon);
                                            plotABuilding(whereIAm,"where I am", "",R.drawable.ic_action_cancel);

                                            om.add(buildingOverlay);
                                            mapView.invalidate();

                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (!amIFreeAtMeetUpTime && ! areRandomStudentFree()) {
                    AlertDialog alertDialog = createSimpleDialog("Both me and random student are not available");
                    alertDialog.show();
                } else if (!amIFreeAtMeetUpTime) {
                    AlertDialog alertDialog = createSimpleDialog("I am not available");
                    alertDialog.show();
                } else if (randomStudents.isEmpty()){
                    AlertDialog alertDialog = createSimpleDialog("There is not randomStudent");
                    alertDialog.show();
                } else{
                    AlertDialog alertDialog = createSimpleDialog("random student are not available!");
                    alertDialog.show();
                }
            }
        }// CPSC 210 students: you must complete this method

    }

    public boolean areRandomStudentFree(){
        Student lastStudent = null;
        String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");
        String timeOfDay = sharedPreferences.getString("timeOfDay", "12");
        Boolean returnBoolean = false;
        if(randomStudents.size() == 1){
            Student rs = randomStudents.get(0);
            return rs.getSchedule().haveTimeToMeetUp(dayOfWeek,timeOfDay);
        }
        for(int i = 0 ; i< randomStudents.size(); i++){
            if(i == 0){
                lastStudent = randomStudents.get(i);
            }else{
                Student s = randomStudents.get(i);
                Boolean b1 = lastStudent.getSchedule().haveTimeToMeetUp(dayOfWeek, timeOfDay);
                Boolean b2 = s.getSchedule().haveTimeToMeetUp(dayOfWeek, timeOfDay);
                if(b1&b2){
                    returnBoolean = true;
                }else{
                    return false;
                }
                lastStudent = s;
            }
        }
        return returnBoolean;
    }

    public Set<Place> findMeetUpPlaceForRandomStudents(){
        Set<Place> returnSet = new HashSet<Place>();
        Student lastStudent = null;
        String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");
        String timeOfDay = sharedPreferences.getString("timeOfDay", "12");
        String selectedDistance = sharedPreferences.getString("placeDistance", "100");
        int distance = Integer.parseInt(selectedDistance);
        PlaceFactory placeFactory = PlaceFactory.getInstance();

        if(randomStudents.size() == 1){
            return  placeFactory.findPlacesWithinDistance(randomStudents.get(0).getSchedule().whereAmI(dayOfWeek,timeOfDay).getLatLon(), distance);
        }

        for(int i = 0; i < randomStudents.size(); i++)
            if(i == 0){
                lastStudent = randomStudents.get(i);

            }else if(i == 1){
                Student anotherRandomStudent = randomStudents.get(i);
                Set<Place> placeLastStudent =  placeFactory.findPlacesWithinDistance(lastStudent.getSchedule().whereAmI(dayOfWeek,timeOfDay).getLatLon(), distance);
                Set<Place> placeAnotherRandomStudent =  placeFactory.findPlacesWithinDistance(anotherRandomStudent.getSchedule().whereAmI(dayOfWeek,timeOfDay).getLatLon(), distance);
                if(placeLastStudent.isEmpty()){
                    return null;
                }
                if(placeAnotherRandomStudent.isEmpty()){
                    return null;
                }

                for (Place p1 : placeLastStudent) {
                    for (Place p2 : placeAnotherRandomStudent) {
                        if (p1.equals(p2)) {
                            returnSet.add(p2);
                        }
                    }
                }
                lastStudent = anotherRandomStudent;
            }else{
                Student anotherRandomStudent = randomStudents.get(i);
                Set<Place> placeLastStudent =  placeFactory.findPlacesWithinDistance(lastStudent.getSchedule().whereAmI(dayOfWeek,timeOfDay).getLatLon(), distance);
                Set<Place> placeAnotherRandomStudent =  placeFactory.findPlacesWithinDistance(anotherRandomStudent.getSchedule().whereAmI(dayOfWeek,timeOfDay).getLatLon(), distance);
                if(placeLastStudent.isEmpty()){
                    return null;
                }
                if(placeAnotherRandomStudent.isEmpty()){
                    return null;
                }
                Set<Place> sp = new HashSet<Place>();
                for (Place p1 : placeLastStudent) {
                    for (Place p2 : placeAnotherRandomStudent) {
                        if (p1.equals(p2)) {
                            sp.add(p2);
                        }
                    }
                }
                for (Place p1 : sp) {
                    for (Place p2 : returnSet) {
                        returnSet.remove(p2);
                        if (p1.equals(p2)) {
                            returnSet.add(p2);
                        }
                    }
                }
                lastStudent = anotherRandomStudent;
            }
        return returnSet;
    }

    /**
     * Initialize the PlaceFactory with information from FourSquare
     */
    public void initializePlaces() {
        // CPSC 210 Students: You should not need to touch this method, but
        // you will have to implement GetPlaces below.
        new GetPlaces().execute();
    }

    public String makeRoutingCall(String httpRequest) throws MalformedURLException, IOException {
        URL url = new URL(httpRequest);
        HttpURLConnection client = (HttpURLConnection) url.openConnection();
        InputStream in = client.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        String returnString = br.readLine();
        client.disconnect();
        return returnString;
    }


    /**
     * Plot all buildings referred to in the given information about plotting
     * a schedule.
     * @param schedulePlot All information about the schedule and route to plot.
     */
    private void plotBuildings(SchedulePlot schedulePlot) {

        // CPSC 210 Students: Complete this method by plotting each building in the
        // schedulePlot with an appropriate message displayed
        SortedSet<Section> sections = schedulePlot.getSections();
        LatLon sll = new LatLon(0,0);
        Building lastBuilding = new Building("test", sll);

        for(Section s : sections){
            Building b = s.getBuilding();
            String title = schedulePlot.getName();
            int icon = schedulePlot.getIcon();

            if(lastBuilding.equals(b)){
                String newmsg =  "Course: " + s.getCourse().getCode()+ Integer.toString(s.getCourse().getNumber())+ "\n" +
                                 "Section: " + s.getName() + "\n" +
                                 "CourseTime: " + s.getCourseTimeString() + "\n" +
                                 "Building: " + s.getBuilding().getName();

                LatLon ll = lastBuilding.getLatLon();
                Double newlat = ll.getLatitude()+ 0.000100;
                Double newlng = ll.getLongitude();
                LatLon newll = new LatLon(newlat,newlng);
                Building nb = new Building(lastBuilding.getName(),newll);
                plotABuilding(nb, title, newmsg, icon);
            }
            else {
                String msg = "Course: " + s.getCourse().getCode() + Integer.toString(s.getCourse().getNumber()) + "\n" +
                        "Section: " + s.getName() + "\n" +
                        "CourseTime: " + s.getCourseTimeString() + "\n" +
                        "Building: " + s.getBuilding().getName();
                plotABuilding(b, title, msg, icon);
            }
            lastBuilding = s.getBuilding();
        }
   

        // CPSC 210 Students: You will need to ensure the buildingOverlay is in
        // the overlayManager. The following code achieves this. You should not likely
        // need to touch it
        OverlayManager om = mapView.getOverlayManager();
        om.add(buildingOverlay);

    }



    private void plotBuildingsForRandomStudent(SchedulePlot schedulePlot) {

        SortedSet<Section> sections = schedulePlot.getSections();
        LatLon sll = new LatLon(0,0);
        Building lastBuilding = new Building("test", sll);

        for(Section s : sections){
            Building b = s.getBuilding();
            String title = schedulePlot.getName();
            int icon = schedulePlot.getIcon();

            if(lastBuilding.equals(b)){
                String newmsg =  "Course: " + s.getCourse().getCode()+ Integer.toString(s.getCourse().getNumber())+ "\n" +
                        "Section: " + s.getName() + "\n" +
                        "CourseTime: " + s.getCourseTimeString() + "\n" +
                        "Building: " + s.getBuilding().getName();

                LatLon ll = b.getLatLon();
                Double newlat = ll.getLatitude()+ 0.000100*randomStudents.size();
                Double newlng = ll.getLongitude() + 0.000100*randomStudents.size();
                LatLon newll = new LatLon(newlat,newlng);
                Building nb = new Building(lastBuilding.getName(),newll);
                plotABuilding(nb, title, newmsg, icon);
            }
            else {
                String msg = "Course: " + s.getCourse().getCode() + Integer.toString(s.getCourse().getNumber()) + "\n" +
                        "Section: " + s.getName() + "\n" +
                        "CourseTime: " + s.getCourseTimeString() + "\n" +
                        "Building: " + s.getBuilding().getName();
                LatLon ll = b.getLatLon();
                Double newlat = ll.getLatitude();
                Double newlng = ll.getLongitude() + 0.000100*randomStudents.size();
                LatLon newll = new LatLon(newlat,newlng);
                Building nb = new Building(lastBuilding.getName(),newll);
                plotABuilding(nb, title, msg, icon);

            }
            lastBuilding = b;
        }
        // CPSC 210 Students: You will need to ensure the buildingOverlay is in
        // the overlayManager. The following code achieves this. You should not likely
        // need to touch it
        OverlayManager om = mapView.getOverlayManager();
        om.add(buildingOverlay);
    }

    /**
     * Plot a building onto the map
     * @param building The building to put on the map
     * @param title The title to put in the dialog box when the building is tapped on the map
     * @param msg The message to display when the building is tapped
     * @param drawableToUse The icon to use. Can be R.drawable.ic_action_place (or any icon in the res/drawable directory)
     */
    private void plotABuilding(Building building, String title, String msg, int drawableToUse) {
        // CPSC 210 Students: You should not need to touch this method
        OverlayItem buildingItem = new OverlayItem(title, msg,
                new GeoPoint(building.getLatLon().getLatitude(), building.getLatLon().getLongitude()));

        //Create new marker
        Drawable icon = this.getResources().getDrawable(drawableToUse);

        //Set the bounding for the drawable
        icon.setBounds(
                0 - icon.getIntrinsicWidth() / 2, 0 - icon.getIntrinsicHeight(),
                icon.getIntrinsicWidth() / 2, 0);

        //Set the new marker to the overlay
        buildingItem.setMarker(icon);
        buildingOverlay.addItem(buildingItem);
    }
    /*
    private void removeBuildingsForRandomStudent(SchedulePlot schedulePlot) {

        SortedSet<Section> sections = schedulePlot.getSections();
        LatLon sll = new LatLon(0,0);
        Building lastBuilding = new Building("test", sll);
        OverlayManager om = mapView.getOverlayManager();

        for(Section s : sections){
            Building b = s.getBuilding();
            String title = schedulePlot.getName();
            int icon = schedulePlot.getIcon();

            if(lastBuilding.equals(b)){
                String newmsg =  "Course: " + s.getCourse().getCode()+ Integer.toString(s.getCourse().getNumber())+ "\n" +
                        "Section: " + s.getName() + "\n" +
                        "CourseTime: " + s.getCourseTimeString() + "\n" +
                        "Building: " + s.getBuilding().getName();

                LatLon ll = b.getLatLon();
                Double newlat = ll.getLatitude()+ 0.000100;
                Double newlng = ll.getLongitude() + 0.000100;
                LatLon newll = new LatLon(newlat,newlng);
                Building nb = new Building(lastBuilding.getName(),newll);
                plotABuilding(nb, title, newmsg, icon);
            }
            else {
                String msg = "Course: " + s.getCourse().getCode() + Integer.toString(s.getCourse().getNumber()) + "\n" +
                        "Section: " + s.getName() + "\n" +
                        "CourseTime: " + s.getCourseTimeString() + "\n" +
                        "Building: " + s.getBuilding().getName();
                LatLon ll = b.getLatLon();
                Double newlat = ll.getLatitude();
                Double newlng = ll.getLongitude() + 0.000100;
                LatLon newll = new LatLon(newlat,newlng);
                Building nb = new Building(lastBuilding.getName(),newll);
                plotABuilding(nb, title, msg, icon);

            }
            lastBuilding = b;
        }

         om.remove(buildingOverlay);

        for(Section s : sections){
            Building b = s.getBuilding();
            String title = schedulePlot.getName();
            int icon = schedulePlot.getIcon();

            if(lastBuilding.equals(b)){
                String newmsg =  "Course: " + s.getCourse().getCode()+ Integer.toString(s.getCourse().getNumber())+ "\n" +
                        "Section: " + s.getName() + "\n" +
                        "CourseTime: " + s.getCourseTimeString() + "\n" +
                        "Building: " + s.getBuilding().getName();

                LatLon ll = b.getLatLon();
                Double newlat = ll.getLatitude()+ 0.000100;
                Double newlng = ll.getLongitude() + 0.000100;
                LatLon newll = new LatLon(newlat,newlng);
                Building nb = new Building(lastBuilding.getName(),newll);
                removeABuilding(nb, title, newmsg, icon);
            }
            else {
                String msg = "Course: " + s.getCourse().getCode() + Integer.toString(s.getCourse().getNumber()) + "\n" +
                        "Section: " + s.getName() + "\n" +
                        "CourseTime: " + s.getCourseTimeString() + "\n" +
                        "Building: " + s.getBuilding().getName();
                LatLon ll = b.getLatLon();
                Double newlat = ll.getLatitude();
                Double newlng = ll.getLongitude() + 0.000100;
                LatLon newll = new LatLon(newlat,newlng);
                Building nb = new Building(lastBuilding.getName(),newll);
                removeABuilding(nb, title, msg, icon);

            }
            lastBuilding = b;
        }




    }

    private void removeABuilding(Building building, String title, String msg, int drawableToUse) {
        // CPSC 210 Students: You should not need to touch this method
        OverlayItem buildingItem = new OverlayItem(title, msg,
                new GeoPoint(building.getLatLon().getLatitude(), building.getLatLon().getLongitude()));

        //Create new marker
        Drawable icon = this.getResources().getDrawable(drawableToUse);

        //Set the bounding for the drawable
        icon.setBounds(
                0 - icon.getIntrinsicWidth() / 2, 0 - icon.getIntrinsicHeight(),
                icon.getIntrinsicWidth() / 2, 0);

        //Set the new marker to the overlay
        buildingItem.setMarker(icon);
        buildingOverlay.removeItem(buildingItem);
    }
    */



    /**
     * Initialize your schedule by coding it directly in. This is the schedule
     * that will appear on the map when you select "Show My Schedule".
     */
    private void initializeMySchedule() {
        // CPSC 210 Students; Implement this method

        this.studentManager = new StudentManager();
        studentManager.addStudent("LastName","FirstName",ME_ID);
        me = studentManager.get(ME_ID);

        studentManager.addSectionToSchedule(ME_ID, "CPSC", 210, "201");
        studentManager.addSectionToSchedule(ME_ID,"SCIE",220,"200");
        studentManager.addSectionToSchedule(ME_ID,"MATH",200,"201");
        studentManager.addSectionToSchedule(ME_ID,"JAPN",103,"002");
        studentManager.addSectionToSchedule(ME_ID,"BIOL",201,"201");
    }

    /**
     * Helper to create simple alert dialog to display message
     *
     * @param msg message to display in alert dialog
     * @return the alert dialog
     */
    private AlertDialog createSimpleDialog(String msg) {
        // CPSC 210 Students; You should not need to modify this method
        AlertDialog.Builder dialogBldr = new AlertDialog.Builder(getActivity());
        dialogBldr.setMessage(msg);
        dialogBldr.setNeutralButton(R.string.ok, null);

        return dialogBldr.create();
    }

    /**
     * Create the overlay used for buildings. CPSC 210 students, you should not need to
     * touch this method.
     * @return An overlay
     */
    private ItemizedIconOverlay<OverlayItem> createBuildingOverlay() {
        ResourceProxy rp = new DefaultResourceProxyImpl(getActivity());

        ItemizedIconOverlay.OnItemGestureListener<OverlayItem> gestureListener =
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {

            /**
             * Display building description in dialog box when user taps stop.
             *
             * @param index
             *            index of item tapped
             * @param oi
             *            the OverlayItem that was tapped
             * @return true to indicate that tap event has been handled
             */
            @Override
            public boolean onItemSingleTapUp(int index, OverlayItem oi) {

                new AlertDialog.Builder(getActivity())
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface arg0, int arg1) {
                                if (selectedBuildingOnMap != null) {
                                    mapView.invalidate();
                                }
                            }
                        }).setTitle(oi.getTitle()).setMessage(oi.getSnippet())
                        .show();

                selectedBuildingOnMap = oi;
                mapView.invalidate();
                return true;
            }

            @Override
            public boolean onItemLongPress(int index, OverlayItem oi) {
                // do nothing
                return false;
            }
        };

        return new ItemizedIconOverlay<OverlayItem>(
                new ArrayList<OverlayItem>(), getResources().getDrawable(
                R.drawable.ic_action_place), gestureListener, rp);
    }


    /**
     * Create overlay with a specific color
     * @param colour A string with a hex colour value
     */
    private PathOverlay createPathOverlay(String colour) {
        // CPSC 210 Students, you should not need to touch this method
        PathOverlay po = new PathOverlay(Color.parseColor(colour),
                getActivity());
        Paint pathPaint = new Paint();
        pathPaint.setColor(Color.parseColor(colour));
        pathPaint.setStrokeWidth(4.0f);
        pathPaint.setStyle(Paint.Style.STROKE);
        po.setPaint(pathPaint);
        return po;
    }

   // *********************** Asynchronous tasks

    /**
     * This asynchronous task is responsible for contacting the Meetup web service
     * for the schedule of a random student. The task must plot the retrieved
     * student's route for the schedule on the map in a different colour than the "me" schedule
     * or must display a dialog box that a schedule was not retrieved.
     */
    private class GetRandomSchedule extends AsyncTask<Void, Void, SchedulePlot> {

        // Some overview explanation of asynchronous tasks is on the project web page.

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected SchedulePlot doInBackground(Void... params) {
            String randomStudentString = null;
            try {
                randomStudentString = makeRoutingCall(getStudentURL);
            }
            catch(MalformedURLException me){
                me.printStackTrace();
            }catch(IOException ioe){
                ioe.printStackTrace();
            }

            String randomStudentLastName = null;
            String randomStudentFirstName = null;
            Student randomStudent = null;
            int randomStudentID = 0;
            JSONObject obj = null;

            try{
                obj = new JSONObject(randomStudentString);
                randomStudentLastName = obj.getString("LastName");
                randomStudentFirstName = obj.getString("FirstName");
                randomStudentID = obj.getInt("Id");
                randomStudent = studentManager.get(randomStudentID);
                if(randomStudent != null){
                    return null;
                }

                studentManager.addStudent(randomStudentLastName,randomStudentFirstName,randomStudentID);
                randomStudent = studentManager.get(randomStudentID);

                randomStudents.add(randomStudent);


                JSONArray sectionsJSON = obj.getJSONArray("Sections");
                for (int i = 0 ; i < sectionsJSON.length() ; i++){
                    JSONObject o = sectionsJSON.getJSONObject(i);
                    String courseName = o.getString("CourseName");
                    int courseNumber = o.getInt("CourseNumber");
                    String sectionName = o.getString("SectionName");
                    studentManager.addSectionToSchedule(randomStudentID,courseName,courseNumber,sectionName);
                    }
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }

            String dayOfWeek = sharedPreferences.getString("dayOfWeek","MWF");
            SortedSet<Section> sections = randomStudent.getSchedule().getSections(dayOfWeek);

            int icon;
            String randomStudentName = randomStudent.getLastName() + "," + randomStudent.getFirstName();
            String color;
            if(!routeColors.isEmpty()) {
                color = routeColors.get(0);
                routeColors.remove(0);
            }else{
                color = "#ff2646";
            }

            if(!icons.isEmpty()){
                icon = icons.get(0);
                icons.remove(0);
            }else{
                icon = R.drawable.ic_action_place_red;
            }

            SchedulePlot randomStudentSchedulePlot = new SchedulePlot(sections, randomStudentName, color, icon);

            if(!sections.isEmpty()){

                SortedSet<Section> RSsections = randomStudentSchedulePlot.getSections();

                ArrayList<String> froms = new ArrayList<String>();
                ArrayList<String> tos = new ArrayList<String>();

                for (Section s : RSsections) {
                    LatLon l = s.getBuilding().getLatLon();
                    String latlonString = Double.toString(l.getLatitude()) + "," + Double.toString(l.getLongitude());
                    froms.add(latlonString);
                }
                froms.remove(froms.size() - 1);

                for (Section s : RSsections) {
                    LatLon l = s.getBuilding().getLatLon();
                    String latlonString = Double.toString(l.getLatitude()) + "," + Double.toString(l.getLongitude());
                    tos.add(latlonString);
                }
                tos.remove(0);

                ArrayList<String> httprequests = new ArrayList<String>();
                String begin = "http://open.mapquestapi.com/directions/v2/route?key=Fmjtd%7Cluu82lu1n1%2Cbs%3Do5-948wlz&outFormat=json&routeType=pedestrian&timeType=0&enhancedNarrative=false&shapeFormat=raw&generalize=0&locale=en_US&unit=k&from=";
                String mid =  "&to=";
                int i1 = 1;
                int i2 = 1;

                for (String f : froms) {
                    for (String t : tos) {
                        if (i1 == i2) {
                            String httprequest = begin + f + mid + t;
                            httprequests.add(httprequest);
                        }
                        i2 = i2 + 1;
                    }
                    i1 = i1 + 1;
                    i2 = 1;
                }

                ArrayList<String> response = new ArrayList<String>();
                for (String hr : httprequests) {
                    try {
                        String t = makeRoutingCall(hr);
                        response.add(t);
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                ArrayList<GeoPoint> route = new ArrayList<GeoPoint>();
                for (String s : response) {
                    GeoPoint geopoint = null;
                    try {
                        JSONObject obj2 = new JSONObject(s);
                        JSONArray ary = obj2.getJSONObject("route").getJSONObject("shape").getJSONArray("shapePoints");
                        Double lat = null;
                        Double lng = null;
                        for(int i = 0; i < ary.length() ; i++) {
                            if (i % 2 == 0) {
                                lat = ary.getDouble(i);
                            } else {
                                lng = ary.getDouble(i) + 0.000100;
                                geopoint = new GeoPoint(lat, lng);
                                route.add(geopoint);
                            }
                        }
                    } catch (org.json.JSONException e) {
                        e.printStackTrace();
                    }
                }
                randomStudentSchedulePlot.setRoute(route);
            }

                return randomStudentSchedulePlot;

            // CPSC 210 Students: You must complete this method. It needs to
            // contact the Meetup web service to get a random student's schedule.
            // If it is successful in retrieving a student and their schedule,
            // it needs to remember the student in the randomStudent field
            // and it needs to create and return a schedulePlot object with
            // all relevant information for being ready to retrieve the route
            // and plot the route for the schedule. If no random student is
            // retrieved, return null.
            //
            // Note, leave all determination of routing and plotting until
            // the onPostExecute method below.

        }



        @Override
        protected void onPostExecute(SchedulePlot schedulePlot) {
            if(schedulePlot == null){
                AlertDialog aDialog = createSimpleDialog("Retrieved same randomStudent, please try agian");
                aDialog.show();
            }else{
                SortedSet<Section> sections = schedulePlot.getSections();
                if(sections.isEmpty()){
                    AlertDialog aDialog = createSimpleDialog("no class today!");
                    aDialog.show();
                    }else{
                        plotBuildingsForRandomStudent(schedulePlot);

                        PathOverlay po = createPathOverlay(schedulePlot.getColourOfLine());
                        List<GeoPoint> geopoints = schedulePlot.getRoute();
                        int classSize = geopoints.size();


                       if (classSize == 0){
                            AlertDialog aDialog = createSimpleDialog("Only one class today");
                            aDialog.show();
                        }else{
                            for (GeoPoint g : geopoints) {
                                if(g.equals(null)){
                                    AlertDialog aDialog = createSimpleDialog("The GeoPoint is empty");
                                    aDialog.show();
                                }else {
                                    po.addPoint(g);
                                }
                            }
                        }

                        scheduleOverlay.add(po);
                        OverlayManager om = mapView.getOverlayManager();
                        om.addAll(scheduleOverlay);
                        mapView.invalidate();


                        // CPSC 210 students: When this method is called, it will be passed
                        // whatever schedulePlot object you created (if any) in doBackground
                        // above. Use it to plot the route.
                        schedulePlotLastRandomStudent = schedulePlot;
                        pathOverlayLastRandomStudent = po;
                }
             }
        }
    }

    /**
     * This asynchronous task is responsible for contacting the MapQuest web service
     * to retrieve a route between the buildings on the schedule and for plotting any
     * determined route on the map.
     */
    private class GetRoutingForSchedule extends AsyncTask<SchedulePlot, Void, SchedulePlot> {

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected SchedulePlot doInBackground(SchedulePlot... params) {

            // The params[0] element contains the schedulePlot object
            SchedulePlot scheduleToPlot = params[0];

            SortedSet<Section> sections = scheduleToPlot.getSections();
            if(!sections.isEmpty()){

                ArrayList<String> froms = new ArrayList<String>();
                ArrayList<String> tos = new ArrayList<String>();

                for (Section s : sections){
                    LatLon l = s.getBuilding().getLatLon();
                    String latlonString = Double.toString(l.getLatitude())+","+Double.toString(l.getLongitude());
                    froms.add(latlonString);
                    tos.add(latlonString);
                }
                froms.remove(froms.size() - 1);


                for (Section s : sections){
                    LatLon l = s.getBuilding().getLatLon();
                    String latlonString = Double.toString(l.getLatitude())+","+Double.toString(l.getLongitude());
                    tos.add(latlonString);
                }
                tos.remove(0);

                ArrayList<String> httprequests = new ArrayList<String>();
                String begin = "http://open.mapquestapi.com/directions/v2/route?key=Fmjtd%7Cluu82lu1n1%2Cbs%3Do5-948wlz&outFormat=json&routeType=pedestrian&timeType=0&enhancedNarrative=false&shapeFormat=raw&generalize=0&locale=en_US&unit=k&from=";
                String mid =  "&to=";

                int i1 = 1;
                int i2 = 1;

                for(String f : froms){
                    for (String t : tos){
                        if(i1 == i2){
                            String httprequest = begin + f + mid + t ;
                            httprequests.add(httprequest);
                        }
                        i2 = i2 + 1;
                    }
                    i1 = i1 + 1;
                    i2 = 1;
                }

                ArrayList<String> response = new ArrayList<String>();
                for (String hr : httprequests){
                    try {
                        String t = makeRoutingCall(hr);
                        response.add(t);
                    }catch (Exception e){
                        e.printStackTrace();
                        return null;
                    }
                }

                ArrayList<GeoPoint> route = new ArrayList<GeoPoint>();
                for (String s : response){
                    GeoPoint geopoint = null;
                    try {
                        JSONObject obj = new JSONObject(s);
                        JSONArray ary = obj.getJSONObject("route").getJSONObject("shape").getJSONArray("shapePoints");
                        Double lat = null;
                        Double lng = null;
                        for(int i = 0; i < ary.length() ; i++) {
                            if (i % 2 == 0) {
                                lat = ary.getDouble(i);
                            } else {
                                lng=ary.getDouble(i);
                                geopoint = new GeoPoint(lat, lng);
                                route.add(geopoint);
                            }
                        }
                    }catch(org.json.JSONException e){
                        e.printStackTrace();
                        return null;
                    }
                }

                scheduleToPlot.setRoute(route);
            }
            return scheduleToPlot;

            // CPSC 210 Students: Complete this method. This method should
            // call the MapQuest webservice to retrieve a List<GeoPoint>
            // that forms the routing between the buildings on the
            // schedule. The List<GeoPoint> should be put into
            // scheduleToPlot object.
        }


        /**
         * An example helper method to call a web service
         */


        @Override
        protected void onPostExecute(SchedulePlot schedulePlot) {

            // CPSC 210 Students: This method should plot the route onto the map
            // with the given line colour specified in schedulePlot. If there is
            // no route to plot, a dialog box should be displayed.
            if(schedulePlot == null){
                AlertDialog aDialog = createSimpleDialog("route of mySchedule is not receive or the url call receive wrong data");
                aDialog.show();
            }else{
                    SortedSet<Section> sections = schedulePlot.getSections();
                    if (sections.isEmpty()) {
                    AlertDialog aDialog = createSimpleDialog("The schedule is empty");
                    aDialog.show();
                    } else {
                    plotBuildings(schedulePlot);

                    PathOverlay po = createPathOverlay(schedulePlot.getColourOfLine());
                    List<GeoPoint> geopoints = schedulePlot.getRoute();
                    int classSize = geopoints.size();

                    if (classSize == 0) {
                        AlertDialog aDialog = createSimpleDialog("Only one class today");
                        aDialog.show();
                    } else {
                        for (GeoPoint g : geopoints) {
                            if (g.equals(null)) {
                                AlertDialog aDialog = createSimpleDialog("The GeoPoint is empty");
                                aDialog.show();
                            } else {
                                po.addPoint(g);
                            }
                        }
                    }
                    scheduleOverlay.add(po);
                    OverlayManager om = mapView.getOverlayManager();
                    om.addAll(scheduleOverlay);
                    mapView.invalidate(); // cause map to redraw
                }
            }
        }
                // To actually make something show on the map, you can use overlays.
                // For instance, the following code should show a line on a map
                // PathOverlay po = createPathOverlay("#FFFFFF");
                // po.addPoint(point1); // one end of line
                // po.addPoint(point2); // second end of line

         }




    /**
     * This asynchronous task is responsible for contacting the FourSquare web service
     * to retrieve all places around UBC that have to do with food. It should load
     * any determined places into PlaceFactory and then display a dialog box of how it did
     */
    private class GetPlaces extends AsyncTask<Void, Void, String> {

        protected String doInBackground(Void... params) {
            String respone = null;
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            Date date = new Date();
            String currentDate = dateFormat.format(date);

            try {
                respone = makeRoutingCall("https://api.foursquare.com/v2/venues/explore?ll=49.26114,-123.24686&client_id=ZPDSVZCJSICSCST5Q0A5YTAP0LWXLZGY4TDDHQEQAVYNJ05U&client_secret=WNDULO1XDLKNFJFKV4A1MW1M2QSE52HRX0CNSVVAEN5NOT1J&v="+currentDate+"&section=food&radius=3000");
            }catch(MalformedURLException me){
                me.printStackTrace();
                String s = "MalformedURLException is thrown";
                return s;
            }catch(IOException ioe){
                ioe.printStackTrace();
                String s = "IOExcpetion is thrown";
                return s;
            }
            return respone;
            // CPSC 210 Students: Complete this method to retrieve a string
            // of JSON from FourSquare. Return the string from this method
        }


        protected void onPostExecute(String jSONOfPlaces) {
            PlaceFactory placeFactory = PlaceFactory.getInstance();
            try{
                JSONObject obj = new JSONObject(jSONOfPlaces);
                JSONArray ary = obj.getJSONObject("response").getJSONArray("groups").getJSONObject(0).getJSONArray("items");
                for(int i = 0; i <ary.length(); i++){
                    JSONObject venue = ary.getJSONObject(i).getJSONObject("venue");
                    String name = venue.getString("name");
                    Double lat = venue.getJSONObject("location").getDouble("lat");
                    Double lng = venue.getJSONObject("location").getDouble("lng");
                    LatLon latLon = new LatLon(lat,lng);


                    EatingPlace eatingPlace = new EatingPlace(name,latLon);
                    placeFactory.add(eatingPlace);

                    try{
                        String location = venue.getJSONObject("location").getString("address");
                        eatingPlace.setLocation(location);
                    }catch(JSONException e){
                        e.printStackTrace();
                    }

                    try{
                        String price = venue.getJSONObject("price").getString("message");
                        eatingPlace.setPrice(price);
                    }catch(JSONException e){
                        e.printStackTrace();
                    }

                    try{
                        String contact = venue.getJSONObject("contact").getString("phone");
                        eatingPlace.setPhoneNumber(contact);
                    }catch(JSONException e){
                        e.printStackTrace();
                    }

                    try{
                        String categories = venue.getJSONArray("categories").getJSONObject(0).getString("shortName");
                        eatingPlace.setCategories(categories);
                    }catch(JSONException e){
                        e.printStackTrace();
                    }


                    JSONArray tips = ary.getJSONObject(i).getJSONArray("tips");
                    for(int i2 = 0; i2 < tips.length() ; i2++) {
                        try {
                            JSONObject tip = tips.getJSONObject(i2);
                            String userName = tip.getJSONObject("user").getString("firstName") + " " +
                                              tip.getJSONObject("user").getString("lastName") +
                                              "(" + tip.getJSONObject("user").getString("id") + ")";

                            String text = tip.getString("text");
                            String review = userName + ":" + "\n" + "\"" + text + "\"";
                            eatingPlace.addReviews(review);
                        }catch(JSONException e){
                            e.printStackTrace();
                        }
                    }

                }
                AlertDialog alertDialog = createSimpleDialog("Places have been stored");
                alertDialog.show();
            }catch(JSONException e){
                e.printStackTrace();
                String s1 = "MalformedURLException is thrown";
                String s2 = "IOExcpetion is thrown";
                String s3 = "The place data received from Foursquare is not well-formed";
                AlertDialog alertDialog = createSimpleDialog(s3);

                if(jSONOfPlaces.equals(s1)){
                    alertDialog = createSimpleDialog(s1);
                }else if(jSONOfPlaces.equals(s2)){
                    alertDialog = createSimpleDialog((s2));
                }
                alertDialog.show();
            }


            // CPSC 210 Students: Given JSON from FourQuest, parse it and load
            // PlaceFactory


        }

    }

    private class getMyLocation extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String callString = params[0];
            String toCloestPlaceString = null;
            try {
                toCloestPlaceString = makeRoutingCall(callString);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return  toCloestPlaceString;
        }

        @Override
        protected  void onPostExecute(String str){
            if(str == null){

            }else {
                ArrayList<GeoPoint> route = new ArrayList<GeoPoint>();
                JSONObject obj = null;
                try {
                    obj = new JSONObject(str);
                    JSONArray ary = obj.getJSONObject("route").getJSONObject("shape").getJSONArray("shapePoints");
                    Double lat = null;
                    Double lng = null;
                    GeoPoint geopoint;
                    for (int i = 0; i < ary.length(); i++) {
                        if (i % 2 == 0) {
                            lat = ary.getDouble(i);
                        } else {
                            lng = ary.getDouble(i);
                            geopoint = new GeoPoint(lat, lng);
                            route.add(geopoint);
                        }
                    }
                    PathOverlay po = createPathOverlay("#000000");
                    for (GeoPoint g : route) {
                        if (g.equals(null)) {
                            AlertDialog aDialog = createSimpleDialog("The GeoPoint is empty");
                            aDialog.show();
                        } else {
                            po.addPoint(g);
                        }
                    }
                    OverlayManager om = mapView.getOverlayManager();
                    scheduleOverlay.add(po);
                    om.addAll(scheduleOverlay);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

        }



    }

    /**
     * Initialize the CourseFactory with some courses.
     */
    private void initializeCourses() {
        // CPSC 210 Students: You can change this data if you desire.
        CourseFactory courseFactory = CourseFactory.getInstance();

        Building dmpBuilding = new Building("DMP", new LatLon(49.261474, -123.248060));

        Course cpsc210 = courseFactory.getCourse("CPSC", 210);
        Section aSection = new Section("202", "MWF", "12:00", "12:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);
        aSection = new Section("201", "MWF", "16:00", "16:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);
        aSection = new Section("BCS", "MWF", "12:00", "12:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);

        Course engl222 = courseFactory.getCourse("ENGL", 222);
        aSection = new Section("007", "MWF", "14:00", "14:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        engl222.addSection(aSection);
        aSection.setCourse(engl222);

        Course scie220 = courseFactory.getCourse("SCIE", 220);
        aSection = new Section("200", "MWF", "15:00", "15:50", new Building("Swing", new LatLon(49.262786, -123.255044)));
        scie220.addSection(aSection);
        aSection.setCourse(scie220);

        Course math200 = courseFactory.getCourse("MATH", 200);
        aSection = new Section("201", "MWF", "09:00", "09:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        math200.addSection(aSection);
        aSection.setCourse(math200);

        Course fren102 = courseFactory.getCourse("FREN", 102);
        aSection = new Section("202", "MWF", "11:00", "11:50", new Building("Barber", new LatLon(49.267442,-123.252471)));
        fren102.addSection(aSection);
        aSection.setCourse(fren102);

        Course japn103 = courseFactory.getCourse("JAPN", 103);
        aSection = new Section("002", "MWF", "10:00", "11:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        japn103.addSection(aSection);
        aSection.setCourse(japn103);

        Course scie113 = courseFactory.getCourse("SCIE", 113);
        aSection = new Section("213", "MWF", "13:00", "13:50", new Building("Swing", new LatLon(49.262786, -123.255044)));
        scie113.addSection(aSection);
        aSection.setCourse(scie113);

        Course micb308 = courseFactory.getCourse("MICB", 308);
        aSection = new Section("201", "MWF", "12:00", "12:50", new Building("Woodward", new LatLon(49.264704,-123.247536)));
        micb308.addSection(aSection);
        aSection.setCourse(micb308);

        Course math221 = courseFactory.getCourse("MATH", 221);
        aSection = new Section("202", "TR", "11:00", "12:20", new Building("Klinck", new LatLon(49.266112, -123.254776)));
        math221.addSection(aSection);
        aSection.setCourse(math221);

        Course phys203 = courseFactory.getCourse("PHYS", 203);
        aSection = new Section("201", "TR", "09:30", "10:50", new Building("Hennings", new LatLon(49.266400,-123.252047)));
        phys203.addSection(aSection);
        aSection.setCourse(phys203);

        Course crwr209 = courseFactory.getCourse("CRWR", 209);
        aSection = new Section("002", "TR", "12:30", "13:50", new Building("Geography", new LatLon(49.266039,-123.256129)));
        crwr209.addSection(aSection);
        aSection.setCourse(crwr209);

        Course fnh330 = courseFactory.getCourse("FNH", 330);
        aSection = new Section("002", "TR", "15:00", "16:20", new Building("MacMillian", new LatLon(49.261167,-123.251157)));
        fnh330.addSection(aSection);
        aSection.setCourse(fnh330);

        Course cpsc499 = courseFactory.getCourse("CPSC", 430);
        aSection = new Section("201", "TR", "16:20", "17:50", new Building("Liu", new LatLon(49.267632,-123.259334)));
        cpsc499.addSection(aSection);
        aSection.setCourse(cpsc499);

        Course chem250 = courseFactory.getCourse("CHEM", 250);
        aSection = new Section("203", "TR", "10:00", "11:20", new Building("Klinck", new LatLon(49.266112, -123.254776)));
        chem250.addSection(aSection);
        aSection.setCourse(chem250);

        Course eosc222 = courseFactory.getCourse("EOSC", 222);
        aSection = new Section("200", "TR", "11:00", "12:20", new Building("ESB", new LatLon(49.262866, -123.25323)));
        eosc222.addSection(aSection);
        aSection.setCourse(eosc222);

        Course biol201 = courseFactory.getCourse("BIOL", 201);
        aSection = new Section("201", "TR", "14:00", "15:20", new Building("BioSci", new LatLon(49.263920, -123.251552)));
        biol201.addSection(aSection);
        aSection.setCourse(biol201);
    }

}
