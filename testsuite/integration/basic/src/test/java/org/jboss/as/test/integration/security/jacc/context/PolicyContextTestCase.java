/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.integration.security.jacc.context;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.jboss.as.test.shared.PermissionUtils.createPermissionsXmlAsset;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.security.SecurityPermission;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
@RunAsClient
public class PolicyContextTestCase {

    private static Logger LOGGER = Logger.getLogger(PolicyContextTestCase.class);

    @Deployment(name = "ear")
    public static EnterpriseArchive createDeployment() {
        final String earName = "ear-jacc-context";

        final EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, earName + ".ear");
        final JavaArchive jar = createJar(earName);
        final WebArchive war = createWar(earName);
        ear.addAsModule(war);
        ear.addAsModule(jar);

        ear.addAsManifestResource(createPermissionsXmlAsset(new SecurityPermission("setPolicy")), "permissions.xml");

        return ear;
    }

    @Test
    public void testHttpServletRequestFromPolicyContext(@ArquillianResource URL webAppURL) throws Exception {
        String externalFormURL = webAppURL.toExternalForm();
        String servletURL = externalFormURL.substring(0, externalFormURL.length() - 1) + PolicyContextTestServlet.SERVLET_PATH;
        LOGGER.trace("Testing Jakarta Authorization Context: " + servletURL);

        String response = HttpRequest.get(servletURL, 1000, SECONDS);
        assertTrue(response.contains("HttpServletRequest successfully obtained from both containers."));
    }

    private static JavaArchive createJar(final String jarName) {
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, jarName + ".jar");
        jar.addClasses(PolicyContextTestBean.class);
        jar.addAsManifestResource(PolicyContextTestCase.class.getPackage(), "ejb-jar.xml", "ejb-jar.xml");
        return jar;
    }

    private static WebArchive createWar(final String warName) {
        final WebArchive war = ShrinkWrap.create(WebArchive.class, warName + ".war");
        war.addClass(PolicyContextTestServlet.class);
        return war;
    }

}
