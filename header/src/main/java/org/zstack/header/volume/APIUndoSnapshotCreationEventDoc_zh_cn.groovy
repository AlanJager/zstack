package org.zstack.header.volume

import org.zstack.header.volume.VolumeInventory
import org.zstack.header.errorcode.ErrorCode

doc {

	title "在这里输入结构的名称"

	ref {
		name "inventory"
		path "org.zstack.header.volume.APIUndoSnapshotCreationEvent.inventory"
		desc "null"
		type "VolumeInventory"
		since "4.7.0"
		clz VolumeInventory.class
	}
	field {
		name "success"
		desc ""
		type "boolean"
		since "4.7.0"
	}
	ref {
		name "error"
		path "org.zstack.header.volume.APIUndoSnapshotCreationEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "4.7.0"
		clz ErrorCode.class
	}
}
