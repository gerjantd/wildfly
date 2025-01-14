/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019, Red Hat, Inc., and individual contributors
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

package org.wildfly.clustering.context;

import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

import org.jboss.threads.JBossThreadFactory;
import org.wildfly.security.ParametricPrivilegedAction;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Default {@link ThreadFactory} implementation that applies a specific context {@link ClassLoader}.
 * @author Paul Ferraro
 */
public class DefaultThreadFactory extends ContextualThreadFactory<ClassLoader> {

    private enum ThreadPoolFactory implements ParametricPrivilegedAction<ThreadFactory, Supplier<ThreadGroup>> {
        INSTANCE;

        @Override
        public ThreadFactory run(Supplier<ThreadGroup> group) {
            return new JBossThreadFactory(group.get(), Boolean.FALSE, null, "%G - %t", null, null);
        }
    }

    public DefaultThreadFactory(Class<?> targetClass) {
        this(targetClass, new Supplier<>() {
            @Override
            public ThreadGroup get() {
                return new ThreadGroup(targetClass.getSimpleName());
            }
        });
    }

    DefaultThreadFactory(Class<?> targetClass, Supplier<ThreadGroup> group) {
        this(WildFlySecurityManager.doUnchecked(group, ThreadPoolFactory.INSTANCE), targetClass);
    }

    public DefaultThreadFactory(ThreadFactory factory) {
        this(factory, factory.getClass());
    }

    private DefaultThreadFactory(ThreadFactory factory, Class<?> targetClass) {
        super(factory, WildFlySecurityManager.getClassLoaderPrivileged(targetClass), ContextClassLoaderReference.INSTANCE);
    }
}
