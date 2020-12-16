
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

package com.xyz.path.optical.intf;

import org.onosproject.net.Device;
import org.onosproject.net.ElementId;
import org.onosproject.net.Path;
import org.onosproject.net.topology.Topology;

import java.util.Set;

/**
 * create By ONOS_LoadBalance
 * @author xyz
 * @Data 12/14/20
 */
public interface XyzRoutingService {
    Set<Path> getLoadBalancePaths(ElementId src , ElementId dst);

    Set<Path> getLoadBalancePaths(Topology topo, ElementId src, ElementId dst);
}
