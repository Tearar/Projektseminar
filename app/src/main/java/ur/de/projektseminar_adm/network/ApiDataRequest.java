package ur.de.projektseminar_adm.network;

import android.util.Log;

import com.octo.android.robospice.request.retrofit.RetrofitSpiceRequest;

import ur.de.projektseminar_adm.model.ClusterList;

/**
 * Created by Andreas on 27.01.2017.
 */

public class ApiDataRequest extends RetrofitSpiceRequest<ClusterList, CalendarTestRepo> {

    private String TAG = "";
    ApiDataRequestBody apiDataRequestBody;

    public ApiDataRequest(ApiDataRequestBody apiDataRequestBody){
        super(ClusterList.class, CalendarTestRepo.class);
        this.apiDataRequestBody = apiDataRequestBody;
    }

    @Override
    public ClusterList loadDataFromNetwork() throws Exception{
        Log.d(TAG, "Start Request");
        return getService().postData(apiDataRequestBody);
    }


}
