package ur.de.projektseminar_adm.network;

import com.octo.android.robospice.retrofit.RetrofitGsonSpiceService;

/**
 * Created by Andreas on 27.01.2017.
 */

public class CalendarTestService extends RetrofitGsonSpiceService {

    private final static String BASE = "wi2-intern.ur.de";
    private final static int PORT = 8080;
    private final static String BASE_URL = "http://" + BASE + ":" + PORT + "/";

    @Override
    public void onCreate(){
        super.onCreate();
        addRetrofitInterface(CalendarTestRepo.class);
    }

    @Override
    protected String getServerUrl(){
        return BASE_URL;
    }
}
