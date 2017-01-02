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
import com.opencsv.CSVWriter;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
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
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends Activity
        implements EasyPermissions.PermissionCallbacks {
    GoogleAccountCredential mCredential;
    private TextView mOutputText;
    private Button mCallApiButton, mWriteToStorageButton, mPickTimeButton, mPickDateButton;
    ProgressDialog mProgress;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    static final int TIME_DIALOG_ID = 0;
    static final int DATE_DIALOG_ID = 1;
    int yearInput, monthInput, dayInput, hourInput, minuteInput;

    private static final String BUTTON_TEXT = "Call Google Calendar API";
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = { CalendarScopes.CALENDAR_READONLY };
    private List<String> eventStrings = new ArrayList<String>();

    /**
     * Create the main activity.
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mOutputText = (TextView)findViewById(R.id.mOutPutText);
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
    protected Dialog onCreateDialog(int id){
        if (id == TIME_DIALOG_ID){
            return new TimePickerDialog(MainActivity.this, kTimePickListener, hourInput, minuteInput, true);
        }
        if (id == DATE_DIALOG_ID){
            return new DatePickerDialog(MainActivity.this, kDatePickListener, yearInput, monthInput, dayInput);
        }
        return null;
    }

    protected TimePickerDialog.OnTimeSetListener kTimePickListener = new TimePickerDialog.OnTimeSetListener() {
        @Override
        public void onTimeSet(TimePicker timePicker, int hourOfDay, int minute) {
            hourInput = hourOfDay;
            minuteInput = minute;
            Toast.makeText(MainActivity.this, "Time: "+hourInput+" : "+minuteInput, Toast.LENGTH_LONG).show();
        }
    };

    protected DatePickerDialog.OnDateSetListener kDatePickListener = new DatePickerDialog.OnDateSetListener() {
        @Override
        public void onDateSet(DatePicker datePicker, int year, int month, int day) {
            yearInput = year;
            monthInput = month+1;
            dayInput = day;
            Toast.makeText(MainActivity.this, "Date: "+dayInput+"."+monthInput+"."+yearInput, Toast.LENGTH_LONG).show();
            showDialog(TIME_DIALOG_ID);
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
        if (! isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (! isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
            new MakeRequestTask(mCredential).execute();
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
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
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
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
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
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
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
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
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

    /**
     * An asynchronous task that handles the Google Calendar API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
        private com.google.api.services.calendar.Calendar mService = null;
        private Exception mLastError = null;

        public MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.calendar.Calendar.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Google Calendar API Android Quickstart")
                    .build();
        }

        /**
         * Background task to call Google Calendar API.
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
         * Fetch a list of the next 10 events from the primary calendar.
         * @return List of Strings describing returned events.
         * @throws IOException
         */
        private List<String> getDataFromApi() throws IOException {
            //Show matching events from up to 1 year ago
            DateTime date = createDateFromInput(yearInput, monthInput, dayInput, hourInput, minuteInput);
            //DateTime date = new DateTime("2016-10-05T00:00:00");
            int dayOfWeek = getDayofWeek(date);

            Events events = mService.events().list("primary")
                    .setMaxResults(20)
                    .setTimeMin(date)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();

            List<Event> items = events.getItems();

            for (Event event : items) {
                DateTime start = event.getStart().getDateTime();
                DateTime end = event.getEnd().getDateTime();
                if (start == null) {
                    // All-day events don't have start times, so just use
                    // the start date.
                    start = event.getStart().getDate();
                }

                //int dayOfWeekEvent = getDayofWeek(start);

                //Returns only the events where dayOfWeek matches
                //if(dayOfWeek == dayOfWeekEvent) {

                    String formattedOutput = formatOutput(event.getSummary(), start, end, event.getLocation());
                    eventStrings.add(formattedOutput);
                    //eventStrings.add(String.format("%s, %s, %s, %s", event.getSummary(), start, end, event.getLocation()));
                }
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
                //output.add(0, "Data retrieved using the Google Calendar API:");
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

    //Creates a CSV file on the storage
    private void writeCSV(List<String>eventStrings){

        String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        String fileName = "CalendarData.csv";
        String filePath = baseDir + File.separator + fileName;
        CSVWriter writer = null;

        File file = new File(filePath);
        if(file.exists()){
            Log.d("FileExists", "FileExists");
            file.delete();
        }
        //Convert List to Array
        String[]stringArray = new String[eventStrings.size()];
        stringArray = eventStrings.toArray(stringArray);

        try{
            writer = new CSVWriter(new FileWriter(filePath), '\n');
            writer.writeNext(stringArray);
            writer.close();
            Toast.makeText(this, fileName + " created", Toast.LENGTH_LONG).show();
        }
        catch (IOException e){
            Log.d("Couldnt write to Phone", "Error");
        }

    }
    //Formats the API-Output
    private String formatOutput(String summary, DateTime start, DateTime end, String location) {
        StringBuilder completeString = new StringBuilder();
        completeString.append(summary).append(", ");
        int dayOfWeek = getDayofWeek(start);
        completeString.append(dayOfWeek).append(", ");

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
        if(location != null) {
            double lat = latLng.latitude;
            double lng = latLng.longitude;
            completeString.append(lat).append(", ").append(lng);
        }

        String newString = completeString.toString();
        return newString;

        }

    //Returns day of week as int
    private int getDayofWeek(DateTime date){
        String dateString = date.toString();

        String year = dateString.substring(0, 4);
        int intYear = Integer.valueOf(year);

        String month = dateString.substring(5, 7);
        int intMonth = Integer.valueOf(month);

        String day = dateString.substring(8, 10);
        int intDay = Integer.valueOf(day);

        java.util.Calendar calendar = new GregorianCalendar(intYear, intMonth-1, intDay);
        int result = calendar.get(java.util.Calendar.DAY_OF_WEEK);

        return result;
    }

    //Creates DateTime format from user-input time and date
    private DateTime createDateFromInput(int year, int month, int day, int hour, int minute){
        //"2016-10-05T00:00:00"
        StringBuilder buildDate = new StringBuilder();
        buildDate.append(year-1).append("-");
        if (month < 10){
            buildDate.append("0");
        }
        buildDate.append(month).append("-");
        if(day <10){
            buildDate.append("0");
        }
        buildDate.append(day).append("T");
        if(hour < 10){
            buildDate.append("0");
        }
        buildDate.append(hour).append(":");
        if(minute < 10){
            buildDate.append("0");
        }
        buildDate.append(minute).append(":00");
        String dateString = buildDate.toString();
        DateTime date = new DateTime(dateString);
        return date;
    }

    private void setupButtons(){
        mCallApiButton = (Button)findViewById(R.id.mCallApiButton);
        mCallApiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(yearInput == 0 || monthInput == 0 || dayInput == 0 /*|| hourInput == 0 || minuteInput == 0*/){
                    Toast.makeText(MainActivity.this, "Enter Time and/or Date first", Toast.LENGTH_LONG).show();
                }
                else {
                    mCallApiButton.setEnabled(false);
                    getResultsFromApi();
                    mCallApiButton.setEnabled(true);
                }
            }
        });
        mWriteToStorageButton = (Button)findViewById(R.id.mWriteToStorageButton);
        mWriteToStorageButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                if(eventStrings.isEmpty()){
                    Toast.makeText(MainActivity.this, "There is nothing to write", Toast.LENGTH_LONG).show();
                }
                else {
                    mWriteToStorageButton.setEnabled(false);
                    writeCSV(eventStrings);
                    mWriteToStorageButton.setEnabled(true);
                }
            }
        });
        mPickTimeButton = (Button)findViewById(R.id.mPickTimeButton);
        mPickTimeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showDialog(TIME_DIALOG_ID);
                    }
                }
        );
        mPickDateButton = (Button)findViewById(R.id.mPickDateButton);
        mPickDateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDialog(DATE_DIALOG_ID);
            }
        });
    }

    //Returns a LatLng Object (coordinates) from a certain adress
    private LatLng getLocationFromAddress(Context context, String place){

        Geocoder coder = new Geocoder(context);
        List<Address> address;
        LatLng p1 = null;

        try {
            address = coder.getFromLocationName(place, 5);
            if (address == null) {
                return null;
            }
            Address location = address.get(0);
            location.getLatitude();
            location.getLongitude();

            p1 = new LatLng(location.getLatitude(), location.getLongitude() );

        } catch (Exception ex) {

            ex.printStackTrace();
        }

        return p1;
    }

    }
