/*
   Copyright 2022 WeAreFrank!

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.nn.adapterframework.http.authentication;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.auth.Credentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;

import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.ClientCredentialsGrant;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResourceOwnerPasswordCredentialsGrant;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.token.AccessToken;

import nl.nn.adapterframework.http.HttpSenderBase;
import nl.nn.adapterframework.task.TimeoutGuard;
import nl.nn.adapterframework.util.CredentialFactory;
import nl.nn.adapterframework.util.Misc;
import nl.nn.adapterframework.util.StreamUtil;

public class OAuthAccessTokenManager {

	private String tokenEndpoint;
	private Scope scope;
	private CredentialFactory client_cf;
	private boolean useClientCredentialsGrant;
	private HttpSenderBase httpSender;
	
	private AccessToken accessToken;
	private long accessTokenRefreshTime;

	public OAuthAccessTokenManager(String tokenEndpoint, String scope, CredentialFactory client_cf, boolean useClientCredentialsGrant, HttpSenderBase httpSender) {
		this.tokenEndpoint = tokenEndpoint;
		this.scope = StringUtils.isNotEmpty(scope) ? Scope.parse(scope) : null;
		this.client_cf = client_cf;
		this.useClientCredentialsGrant = useClientCredentialsGrant;
		this.httpSender = httpSender;
	}


	public synchronized void retrieveAccessToken(Credentials credentials) throws HttpAuthenticationException {
		TokenRequest request = createRequest(credentials);
		HttpRequestBase apacheHttpRequest = convertToApacheHttpRequest(request.toHTTPRequest());

		CloseableHttpClient apacheHttpClient = httpSender.getHttpClient();
		TimeoutGuard tg = new TimeoutGuard(1+httpSender.getTimeout()/1000,"token retrieval") {

			@Override
			protected void abort() {
				apacheHttpRequest.abort();
			}

		};
		try (CloseableHttpResponse apacheHttpResponse = apacheHttpClient.execute(apacheHttpRequest)) {
			String responseBody = StreamUtil.streamToString(apacheHttpResponse.getEntity().getContent(), null, null);
			HTTPResponse httpResponse = convertFromApacheHttpResponse(apacheHttpResponse, responseBody);
			parseResponse(httpResponse, responseBody);
		} catch (IOException e) {
			apacheHttpRequest.abort();
			throw new HttpAuthenticationException(e);
		} finally {
			if (tg.cancel()) {
				throw new HttpAuthenticationException("timeout of ["+httpSender.getTimeout()+"] ms exceeded");
			}
		}
	}

	private TokenRequest createRequest(Credentials credentials) throws HttpAuthenticationException {
		AuthorizationGrant grant;

		if (useClientCredentialsGrant) {
			grant = new ClientCredentialsGrant();
		} else {
			String username = credentials.getUserPrincipal().getName();
			Secret password = new Secret(credentials.getPassword());
			grant = new ResourceOwnerPasswordCredentialsGrant(username, password);
		}

		// The credentials to authenticate the client at the token endpoint
		ClientID clientID = new ClientID(client_cf.getUsername());
		Secret clientSecret = new Secret(client_cf.getPassword());
		ClientAuthentication clientAuth = new ClientSecretBasic(clientID, clientSecret);

		try {
			URI _tokenEndpoint = new URI(tokenEndpoint);
			return new TokenRequest(_tokenEndpoint, clientAuth, grant, scope);
		} catch (URISyntaxException e) {
			throw new HttpAuthenticationException("illegal token endpoint", e);
		}
	}
	
	private void parseResponse(HTTPResponse httpResponse, String responseBody) throws HttpAuthenticationException {
		try {
			TokenResponse response = TokenResponse.parse(httpResponse);
			if (! response.indicatesSuccess()) {
				// We got an error response...
				TokenErrorResponse errorResponse = response.toErrorResponse();
				throw new HttpAuthenticationException(errorResponse.toJSONObject().toString());
			}

			AccessTokenResponse successResponse = response.toSuccessResponse();
			
			// Get the access token
			accessToken = successResponse.getTokens().getAccessToken();
			// accessToken will be refreshed when it is half way expiration
			accessTokenRefreshTime = System.currentTimeMillis() + 500 * accessToken.getLifetime();
		} catch (ParseException e) {
			throw new HttpAuthenticationException("Could not parse TokenResponse: "+responseBody, e);
		}
	}
	
	// convert the Nimbus HTTPRequest into an Apache HttpClient HttpRequest
	private HttpRequestBase convertToApacheHttpRequest(HTTPRequest httpRequest) throws HttpAuthenticationException {
		HttpRequestBase apacheHttpRequest;
		switch (httpRequest.getMethod()) {
			case GET:
				String url = Misc.concatStrings(httpRequest.getURL().toExternalForm(), "?", httpRequest.getQuery());
				apacheHttpRequest = new HttpGet(url);
				break;
			case POST:
				apacheHttpRequest = new HttpPost(httpRequest.getURL().toExternalForm());
			try {
				((HttpPost)apacheHttpRequest).setEntity(new StringEntity(httpRequest.getQuery()));
			} catch (UnsupportedEncodingException e) {
				throw new HttpAuthenticationException("Could not create TokenRequest", e);
			}
				break;
			default:
				throw new IllegalStateException("Illegal Method, must be GET or POST");
		}
		httpRequest.getHeaderMap().forEach((k,l) -> l.forEach(v -> apacheHttpRequest.addHeader(k, v)));
		return apacheHttpRequest;
	}

	private HTTPResponse convertFromApacheHttpResponse(CloseableHttpResponse apacheHttpResponse, String responseBody) throws HttpAuthenticationException {
		StatusLine statusLine = apacheHttpResponse.getStatusLine();

		if (statusLine.getStatusCode()!=200) {
			throw new HttpAuthenticationException("Could not retrieve token: ("+statusLine.getStatusCode()+") "+statusLine.getReasonPhrase()+": "+responseBody);
		}

		HTTPResponse httpResponse = new HTTPResponse(statusLine.getStatusCode());
		httpResponse.setStatusMessage(statusLine.getReasonPhrase());
		for(Header header:apacheHttpResponse.getAllHeaders()) {
			httpResponse.setHeader(header.getName(), header.getValue());
		}
		httpResponse.setContent(responseBody);
		return httpResponse;
	}
	
	
	public String getAccessToken(Credentials credentials) throws HttpAuthenticationException {
		if (accessToken==null || System.currentTimeMillis() > accessTokenRefreshTime) {
			// retrieve a fresh token if there is none, or it needs to be refreshed
			retrieveAccessToken(credentials);
		}
		return accessToken.toAuthorizationHeader();
	}
}