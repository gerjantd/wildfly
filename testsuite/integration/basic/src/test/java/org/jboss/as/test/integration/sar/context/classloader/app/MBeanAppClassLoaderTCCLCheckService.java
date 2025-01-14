/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.sar.context.classloader.app;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Properties;

public class MBeanAppClassLoaderTCCLCheckService implements MBeanAppClassLoaderTCCLCheckServiceMBean {

    public static final String APP_CL = "application-loader";
    public static final String CONSTRUCTOR_TCCL = "constructor-tccl";
    public static final String ATTR_WRITE_TCCL = "attr-write-tccl";
    public static final String ATTR_READ_TCCL = "attr-read-tccl";
    public static final String INVOKE_TCCL = "invoke-tccl";
    public static final String CREATE_TCCL = "create-tccl";
    public static final String START_TCCL = "start-tccl";
    public static final String STOP_TCCL = "stop-tccl";
    public static final String DESTROY_TCCL = "destroy-tccl";

    private final Properties properties = new Properties();
    private volatile Path file;

    public MBeanAppClassLoaderTCCLCheckService() {
        properties.put(APP_CL, String.valueOf(System.identityHashCode(this.getClass().getClassLoader())));
        storeTccl(CONSTRUCTOR_TCCL);
    }

    @Override
    public void create() throws Exception {
        storeTccl(CREATE_TCCL);
    }

    @Override
    public void start() throws Exception {
        storeTccl(START_TCCL);
    }

    @Override
    public void stop() {
        storeTccl(STOP_TCCL);
    }

    @Override
    public void destroy() {
        storeTccl(DESTROY_TCCL);
    }

    @Override
    public void setFile(String path) throws Exception {
        System.out.println("setFile " + path);
        storeTccl(ATTR_WRITE_TCCL);
        if (file == null) {
            file = Path.of(path);
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file.toFile()))) {
                properties.store(out, "Test");
            }
        } else {
            throw new IllegalStateException("Only called once");
        }
    }

    @Override
    public String getFile() {
        storeTccl(ATTR_READ_TCCL);
        return null;
    }

    @Override
    public void method() {
        storeTccl(INVOKE_TCCL);
    }

    private void storeTccl(String id) {
        System.out.println("storeTccl " + id);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        String clId = String.valueOf(System.identityHashCode(cl));
        if (file == null) {
            properties.put(id, clId);
        } else {
            try {
                String s = id + "=" + clId + "\n";
                AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                    Files.write(file, s.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
                    return null;
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
