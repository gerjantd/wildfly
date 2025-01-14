/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020, Red Hat, Inc., and individual contributors
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
package org.wildfly.extension.microprofile.faulttolerance.tck;

import static org.jboss.as.test.shared.PermissionUtils.createPermissionsXmlAsset;

import java.lang.reflect.ReflectPermission;
import java.util.PropertyPermission;

import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.container.ClassContainer;
import org.jboss.shrinkwrap.api.container.LibraryContainer;
import org.jboss.shrinkwrap.api.container.ManifestContainer;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

/**
 * Adapted from SmallRye Fault Tolerance project.
 *
 * @author Radoslav Husar
 */
public class FaultToleranceApplicationArchiveProcessor implements ApplicationArchiveProcessor {

    @Override
    public void process(Archive<?> applicationArchive, TestClass testClass) {
        if (!(applicationArchive instanceof ClassContainer)) {
            return;
        }
        ClassContainer<?> classContainer = (ClassContainer<?>) applicationArchive;

        if (applicationArchive instanceof LibraryContainer) {
            JavaArchive additionalBeanArchive = ShrinkWrap.create(JavaArchive.class);
            additionalBeanArchive.addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
            ((LibraryContainer<?>) applicationArchive).addAsLibrary(additionalBeanArchive);
        } else {
            classContainer.addAsResource(EmptyAsset.INSTANCE, "META-INF/beans.xml");
        }

        if (!applicationArchive.contains("META-INF/beans.xml")) {
            applicationArchive.add(EmptyAsset.INSTANCE, "META-INF/beans.xml");
        }

        // Run the TCK with security manager
        if (applicationArchive instanceof ManifestContainer) {
            ManifestContainer<?> mc = (ManifestContainer<?>) applicationArchive;
            mc.addAsManifestResource(createPermissionsXmlAsset(
                    // Permissions required by test instrumentation - arquillian-core.jar and arquillian-testng.jar
                    new ReflectPermission("suppressAccessChecks"),
                    new PropertyPermission("*", "read"),
                    new RuntimePermission("accessDeclaredMembers"),
                    // Permissions required by test instrumentation - awaitility.jar
                    new RuntimePermission("setDefaultUncaughtExceptionHandler"),
                    new RuntimePermission("modifyThread")
            ), "permissions.xml");
        }
    }

}
