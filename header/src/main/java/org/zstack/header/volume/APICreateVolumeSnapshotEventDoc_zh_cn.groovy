package org.zstack.header.volume

import org.zstack.header.errorcode.ErrorCode
import org.zstack.header.storage.snapshot.VolumeSnapshotInventory

doc {

	title "快照清单"

	ref {
		name "error"
		path "org.zstack.header.volume.APICreateVolumeSnapshotEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "0.6"
		clz ErrorCode.class
	}
	ref {
		name "inventory"
		path "org.zstack.header.volume.APICreateVolumeSnapshotEvent.inventory"
		desc "快照清单"
		type "VolumeSnapshotInventory"
		since "0.6"
		clz VolumeSnapshotInventory.class
	}
}
