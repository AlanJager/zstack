package org.zstack.header.network.l3

import org.zstack.header.errorcode.ErrorCode

doc {

	title "删除保留地址段"

	field {
		name "success"
		desc ""
		type "boolean"
		since "5.1.0"
	}
	ref {
		name "error"
		path "org.zstack.header.network.l3.APIDeleteReservedIpRangeEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "5.1.0"
		clz ErrorCode.class
	}
}
