package org.zstack.test.storage.primary.smp;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.compute.cluster.ClusterSystemTags;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery;
import org.zstack.header.cluster.ClusterInventory;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.host.*;
import org.zstack.header.identity.SessionInventory;
import org.zstack.header.network.l3.IpRangeInventory;
import org.zstack.header.simulator.SimulatorHostVO;
import org.zstack.header.storage.primary.*;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.vm.VmInstanceState;
import org.zstack.header.vm.VmInstanceVO;
import org.zstack.header.zone.ZoneInventory;
import org.zstack.simulator.kvm.KVMSimulatorConfig;
import org.zstack.storage.primary.PrimaryStorageSystemTags;
import org.zstack.storage.primary.smp.SMPConstants;
import org.zstack.storage.primary.smp.SMPPrimaryStorageSimulatorConfig;
import org.zstack.tag.TagManager;
import org.zstack.test.*;
import org.zstack.test.compute.host.DeletHostExtension;
import org.zstack.test.deployer.Deployer;
import org.zstack.test.storage.backup.sftp.TestSftpBackupStorageDeleteImage2;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import javax.persistence.TypedQuery;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 1. use smp storage attached to an empty cluster
 * 2. add a host
 * <p>
 * confirm smp storage's size is set
 */
public class TestSmpPrimaryStorage7 {
    Deployer deployer;
    Api api;
    ComponentLoader loader;
    CloudBus bus;
    DatabaseFacade dbf;
    SessionInventory session;
    SMPPrimaryStorageSimulatorConfig config;
    KVMSimulatorConfig kconfig;
    TagManager tagMgr;
    DeletHostExtension ext;

    @Before
    public void setUp() throws Exception {
        DBUtil.reDeployDB();
        WebBeanConstructor con = new WebBeanConstructor();
        deployer = new Deployer("deployerXml/smpPrimaryStorage/TestSmpPrimaryStorage7.xml", con);
        deployer.addSpringConfig("KVMRelated.xml");
        deployer.addSpringConfig("smpPrimaryStorageSimulator.xml");
        deployer.addSpringConfig("sharedMountPointPrimaryStorage.xml");
        deployer.build();
        api = deployer.getApi();
        loader = deployer.getComponentLoader();
        bus = loader.getComponent(CloudBus.class);
        dbf = loader.getComponent(DatabaseFacade.class);
        kconfig = loader.getComponent(KVMSimulatorConfig.class);
        config = loader.getComponent(SMPPrimaryStorageSimulatorConfig.class);
        tagMgr = loader.getComponent(TagManager.class);
        session = api.loginAsAdmin();
    }

    @Test
    public void test() throws ApiSenderException {
        PrimaryStorageInventory smp = deployer.primaryStorages.get("smp");

        String clusterUuid = deployer.clusters.get("Cluster1").getUuid();
        Assert.assertNotNull(clusterUuid);

        String hostUuid = deployer.hosts.get("host1").getUuid();
        HostVO hvo = dbf.findByUuid(hostUuid, HostVO.class);
        hvo.setState(HostState.Maintenance);
        dbf.updateAndRefresh(hvo);
        api.deleteHost(hostUuid);

        String sql = "select pri.uuid" +
                " from PrimaryStorageVO pri, PrimaryStorageClusterRefVO ref" +
                " where pri.uuid = ref.primaryStorageUuid" +
                " and ref.clusterUuid = :cuuid" +
                " and pri.type = :ptype";
        TypedQuery<String> q = dbf.getEntityManager().createQuery(sql, String.class);
        q.setParameter("cuuid", clusterUuid);
        q.setParameter("ptype", SMPConstants.SMP_TYPE);
        List<String> prUuids = q.getResultList();
        Assert.assertNotNull(prUuids);
        Assert.assertFalse(prUuids.isEmpty());

        SimpleQuery<HostVO> hq = dbf.createQuery(HostVO.class);
        hq.select(HostVO_.uuid);
        hq.add(HostVO_.clusterUuid, SimpleQuery.Op.EQ, clusterUuid);
        final List<String> hostUuids = hq.listValue();

        Assert.assertTrue(hostUuids.isEmpty());

        SimpleQuery<PrimaryStorageClusterRefVO> pscq = dbf.createQuery(PrimaryStorageClusterRefVO.class);
        pscq.select(PrimaryStorageClusterRefVO_.primaryStorageUuid);
        pscq.add(PrimaryStorageClusterRefVO_.clusterUuid, SimpleQuery.Op.EQ, clusterUuid);
        final List<String> psUuids = pscq.listValue();

        Assert.assertFalse(psUuids.isEmpty());

        PrimaryStorageVO psvo= dbf.findByUuid(smp.getUuid(), PrimaryStorageVO.class);
        PrimaryStorageCapacityVO retVo = dbf.findByUuid(psvo.getCapacity().getUuid(), PrimaryStorageCapacityVO.class);
        Assert.assertEquals(0, retVo.getTotalCapacity());
        Assert.assertEquals(0, retVo.getTotalPhysicalCapacity());
        Assert.assertEquals(0, retVo.getAvailablePhysicalCapacity());
    }
}
//
//    CLogger logger = Utils.getLogger(TestSftpBackupStorageDeleteImage2.class);
//    Deployer deployer;
//    Api api;
//    ComponentLoader loader;
//    CloudBus bus;
//    DatabaseFacade dbf;
//    SessionInventory session;
//    KVMSimulatorConfig config;
//
//    @Before
//    public void setUp() throws Exception {
//        DBUtil.reDeployDB();
//        WebBeanConstructor con = new WebBeanConstructor();
//        deployer = new Deployer("deployerXml/kvm/TestDeleteIpRangeOnKvm.xml", con);
//        deployer.addSpringConfig("KVMRelated.xml");
//        deployer.build();
//        api = deployer.getApi();
//        loader = deployer.getComponentLoader();
//        bus = loader.getComponent(CloudBus.class);
//        dbf = loader.getComponent(DatabaseFacade.class);
//        config = loader.getComponent(KVMSimulatorConfig.class);
//        session = api.loginAsAdmin();
//    }
//
//    @Test
//    public void test() throws ApiSenderException, InterruptedException {
//        VmInstanceInventory vm = deployer.vms.get("TestVm");
//        HostInventory host = deployer.hosts.get("host1");
//        config.pingSuccess = false;
//        HostVO hvo = dbf.findByUuid(host.getUuid(), HostVO.class);
//        hvo.setState(HostState.Maintenance);
//        dbf.update(hvo);
//
//        api.deleteHost(hvo.getUuid());
//
//        IpRangeInventory ipr = deployer.ipRanges.get("TestIpRange");
//        api.deleteIpRange(ipr.getUuid());
//        config.pingSuccess = true;
//        api.reconnectHost(host.getUuid());
//        TimeUnit.SECONDS.sleep(3);
//        VmInstanceVO vmvo = dbf.findByUuid(vm.getUuid(), VmInstanceVO.class);
//        Assert.assertEquals(VmInstanceState.Stopped, vmvo.getState());
//    }