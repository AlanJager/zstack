package org.zstack.scheduler;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.MessageSafe;
import org.zstack.core.cloudbus.ResourceDestinationMaker;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.*;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.core.thread.AsyncThread;
import org.zstack.core.thread.SyncThread;
import org.zstack.header.AbstractService;
import org.zstack.header.core.scheduler.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.identity.AccountResourceRefInventory;
import org.zstack.header.identity.ResourceOwnerPreChangeExtensionPoint;
import org.zstack.header.managementnode.ManagementNodeChangeListener;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.header.message.Message;
import org.zstack.header.vm.*;
import org.zstack.identity.AccountManager;
import org.zstack.utils.Utils;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.sql.Timestamp;
import java.util.*;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.JobKey.jobKey;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.TriggerKey.triggerKey;

/**
 * Created by Mei Lei on 6/22/16.
 */
public class SchedulerFacadeImpl extends AbstractService implements SchedulerFacade, ManagementNodeReadyExtensionPoint,
        ManagementNodeChangeListener, ResourceOwnerPreChangeExtensionPoint, VmStateChangedExtensionPoint,
        VmBeforeExpungeExtensionPoint, VmInstanceDestroyExtensionPoint, RecoverVmExtensionPoint {
    private static final CLogger logger = Utils.getLogger(SchedulerFacadeImpl.class);

    @Autowired
    private transient CloudBus bus;
    @Autowired
    private transient ErrorFacade errf;
    @Autowired
    protected transient DatabaseFacade dbf;
    @Autowired
    private transient ResourceDestinationMaker destinationMaker;
    @Autowired
    private PluginRegistry pluginRgty;
    @Autowired
    private AccountManager acntMgr;

    private Scheduler scheduler;

    private Map<String, SchedulerJobFactory> schedulerJobFactories = Collections.synchronizedMap(new HashMap<String, SchedulerJobFactory>());

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    protected SchedulerJobVO self;

    protected SchedulerJobInventory getInventory() {
        return SchedulerJobInventory.valueOf(self);
    }

    @Override
    @MessageSafe
    public void handleMessage(Message msg) {
        if (msg instanceof APIDeleteSchedulerJobMsg) {
            handle((APIDeleteSchedulerJobMsg) msg);
        } else if (msg instanceof APIUpdateSchedulerJobMsg) {
            handle((APIUpdateSchedulerJobMsg) msg);
        } else if (msg instanceof APIChangeSchedulerStateMsg) {
            handle((APIChangeSchedulerStateMsg) msg);
        } else if (msg instanceof APICreateSchedulerTriggerMsg) {
            handle((APICreateSchedulerTriggerMsg) msg);
        } else if (msg instanceof APIUpdateSchedulerTriggerMsg) {
            handle((APIUpdateSchedulerTriggerMsg) msg);
        } else if (msg instanceof APIDeleteSchedulerTriggerMsg) {
            handle((APIDeleteSchedulerTriggerMsg) msg);
        } else if (msg instanceof APIAddSchedulerJobToSchedulerTriggerMsg) {
            handle((APIAddSchedulerJobToSchedulerTriggerMsg) msg);
        } else if (msg instanceof APIRemoveSchedulerJobFromSchedulerTriggerMsg) {
            handle((APIRemoveSchedulerJobFromSchedulerTriggerMsg) msg);
        } else if (msg instanceof APICreateSchedulerJobMsg) {
            handle((APICreateSchedulerJobMsg) msg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    private void handle(APICreateSchedulerJobMsg msg) {
        APICreateSchedulerJobEvent evt = new APICreateSchedulerJobEvent(msg.getId());

        final SchedulerJobFactory factory = new SchedulerJobFactoryImpl();
        SchedulerJob job = factory.createSchedulerJob(msg);
        if (job == null) {
            throw new CloudRuntimeException("No suitable scheduler job %s can be found: " + msg.getType());
        }
        
        SchedulerJobVO vo = new SchedulerJobVO();
        vo.setName(msg.getName());
        vo.setDescription(msg.getDescription());
        if (msg.getResourceUuid() != null) {
            vo.setUuid(msg.getResourceUuid());
        } else {
            vo.setUuid(Platform.getUuid());
        }
        vo.setJobClassName(job.getClass().getName());
        vo.setJobData(JSONObjectUtil.toJsonString(job));
        vo.setManagementNodeUuid(Platform.getManagementServerId());
        vo.setTargetResourceUuid(msg.getTargetResourceUuid());
        dbf.persistAndRefresh(vo);

        evt.setInventory(SchedulerJobInventory.valueOf(vo));
        bus.publish(evt);
    }

    private void handle(APIRemoveSchedulerJobFromSchedulerTriggerMsg msg) {
        APIRemoveSchedulerJobFromSchedulerTriggerEvent evt = new APIRemoveSchedulerJobFromSchedulerTriggerEvent(msg.getId());
        try {
            scheduler.unscheduleJob(triggerKey(msg.getSchedulerTriggerUuid(), msg.getSchedulerTriggerUuid() + "." + msg.getSchedulerJobUuid()));
        } catch (SchedulerException se) {
            throw new RuntimeException(se);
        }

        SQL.New(SchedulerJobSchedulerTriggerRefVO.class)
                .eq(SchedulerJobSchedulerTriggerRefVO_.schedulerJobUuid, msg.getSchedulerJobUuid())
                .eq(SchedulerJobSchedulerTriggerRefVO_.schedulerTriggerUuid, msg.getSchedulerTriggerUuid())
                .delete();

        bus.publish(evt);
    }

    private void handle(APIAddSchedulerJobToSchedulerTriggerMsg msg) {
        APIAddSchedulerJobToSchedulerTriggerEvent evt = new APIAddSchedulerJobToSchedulerTriggerEvent(msg.getId());
        // run scheduler
        SchedulerTask task = prepareSchedulerTaskFromMsg(msg);
        if (runScheduler(task)) {
            SchedulerJobSchedulerTriggerRefVO vo = new SchedulerJobSchedulerTriggerRefVO();
            vo.setUuid(Platform.getUuid());
            vo.setSchedulerJobUuid(msg.getSchedulerJobUuid());
            vo.setSchedulerTriggerUuid(msg.getSchedulerTriggerUuid());
            vo.setTaskData(JSONObjectUtil.toJsonString(task));
            vo.setTaskClassName(task.getClass().getName());
            dbf.persistAndRefresh(vo);
            bus.publish(evt);
        }

    }

    private SchedulerTask prepareSchedulerTaskFromMsg(APIAddSchedulerJobToSchedulerTriggerMsg msg) {
        SchedulerJobVO job = dbf.findByUuid(msg.getSchedulerJobUuid(), SchedulerJobVO.class);
        SchedulerTriggerVO trigger = dbf.findByUuid(msg.getSchedulerTriggerUuid(), SchedulerTriggerVO.class);
        SchedulerTask task = new SchedulerTask();
        task.setStartTime(trigger.getStartTime());
        task.setJobUuid(job.getUuid());
        task.setTargetResourceUuid(job.getTargetResourceUuid());
        task.setJobClassName(job.getJobClassName());
        task.setJobData(job.getJobData());
        task.setTriggerUuid(trigger.getUuid());
        task.setTaskInterval(trigger.getSchedulerInterval());
        task.setTaskRepeatCount(trigger.getRepeatCount());
        task.setType(trigger.getSchedulerType());
        task.setStopTime(trigger.getStopTime());

        return task;
    }

    private void handle(APIDeleteSchedulerTriggerMsg msg) {
        APIDeleteSchedulerTriggerEvent evt = new APIDeleteSchedulerTriggerEvent(msg.getId());
        dbf.removeByPrimaryKey(msg.getUuid(), SchedulerTriggerVO.class);
        bus.publish(evt);
    }

    private void handle(APIUpdateSchedulerTriggerMsg msg) {
        APIUpdateSchedulerTriggerEvent evt = new APIUpdateSchedulerTriggerEvent(msg.getId());
        SchedulerTriggerVO vo = updateSchedulerTrigger(msg);

        if (vo != null) {
            dbf.updateAndRefresh(vo);
        }
        evt.setInventory(SchedulerTriggerInventory.valueOf(vo));
        bus.publish(evt);
    }

    private SchedulerTriggerVO updateSchedulerTrigger(APIUpdateSchedulerTriggerMsg msg) {
        SchedulerTriggerVO vo = dbf.findByUuid(msg.getUuid(), SchedulerTriggerVO.class);

        if (msg.getName() != null) {
            vo.setName(msg.getName());
        }

        if (msg.getDescription() != null) {
            vo.setDescription(msg.getDescription());
        }

        return vo;
    }

    private void handle(APICreateSchedulerTriggerMsg msg) {
        APICreateSchedulerTriggerEvent evt = new APICreateSchedulerTriggerEvent(msg.getId());
        Timestamp start = new Timestamp(msg.getStartTime());
        SchedulerTriggerVO vo = new SchedulerTriggerVO();
        if (msg.getResourceUuid() != null) {
            vo.setUuid(msg.getResourceUuid());
        } else {
            vo.setUuid(Platform.getUuid());
        }
        vo.setName(msg.getName());
        vo.setDescription(msg.getDescription());
        if (msg.getStartTime() == 0) {
            vo.setStartTime(null);
        } else {
            vo.setStartTime(new Timestamp(msg.getStartTime() * 1000L));
        }

        if (msg.getSchedulerType().equals(SchedulerConstant.SIMPLE_TYPE_STRING)) {
            // if execute once
            if (msg.getStartTime() != null && msg.getStartTime() != 0) {
                if (msg.getRepeatCount() != null && msg.getRepeatCount() == 1) {
                    vo.setStopTime(start);
                } else {
                    vo.setStopTime(new Timestamp(start.getTime() * 1000L + (long) msg.getRepeatCount() * (long) msg.getSchedulerInterval() * 1000L));
                }

            } else {
                vo.setStopTime(null);
            }
        } else {
            vo.setStopTime(null);
        }

        vo.setRepeatCount(msg.getRepeatCount());
        vo.setSchedulerInterval(msg.getSchedulerInterval());
        vo.setSchedulerType(msg.getSchedulerType());
        dbf.persist(vo);

        evt.setInventory(SchedulerTriggerInventory.valueOf(vo));
        bus.publish(evt);
    }

    private void handle(APIChangeSchedulerStateMsg msg) {
        self = Q.New(SchedulerJobSchedulerTriggerRefVO.class)
                .eq(SchedulerJobSchedulerTriggerRefVO_.uuid, msg.getSchedulerUuid())
                .find();
        if (msg.getStateEvent().equals("enable")) {
            resumeSchedulerJob(msg.getUuid());
            APIChangeSchedulerStateEvent evt = new APIChangeSchedulerStateEvent(msg.getId());
            evt.setInventory(getInventory());
            bus.publish(evt);
        } else {
            pauseSchedulerJob(msg.getUuid());
            APIChangeSchedulerStateEvent evt = new APIChangeSchedulerStateEvent(msg.getId());
            evt.setInventory(getInventory());
            bus.publish(evt);
        }

    }

    private void handle(APIDeleteSchedulerJobMsg msg) {
        APIDeleteSchedulerJobEvent evt = new APIDeleteSchedulerJobEvent(msg.getId());
        dbf.removeByPrimaryKey(msg.getUuid(), SchedulerJobVO.class);
        bus.publish(evt);
    }

    private void handle(APIUpdateSchedulerJobMsg msg) {
        SchedulerJobVO vo = updateSchedulerJob(msg);
        if (vo != null) {
            self = dbf.updateAndRefresh(vo);
        }
        APIUpdateSchedulerJobEvent evt = new APIUpdateSchedulerJobEvent(msg.getId());
        evt.setInventory(getInventory());
        bus.publish(evt);
    }

    private SchedulerJobVO updateSchedulerJob(APIUpdateSchedulerJobMsg msg) {
        SchedulerJobVO self = dbf.findByUuid(msg.getSchedulerUuid(), SchedulerJobVO.class);
        if (msg.getName() != null) {
            self.setName(msg.getName());
        }
        if (msg.getDescription() != null) {
            self.setDescription(msg.getDescription());
        }
        return self;
    }


    public String getId() {
        return bus.makeLocalServiceId(SchedulerConstant.SERVICE_ID);
    }

    public boolean start() {
        try {
            scheduler = StdSchedulerFactory.getDefaultScheduler();
            scheduler.start();
        } catch (SchedulerException e) {
            logger.warn("Start Scheduler failed!");
            throw new RuntimeException(e);
        }

        return true;
    }

    public boolean stop() {
        try {
            scheduler.shutdown();
        } catch (SchedulerException e) {
            logger.warn("Stop Scheduler failed!");
            throw new RuntimeException(e);
        }
        return true;
    }

    @Transactional
    private void updateSchedulerStatus(String uuid, String state) {
        SQL.New(SchedulerJobSchedulerTriggerRefVO.class)
                .eq(SchedulerJobSchedulerTriggerRefVO_.uuid, uuid)
                .set(SchedulerJobSchedulerTriggerRefVO_.state, state)
                .update();
    }

    public void pauseSchedulerJob(String uuid) {
        logger.debug(String.format("Scheduler %s will change status to Disabled", uuid));
        SchedulerJobSchedulerTriggerRefVO vo = dbf.findByUuid(uuid, SchedulerJobSchedulerTriggerRefVO.class);

        try {
            scheduler.pauseJob(jobKey(vo.getSchedulerJobUuid(), vo.getSchedulerTriggerUuid()));
            updateSchedulerStatus(uuid, SchedulerState.Disabled.toString());
        } catch (SchedulerException e) {
            logger.warn(String.format("Pause Scheduler %s failed!", uuid));
            throw new RuntimeException(e);
        }
    }

    public void resumeSchedulerJob(String uuid) {
        if (!destinationMaker.isManagedByUs(uuid)) {
            logger.debug(String.format("Scheduler %s not managed by us, will not be resume", uuid));
        } else {
            logger.debug(String.format("Scheduler %s will change status to Enabled", uuid));
            SchedulerJobSchedulerTriggerRefVO vo = dbf.findByUuid(uuid, SchedulerJobSchedulerTriggerRefVO.class);
            try {
                scheduler.resumeJob(jobKey(vo.getSchedulerJobUuid(), vo.getSchedulerTriggerUuid()));
                updateSchedulerStatus(uuid, SchedulerState.Enabled.toString());
            } catch (SchedulerException e) {
                logger.warn(String.format("Resume Scheduler %s failed!", uuid));
                throw new RuntimeException(e);
            }

        }
    }

    public void deleteSchedulerJobByResourceUuid(String uuid) {
        new SQLBatch() {
            @Override
            protected void scripts() {
                List<String> jobUuids = Q.New(SchedulerJobVO.class)
                        .select(SchedulerJobVO_.uuid)
                        .eq(SchedulerJobVO_.targetResourceUuid, uuid)
                        .listValues();

                if (!jobUuids.isEmpty()) {
                    List<String> uuids = Q.New(SchedulerJobSchedulerTriggerRefVO.class)
                            .select(SchedulerJobSchedulerTriggerRefVO_.uuid)
                            .in(SchedulerJobSchedulerTriggerRefVO_.schedulerJobUuid, jobUuids)
                            .listValues();

                    for (String uuid : uuids) {
                        deleteSchedulerJob(uuid);
                    }
                }
            }
        }.execute();
    }

    private void deleteSchedulerJob(String uuid) {
        if (!destinationMaker.isManagedByUs(uuid)) {
            logger.debug(String.format("Scheduler %s not managed by us, will not be deleted", uuid));
        } else {
            logger.debug(String.format("Scheduler %s will be deleted", uuid));
            SchedulerJobSchedulerTriggerRefVO vo = dbf.findByUuid(uuid, SchedulerJobSchedulerTriggerRefVO.class);
            try {
                scheduler.deleteJob(jobKey(vo.getSchedulerJobUuid(), vo.getSchedulerTriggerUuid()));
                dbf.removeByPrimaryKey(uuid, SchedulerJobSchedulerTriggerRefVO.class);
            } catch (SchedulerException e) {
                logger.warn(String.format("Delete Scheduler %s failed!", uuid));
                throw new RuntimeException(e);
            }
        }
    }

    private void loadSchedulerManagedByUs() {
        new SQLBatch() {
            @Override
            protected void scripts() {
                List<String> ids = Q.New(SchedulerJobVO.class)
                        .select(SchedulerJobVO_.uuid)
                        .isNull(SchedulerJobVO_.managementNodeUuid)
                        .listValues();

                if (ids == null) {
                    return;
                }
                List<String> ours = new ArrayList<String>();
                for (String id : ids) {
                    if (destinationMaker.isManagedByUs(id)) {
                        ours.add(id);
                        List<SchedulerJobVO> vos = Q.New(SchedulerJobVO.class).in(SchedulerJobVO_.uuid, ours).list();
                        for (SchedulerJobVO vo : vos) {
                            vo.setManagementNodeUuid(Platform.getManagementServerId());
                            merge(vo);
                        }
                    }
                }
                jobLoader(ours);
            }
        }.execute();

    }

    private void jobLoader(List<String> ours) {
        if (ours.isEmpty()) {
            logger.debug("no Scheduler managed by us");
        } else {
            logger.debug(String.format("Scheduler is going to load %s jobs", ours.size()));
            List<SchedulerJobSchedulerTriggerRefVO> schedulerRecords = Q.New(SchedulerJobSchedulerTriggerRefVO.class)
                    .in(SchedulerJobSchedulerTriggerRefVO_.schedulerJobUuid, ours).list();
            for (SchedulerJobSchedulerTriggerRefVO schedulerRecord : schedulerRecords) {
                try {
                    SchedulerTask rebootJob = (SchedulerTask) JSONObjectUtil.toObject(schedulerRecord.getTaskData(), Class.forName(schedulerRecord.getTaskClassName()));
                    if (schedulerRecord.getState().equals(SchedulerState.Enabled.toString())) {
                        runScheduler(rebootJob, false);
                    }
                } catch (ClassNotFoundException e) {
                    logger.warn(String.format("Load Scheduler %s failed!", schedulerRecord.getUuid()));
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void loadSchedulerJobs() {
        loadSchedulerManagedByUs();
    }

    private void takeOverScheduler() {
        logger.debug(String.format("Starting to take over Scheduler job "));
        int qun = 10000;
        long amount = dbf.count(SchedulerVO.class);
        int times = (int) (amount / qun) + (amount % qun != 0 ? 1 : 0);
        int start = 0;
        for (int i = 0; i < times; i++) {
            SimpleQuery<SchedulerVO> q = dbf.createQuery(SchedulerVO.class);
            q.select(SchedulerVO_.uuid);
            q.add(SchedulerVO_.managementNodeUuid, SimpleQuery.Op.NULL);
            q.setLimit(qun);
            q.setStart(start);
            List<String> uuids = q.listValue();
            List<String> ours = new ArrayList<String>();
            for (String id : uuids) {
                if (destinationMaker.isManagedByUs(id)) {
                    ours.add(id);
                    q = dbf.createQuery(SchedulerVO.class);
                    q.add(SchedulerVO_.uuid, SimpleQuery.Op.IN, ours);
                    List<SchedulerVO> vos = q.list();
                    for (SchedulerVO vo : vos) {
                        vo.setManagementNodeUuid(Platform.getManagementServerId());
                        dbf.updateAndRefresh(vo);
                    }
                }
            }
            jobLoader(ours);
            start += qun;
        }
    }

    public boolean runScheduler(SchedulerTask schedulerJob) {
        return runScheduler(schedulerJob, true);
    }

    private boolean runScheduler(SchedulerTask schedulerJob, boolean saveDB) {
        logger.debug(String.format("Starting to generate Scheduler job %s", schedulerJob.getClass().getName()));
        Boolean startNow = false;
        String jobData = schedulerJob.getJobData();
        String jobClassName = schedulerJob.getJobClassName();

        if (schedulerJob.getStartTime() == null && schedulerJob.getType().equals(SchedulerConstant.SIMPLE_TYPE_STRING)) {
            startNow = true;
        }
        try {
            JobDetail job = newJob(SchedulerRunner.class)
                    .withIdentity(schedulerJob.getJobUuid(), schedulerJob.getTriggerUuid())
                    .usingJobData("jobClassName", jobClassName)
                    .usingJobData("jobData", jobData)
                    .build();

            String triggerGroup = schedulerJob.getTriggerUuid() + "." + schedulerJob.getJobUuid();
            String triggerId = schedulerJob.getTriggerUuid();

            // use triggerUuid.jobUuid as the key of a new trigger and jobUuid as group name
            // to support that several jobs use same trigger
            if (schedulerJob.getType().equals("simple")) {
                Trigger trigger;
                if (schedulerJob.getTaskRepeatCount() != null) {
                    if (schedulerJob.getTaskRepeatCount() == 1) {
                        //repeat only once, ignore interval
                        if (startNow) {
                            trigger = newTrigger()
                                    .withIdentity(triggerId, triggerGroup)
                                    .withSchedule(simpleSchedule()
                                    .withMisfireHandlingInstructionNextWithRemainingCount())
                                    .build();
                        } else {
                            trigger = newTrigger()
                                    .withIdentity(triggerId, triggerGroup)
                                    .startAt(schedulerJob.getStartTime())
                                    .withSchedule(simpleSchedule()
                                    .withMisfireHandlingInstructionNextWithRemainingCount())
                                    .build();
                        }

                    } else {
                        //repeat more than once
                        if (startNow) {
                            trigger = newTrigger()
                                    .withIdentity(triggerId, triggerGroup)
                                    .withSchedule(simpleSchedule()
                                            .withIntervalInSeconds(schedulerJob.getTaskInterval())
                                            .withRepeatCount(schedulerJob.getTaskRepeatCount() - 1)
                                            .withMisfireHandlingInstructionNextWithRemainingCount())
                                    .build();
                        } else {
                            trigger = newTrigger()
                                    .withIdentity(triggerId, triggerGroup)
                                    .startAt(schedulerJob.getStartTime())
                                    .withSchedule(simpleSchedule()
                                            .withIntervalInSeconds(schedulerJob.getTaskInterval())
                                            .withRepeatCount(schedulerJob.getTaskRepeatCount() - 1)
                                            .withMisfireHandlingInstructionNextWithRemainingCount())
                                    .build();
                        }
                    }
                } else {
                    if (startNow) {
                        trigger = newTrigger()
                                .withIdentity(triggerId, triggerGroup)
                                .withSchedule(simpleSchedule()
                                        .withIntervalInSeconds(schedulerJob.getTaskInterval())
                                        .repeatForever()
                                        .withMisfireHandlingInstructionNextWithRemainingCount())
                                .build();
                    } else {
                        trigger = newTrigger()
                                .withIdentity(triggerId, triggerGroup)
                                .startAt(schedulerJob.getStartTime())
                                .withSchedule(simpleSchedule()
                                        .withIntervalInSeconds(schedulerJob.getTaskInterval())
                                        .repeatForever()
                                        .withMisfireHandlingInstructionNextWithRemainingCount())
                                .build();
                    }
                }

                scheduler.scheduleJob(job, trigger);
            } else if (schedulerJob.getType().equals(SchedulerConstant.CRON_TYPE_STRING)) {
                CronTrigger trigger = newTrigger()
                        .withIdentity(triggerId, triggerGroup)
                        .withSchedule(cronSchedule(schedulerJob.getCron())
                        .withMisfireHandlingInstructionIgnoreMisfires())
                        .build();
                scheduler.scheduleJob(job, trigger);
            }
        } catch (SchedulerException se) {
            logger.warn(String.format("Run Scheduler task by job[uuid:%s] and trigger[uuid:%s] failed", schedulerJob.getJobUuid(), schedulerJob.getTriggerUuid()));
            throw new RuntimeException(se);
        }

        return true;
    }

    @AsyncThread
    @Override
    public void managementNodeReady() {
        logger.debug(String.format("Management node[uuid:%s] joins, start loading Scheduler jobs...", Platform.getManagementServerId()));
        loadSchedulerJobs();
    }

    @Override
    public void nodeJoin(String nodeId) {

    }

    @Override
    @SyncThread
    public void nodeLeft(String nodeId) {
        logger.debug(String.format("Management node[uuid:%s] left, node[uuid:%s] starts to take over schedulers", nodeId, Platform.getManagementServerId()));
        takeOverScheduler();
    }

    @Override
    public void iAmDead(String nodeId) {

    }

    @Override
    public void iJoin(String nodeId) {

    }

    @Override
    public void resourceOwnerPreChange(AccountResourceRefInventory ref, String newOwnerUuid) {
        new SQLBatch() {
            @Override
            protected void scripts() {
                List<String> uuids = getSchedulerUuidsByResourceUuid(ref.getResourceUuid());

                if (!uuids.isEmpty()) {
                    for (String uuid : uuids) {
                        if (!destinationMaker.isManagedByUs(uuid)) {
                            logger.debug(String.format("Scheduler %s not managed by us, will not to pause it", uuid));
                        } else {
                            logger.debug(String.format("resource %s: %s scheduler %s will be paused",
                                    ref.getResourceType(), ref.getResourceUuid(), uuid));
                            pauseSchedulerJob(uuid);
                        }
                    }
                } else {
                    logger.debug(String.format("resource %s: %s not set any scheduler", ref.getResourceType(), ref.getResourceUuid()));
                }
            }
        }.execute();
    }

    public void vmStateChanged(VmInstanceInventory vm, VmInstanceState oldState, VmInstanceState newState) {
        new SQLBatch() {
            @Override
            protected void scripts() {
                List<String> uuids = getSchedulerUuidsByResourceUuid(vm.getUuid());

                if (!uuids.isEmpty()) {
                    for (String uuid : uuids) {
                        if (oldState.toString().equals("Running") && newState.toString().equals("Unknown")) {
                            pauseSchedulerJob(uuid);
                        } else if (oldState.toString().equals("Unknown") && newState.toString().equals("Running")) {
                            resumeSchedulerJob(uuid);
                        }
                    }
                } else {
                    logger.debug(String.format("vm %s not set any scheduler", vm.getUuid()));
                }
            }
        }.execute();
    }


    public String preDestroyVm(VmInstanceInventory inv) {
        return null;
    }

    public void beforeDestroyVm(VmInstanceInventory inv) {
        List<String> uuids = getSchedulerUuidsByResourceUuid(inv.getUuid());

        if (!uuids.isEmpty()) {
            for (String uuid : uuids) {
                pauseSchedulerJob(uuid);
            }
        }
    }

    @Transactional
    private List<String> getSchedulerUuidsByResourceUuid(String resourceUuid) {
        List<String> uuids = new ArrayList<>();

        List<String> jobUuids = Q.New(SchedulerJobVO.class)
                .select(SchedulerJobVO_.uuid)
                .eq(SchedulerJobVO_.targetResourceUuid, resourceUuid).listValues();

        if (!jobUuids.isEmpty()) {
            uuids = Q.New(SchedulerJobSchedulerTriggerRefVO.class)
                    .select(SchedulerJobSchedulerTriggerRefVO_.uuid)
                    .in(SchedulerJobSchedulerTriggerRefVO_.schedulerJobUuid, jobUuids)
                    .listValues();
        }

        return uuids;
    }

    public void afterDestroyVm(VmInstanceInventory vm) {

    }

    public void failedToDestroyVm(VmInstanceInventory vm, ErrorCode reason) {

    }

    public void preRecoverVm(VmInstanceInventory vm) {

    }

    public void beforeRecoverVm(VmInstanceInventory vm) {

    }

    public void afterRecoverVm(VmInstanceInventory vm) {

    }

    public void vmBeforeExpunge(VmInstanceInventory inv) {
        logger.debug(String.format("will delete scheduler before expunge vm"));

        deleteSchedulerJobByResourceUuid(inv.getUuid());
    }
}
