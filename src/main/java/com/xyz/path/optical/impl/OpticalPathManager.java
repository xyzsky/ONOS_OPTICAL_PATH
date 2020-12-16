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

import com.xyz.path.optical.intf.OpticalPathService;


import org.onlab.graph.ScalarWeight;
import org.onlab.graph.Weight;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.IpPrefix;
import org.onlab.util.Bandwidth;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;


import org.onosproject.net.Annotations;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.EncapsulationType;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.intent.Constraint;
import org.onosproject.net.intent.IntentExtensionService;

import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.PathIntent;
import org.onosproject.net.intent.constraint.BandwidthConstraint;
import org.onosproject.net.intent.constraint.EncapsulationConstraint;
import org.onosproject.net.intent.constraint.PartialFailureConstraint;
import org.onosproject.net.provider.ProviderId;

import org.onosproject.net.resource.ResourceService;
import org.onosproject.net.topology.LinkWeigher;
import org.onosproject.net.topology.MetricLinkWeight;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Strings.isNullOrEmpty;


@Component(immediate = true,service = OpticalPathManager.class)
public class OpticalPathManager implements OpticalPathService {
    private static Logger log = LoggerFactory.getLogger(OpticalPathManager.class);
    // By default, allocate 50 GHz lambdas (4 slots of 12.5 GHz) for each intent.
    private static final int SLOT_COUNT = 4;
    private static final ProviderId PROVIDER_ID = new ProviderId("opticalpath",
                                                                 "org.onosproject.net.optical.path");


    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentExtensionService intentManager;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ResourceService resourceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    private ApplicationId appId;

    @Activate
    public void activate(){
        appId = coreService.registerApplication("org.onosproject.optical.path");
        log.info("optical path service started");
    }

    @Deactivate
    public void deactivate(){
        log.info("optical path service stopped");
    }


    //set optical path
    @Override
    public String setOpticalPath(String ingressDeviceString, String egressDeviceString) {

        DeviceId srcId = DeviceId.deviceId(URI.create(ingressDeviceString));
        DeviceId dstId = DeviceId.deviceId(URI.create(egressDeviceString));
        //get optical path set
        Path path = null;
        try{
             path = getOpticalPath(srcId,dstId).iterator().next();
        }catch (Exception e){
             return " no path";
        }



        TrafficSelector selector = DefaultTrafficSelector.builder().build();
        TrafficTreatment treatment = DefaultTrafficTreatment.builder().build();



        PathIntent pathIntent = PathIntent.builder()
                .appId(appId)
                .key(null)
                .path(path)
                .selector(selector)
                .treatment(treatment)
                .build();
        intentService.submit(pathIntent);
        return pathIntent.toString();
    }

    private TrafficSelector buildTrafficSelector() {
        return null;
    }

    private TrafficTreatment buildTrafficTreatment() {
        return null;
    }

    private List<Constraint> buildConstraints() {

        return null;
    }

    private Set<Path> getOpticalPath(DeviceId srcId, DeviceId dstId) {
        //get cur topo
        Topology curTopology = topologyService.currentTopology();
        //get paths set
        Set<Path> paths = topologyService.getPaths(curTopology,srcId,dstId,weight);
        return paths;
    }

    //get LinkWeighter class
    LinkWeigher weight = new LinkWeigher() {
        @Override
        public Weight getInitialWeight() {
            return ScalarWeight.toWeight(0.0);
        }

        @Override
        public Weight getNonViableWeight() {
            return ScalarWeight.NON_VIABLE_WEIGHT;
        }

        /**
         *
         * @param edge edge to be weighed
         * @return the metric retrieved from the annotations otherwise 1
         */
        @Override
        public Weight weight(TopologyEdge edge) {

            log.debug("Link {} metric {}", edge.link(), edge.link().annotations().value("metric"));

            // Disregard inactive or non-optical links
            if (edge.link().state() == Link.State.INACTIVE) {
                return ScalarWeight.toWeight(-1);
            }
            Annotations annotations = edge.link().annotations();
            if (annotations != null &&
                    annotations.value("metric") != null && !annotations.value("metric").isEmpty()) {
                double metric = Double.parseDouble(annotations.value("metric"));
                return ScalarWeight.toWeight(metric);
            } else {
                return ScalarWeight.toWeight(1);
            }
        }
    };




}
