package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.core.scheduler.APICreateSchedulerJobMessage;
import org.zstack.header.core.scheduler.SchedulerVO;
import org.zstack.header.host.HostVO;
import org.zstack.header.identity.Action;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.notification.ApiNotification;
import org.zstack.header.rest.RestRequest;

/**
 * Created by root on 7/30/16.
 */
@Action(category = VmInstanceConstant.ACTION_CATEGORY)
@RestRequest(
        path = "/vm-instances/{vmUuid}/schedulers/starting",
        method = HttpMethod.POST,
        parameterName = "params",
        responseClass = APICreateStartVmInstanceSchedulerEvent.class
)
public class APICreateStartVmInstanceSchedulerJobMsg extends APICreateSchedulerJobMessage implements VmInstanceMessage {
    /**
     * @desc vm uuid
     */
    @APIParam(resourceType = VmInstanceVO.class, checkAccount = true, operationTarget = true)
    private String vmUuid;

    @APIParam(resourceType = ClusterVO.class, required = false)
    private String clusterUuid;
    @APIParam(resourceType = HostVO.class, required = false)
    private String hostUuid;

    public String getClusterUuid() {
        return clusterUuid;
    }

    public void setClusterUuid(String clusterUuid) {
        this.clusterUuid = clusterUuid;
    }

    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getVmUuid() {
        return vmUuid;
    }

    public void setVmUuid(String vmUuid) {
        this.vmUuid = vmUuid;
    }

    @Override
    public String getVmInstanceUuid() {
        return getVmUuid();
    }

    @Override
    public String getTargetResourceUuid() {
        return getVmUuid();
    }
 
    public static APICreateStartVmInstanceSchedulerJobMsg __example__() {
        APICreateStartVmInstanceSchedulerJobMsg msg = new APICreateStartVmInstanceSchedulerJobMsg();
        msg.setName("create-vm-scheduler");
        msg.setDescription("for test create vm scheduler");
        msg.setVmUuid(uuid());
        return msg;
    }

    public ApiNotification __notification__() {
        APIMessage that = this;

        return new ApiNotification() {
            @Override
            public void after(APIEvent evt) {
                if (evt.isSuccess()) {
                    ntfy("Start vm scheduler was created").resource(((APICreateStartVmInstanceSchedulerEvent) evt).getInventory().getUuid(), SchedulerVO.class.getSimpleName())
                            .messageAndEvent(that, evt).done();
                }
            }
        };
    }

}
