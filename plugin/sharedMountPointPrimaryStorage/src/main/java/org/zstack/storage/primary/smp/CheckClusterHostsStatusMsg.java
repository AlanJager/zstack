package org.zstack.storage.primary.smp;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.storage.primary.PrimaryStorage;
import org.zstack.header.storage.primary.PrimaryStorageMessage;

/**
 * Created by zouye on 2017/2/23.
 */
public class CheckClusterHostsStatusMsg extends NeedReplyMessage implements PrimaryStorageMessage {
    private String clusterUuid;
    private String primaryStorageUuid;

    public String getClusterUuid() {
        return clusterUuid;
    }

    public void setClusterUuid(String clusterUuid) {
        this.clusterUuid = clusterUuid;
    }

    @Override
    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }
}