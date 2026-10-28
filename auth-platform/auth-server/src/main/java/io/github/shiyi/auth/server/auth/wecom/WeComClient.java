package io.github.shiyi.auth.server.auth.wecom;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Minimal WeCom API client: access-token cache + OAuth code → userid exchange.
 * Uses the public qyapi.weixin.qq.com endpoints (see WeCom server API docs).
 */
@Component
public class WeComClient {

    private static final Logger log = LoggerFactory.getLogger(WeComClient.class);

    private final WeComProperties props;
    private final RestClient rest = RestClient.create();

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public WeComClient(WeComProperties props) {
        this.props = props;
    }

    private synchronized String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        TokenResp resp = rest.get()
                .uri("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid={c}&corpsecret={s}",
                        props.getCorpId(), props.getSecret())
                .retrieve()
                .body(TokenResp.class);
        if (resp == null || resp.access_token() == null) {
            throw new IllegalStateException("WeCom gettoken failed");
        }
        cachedToken = resp.access_token();
        // refresh 2 minutes before actual expiry
        tokenExpiry = Instant.now().plusSeconds(Math.max(60, resp.expires_in() - 120));
        log.info("WeCom access token refreshed");
        return cachedToken;
    }

    /** Exchanges a QR-login OAuth code for the WeCom userid. */
    public String useridFromCode(String code) {
        UseridResp resp = rest.get()
                .uri("https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token={t}&code={c}",
                        accessToken(), code)
                .retrieve()
                .body(UseridResp.class);
        if (resp == null || resp.userid() == null) {
            throw new IllegalStateException("WeCom code exchange failed (invalid or expired code)");
        }
        return resp.userid();
    }

    // JSON bodies (field names match the WeCom API exactly)
    record TokenResp(String access_token, Integer expires_in, Integer errcode, String errmsg) {}
    record UseridResp(String userid, Integer errcode, String errmsg) {}
}
