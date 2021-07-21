package org.zstack.network.service.lb

import org.zstack.header.errorcode.ErrorCode
import org.zstack.network.service.lb.APIChangeAccessControlListServerGroupEvent.LoadBalancerListerAcl

doc {

	title "在这里输入结构的名称"

	field {
		name "success"
		desc ""
		type "boolean"
		since "0.6"
	}
	ref {
		name "error"
		path "org.zstack.network.service.lb.APIChangeAccessControlListServerGroupEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "0.6"
		clz ErrorCode.class
	}
	ref {
		name "inventory"
		path "org.zstack.network.service.lb.APIChangeAccessControlListServerGroupEvent.inventory"
		desc "null"
		type "LoadBalancerListerAcl"
		since "0.6"
		clz LoadBalancerListerAcl.class
	}
}
