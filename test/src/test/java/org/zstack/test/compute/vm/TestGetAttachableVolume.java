package org.zstack.test.compute.vm;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery;
import org.zstack.header.configuration.DiskOfferingVO;
import org.zstack.header.configuration.DiskOfferingVO_;
import org.zstack.header.vm.APIGetVmAttachableDataVolumeMsg;
import org.zstack.header.vm.APIGetVmAttachableDataVolumeReply;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.volume.APIGetDataVolumeAttachableVmMsg;
import org.zstack.header.volume.VolumeInventory;
import org.zstack.header.volume.VolumeStatus;
import org.zstack.header.volume.VolumeVO;
import org.zstack.test.Api;
import org.zstack.test.ApiSender;
import org.zstack.test.ApiSenderException;
import org.zstack.test.DBUtil;
import org.zstack.test.deployer.Deployer;

/**
 * Created by zouye on 2017/2/16.
 */
public class TestGetAttachableVolume {
    Deployer deployer;
    Api api;
    ComponentLoader loader;
    CloudBus bus;
    DatabaseFacade dbf;

    @Before
    public void setUp() throws Exception {
        DBUtil.reDeployDB();
        deployer = new Deployer("deployerXml/vm/TestAttachVolumeToVm.xml");
        deployer.build();
        api = deployer.getApi();
        loader = deployer.getComponentLoader();
        bus = loader.getComponent(CloudBus.class);
        dbf = loader.getComponent(DatabaseFacade.class);
    }

    @Test
    public void test() throws ApiSenderException, InterruptedException {
        SimpleQuery<DiskOfferingVO> dq = dbf.createQuery(DiskOfferingVO.class);
        dq.add(DiskOfferingVO_.name, SimpleQuery.Op.EQ, "TestDataDiskOffering");
        DiskOfferingVO dvo = dq.find();
        VolumeInventory vinv = api.createDataVolume("TestData", dvo.getUuid());
        VolumeInventory vinv1 = api.createDataVolume("TestData-1", dvo.getUuid());
        VolumeInventory vinv2 = api.createDataVolume("TestData-2", dvo.getUuid());
        VolumeInventory vinv3 = api.createDataVolume("ShareableData", dvo.getUuid());

        vinv3.setShareable(true);

        VmInstanceInventory vminv = api.listVmInstances(null).get(0);
        vinv = api.attachVolumeToVm(vminv.getUuid(), vinv.getUuid());
        vinv1 = api.attachVolumeToVm(vinv1.getUuid(), vinv1.getUuid());
        vinv2 = api.attachVolumeToVm(vinv2.getUuid(), vinv2.getUuid());
        vinv3 = api.attachVolumeToVm(vinv3.getUuid(), vinv3.getUuid());

        Assert.assertEquals(Integer.valueOf(2), vinv.getDeviceId());
        Assert.assertEquals(Integer.valueOf(2), vinv1.getDeviceId());
        Assert.assertEquals(Integer.valueOf(2), vinv2.getDeviceId());
        Assert.assertEquals(Integer.valueOf(2), vinv3.getDeviceId());

        Assert.assertTrue(vinv.isAttached());
        Assert.assertTrue(vinv1.isAttached());
        Assert.assertTrue(vinv2.isAttached());
        Assert.assertTrue(vinv3.isAttached());

        Assert.assertEquals(VolumeStatus.Ready.toString(), vinv.getStatus());
        Assert.assertEquals(VolumeStatus.Ready.toString(), vinv1.getStatus());
        Assert.assertEquals(VolumeStatus.Ready.toString(), vinv2.getStatus());
        Assert.assertEquals(VolumeStatus.Ready.toString(), vinv3.getStatus());

        Assert.assertNotNull(vinv.getPrimaryStorageUuid());
        Assert.assertNotNull(vinv1.getPrimaryStorageUuid());
        Assert.assertNotNull(vinv2.getPrimaryStorageUuid());
        Assert.assertNotNull(vinv3.getPrimaryStorageUuid());

        Assert.assertNotNull(vinv.getVmInstanceUuid());
        Assert.assertNotNull(vinv1.getVmInstanceUuid());
        Assert.assertNotNull(vinv2.getVmInstanceUuid());
        Assert.assertNull(vinv3.getVmInstanceUuid());

        APIGetVmAttachableDataVolumeMsg msg = new APIGetVmAttachableDataVolumeMsg();
        msg.setVmInstanceUuid(vminv.getUuid());

        ApiSender sender = new ApiSender();
        APIGetVmAttachableDataVolumeReply reply = sender.call(msg, APIGetVmAttachableDataVolumeReply.class);

        Assert.assertTrue(reply.getInventories().isEmpty());
        Assert.assertTrue(reply.getInventories().size() == 0);
    }
}
