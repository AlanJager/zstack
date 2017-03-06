package org.zstack.storage.primary.smp;

import org.zstack.header.message.MessageReply;

/**
 * Created by zouye on 2017/3/6.
 */
public class CheckMountDirReply extends MessageReply {
    private String backupStorageInstallPath;

    public String getBackupStorageInstallPath() {
        return backupStorageInstallPath;
    }

    public void setBackupStorageInstallPath(String backupStorageInstallPath) {
        this.backupStorageInstallPath = backupStorageInstallPath;
    }
}
