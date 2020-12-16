# ONOS_OPTICAL_PATH
dev by xyz 

## error
1. {
     "code": 500,
     "message": "com.google.common.collect.ImmutableList.copyOf(ImmutableList.java:257)"
   }
   no solution
2.  22/12/20 Error
````
            ObjectNode root = readTreeFromStream(mapper(),stream);
            ObjectNode ingressPoint = (ObjectNode) root.get("ingressPoint");
            JsonNode srcc = ingressPoint.get("device");
            JsonNode ingress = root.get("ingressPoint").get("device");
            JsonNode egress = root.get("egressPoint").get("device");

            //DeviceId ingresss = codec(DeviceId.class).decode((ObjectNode) ingress, this);

            // NOTICE: parse string type
            DeviceId src = DeviceId.deviceId(ingress.asText());
            String intent = opticalPathservice.setOpticalPath(ingress.asText(),egress.asText());
            DeviceId dstId = DeviceId.deviceId(URI.create(egressDeviceString));