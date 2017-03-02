package org.zstack.test.storage.primary.smp;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.compute.cluster.ClusterSystemTags;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.host.HostInventory;
import org.zstack.header.host.HostVO;
import org.zstack.header.host.HostVO_;
import org.zstack.header.identity.SessionInventory;
import org.zstack.header.storage.primary.*;
import org.zstack.simulator.kvm.KVMSimulatorConfig;
import org.zstack.storage.primary.PrimaryStorageSystemTags;
import org.zstack.storage.primary.smp.SMPConstants;
import org.zstack.storage.primary.smp.SMPPrimaryStorageSimulatorConfig;
import org.zstack.tag.TagManager;
import org.zstack.test.Api;
import org.zstack.test.ApiSenderException;
import org.zstack.test.DBUtil;
import org.zstack.test.WebBeanConstructor;
import org.zstack.test.deployer.Deployer;

import javax.persistence.TypedQuery;
import java.util.List;

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

        HostInventory inv = deployer.hosts.get("host1");
        String clusterUuid = deployer.clusters.get("Cluster1").getUuid();
        Assert.assertNotNull(clusterUuid);

        api.deleteHost(inv.getUuid());

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
        PrimaryStorageCapacityVO retVo = psvo.getCapacity();
        Assert.assertEquals(0, retVo.getTotalCapacity());
        Assert.assertEquals(0, retVo.getTotalPhysicalCapacity());
        Assert.assertEquals(0, retVo.getAvailablePhysicalCapacity());
    }
}
