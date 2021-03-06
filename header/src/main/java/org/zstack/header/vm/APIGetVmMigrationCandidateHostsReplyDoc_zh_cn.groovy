package org.zstack.header.vm

import org.zstack.header.errorcode.ErrorCode
import org.zstack.header.host.HostInventory

doc {

    title "可迁移物理机"

    ref {
        name "error"
        path "org.zstack.header.vm.APIGetVmMigrationCandidateHostsReply.error"
        desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null", false
        type "ErrorCode"
        since "0.6"
        clz ErrorCode.class
    }
    ref {
        name "inventories"
        path "org.zstack.header.vm.APIGetVmMigrationCandidateHostsReply.inventories"
        desc "null"
        type "List"
        since "0.6"
        clz HostInventory.class
    }
}
