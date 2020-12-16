/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xyz.path.optical.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.xyz.path.optical.intf.XyzRoutingService;
import org.onlab.graph.ScalarWeight;
import org.onlab.graph.Weight;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.IpPrefix;
import org.onosproject.common.DefaultTopology;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultPath;
import org.onosproject.net.DeviceId;
import org.onosproject.net.ElementId;
import org.onosproject.net.HostId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.PathIntent;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.statistic.PortStatisticsService;
import org.onosproject.net.topology.DefaultTopologyVertex;
import org.onosproject.net.topology.MetricLinkWeight;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.topology.TopologyVertex;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author xyz
 * @Data 14/12/20
 */
//Component 1
@Component(immediate = true,service = {XyzRoutingService.class})
public class XyzPathManager implements XyzRoutingService {


    private static final Logger log = getLogger(XyzPathManager.class);

    private  final LoadBalanceRouting routing = new LoadBalanceRouting();

    //2
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private PortStatisticsService portStatisticsService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private LinkService linkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private IntentService intentService;

    private ApplicationId appId;
    private ConcurrentMap<Set<Criterion>, Intent> intentMap = new ConcurrentHashMap<>();
    private InternalPacketProcessor packetProcessor = new InternalPacketProcessor();


    @Activate
    protected void activate(){
        intentMap.clear();

        appId = coreService.registerApplication("org.onosproject.ONOS_OPTICAL_PATH");
        //3
        packetService.addProcessor(packetProcessor,PacketProcessor.director(0));

        packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(),
                                     PacketPriority.REACTIVE,appId);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate(){
        packetService.removeProcessor(packetProcessor);
        packetService.cancelPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(),
                                    PacketPriority.REACTIVE,appId);
        //delete intents
        intentMap.values().forEach(intent -> {
            intentService.withdraw(intent);
            intentService.purge(intent);
        });
        intentMap.clear();
        log.info("Stopped");
    }




    @Override
    public Set<Path> getLoadBalancePaths(ElementId src, ElementId dst) {

        return routing.getLoadBalancePaths(src, dst);
    }


    /**
     *
     * @param topo
     * @param src
     * @param dst
     * @return
     */
    @Override
    public Set<Path> getLoadBalancePaths(Topology topo, ElementId src, ElementId dst) {

        return routing.getLoadBalancePaths(topo,src,dst);
    }

    private class InternalPacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {

            if (context.isHandled()) {
                return;
            }

            Ethernet pkt = context.inPacket().parsed();
            if (pkt.getEtherType() == Ethernet.TYPE_IPV4) {

                HostId srcHostId = HostId.hostId(pkt.getSourceMAC());
                HostId dstHostId = HostId.hostId(pkt.getDestinationMAC());

                Set<Path> paths = getLoadBalancePaths(srcHostId, dstHostId);
                if (paths.isEmpty()) {
                    log.warn("paths is Empty !!! no Path is available");
                    context.block();
                    return;
                }

                IPv4 ipPkt = (IPv4) pkt.getPayload();
                TrafficSelector selector = DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPSrc(IpPrefix.valueOf(ipPkt.getSourceAddress(), 32))
                        .matchIPDst(IpPrefix.valueOf(ipPkt.getDestinationAddress(), 32))
                        .build();

                boolean isContain;
//                synchronized (intentMap) {
                isContain = intentMap.containsKey(selector.criteria());
//                }
                if (isContain) {
                    context.block();
                    return;
                }


                Path result = paths.iterator().next();
                log.info("\n------ Mao Path Info ------\nSrc:{}, Dst:{}\n{}",
                         IpPrefix.valueOf(ipPkt.getSourceAddress(), 32).toString(),
                         IpPrefix.valueOf(ipPkt.getDestinationAddress(), 32),
                         result.links().toString().replace("Default", "\n"));

                PathIntent pathIntent = PathIntent.builder()
                        .path(result)
                        .appId(appId)
                        .priority(65432)
                        .selector(selector)
                        .treatment(DefaultTrafficTreatment.emptyTreatment())
                        .build();

                intentService.submit(pathIntent);

//                synchronized (intentMap) {
                intentMap.put(selector.criteria(), pathIntent);
//                }

                context.block();
            }
        }
    }


    /**
     * Load_Balance module
     */
    private class LoadBalanceRouting {

        private final ProviderId PID = new ProviderId("bupt","xyz",true);
        private final BandwidthLinkWeight bandwidthLinkWeightTool = new BandwidthLinkWeight();


        public Set<Path> getLoadBalancePaths(ElementId src, ElementId dst) {
            Topology topology  = topologyService.currentTopology();
            return getLoadBalancePaths(topology,src,dst);
        }

        public Set<Path> getLoadBalancePaths(Topology topo, ElementId src, ElementId dst) {

            if(src instanceof DeviceId && dst instanceof DeviceId) {
                Set<List<TopologyEdge>> allRoutes = findAllRoutes(topo,(ElementId) src,(ElementId) dst);

                Set<Path> allPaths = calculateRoutesCost(allRoutes);

                Path linkPath = selectOne(allPaths);

                //use Set to be compatible with ONOS API
                return linkPath != null ? ImmutableSet.of(linkPath) : ImmutableSet.of();
            } else if(src instanceof HostId && dst instanceof HostId){
                return ImmutableSet.of();
            } else {
                return ImmutableSet.of();
            }
        }


        // -------------------calculate path cost----------------------------//

        private Set<Path> calculateRoutesCost(Set<List<TopologyEdge>> allRoutes) {
            Set<Path> paths = new HashSet<>();
            allRoutes.forEach(route -> {
                double cost = maxLinkWeight(route);
                paths.add(parseEdgeToPath(route,cost));
            });
            return paths;
        }

        //route contains link, src ,dst
        private Path parseEdgeToPath(List<TopologyEdge> routes, double cost) {
            ArrayList<Link> links = new ArrayList<>();
            routes.forEach(route -> links.add(route.link()));
            return new DefaultPath(PID,links,ScalarWeight.toWeight(cost));
        }

        //calculate edges cost
        private double maxLinkWeight(List<TopologyEdge> edges) {
            Weight weight = ScalarWeight.toWeight(0);
            for(TopologyEdge edge : edges) {
                Weight linkWeight = bandwidthLinkWeightTool.weight(edge);
                weight = linkWeight.compareTo(weight) > 0 ? linkWeight:weight;
            }
            return ((ScalarWeight) weight).value();
        }

        // -------------------select one path----------------------------//
        private Path selectOne(Set<Path> allPaths) {
            if(allPaths.size() < 1)
                return null;
            return getMinHopPath(getMinCostPath(new ArrayList(allPaths)));
        }

        private Path getMinHopPath(List<Path> minCostPaths) {
            Path result = minCostPaths.get(0);
            for(int i=1,pathCount=minCostPaths.size();i<pathCount;i++){
                Path temp = minCostPaths.get(i);
                result = result.links().size() < temp.links().size() ? result:temp;
            }
            return result;
        }

        private List<Path> getMinCostPath(List<Path> paths) {
            final double measureTolerance = 0.05;
            //Sort by Cost in order
            paths.sort((p1, p2) -> p1.cost() > p2.cost() ? 1 : (p1.cost() < p2.cost() ? -1 : 0));

            //paths.sort(((p1, p2) -> ((ScalarWeight) p1).value() > ((ScalarWeight) p2).value() ? 1:
             //       ((ScalarWeight) p1).value() < ((ScalarWeight) p2).value() ? -1:0 ));

            // get paths with similar lowest cost within measureTolerance range.
            List<Path> minCostPaths = new ArrayList<>();
            Path result = paths.get(0);
            minCostPaths.add(result);
            for (int i = 1, pathCount = paths.size(); i < pathCount; i++) {
                Path temp = paths.get(i);
                if (temp.cost() - result.cost() < measureTolerance) {
                    minCostPaths.add(temp);
                }
            }
            return minCostPaths;
        }

        /**
         *
         * @param topo
         * @param src
         * @param dst
         * @return
         */
        private Set<List<TopologyEdge>> findAllRoutes(Topology topo, ElementId src, ElementId dst) {

            if(!(topo instanceof DefaultTopology)) {
                log.error("topology is not the object of DefaultTopology.");
                // ImmutableSet.of = EMPTY;
                //static final RegularImmutableSet<Object> EMPTY = new RegularImmutableSet(new Object[0], 0, (Object[])null, 0);
                return ImmutableSet.of();
            }
            Set<List<TopologyEdge>> graphResult = new HashSet<>();
            dfsFindAllRoutes(new DefaultTopologyVertex((DeviceId) src), new DefaultTopologyVertex((DeviceId) dst),
                             new ArrayList<>(), new ArrayList<>(),
                             ((DefaultTopology) topo).getGraph(), graphResult);
            return graphResult;
        }

        /**
         * Get all possible path between Src and Dst using DFS, by Mao.
         * DFS Core, Recursion Part.
         * @param src       source node  every recursion
         * @param dst       dst node
         * @param passedLink link arraylist
         * @param passedDevice device list
         * @param graph
         * @param graphResult set of all paths
         */
        private void dfsFindAllRoutes(TopologyVertex src,TopologyVertex dst,
                                      List<TopologyEdge> passedLink, List<TopologyVertex> passedDevice,
                                      TopologyGraph graph, Set<List<TopologyEdge>> graphResult) {
            if(src.equals(dst)){
                return;
            }
            passedDevice.add(src);
            //get src's edge link(links set)
            Set<TopologyEdge> egressSrc = graph.getEdgesFrom(src);
            egressSrc.forEach(egress -> {
                TopologyVertex vertexDst = egress.dst();
                if(vertexDst.equals(dst)){
                    //Gain a Path
                    passedLink.add(egress);
                    graphResult.add(ImmutableList.copyOf(passedLink.iterator()));
                    passedLink.remove(egress);
                }else if(!passedDevice.contains(vertexDst)){
                    //DFS into
                    passedLink.add(egress);
                    dfsFindAllRoutes(vertexDst, dst, passedLink, passedDevice, graph, graphResult);
                    passedLink.remove(egress);
                }else {

                }
            });
            passedDevice.remove(src);

        }

    }


    /**
     * calculate each link weight
     */
    private class BandwidthLinkWeight extends MetricLinkWeight {
        private static final double LINK_WEIGHT_IDLE = 0;
        private static final double LINK_WEIGHT_DOWN = 100.0;
        private static final double LINK_WEIGHT_FULL = 100.0;

        /**
         * init link weight
         * @return
         */
        @Override
        public Weight getInitialWeight() {
            return ScalarWeight.toWeight(LINK_WEIGHT_IDLE);
        }

        @Override
        public Weight getNonViableWeight() {
            return ScalarWeight.toWeight(LINK_WEIGHT_DOWN);
        }

        @Override
        public Weight weight(TopologyEdge edge) {
            if(edge.link().state() == Link.State.INACTIVE){
                return ScalarWeight.toWeight(LINK_WEIGHT_DOWN);
            }
            long linkWriteSpeed = getLinkWriteSpeed(edge.link());

            long interLinkRestSpeed = linkWriteSpeed - getLinkLoadSpeed(edge.link());

            if(interLinkRestSpeed <= 0){
                return ScalarWeight.toWeight(LINK_WEIGHT_FULL);
            }
            return ScalarWeight.toWeight(100 - interLinkRestSpeed / linkWriteSpeed * 100);
        }

        private long getLinkWriteSpeed(Link link) {
            long srcSpeed = getPortWriteSpeed(link.src());
            long dstSpeed = getPortWriteSpeed(link.dst());
            return Math.min(srcSpeed,dstSpeed);
        }

        private long getLinkLoadSpeed(Link link) {
            long srcSpeed = getPortLoadSpeed(link.src());
            long dstSpeed = getPortLoadSpeed(link.dst());
            return Math.max(srcSpeed,dstSpeed);
        }

        /**
         * unit:bps
         * @param port
         * @return
         */
        private long getPortLoadSpeed(ConnectPoint port) {
            return portStatisticsService.load(port).rate() * 8;
        }

        /**
         *unit: Mbps
         * @param port
         * @return
         */
        private long getPortWriteSpeed(ConnectPoint port) {
            assert port.elementId() instanceof DeviceId;
            return deviceService.getPort(port.deviceId(),port.port()).portSpeed() * 1000000;
        }


    }
}
