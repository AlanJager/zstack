package org.zstack.core.scheduler;

import org.zstack.header.core.scheduler.APICreateSchedulerJobMessage;
import org.zstack.header.core.scheduler.SchedulerJobInventory;
import org.zstack.header.core.scheduler.SchedulerJobVO;

/**
 * Created by AlanJager on 2017/6/9.
 */
public class VmInstanceSchedulerJobFactory implements SchedulerJobFactory {
    public static final String schedulerJobType = SchedulerConstant.VM_SCHEDULER_TYPE;

    @Override
    public SchedulerJobVO createSchedulerJob(SchedulerJobVO vo, APICreateSchedulerJobMessage msg) {

    }

    @Override
    public SchedulerJobVO getSchedulerJob(SchedulerJobVO vo) {
        return null;
    }

    @Override
    public String getSchedulerJobType() {
        return null;
    }


    @Override
    public SchedulerJobInventory getSchedulerJobInventory(String uuid) {
        return null;
    }
}
