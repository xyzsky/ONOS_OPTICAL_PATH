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

package com.xyz.path.optical.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xyz.path.optical.impl.OpticalPathManager;
import com.xyz.path.optical.impl.XyzPathManager;
import com.xyz.path.optical.intf.OpticalPathService;
import com.xyz.path.optical.intf.XyzRoutingService;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.ElementId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.statistic.PortStatisticsService;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.onlab.util.Tools.readTreeFromStream;
import static org.slf4j.LoggerFactory.getLogger;

@Path("optical")
public class XyzRestResource extends AbstractWebResource {

    private Logger logger = getLogger(XyzRestResource.class);
    private OpticalPathManager opticalPathservice = get(OpticalPathManager.class);

    private LinkService linkService = get(LinkService.class);
    private PortStatisticsService portStatisticsService = get(PortStatisticsService.class);
    private DeviceService deviceService = get(DeviceService.class);

    @GET
    @Path("hello")
    @Produces(MediaType.APPLICATION_JSON)
    public Response hello() {
        ObjectNode root = mapper().createObjectNode();
        root.put("bupt",100876)
                .put("xyz",2019111626);
        ArrayNode arrayNode = root.putArray("optical_network");
        arrayNode.add(123).add("127.0.0.1").add("10.0.8.211");
        return ok(root.toString()).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("setIntent")
    public Response setIntent(@QueryParam("ingressDeviceString") String ingressDeviceString,
                                    @QueryParam("egressDeviceString") String egressDeviceString) {
        logger.info("post begin");
        ObjectNode root = mapper().createObjectNode();

//        ElementId src =
//        String intent = xyzRoutingService.getLoadBalancePaths(ingressDeviceString,egressDeviceString);
//        root.put("intent", intent);
        return ok(root).build();
    }

    /**
     *
     * @param stream input JSON
     * @return
     * @onos.rsModel OpticalPathIntent
     */
    @Path("opticalpath")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @POST
    public Response setOpticalPath(InputStream stream) {
        try {
            ObjectNode root = readTreeFromStream(mapper(),stream);
            ObjectNode ingressPoint = (ObjectNode) root.get("ingressPoint");
            JsonNode srcc = ingressPoint.get("device");
            JsonNode ingress = root.get("ingressPoint").get("device");
            JsonNode egress = root.get("egressPoint").get("device");

            //DeviceId ingresss = codec(DeviceId.class).decode((ObjectNode) ingress, this);

            DeviceId src = DeviceId.deviceId(ingress.asText());
            String intent = opticalPathservice.setOpticalPath(ingress.asText(),egress.asText());

            //ObjectNode root = mapper().createObjectNode();
            root.put("path",1);
            return ok(root).build();
        }catch (IOException ioe){
            throw new IllegalArgumentException();
        }
    }

    /**
     * Submits a new optical intent.
     * Creates and submits optical intents from the JSON request.
     *
     * @param stream input JSON
     * @return status of the request - CREATED if the JSON is correct,
     * BAD_REQUEST if the JSON is invalid
     * @onos.rsModel PrejudgeResourceForOpticalService
     */
    @Path("prejugeResource")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @POST
    public Response prejudgeResource(InputStream stream){

        ObjectNode root = mapper().createObjectNode();
        String success = "resource prejudge success!";
        root.put("result",success);
        return ok(root).build();
    }

}
