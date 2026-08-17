package com.billdesk.pg.payments.simulator.dto;

import java.util.Map;
import org.springframework.http.HttpMethod;

/**
 * What to send the browser back to on the callback/return leg, and how to fire the matching S2S
 * call to the same URL (design doc §2/§3.1's "how each bank redirects back" — for HRE, a 302 to
 * the captured DynamicUrl with these as query params, per HRE.md §2's sequence diagram; open
 * question §10.3 flags this transport as worth confirming against the real api-side handler
 * before treating it as final). The HTTP method for the S2S leg is bank-declared here rather than
 * hardcoded in the generic delivery code, so a bank whose S2S call isn't a plain GET can say so
 * without any change outside its own plugin.
 */
public class CallbackDelivery {

    private final String targetUrl;
    private final Map<String, String> queryParams;
    private final Map<String, String> formFields;
    private final HttpMethod s2sMethod;

    public CallbackDelivery(
            String targetUrl,
            Map<String, String> queryParams) {

        this(
                targetUrl,
                queryParams,
                Map.of(),
                HttpMethod.GET
        );
    }

    public CallbackDelivery(
            String targetUrl,
            Map<String, String> queryParams,
            Map<String, String> formFields,
            HttpMethod s2sMethod) {

        this.targetUrl = targetUrl;
        this.queryParams = queryParams;
        this.formFields = formFields;
        this.s2sMethod = s2sMethod;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public Map<String, String> getFormFields() {
        return formFields;
    }

    public HttpMethod getS2sMethod() {
        return s2sMethod;
    }
}
