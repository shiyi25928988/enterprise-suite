package io.github.shiyi.auth.server.auth;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Public-client authentication for the DEVICE flow, replacing SAS 7.1.2's
 * {@code PublicClientAuthenticationProvider} for {@code NONE}-method tokens.
 *
 * <p>The stock provider unconditionally delegates to
 * {@code CodeVerifierAuthenticator.authenticateRequired}, whose
 * {@code authenticate(...)} returns {@code false} for every grant other than
 * {@code authorization_code} and then throws {@code invalid_grant
 * ("code_verifier")} — making the device flow unusable for public clients on
 * 7.1.2. This provider performs the same client_id existence and
 * public-registration checks but skips the PKCE verifier step entirely
 * (device grants carry no authorization code, so there is nothing to
 * verify).</p>
 */
public final class DevicePublicClientAuthenticationProvider implements AuthenticationProvider {

    private static final OAuth2Error INVALID_CLIENT_ERROR =
            new OAuth2Error("invalid_client", "Client authentication failed", null);

    private final RegisteredClientRepository registeredClientRepository;

    public DevicePublicClientAuthenticationProvider(RegisteredClientRepository registeredClientRepository) {
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2ClientAuthenticationToken clientAuthentication = (OAuth2ClientAuthenticationToken) authentication;
        if (ClientAuthenticationMethod.NONE.equals(clientAuthentication.getClientAuthenticationMethod())) {
            String clientId = clientAuthentication.getPrincipal().toString();
            RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
            if (registeredClient == null
                    || !registeredClient.getClientAuthenticationMethods()
                            .contains(ClientAuthenticationMethod.NONE)) {
                throw new OAuth2AuthenticationException(INVALID_CLIENT_ERROR);
            }
            return new OAuth2ClientAuthenticationToken(
                    registeredClient, ClientAuthenticationMethod.NONE, null);
        }
        return null;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
