package ur.de.projektseminar_adm.network;

import retrofit.http.Body;
import retrofit.http.POST;
import ur.de.projektseminar_adm.model.ClusterList;

/**
 * Created by Andreas on 27.01.2017.
 */

public interface CalendarTestRepo {

    @POST("/api/test")
    ClusterList postData(@Body ApiDataRequestBody apiDataRequestBody);

}


