package org.zstack.header.image

import java.lang.Long
import java.lang.Boolean
import java.sql.Timestamp
import org.zstack.header.image.ImageBackupStorageRefInventory
import org.zstack.header.tag.SystemTagInventory

doc {

	title "镜像清单"

	field {
		name "uuid"
		desc "资源的UUID，唯一标示该资源"
		type "String"
		since "0.6"
	}
	field {
		name "name"
		desc "资源名称"
		type "String"
		since "0.6"
	}
	field {
		name "description"
		desc "资源的详细描述"
		type "String"
		since "0.6"
	}
	field {
		name "state"
		desc "镜像的启动状态"
		type "String"
		since "0.6"
	}
	field {
		name "status"
		desc "镜像的就绪状态"
		type "String"
		since "0.6"
	}
	field {
		name "size"
		desc "镜像大小"
		type "Long"
		since "0.6"
	}
	field {
		name "actualSize"
		desc "镜像真实容量"
		type "Long"
		since "0.6"
	}
	field {
		name "md5Sum"
		desc "镜像的md5值"
		type "String"
		since "0.6"
	}
	field {
		name "url"
		desc "镜像的URL地址"
		type "String"
		since "0.6"
	}
	field {
		name "mediaType"
		desc "镜像的类型"
		type "String"
		since "0.6"
	}
	field {
		name "guestOsType"
		desc "镜像对应的客户机操作系统类型"
		type "String"
		since "0.6"
	}
	field {
		name "type"
		desc "内部使用字段"
		type "String"
		since "0.6"
	}
	field {
		name "platform"
		desc "镜像的系统平台"
		type "String"
		since "4.1.0"
	}
	field {
		name "architecture"
		desc "镜像CPU架构"
		type "String"
		since "4.1.0"
	}
	field {
		name "format"
		desc "镜像的格式，比如：raw"
		type "String"
		since "0.6"
	}
	field {
		name "system"
		desc "标识是否为系统镜像"
		type "Boolean"
		since "0.6"
	}
	field {
		name "virtio"
		desc "是否支持virtio"
		type "Boolean"
		since "0.6"
	}
	field {
		name "createDate"
		desc "创建时间"
		type "Timestamp"
		since "0.6"
	}
	field {
		name "lastOpDate"
		desc "最后一次修改时间"
		type "Timestamp"
		since "0.6"
	}
	ref {
		name "backupStorageRefs"
		path "org.zstack.header.image.ImageInventory.backupStorageRefs"
		desc "null"
		type "List"
		since "0.6"
		clz ImageBackupStorageRefInventory.class
	}
}
