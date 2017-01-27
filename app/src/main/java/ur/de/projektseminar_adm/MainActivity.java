package ur.de.projektseminar_adm;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.model.LatLng;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.client.util.DateTime;

import com.google.api.services.calendar.model.*;
import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.DurationInMillis;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.RequestListener;
import com.opencsv.CSVWriter;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;
import ur.de.projektseminar_adm.model.Cluster;
import ur.de.projektseminar_adm.model.ClusterList;
import ur.de.projektseminar_adm.network.ApiDataRequest;
import ur.de.projektseminar_adm.network.ApiDataRequestBody;
import ur.de.projektseminar_adm.network.CalendarTestService;

import static com.google.api.client.http.HttpMethods.POST;

public class MainActivity extends Activity implements EasyPermissions.PermissionCallbacks {
    GoogleAccountCredential mCredential;
    private TextView mOutputText;
    private Button mCallApiButton, mWriteToStorageButton, mPickEndTimeButton, mPickDateAndStartTimeButton;
    private ProgressDialog mProgress;
    private Exception mLastError = null;
    private Calendar mService = null;
    private StringBuilder postString;
    private String stringToPost;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    static final int START_TIME_DIALOG_ID = 0;
    static final int END_TIME_DIALOG_ID = 2;
    static final int DATE_DIALOG_ID = 1;
    static final int CALL_API_ID = 1;
    static final int INSERT_EVENT_ID = 2;
    int yearInput, monthInput, dayInput, startHourInput, startMinuteInput, endHourInput, endMinuteInput;

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {CalendarScopes.CALENDAR};
    private List<String> eventStrings = new ArrayList<String>();
    private ArrayList<Cluster> localClusterList;

    /**
     * Create the main activity.
     *
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mOutputText = (TextView) findViewById(R.id.mOutPutText);
        mOutputText.setMovementMethod(new ScrollingMovementMethod());
        mOutputText.setText("");
        setupButtons();
        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling Google Calendar API ...");

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        }

    @Override
    protected Dialog onCreateDialog(int id) {

        java.util.Calendar c = java.util.Calendar.getInstance();
        switch (id) {
            case START_TIME_DIALOG_ID:
                startHourInput = c.get(java.util.Calendar.HOUR_OF_DAY);
                startMinuteInput = c.get(java.util.Calendar.MINUTE);
                return new TimePickerDialog(MainActivity.this, kStartTimePickListener, startHourInput, startMinuteInput, true);
            case DATE_DIALOG_ID:
                yearInput = c.get(java.util.Calendar.YEAR);
                monthInput = c.get(java.util.Calendar.MONTH);
                dayInput = c.get(java.util.Calendar.DAY_OF_MONTH);
                return new DatePickerDialog(MainActivity.this, kDatePickListener, yearInput, monthInput, dayInput);
            case END_TIME_DIALOG_ID:
                endHourInput = c.get(java.util.Calendar.HOUR_OF_DAY);
                endMinuteInput = c.get(java.util.Calendar.MINUTE);
                return new TimePickerDialog(MainActivity.this, kEndTimePickListener, endHourInput, endMinuteInput, true);
            default:
                return null;
        }

    }

    protected DatePickerDialog.OnDateSetListener kDatePickListener = new DatePickerDialog.OnDateSetListener() {
        @Override
        public void onDateSet(DatePicker datePicker, int year, int month, int day) {
            yearInput = year;
            monthInput = month + 1;
            dayInput = day;
            Toast.makeText(MainActivity.this, "Date: " + dayInput + "." + monthInput + "." + yearInput, Toast.LENGTH_LONG).show();
            showDialog(START_TIME_DIALOG_ID);
        }
    };

    protected TimePickerDialog.OnTimeSetListener kStartTimePickListener = new TimePickerDialog.OnTimeSetListener() {
        @Override
        public void onTimeSet(TimePicker timePicker, int hourOfDay, int minute) {
            startHourInput = hourOfDay;
            startMinuteInput = minute;
            Toast.makeText(MainActivity.this, "Starting Time: " + startHourInput + ":" + startMinuteInput, Toast.LENGTH_LONG).show();
        }
    };

    protected TimePickerDialog.OnTimeSetListener kEndTimePickListener = new TimePickerDialog.OnTimeSetListener() {
        @Override
        public void onTimeSet(TimePicker timePicker, int hourOfDay, int minute) {
            endHourInput = hourOfDay;
            endMinuteInput = minute;
            Toast.makeText(MainActivity.this, "Ending Time: " + endHourInput + ":" + endMinuteInput, Toast.LENGTH_LONG).show();
            //insertEventThroughApi();
        }
    };

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount(CALL_API_ID);
        } else if (!isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
            new CallApiTask(mCredential).execute();
        }
    }

    private void insertEventThroughApi(){
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount(INSERT_EVENT_ID);
        } else if (!isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
            new InsertEventTask(mCredential).execute();
        }

    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount(int id) {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                if(id == CALL_API_ID){
                    getResultsFromApi();
                }
                if(id == INSERT_EVENT_ID){
                    insertEventThroughApi();
                }
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     *
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode  code indicating the result of the incoming
     *                    activity result.
     * @param data        Intent (containing result data) returned by incoming
     *                    activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mOutputText.setText(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     *
     * @param requestCode  The request code passed in
     *                     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     *
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     *
     * @return true if Google Play Services is available and up to
     * date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        final int connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this);

        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        final int connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }

    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     *
     * @param connectionStatusCode code describing the presence (or lack of)
     *                             Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    private class InsertEventTask extends AsyncTask<Void, Void, String> {
        //private Calendar mService = null;
        //private Exception mLastError = null;

        public InsertEventTask(GoogleAccountCredential credential){
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new Calendar.Builder(transport, jsonFactory, credential).setApplicationName("Calendar Test").build();
        }

        @Override
        protected String doInBackground(Void... params){

            try {
                addNewEventToCalendar();
            }
            catch (Exception e){
                mLastError = e;
                cancel(true);
                return null;
            }
            return "Executed";
        }

        private void addNewEventToCalendar() throws IOException {
            DateTime start = createDateFromInput(yearInput, monthInput, dayInput, startHourInput, startMinuteInput);
            EventDateTime eventStart = new EventDateTime().setDateTime(start).setTimeZone("Europe/Berlin");
            DateTime end = createDateFromInput(yearInput, monthInput, dayInput, endHourInput, endMinuteInput);
            EventDateTime eventEnd = new EventDateTime().setDateTime(end).setTimeZone("Europe/Berlin");
            Event event = new Event()
                    .setSummary("Test")
                    .setLocation("Regensburg")
                    .setDescription("TestBeschreibung")
                    .setStart(eventStart)
                    .setEnd(eventEnd);
            String calendarId = "primary";

            event = mService.events().insert(calendarId, event).execute();
        }

        @Override
        protected void onCancelled() {
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(), MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    mOutputText.setText("The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                mOutputText.setText("Request cancelled.");
            }
        }

    }

    /**
     * An asynchronous task that handles the Google Calendar API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class CallApiTask extends AsyncTask<Void, Void, List<String>> {
        //private Calendar mService = null;
        //private Exception mLastError = null;

        public CallApiTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new Calendar.Builder(transport, jsonFactory, credential)
                    .setApplicationName("Calendar Test")
                    .build();
        }

        /**
         * Background task to call Google Calendar API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch a list of the next 500 events from the primary calendar.
         *
         * @return List of Strings describing returned events.
         * @throws IOException
         */
        private List<String> getDataFromApi() throws IOException {
            //Show matching events from up to 1 year agoo
            DateTime date = createDateFromInput(yearInput, monthInput, dayInput, startHourInput, startMinuteInput);
            //DateTime date = new DateTime("2016-10-05T00:00:00");
            //int dayOfWeek = getDayOfWeek(date);

            Events events = mService.events().list("primary")
                    .setMaxResults(500)
                    .setTimeMin(date)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .setShowDeleted(false)
                    .execute();

            List<Event> items = events.getItems();
            postString = new StringBuilder();

            for (Event event : items) {
                DateTime start = event.getStart().getDateTime();
                DateTime end = event.getEnd().getDateTime();
                if (start == null) {
                    // All-day events don't have start times, so just use the start date.
                    start = event.getStart().getDate();
                }

                //int dayOfWeekEvent = getDayofWeek(start);

                //Returns only the events where dayOfWeek matches
                //if(dayOfWeek == dayOfWeekEvent) {

                String formattedOutput = formatOutput(event.getSummary(), start, end, event.getLocation());
                postString.append(formattedOutput);


                eventStrings.add(formattedOutput);
            }
            stringToPost = postString.toString();
            //Log.d("StringTest", stringToPost);
            //}
            return eventStrings;
        }

        @Override
        protected void onPreExecute() {
            mOutputText.setText("");
            mProgress.show();
        }

        @Override
        protected void onPostExecute(List<String> output) {
            mProgress.hide();
            if (output == null || output.size() == 0) {
                mOutputText.setText("No results returned.");
            } else {
                output.add(0, "ident, dayOfWeek, weekOfYear, startingTime, endingTime, lat, long");
                mOutputText.setText(TextUtils.join("\n", output));
            }
        }

        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    mOutputText.setText("The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                mOutputText.setText("Request cancelled.");
            }
        }


    }
    public void postStringToWebservice() {

        ApiDataRequestBody apiDataRequestBody = new ApiDataRequestBody(stringToPost);
        ApiDataRequest adr = new ApiDataRequest(apiDataRequestBody);

        SpiceManager spiceManager = new SpiceManager(CalendarTestService.class);
        spiceManager.execute(adr, "adr", DurationInMillis.ONE_SECOND * 5, new RequestListener<ClusterList>() {
            @Override
            public void onRequestFailure(SpiceException spiceException) {
                Toast.makeText(MainActivity.this, "failure", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRequestSuccess(ClusterList clusterList) {
                localClusterList = new ArrayList<Cluster>(clusterList.getClusterList());
                Log.d("ClusterTest", localClusterList.get(0).toString());

            }
        });

        /*String stringToPost = postString.toString();
        URL url = new URL("http://test.com");
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        try{
            connection.setRequestMethod("POST");
            connection.setChunkedStreamingMode(0);
            OutputStream out = new BufferedOutputStream(connection.getOutputStream());


        }finally {
            connection.disconnect();
        }*/

    }

    //Creates a CSV file on the storage
    private void writeCSV(List<String> eventStrings) {

        String baseDir = Environment.getExternalStorageDirectory().getAbsolutePath();
        String fileName = "CalendarData.csv";
        String filePath = baseDir + File.separator + fileName;
        CSVWriter writer = null;

        File file = new File(filePath);
        if (file.exists()) {
            file.delete();
        }
        //Convert List to Array
        String[] stringArray = new String[eventStrings.size()];
        stringArray = eventStrings.toArray(stringArray);

        try {
            writer = new CSVWriter(new FileWriter(filePath), '\n', CSVWriter.NO_QUOTE_CHARACTER);
            writer.writeNext(stringArray);
            writer.close();
            Toast.makeText(this, fileName + " created", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.d("Couldnt write to Phone", "Error");
        }

    }

    //Formats the API-Output
    private String formatOutput(String summary, DateTime start, DateTime end, String location) {
        StringBuilder completeString = new StringBuilder();
        completeString.append(summary).append(", ");
        int dayOfWeek = getDayofWeek(start);
        completeString.append(dayOfWeek).append(", ");
        int weekOfYear = getWeekOfYear(start);
        completeString.append(weekOfYear).append(", ");

        StringBuilder buildStartString = new StringBuilder();
        String startString = start.toString();
        buildStartString.append(startString.substring(11, 13)).append(".").append(startString.substring(14, 16));
        startString = buildStartString.toString();
        completeString.append(startString).append(", ");

        String endString = end.toString();
        StringBuilder buildEndString = new StringBuilder();
        buildEndString.append(endString.substring(11, 13)).append(".").append(endString.substring(14, 16));
        endString = buildEndString.toString();
        completeString.append(endString).append(", ");
        LatLng latLng = getLocationFromAddress(MainActivity.this, location);
        if (location != null) {
            double lat = latLng.latitude;
            double lng = latLng.longitude;
            completeString.append(lat).append(", ").append(lng);
        }

        String newString = completeString.toString();
        return newString;

    }

    //Returns day of week as int
    private int getDayofWeek(DateTime date) {
        String dateString = date.toString();

        String year = dateString.substring(0, 4);
        int intYear = Integer.valueOf(year);

        String month = dateString.substring(5, 7);
        int intMonth = Integer.valueOf(month);

        String day = dateString.substring(8, 10);
        int intDay = Integer.valueOf(day);

        java.util.Calendar calendar = new GregorianCalendar(intYear, intMonth - 1, intDay);
        int dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK);
        if (dayOfWeek == 1) {
            dayOfWeek = 6;
        } else {
            dayOfWeek = dayOfWeek - 2;
        }
        return dayOfWeek;
    }

    //Returns 0 if week is even, 1 if week is uneven
    private int getWeekOfYear(DateTime date) {
        String dateString = date.toString();

        String year = dateString.substring(0, 4);
        int intYear = Integer.valueOf(year);

        String month = dateString.substring(5, 7);
        int intMonth = Integer.valueOf(month);

        String day = dateString.substring(8, 10);
        int intDay = Integer.valueOf(day);

        java.util.Calendar calendar = new GregorianCalendar(intYear, intMonth - 1, intDay);
        int weekOfYear = calendar.get(java.util.Calendar.WEEK_OF_YEAR);
        switch (weekOfYear % 2) {
            case 0:
                weekOfYear = 0;
                break;
            case 1:
                weekOfYear = 1;
        }

        return weekOfYear;
    }

    //Creates DateTime format from user-input time and date
    private DateTime createDateFromInput(int year, int month, int day, int hour, int minute) {

        Date date = new GregorianCalendar(year - 1, month - 1, day, hour, minute).getTime();
        DateTime dateTime = new DateTime(date, TimeZone.getDefault());

        return dateTime;
    }

    private void setupButtons() {
        mCallApiButton = (Button) findViewById(R.id.mCallApiButton);
        mCallApiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (yearInput == 0 || monthInput == 0 || dayInput == 0) {
                    Toast.makeText(MainActivity.this, "Enter Time and/or Date first", Toast.LENGTH_LONG).show();
                }
                if (yearInput < 1000) {
                    Toast.makeText(MainActivity.this, "Enter a valid year(>1000)", Toast.LENGTH_LONG).show();
                } else {
                    mCallApiButton.setEnabled(false);
                    getResultsFromApi();
                    mCallApiButton.setEnabled(true);
                }
            }
        });
        mWriteToStorageButton = (Button) findViewById(R.id.mWriteToStorageButton);
        mWriteToStorageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (eventStrings.isEmpty()) {
                    Toast.makeText(MainActivity.this, "There is nothing to write", Toast.LENGTH_LONG).show();
                } else {
                    mWriteToStorageButton.setEnabled(false);
                    //writeCSV(eventStrings);
                    /*try {
                        postStringToWebservice();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }*/
                    mWriteToStorageButton.setEnabled(true);
                }
            }
        });
        mPickEndTimeButton = (Button) findViewById(R.id.mPickEndTimeButton);
        mPickEndTimeButton.setOnClickListener(new View.OnClickListener() {
                                                  @Override
                                                  public void onClick(View view) {
                                                      showDialog(END_TIME_DIALOG_ID);
                                                  }
                                              }
        );
        mPickDateAndStartTimeButton = (Button) findViewById(R.id.mPickDateAndStartTimeButton);
        mPickDateAndStartTimeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDialog(DATE_DIALOG_ID);
            }
        });
    }

    //Returns a LatLng Object (coordinates) from a given adress
    private LatLng getLocationFromAddress(Context context, String place) {

        Geocoder coder = new Geocoder(context);
        List<Address> address;
        LatLng latLng = null;

        try {
            address = coder.getFromLocationName(place, 5);
            if (address == null) {
                return null;
            }
            Address location = address.get(0);
            location.getLatitude();
            location.getLongitude();

            latLng = new LatLng(location.getLatitude(), location.getLongitude());

        } catch (Exception ex) {

            ex.printStackTrace();
        }

        return latLng;
    }


}
