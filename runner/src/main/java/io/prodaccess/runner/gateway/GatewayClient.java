package io.prodaccess.runner.gateway;

import io.prodaccess.common.protocol.ApiDef;
import java.io.IOException;
import java.util.Map;

/**
 * The seam to the API Gateway. {@link SimpleGatewayClient} is a plain OAuth client-credentials + HTTP
 * implementation; replace it with the one built on the real Gateway SDK (auth, token refresh, signing).
 * One instance serves exactly one Client identity for the whole life of the process.
 */
public interface GatewayClient {

    record Response(int status, String contentType, String body) {}

    Response call(ApiDef api, Map<String, String> path, Map<String, String> query, Map<String, String> headers, String bodyJson)
            throws IOException, InterruptedException;
}
