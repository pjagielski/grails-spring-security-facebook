/*
 * Copyright 2011 Patrick Schmidt
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

package grails.plugins.springsecurity.facebook

import javax.servlet.http.HttpServletRequest
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.grails.plugins.springsecurity.SecurityFilterPosition
import org.springframework.beans.factory.InitializingBean

import org.springframework.security.core.AuthenticationException
import javax.servlet.http.HttpServletResponse

import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter
import org.springframework.security.core.Authentication

import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken

/**
 *
 *
 * @author Patrick Schmidt
 */
class FacebookAuthenticationProcessingFilter extends AbstractAuthenticationProcessingFilter
    implements InitializingBean {

	static log = LogFactory.getLog(FacebookAuthenticationProcessingFilter.class)

	def apiKey
	def secretKey
	def domains

	/**
	 * Check whether all required properties have been set.
	 */
	void afterPropertiesSet() {
        super.afterPropertiesSet()
		assert (apiKey && secretKey) || domains, "Either provide facebook apiKey and secretKey or set both per domain."
	}

    FacebookAuthenticationProcessingFilter() {
        super("/j_spring_security_facebook_check")
    }

    @Override
    Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        Authentication authResult = null;

        Object principal = getPreAuthenticatedPrincipal(request);
        Object credentials = getPreAuthenticatedCredentials(request);

        if (principal == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("No pre-authenticated principal found in request");
            }

            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("preAuthenticatedPrincipal = " + principal + ", trying to authenticate");
        }

        try {
            PreAuthenticatedAuthenticationToken authRequest = new PreAuthenticatedAuthenticationToken(principal, credentials);
            authRequest.setDetails(authenticationDetailsSource.buildDetails(request));
            authResult = authenticationManager.authenticate(authRequest);
            successfulAuthentication(request, response, authResult);
        } catch (AuthenticationException failed) {
            unsuccessfulAuthentication(request, response, failed);
        }
    }

    /**
	 * If facebook cookies are given extracts the current facebook user and
	 * returns his user id, otherwise null is returned. The users session will
	 * be checked by the WebAuthenticationDetailsSource populating a
	 * FacebookAuthenticationDetails which will be set as the authentication
	 * token's details and evaluated by the FacebookAuthenticationProvider,
	 * which will delegate the local user association task and return an
	 * appropriate AuthenticationToken which will be placed in the security
	 * context or throw an BadCredentialsException or similar if the user is
	 * not logged into facebook.
	 *
	 * @param request
	 * @return
	 */
	@Override
	protected def getPreAuthenticatedPrincipal(HttpServletRequest request) {
		log.debug "getting preauthenticated facebook principal"

		def apiKey = this.apiKey
		def secretKey = this.secretKey

		// evaluate domains
		if (domains) {
			def currentDomain = extractDomain(request)
			apiKey = domains[currentDomain]?.apiKey
			secretKey = domains[currentDomain]?.secretKey

		}

		// evaluate cookies
		def cookieValue = request.cookies.find { it.name == "fbs_${apiKey}" }?.value
        log.debug "cookie ${cookieValue}"
		if (!cookieValue) { return null }

		// verify cookie
		def utils = new FacebookUtils(apiKey, secretKey)
		def cookie = utils.getCookie(cookieValue)
		if (!cookie.valid) { return null }

		// store apiKey/secret key in session for later use
		request.facebook = [apiKey: apiKey, secretKey: secretKey]

		// return facebook user id
		cookie.uid
	}

	/**
	 *
	 * @param request
	 * @return
	 */
	@Override
	protected def getPreAuthenticatedCredentials(HttpServletRequest request) {
		"N/A"
	}

	/**
	 *
	 * @return
	 */
	int getOrder() {
		SecurityFilterPosition.PRE_AUTH_FILTER.order
	}

	private extractDomain(HttpServletRequest request) {
		UrlHelper.getTLD request.requestURL.toString()
	}

}