package ur.de.projektseminar_adm;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import com.google.android.gms.maps.model.LatLng;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.EventDateTime;

import java.io.Serializable;
import java.util.List;

/**
 * Created by Andreas on 29.11.2016.
 */

public class CustomEvent implements Serializable {

    private String ident, location;
    private EventDateTime startTime, endTime;

    public String getIdent(){
        return ident;
    }

    public String getLocation(){
        return location;
    }

    public EventDateTime getStart(){
        return startTime;
    }

    public EventDateTime getEndTime(){
        return endTime;
    }
    public LatLng getLocationFromAddress(Context context){

        Geocoder coder = new Geocoder(context);
        List<Address> address;
        LatLng p1 = null;

        try {
            address = coder.getFromLocationName(location, 5);
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
