package org.zstack.expon.sdk.volume;

import org.springframework.http.HttpMethod;
import org.zstack.expon.sdk.ExponQuery;
import org.zstack.expon.sdk.ExponQueryRequest;
import org.zstack.expon.sdk.ExponRestRequest;

import java.util.HashMap;
import java.util.Map;

@ExponRestRequest(
        path = "/block/snaps",
        method = HttpMethod.GET,
        responseClass = QueryVolumeSnapshotResponse.class,
        sync = true
)
@ExponQuery(inventoryClass = VolumeModule.class, replyClass = QueryVolumeResponse.class)
public class QueryVolumeSnapshotRequest extends ExponQueryRequest {

    private static final HashMap<String, Parameter> parameterMap = new HashMap<>();

    @Override
    public Map<String, Parameter> getParameterMap() {
        return parameterMap;
    }
}
