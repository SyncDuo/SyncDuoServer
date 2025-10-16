package com.syncduo.server.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

@Configuration
public class RestClientConfig {
    @Bean
    public RestClient rcloneRestClient(
            @Value("${syncduo.server.rclone.httpBaseUrl}") String baseUrl,
            @Value("${syncduo.server.rclone.httpUser}") String user,
            @Value("${syncduo.server.rclone.httpPassword}") String password) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(user, password);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .requestFactory(getClientHttpRequestFactory())
                .build();
    }

    @Bean
    public RestClient rslsyncRestClient(
            @Value("${syncduo.server.rslsync.httpBaseUrl}") String baseUrl,
            @Value("${syncduo.server.rslsync.user}") String user,
            @Value("${syncduo.server.rslsync.password}") String password) {
        this.disableSSLCertificateValidation();
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(user, password);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }

    /**
     * Should strictly be used only in the local environment.
     */
    private void disableSSLCertificateValidation() {
        // Create SSL context to trust all certificates
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        // Define trust managers to accept all certificates
        TrustManager[] trustManagers = new TrustManager[]{new X509TrustManager() {
            // Method to check client's trust - accepting all certificates
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
            }

            // Method to check server's trust - accepting all certificates
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
            }

            // Method to get accepted issuers - returning an empty array
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};

        // Initialize SSL context with the defined trust managers
        try {
            sslContext.init(null, trustManagers, null);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        }

        // Disable SSL verification for RestTemplate
        // Set the default SSL socket factory to use the custom SSL context
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

        // Set the default hostname verifier to allow all hostnames
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
    }

    private ClientHttpRequestFactory getClientHttpRequestFactory() {
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(15); // 15 秒超时
        clientHttpRequestFactory.setConnectionRequestTimeout(15); // 15 秒超时
        return clientHttpRequestFactory;
    }
}
