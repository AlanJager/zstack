package org.zstack.core.scheduler;

import org.zstack.header.core.scheduler.APICreateSchedulerJobMessage;
import org.zstack.header.core.scheduler.SchedulerJobInventory;
import org.zstack.header.core.scheduler.SchedulerJobVO;

/**
 * Created by AlanJager on 2017/6/9.
 */
public interface SchedulerJobFactory {
    SchedulerJobVO createSchedulerJob(SchedulerJobVO vo, APICreateSchedulerJobMessage msg);

    SchedulerJobVO getSchedulerJob(SchedulerJobVO vo);

    String getSchedulerJobType();

    SchedulerJobInventory getSchedulerJobInventory(String uuid);
}
