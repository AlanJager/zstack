package org.zstack.network.l2.vxlan.vxlanNetwork;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.asyncbatch.While;
import org.zstack.core.cascade.CascadeFacade;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusListCallBack;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.core.workflow.ShareFlow;
import org.zstack.header.core.Completion;
import org.zstack.header.core.WhileDoneCompletion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.host.*;
import org.zstack.header.identity.Quota;
import org.zstack.header.identity.ReportQuotaExtensionPoint;
import org.zstack.header.identity.quota.QuotaMessageHandler;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.Message;
import org.zstack.header.message.MessageReply;
import org.zstack.header.network.l2.*;
import org.zstack.network.l2.*;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.zstack.utils.CollectionDSL.list;

/**
 * Created by weiwang on 01/03/2017.
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class VxlanNetwork extends L2NoVlanNetwork implements ReportQuotaExtensionPoint {
    private static final CLogger logger = Utils.getLogger(VxlanNetwork.class);

    @Autowired
    protected L2NetworkExtensionPointEmitter extpEmitter;
    @Autowired
    protected CloudBus bus;
    @Autowired
    protected DatabaseFacade dbf;
    @Autowired
    protected L2NetworkManager l2Mgr;
    @Autowired
    protected CascadeFacade casf;
    @Autowired
    protected ErrorFacade errf;

    public VxlanNetwork(L2NetworkVO self) {
        super(self);
    }

    public VxlanNetwork() {
        super(null);
    }

    private VxlanNetworkVO getSelf() {
        return dbf.findByUuid(self.getUuid(), VxlanNetworkVO.class);
    }

    @Override
    public void deleteHook(Completion completion) {
        if (L2NetworkGlobalConfig.DeleteL2BridgePhysically.value(Boolean.class)) {
            deleteL2Bridge(completion);
        } else {
            completion.success();
        }
    }

    @Override
    protected L2NetworkInventory getSelfInventory() {
        return L2VxlanNetworkInventory.valueOf(getSelf());
    }

    @Override
    public void handleMessage(Message msg) {
        try {
            if (msg instanceof APIMessage) {
                handleApiMessage((APIMessage) msg);
            } else {
                handleLocalMessage(msg);
            }
        } catch (Exception e) {
            bus.logExceptionWithMessageDump(msg, e);
            bus.replyErrorByMessageType(msg, e);
        }
    }

    private void handleLocalMessage(Message msg) {
        if (msg instanceof PrepareL2NetworkOnHostMsg) {
            handle((PrepareL2NetworkOnHostMsg) msg);
        } else if (msg instanceof L2NetworkDeletionMsg) {
            handle((L2NetworkDeletionMsg) msg);
        } else if (msg instanceof CheckL2NetworkOnHostMsg) {
            handle((CheckL2NetworkOnHostMsg) msg);
        } else if (msg instanceof PrepareL2NetworkOnHostsMsg) {
            handle((PrepareL2NetworkOnHostsMsg) msg);
        } else if (msg instanceof L2NetworkMessage) {
            superHandle((L2NetworkMessage) msg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    private void handle(final PrepareL2NetworkOnHostMsg msg) {
        final PrepareL2NetworkOnHostReply reply = new PrepareL2NetworkOnHostReply();
        prepareL2NetworkOnHosts(asList(msg.getHost()), new Completion(msg) {
            @Override
            public void success() {
                bus.reply(msg, reply);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                reply.setError(errorCode);
                bus.reply(msg, reply);
            }
        });
    }

    private void handle(final PrepareL2NetworkOnHostsMsg msg) {
        final PrepareL2NetworkOnHostsReply reply = new PrepareL2NetworkOnHostsReply();
        List<HostInventory> hosts = HostInventory.valueOf(Q.New(HostVO.class).in(HostVO_.uuid, msg.getHosts()).list());
        prepareL2NetworkOnHosts(hosts, new Completion(msg) {
            @Override
            public void success() {
                bus.reply(msg, reply);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                reply.setError(errorCode);
                bus.reply(msg, reply);
            }
        });
    }


    @Transactional
    private void changeL2NetworkVniInDb(final APIChangeL2NetworkVlanIdMsg msg) {
        if (!self.getVirtualNetworkId().equals(msg.getVlan())) {
            VxlanNetworkVO vo = getSelf();
            vo.setVirtualNetworkId(msg.getVlan());
            vo.setVni(msg.getVlan());
            dbf.updateAndRefresh(vo);
        }
    }

    private void changeL2NetworkVni(final APIChangeL2NetworkVlanIdMsg msg, final Completion completion) {
        final FlowChain chain = FlowChainBuilder.newShareFlowChain();
        chain.setName(String.format("change-l2-%s-vni-on-hosts", self.getUuid()));
        chain.then(new ShareFlow() {
            @Override
            public void setup() {
                final List<HostVO> updatedHosts = new ArrayList<>();
                L2NetworkInventory oldInv = self.toInventory();
                L2NetworkInventory newInv = self.toInventory();
                if (msg.getVlan() != null) {
                    newInv.setVirtualNetworkId(msg.getVlan());
                }

                flow(new Flow() {
                    String __name__ = "update-l2-network-vni";

                    @Override
                    public void run(final FlowTrigger trigger, Map data) {
                        new While<>(L2NetworkHostHelper.getHostsByL2NetworkAttachedCluster(newInv)).step((host, whileCompletion) -> {
                            updateVxlanNetwork(oldInv, newInv, host.getUuid(), host.getHypervisorType(), new Completion(whileCompletion) {
                                @Override
                                public void success() {
                                    updatedHosts.add(host);
                                    whileCompletion.done();
                                }

                                @Override
                                public void fail(ErrorCode errorCode) {
                                    logger.error(String.format("update VXLAN network in host:[%s] failed", host.getUuid()));
                                    whileCompletion.addError(errorCode);
                                    whileCompletion.allDone();
                                }
                            });
                        }, 10).run(new WhileDoneCompletion(trigger) {
                            @Override
                            public void done(ErrorCodeList errorCodeList) {
                                if (!errorCodeList.getCauses().isEmpty()) {
                                    trigger.fail(errorCodeList.getCauses().get(0));
                                } else {
                                    trigger.next();
                                }
                            }
                        });
                    }

                    @Override
                    public void rollback(FlowRollback trigger, Map data) {
                        new While<>(updatedHosts).step((host, whileCompletion) -> {
                            updateVxlanNetwork(newInv, oldInv, host.getUuid(), host.getHypervisorType(), new Completion(whileCompletion) {
                                @Override
                                public void success() {
                                    whileCompletion.done();
                                }

                                @Override
                                public void fail(ErrorCode errorCode) {
                                    logger.error(String.format("rollback VXLAN network in host:[%s] failed", host.getUuid()));
                                    whileCompletion.done();
                                }
                            });
                        }, updatedHosts.size()).run(new WhileDoneCompletion(trigger) {
                            @Override
                            public void done(ErrorCodeList errorCodeList) {
                                trigger.rollback();
                            }
                        });
                    }
                });

                done(new FlowDoneHandler(completion) {
                    @Override
                    public void handle(Map data) {
                        completion.success();
                    }
                });

                error(new FlowErrorHandler(completion) {
                    @Override
                    public void handle(ErrorCode errCode, Map data) {
                        completion.fail(errCode);
                    }
                });
            }
        }).start();
    }


    private void prepareL2NetworkOnHosts(final List<HostInventory> hosts, final Completion completion) {
        FlowChain chain = FlowChainBuilder.newSimpleFlowChain();
        chain.setName(String.format("prepare-l2-%s-on-hosts", self.getUuid()));
        chain.then(new NoRollbackFlow() {
            @Override
            public void run(final FlowTrigger trigger, Map data) {
                List<CheckL2NetworkOnHostMsg> cmsgs = new ArrayList<CheckL2NetworkOnHostMsg>();
                for (HostInventory h : hosts) {
                    CheckL2NetworkOnHostMsg cmsg = new CheckL2NetworkOnHostMsg();
                    cmsg.setHostUuid(h.getUuid());
                    cmsg.setL2NetworkUuid(self.getUuid());
                    bus.makeTargetServiceIdByResourceUuid(cmsg, L2NetworkConstant.SERVICE_ID, self.getUuid());
                    cmsgs.add(cmsg);
                }

                if (cmsgs.isEmpty()) {
                    trigger.next();
                    return;
                }

                bus.send(cmsgs, new CloudBusListCallBack(trigger) {
                    @Override
                    public void run(List<MessageReply> replies) {
                        for (MessageReply r : replies) {
                            if (!r.isSuccess()) {
                                trigger.fail(r.getError());
                                return;
                            }
                        }

                        trigger.next();
                    }
                });
            }
        }).then(new NoRollbackFlow() {
            private void realize(final Iterator<HostInventory> it, final FlowTrigger trigger) {
                if (!it.hasNext()) {
                    trigger.next();
                    return;
                }

                HostInventory host = it.next();
                realizeNetwork(host.getUuid(), host.getHypervisorType(), new Completion(trigger) {
                    @Override
                    public void success() {
                        realize(it, trigger);
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        trigger.fail(errorCode);
                    }
                });
            }

            @Override
            public void run(FlowTrigger trigger, Map data) {
                realize(hosts.iterator(), trigger);
            }
        }).done(new FlowDoneHandler(completion) {
            @Override
            public void handle(Map data) {
                completion.success();
            }
        }).error(new FlowErrorHandler(completion) {
            @Override
            public void handle(ErrorCode errCode, Map data) {
                completion.fail(errCode);
            }
        }).start();
    }

    protected void updateVxlanNetwork(L2NetworkInventory oldInv, L2NetworkInventory newInv, String hostUuid, String htype, Completion completion) {
        final HypervisorType hvType = HypervisorType.valueOf(htype);
        final L2NetworkType l2Type = L2NetworkType.valueOf(self.getType());
        final VSwitchType vSwitchType = VSwitchType.valueOf(self.getvSwitchType());
        L2NetworkRealizationExtensionPoint ext = l2Mgr.getRealizationExtension(l2Type, vSwitchType, hvType);
        ext.update(oldInv, newInv, hostUuid, completion);
    }

    protected void realizeNetwork(String hostUuid, String htype, Completion completion) {
        final HypervisorType hvType = HypervisorType.valueOf(htype);
        final L2NetworkType l2Type = L2NetworkType.valueOf(self.getType());
        final VSwitchType vSwitchType = VSwitchType.valueOf(self.getvSwitchType());

        L2NetworkRealizationExtensionPoint ext = l2Mgr.getRealizationExtension(l2Type, vSwitchType, hvType);
        ext.realize(getSelfInventory(), hostUuid, completion);
    }

    private void handle(final CheckL2NetworkOnHostMsg msg) {
        superHandle((L2NetworkMessage) msg);
    }

    private void handle(L2NetworkDeletionMsg msg) {
        L2NetworkInventory inv = L2NetworkInventory.valueOf(self);
        extpEmitter.beforeDelete(inv);
        L2NetworkDeletionReply reply = new L2NetworkDeletionReply();
        deleteHook(new Completion(msg) {
            @Override
            public void success() {
                dbf.removeByPrimaryKey(msg.getL2NetworkUuid(), L2NetworkVO.class);
                extpEmitter.afterDelete(inv);
                bus.reply(msg, reply);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                reply.setError(errorCode);
                bus.reply(msg, reply);
            }
        });

    }

    private void handleApiMessage(APIMessage msg) {
        if (msg instanceof APIAttachL2NetworkToClusterMsg) {
            handle((APIAttachL2NetworkToClusterMsg) msg);
        } else if (msg instanceof APIDetachL2NetworkFromClusterMsg) {
            handle((APIDetachL2NetworkFromClusterMsg) msg);
        } else if (msg instanceof APIChangeL2NetworkVlanIdMsg) {
            handle((APIChangeL2NetworkVlanIdMsg) msg);
        } else if (msg instanceof L2NetworkMessage) {
            superHandle((L2NetworkMessage) msg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    protected void handle(final APIChangeL2NetworkVlanIdMsg msg) {
        APIChangeL2NetworkVlanIdEvent event = new APIChangeL2NetworkVlanIdEvent(msg.getId());
        if (self.getVirtualNetworkId().equals(msg.getVlan())) {
            event.setInventory(getSelfInventory());
            bus.publish(event);
            return;
        }
        thdf.chainSubmit(new ChainTask(msg) {
            @Override
            public String getSyncSignature() {
                return String.format("change-l2-network-%s-vlan", msg.getL2NetworkUuid());
            }

            @Override
            public void run(SyncTaskChain chain) {
                changeL2NetworkVni(msg, new Completion(chain) {
                    @Override
                    public void success() {
                        changeL2NetworkVniInDb(msg);
                        extpEmitter.afterUpdate(getSelfInventory());
                        event.setInventory(getSelfInventory());
                        bus.publish(event);
                        chain.next();
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        event.setError(errorCode);
                        event.setInventory(getSelfInventory());
                        bus.publish(event);
                        chain.next();
                    }
                });
            }

            @Override
            public String getName() {
                return getSyncSignature();
            }
        });
    }

    protected void handle(final APIDetachL2NetworkFromClusterMsg msg) {
        throw new CloudRuntimeException("VxlanNetwork can not detach from cluster which VxlanNetworkPool should be used");
    }

    protected void handle(final APIAttachL2NetworkToClusterMsg msg) {
        throw new CloudRuntimeException("VxlanNetwork can not attach to cluster which VxlanNetworkPool should be used");
    }

    private void superHandle(L2NetworkMessage msg) {
        super.handleMessage((Message) msg);
    }

    @Override
    public List<Quota> reportQuota() {
        Quota quota = new Quota();
        quota.defineQuota(new VxlanNumQuotaDefinition());
        quota.addQuotaMessageChecker(new QuotaMessageHandler<>(APICreateL2VxlanNetworkMsg.class)
                .addCounterQuota(VxlanNetworkQuotaConstant.VXLAN_NUM));

        return list(quota);
    }

}
