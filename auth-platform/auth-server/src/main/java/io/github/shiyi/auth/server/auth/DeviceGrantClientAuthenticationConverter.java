package io.github.shiyi.auth.server.auth;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

/**
 * Client authentication converter for PUBLIC clients on the device flow.
 *
 * <p>Gap in Spring Authorization Server 7.1.2: none of the built-in
 * converters authenticates an unattended PUBLIC client on
 * {@code /oauth2/device_authorization} (the endpoint that mints device
 * codes), and {@code PublicClientAuthenticationConverter} only matches PKCE
 * {@code grant_type=authorization_code} token requests. Without it the
 * device authorization request dies with {@code invalid_client} before
 * {@code PublicClientAuthenticationProvider} gets a chance.</p>
 *
 * <p>This converter covers both spots:</p>
 * <ol>
 *   <li>{@code POST /oauth2/device_authorization} with a {@code client_id}
 *       and no client credentials — the device itself, headless.</li>
 *   <li>Token requests with {@code grant_type=device_code} carrying a
 *       {@code client_id} but no client credentials — the device polling
 *       for its token after the user approved.</li>
 * </ol>
 *
 * <p>In both cases it builds a {@code ClientAuthenticationMethod.NONE}
 * authentication token; {@code PublicClientAuthenticationProvider} then
 * verifies the client exists and is registered as public. Confidential
 * clients (Basic header or client_secret) are left to the default
 * converters.</p>
 */
public final class DeviceGrantClientAuthenticationConverter implements AuthenticationConverter {

    private static final String GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code";

    /** SAS default device authorization endpoint path. */
    private static final String DEVICE_AUTHORIZATION_ENDPOINT = "/oauth2/device_authorization";

    @Override
    public Authentication convert(HttpServletRequest request) {
        boolean deviceAuthorizationRequest = HttpMethod.POST.matches(request.getMethod())
                && request.getRequestURI().endsWith(DEVICE_AUTHORIZATION_ENDPOINT);
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        boolean deviceTokenRequest = GRANT_TYPE_DEVICE_CODE.equals(grantType);
        if (!deviceAuthorizationRequest && !deviceTokenRequest) {
            return null;
        }

        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (!StringUtils.hasText(clientId)) {
            // No client identity at all — let the default converters produce
            // the canonical invalid_client error downstream.
            return null;
        }

        // Confidential client authentication attempts are not ours to handle.
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.toLowerCase().startsWith("basic ")) {
            return null;
        }
        if (StringUtils.hasText(request.getParameter(OAuth2ParameterNames.CLIENT_SECRET))) {
            return null;
        }

        Map<String, Object> additionalParameters = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (!OAuth2ParameterNames.CLIENT_ID.equals(key)
                    && !OAuth2ParameterNames.GRANT_TYPE.equals(key)
                    && values != null && values.length > 0) {
                additionalParameters.put(key, values[0]);
            }
        });

        return new OAuth2ClientAuthenticationToken(
                clientId, ClientAuthenticationMethod.NONE, clientId, additionalParameters);
    }
}
