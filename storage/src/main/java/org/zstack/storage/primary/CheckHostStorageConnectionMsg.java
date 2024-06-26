package org.zstack.storage.primary;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.storage.primary.PrimaryStorageMessage;

import java.util.List;

public class CheckHostStorageConnectionMsg extends NeedReplyMessage implements PrimaryStorageMessage {
    private String primaryStorageUuid;
    private List<String> hostUuids;

    @Override
    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }

    public List<String> getHostUuids() {
        return hostUuids;
    }

    public void setHostUuids(List<String> hostUuids) {
        this.hostUuids = hostUuids;
    }
}