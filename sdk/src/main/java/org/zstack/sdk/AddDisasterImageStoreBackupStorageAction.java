package org.zstack.sdk;

import java.util.HashMap;
import java.util.Map;
import org.zstack.sdk.*;

public class AddDisasterImageStoreBackupStorageAction extends AbstractAction {

    private static final HashMap<String, Parameter> parameterMap = new HashMap<>();

    private static final HashMap<String, Parameter> nonAPIParameterMap = new HashMap<>();

    public static class Result {
        public ErrorCode error;
        public org.zstack.sdk.AddImageStoreBackupStorageResult value;

        public Result throwExceptionIfError() {
            if (error != null) {
                throw new ApiException(
                    String.format("error[code: %s, description: %s, details: %s]", error.code, error.description, error.details)
                );
            }
            
            return this;
        }
    }

    @Param(required = false, nonempty = false, nullElements = false, emptyString = true, noTrim = false)
    public java.lang.String attachPoint;

    @Param(required = false, nonempty = false, nullElements = false, emptyString = true, noTrim = false)
    public java.lang.String endPoint;

    @Param(required = true, maxLength = 255, nonempty = false, nullElements = false, emptyString = false, noTrim = false)
    public java.lang.String hostname;

    @Param(required = true, maxLength = 255, nonempty = false, nullElements = false, emptyString = true, noTrim = false)
    public java.lang.String username;

    @Param(required = true, maxLength = 255, nonempty = false, nullElements = false, emptyString = true, noTrim = false)
    public java.lang.String password;

    @Param(required = false, nonempty = false, nullElements = false, emptyString = true, numberRange = {1L,65535L}, noTrim = false)
    public int sshPort = 22;

    @Param(required = true, maxLength = 2048, nonempty = false, nullElements = false, emptyString = false, noTrim = false)
    public java.lang.String url;

    @Param(required = true, maxLength = 255, nonempty = false, nullElements = false, emptyString = true, noTrim = false)
    public java.lang.String name;

    @Param(required = false, maxLength = 2048, nonempty = false, nullElements = false, emptyString = true, noTrim = false)
    public java.lang.String description;

    @Param(required = false)
    public java.lang.String type;

    @Param(required = false, nonempty = false, nullElements = false, emptyString = true, noTrim = false)
    public boolean importImages = false;

    @Param(required = false)
    public java.lang.String resourceUuid;

    @Param(required = false)
    public java.util.List systemTags;

    @Param(required = false)
    public java.util.List userTags;

    @Param(required = false)
    public String sessionId;

    @Param(required = false)
    public String accessKeyId;

    @Param(required = false)
    public String accessKeySecret;

    @NonAPIParam
    public long timeout = -1;

    @NonAPIParam
    public long pollingInterval = -1;


    private Result makeResult(ApiResult res) {
        Result ret = new Result();
        if (res.error != null) {
            ret.error = res.error;
            return ret;
        }
        
        org.zstack.sdk.AddImageStoreBackupStorageResult value = res.getResult(org.zstack.sdk.AddImageStoreBackupStorageResult.class);
        ret.value = value == null ? new org.zstack.sdk.AddImageStoreBackupStorageResult() : value; 

        return ret;
    }

    public Result call() {
        ApiResult res = ZSClient.call(this);
        return makeResult(res);
    }

    public void call(final Completion<Result> completion) {
        ZSClient.call(this, new InternalCompletion() {
            @Override
            public void complete(ApiResult res) {
                completion.complete(makeResult(res));
            }
        });
    }

    protected Map<String, Parameter> getParameterMap() {
        return parameterMap;
    }

    protected Map<String, Parameter> getNonAPIParameterMap() {
        return nonAPIParameterMap;
    }

    protected RestInfo getRestInfo() {
        RestInfo info = new RestInfo();
        info.httpMethod = "POST";
        info.path = "/backup-storage/image-store/disaster";
        info.needSession = true;
        info.needPoll = true;
        info.parameterName = "params";
        return info;
    }

}
