package com.leap.tradeapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leap.tradeapi.support.TestTokens;

import jakarta.servlet.FilterChain;

class JwtAuthFilterTest {

    private final JwtService jwtService = new JwtService(TestTokens.SECRET, TestTokens.ISSUER);
    private CallerContext caller;
    private JwtAuthFilter filter;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        caller = new CallerContext();
        filter = new JwtAuthFilter(jwtService, caller, new ObjectMapper());
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    private MockHttpServletRequest apiRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/1");
        request.setRequestURI("/api/v1/accounts/1");
        return request;
    }

    @Test
    void a_valid_token_populates_the_caller_and_continues_the_chain() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.validFor(1L)));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(caller.getAccountId()).isEqualTo(1L);
    }

    @Test
    void a_missing_header_is_401_with_the_error_envelope_and_no_chain() throws Exception {
        filter.doFilter(apiRequest(), response, chain);

        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("\"errorCode\":\"AUTH-401\"")
                .contains("\"message\":\"Unauthorised\"");
    }

    @Test
    void a_non_bearer_scheme_is_401() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void an_expired_token_is_401_with_the_same_body_as_a_forged_one() throws Exception {
        MockHttpServletRequest expiredReq = apiRequest();
        expiredReq.addHeader(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.expiredFor(1L)));
        MockHttpServletResponse expiredRes = new MockHttpServletResponse();
        filter.doFilter(expiredReq, expiredRes, chain);

        MockHttpServletRequest forgedReq = apiRequest();
        forgedReq.addHeader(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.forgedSignatureFor(1L)));
        MockHttpServletResponse forgedRes = new MockHttpServletResponse();
        filter.doFilter(forgedReq, forgedRes, chain);

        assertThat(expiredRes.getStatus()).isEqualTo(401);
        assertThat(forgedRes.getStatus()).isEqualTo(401);
        assertThat(expiredRes.getContentAsString()).isEqualTo(forgedRes.getContentAsString());
    }

    @Test
    void routes_outside_the_api_prefix_are_not_filtered() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRequestURI("/actuator/health");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}